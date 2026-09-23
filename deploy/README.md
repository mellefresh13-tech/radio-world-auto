# API deployment

The API is deployed from the GitHub repository with Railway's normal source deployment flow.

## Railway

Create a service from the GitHub repository.

Set:

- **Root Directory:** `/api`
- **Start Command:** `sh start.sh`

Do not configure Docker Image or a Dockerfile for this service.

Railpack detects the Python application from `requirements.txt` and installs the dependencies automatically.

The service listens on Railway's `PORT`.

Health check:

```text
GET /health
```

After deployment, generate a public Railway domain in the service Networking settings.

## Catalog data

The catalog GitHub Actions workflow builds and validates `radio.db`, then publishes the latest database to the `catalog-data` branch.

The API downloads:

`https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/radio.db`

on startup and refreshes it every 6 hours.

Override the URL or interval with Railway variables:

```text
CATALOG_DB_URL
CATALOG_REFRESH_SECONDS
```

## Local run

```bash
cd api
python -m pip install -r requirements.txt
PYTHONPATH=src uvicorn radio_api.app:app --reload
```
