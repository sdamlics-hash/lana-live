"""One explicit loopback dialogue probe. Never print credentials, reply text or error bodies."""
import argparse
import json
import os
from pathlib import Path
import shlex
import urllib.error
import urllib.request

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None

def read_token(path):
    token=os.environ.get('LANA_BACKEND_TOKEN','')
    if token:return token
    for line in Path(path).read_text().splitlines():
        name,sep,value=line.strip().partition('=')
        if sep and name.removeprefix('export ').strip()=='LANA_BACKEND_TOKEN':
            parts=shlex.split(value,comments=True,posix=True)
            if len(parts)!=1:raise ValueError('Invalid token configuration')
            token=parts[0]
    return token

def probe(token,port=8765):
    if not isinstance(token,str) or len(token)<32:raise ValueError('Missing token')
    if type(port) is not int or not 1<=port<=65535:raise ValueError('Invalid port')
    body=json.dumps({'message':'Ответь одним словом: связь.','history':[]},ensure_ascii=False).encode()
    request=urllib.request.Request('http://127.0.0.1:'+str(port)+'/v1/lana/chat',data=body,method='POST',
        headers={'Authorization':'Bearer '+token,'Content-Type':'application/json; charset=utf-8','Accept':'application/json'})
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    try:
        with opener.open(request,timeout=30) as response:
            status=response.status
            raw=response.read(8193)
            if len(raw)>8192:return {'http_status':status,'dialogue_verified':False,'error':'response_too_large'}
    except urllib.error.HTTPError as exc:
        return {'http_status':exc.code,'dialogue_verified':False,'error':'http_error'}
    except (urllib.error.URLError,TimeoutError,OSError):
        return {'dialogue_verified':False,'error':'connection_or_timeout'}
    try:data=json.loads(raw)
    except (ValueError,UnicodeError):return {'http_status':status,'dialogue_verified':False,'error':'invalid_json'}
    reply=data.get('reply') if isinstance(data,dict) else None
    valid=status==200 and isinstance(reply,str) and bool(reply.strip())
    return {'http_status':status,'dialogue_verified':valid,'reply_length':len(reply) if isinstance(reply,str) else 0,
            'openai_provider_verified':False,'scope':'VM loopback dialogue only; not Samsung/HTTPS/ChatGPT history'}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--send-one-question',action='store_true');args=parser.parse_args()
    if not args.send_one_question:raise SystemExit('No request sent. Use --send-one-question for exactly one potentially billable dialogue probe.')
    try:
        token=read_token(Path.home()/'lana_backend/.env')
        result=probe(token)
    except (OSError,ValueError):result={'dialogue_verified':False,'error':'token_configuration_unavailable'}
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
