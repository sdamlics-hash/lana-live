FROM python:3.11-slim
WORKDIR /srv/lana
RUN pip install --no-cache-dir fastapi==0.115.12 uvicorn==0.34.2 openai==1.75.0
COPY backend /srv/lana/backend
EXPOSE 8765
CMD ["uvicorn", "backend.lana_api:app", "--host", "0.0.0.0", "--port", "8765"]
