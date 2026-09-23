FROM python:3.12-slim

WORKDIR /app

COPY api/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY api/src ./src
COPY data/generated/radio.db ./data/radio.db

RUN useradd --create-home --uid 10001 appuser &&     chown -R appuser:appuser /app

USER appuser

ENV PYTHONPATH=/app/src
ENV RADIO_DB_PATH=/app/data/radio.db

EXPOSE 8000

CMD ["sh", "-c", "uvicorn radio_api.app:app --host 0.0.0.0 --port ${PORT:-8000}"]
