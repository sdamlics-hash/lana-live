"""Run: pip install pytest httpx fastapi openai; pytest backend/test_lana_api.py"""
import os
from fastapi.testclient import TestClient
from backend.lana_api import app

client = TestClient(app)


def test_rejects_unauthenticated_request(monkeypatch):
    monkeypatch.setenv("LANA_BACKEND_TOKEN", "test-secret")
    response = client.post("/v1/lana/chat", json={"message": "Привет"})
    assert response.status_code == 401


def test_rejects_wrong_token(monkeypatch):
    monkeypatch.setenv("LANA_BACKEND_TOKEN", "test-secret")
    response = client.post("/v1/lana/chat", headers={"Authorization": "Bearer wrong"}, json={"message": "Привет"})
    assert response.status_code == 401


def test_reports_unconfigured_backend(monkeypatch):
    monkeypatch.setenv("LANA_BACKEND_TOKEN", "test-secret")
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    response = client.post("/v1/lana/chat", headers={"Authorization": "Bearer test-secret"}, json={"message": "Привет"})
    assert response.status_code == 503


def test_rejects_blank_message(monkeypatch):
    monkeypatch.setenv("LANA_BACKEND_TOKEN", "test-secret")
    response = client.post("/v1/lana/chat", headers={"Authorization": "Bearer test-secret"}, json={"message": ""})
    assert response.status_code == 422


def test_rejects_excessive_history(monkeypatch):
    monkeypatch.setenv("LANA_BACKEND_TOKEN", "test-secret")
    response = client.post("/v1/lana/chat", headers={"Authorization": "Bearer test-secret"}, json={"message": "Привет", "history": [{"role": "user", "content": "a"}] * 13})
    assert response.status_code == 422
