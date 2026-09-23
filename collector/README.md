# Catalog collector

Python-пайплайн для построения собственного мирового каталога.

## Источники

- Radio Browser
- IPRD
- Icecast / public directories
- official station websites
- search discovery

Каждый источник должен иметь отдельный адаптер. После ingestion данные проходят normalization, deduplication и stream verification.

## Локальный запуск

```bash
python -m pip install -r requirements.txt
python -m pytest
```

Для сетевого ingestion:

```bash
python -m radio_catalog.cli --output data/generated/raw-normalized.json
```

Production-режиму ещё нужны caching, retries, rate limiting, incremental updates и отдельное хранилище raw snapshots.
