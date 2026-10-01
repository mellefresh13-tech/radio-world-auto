# Radio World Auto API

Read-only API over the canonical radio catalog.

## Local run

From the repository root:

```bash
cd api
python -m pip install -r requirements.txt
PYTHONPATH=src uvicorn radio_api.app:app --reload
```

Default database:

`data/radio.db`

For a deployed instance, the catalog database is downloaded from `CATALOG_DB_URL` on startup and refreshed periodically.

Override with:

```bash
RADIO_DB_PATH=/path/to/radio.db
CATALOG_DB_URL=https://example.com/radio.db
CATALOG_REFRESH_SECONDS=21600
```

## Railway

Connect the GitHub repository as the service source and set the service Root Directory to:

`/api`

Railpack detects Python from `requirements.txt`.

Start command:

```text
sh start.sh
```

The service listens on the Railway `PORT` and exposes:

`GET /health`

## Endpoints

- `GET /health`
- `GET /countries`
- `GET /genres`
- `GET /stations?q=...`
- `GET /stations?country=DE`
- `GET /stations?genre=Rock`
- `GET /stations/{station_id}`

The API returns only streams currently marked `online`. The app can use the returned stream order for fallback.

## Import

After the collector produces a merged candidate snapshot:

```bash
PYTHONPATH=src python -m radio_api.importer ../data/generated/canonical-candidate.json data/radio.db
```
