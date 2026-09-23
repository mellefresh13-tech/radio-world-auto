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

Override with:

```bash
RADIO_DB_PATH=/path/to/radio.db
```

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

A future production deployment will replace local SQLite with a managed database without changing the public response contract.
