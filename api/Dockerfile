FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY pyproject.toml .
COPY src ./src

RUN mkdir -p /app/data

ENV PYTHONPATH=/app/src
ENV RADIO_DB_PATH=/app/data/radio.db

EXPOSE 8000

CMD ["uvicorn", "radio_api.app:app", "--host", "0.0.0.0", "--port", "8000"]
