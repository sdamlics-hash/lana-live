"""LANA conversational backend prototype.

Install: pip install fastapi uvicorn openai
Environment: OPENAI_API_KEY, LANA_BACKEND_TOKEN (strong random secret)
Run: uvicorn backend.lana_api:app --host 127.0.0.1 --port 8765
Deploy behind HTTPS reverse proxy; do not expose directly to the internet.
"""
import os
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from openai import AsyncOpenAI

app = FastAPI(title="LANA conversational API", docs_url=None, redoc_url=None)

SYSTEM = ("Ты Лана, русскоязычная голосовая помощница. Отвечай на вопрос по существу, "
          "естественно и кратко. Не выдумывай действия, память или доступ к устройству. "
          "Уважай самостоятельность человека и его приватность. "
          "Если не знаешь — скажи прямо. Не называй себя человеком.")

class Turn(BaseModel):
    role: str
    content: str = Field(min_length=1, max_length=4000)

class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=4000)
    history: list[Turn] = Field(default_factory=list, max_length=12)

class ChatResponse(BaseModel):
    reply: str

@app.post("/v1/lana/chat", response_model=ChatResponse)
async def chat(req: ChatRequest, authorization: str | None = Header(default=None)):
    token = os.environ.get("LANA_BACKEND_TOKEN")
    if not token or authorization != f"Bearer {token}":
        raise HTTPException(status_code=401, detail="Unauthorized")
    if not os.environ.get("OPENAI_API_KEY"):
        raise HTTPException(status_code=503, detail="AI backend not configured")
    messages = [{"role": "system", "content": SYSTEM}]
    for item in req.history:
        if item.role in ("user", "assistant"):
            messages.append({"role": item.role, "content": item.content})
    messages.append({"role": "user", "content": req.message})
    try:
        result = await AsyncOpenAI(timeout=20.0).chat.completions.create(
            model=os.environ.get("LANA_MODEL", "gpt-4.1-mini"),
            messages=messages,
            max_tokens=350,
        )
        reply = (result.choices[0].message.content or "").strip()
        if not reply:
            raise ValueError("Empty response")
        return ChatResponse(reply=reply)
    except Exception:
        # Never expose API keys or internal upstream errors to the device.
        raise HTTPException(status_code=502, detail="AI temporarily unavailable")
