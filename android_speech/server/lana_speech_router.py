"""Optional router for the existing FastAPI server. Does not replace its chat routes."""
import hmac
import json
import os
from pathlib import Path
import sqlite3
import threading
import time
import urllib.error
import urllib.request
from fastapi import APIRouter, Depends, Header, HTTPException, Response
from pydantic import BaseModel, constr

class SpeechRequest(BaseModel):
    text: constr(strict=True, min_length=1, max_length=4096)
    class Config:
        extra = 'forbid'

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

def openai_audio(key, text):
    payload = {'model':'gpt-4o-mini-tts', 'voice':'shimmer', 'speed':1.25,
               'input':text, 'response_format':'mp3',
               'instructions':'Speak natural fluent Russian, warmly and clearly. Keep the same calm conversational voice throughout. Read only the supplied text.'}
    request = urllib.request.Request('https://api.openai.com/v1/audio/speech', method='POST',
        data=json.dumps(payload,ensure_ascii=False).encode(),
        headers={'Authorization':'Bearer '+key,'Content-Type':'application/json','Accept':'audio/mpeg'})
    opener=urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(request,timeout=40) as response:
            if response.status != 200 or response.headers.get_content_type() != 'audio/mpeg':
                raise ValueError('Invalid audio response')
            result=response.read(8_000_001)
            if not result or len(result)>8_000_000: raise ValueError('Invalid audio size')
            return result
    except (OSError,ValueError,urllib.error.URLError) as exc:
        raise RuntimeError('speech_provider_unavailable') from None

class SpeechQuota:
    def __init__(self, path):
        self.path=str(path)
        with sqlite3.connect(self.path) as con:
            con.execute('CREATE TABLE IF NOT EXISTS lana_speech_attempts (at REAL NOT NULL)')
    def claim(self, now=None):
        now=time.time() if now is None else now
        with sqlite3.connect(self.path,timeout=5,isolation_level=None) as con:
            con.execute('BEGIN IMMEDIATE')
            con.execute('DELETE FROM lana_speech_attempts WHERE at <= ?', (now-86400,))
            day,hour=con.execute('SELECT COUNT(*),COALESCE(SUM(at>?),0) FROM lana_speech_attempts',(now-3600,)).fetchone()
            if day>=100 or hour>=30:
                con.rollback();return False
            con.execute('INSERT INTO lana_speech_attempts VALUES (?)',(now,));con.commit();return True

def create_router(token=None, api_key=None, quota_path=None, transport=None):
    token=os.environ.get('LANA_BACKEND_TOKEN','') if token is None else token
    api_key=os.environ.get('OPENAI_API_KEY','') if api_key is None else api_key
    if not isinstance(token,str) or len(token)<32: raise ValueError('LANA_BACKEND_TOKEN missing')
    quota=SpeechQuota(quota_path or Path.home()/'lana_backend/lana-speech-usage.sqlite3')
    provider=transport or openai_audio
    active=threading.BoundedSemaphore(1)
    def authorize(authorization: str = Header(default='')):
        if not hmac.compare_digest(authorization.encode(),('Bearer '+token).encode()):
            raise HTTPException(401,'authentication required')
    router=APIRouter(dependencies=[Depends(authorize)])
    @router.get('/v1/lana/speech/status')
    def status():
        return {'schema':'lana.speech.v1','configured':bool(api_key),'voice':'shimmer','speed':1.25,'provider_verified':False}
    @router.post('/v1/lana/speech')
    def speech(data: SpeechRequest):
        if not data.text.strip():raise HTTPException(400,'empty text')
        if not api_key:raise HTTPException(503,'speech not configured')
        if not active.acquire(blocking=False):raise HTTPException(429,'speech already in progress')
        try:
            try:allowed=quota.claim()
            except sqlite3.Error:raise HTTPException(503,'speech quota unavailable') from None
            if not allowed:raise HTTPException(429,'speech attempt limit reached')
            try:audio=provider(api_key,data.text)
            except Exception:raise HTTPException(502,'speech unavailable') from None
            if not isinstance(audio,bytes) or not 0<len(audio)<=8_000_000:raise HTTPException(502,'invalid audio')
            return Response(audio,media_type='audio/mpeg',headers={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'})
        finally:active.release()
    return router
