# Radio World Auto

Android-приложение мирового интернет-радио для автомобилей.

## Railway

Backend подготовлен для обычного подключения GitHub-репозитория к Railway через Railpack, без Docker и GHCR.

Корневые файлы для автоматического определения Python:
- `requirements.txt` — подключает зависимости `api`;
- `main.py` — Python entrypoint;
- `.python-version` — Python 3.12;
- `start.sh` и `Procfile` — резервные варианты запуска.

Поэтому Railway может работать как:

GitHub repository -> Railpack -> Python -> FastAPI

Root Directory менять не требуется.

При старте API сразу скачивает актуальный `radio.db` из публичной ветки `catalog-data`, затем обновляет его каждые 6 часов.

## API

- `GET /health`
- `GET /countries`
- `GET /genres`
- `GET /stations?q=...`
- `GET /stations?country=DE`
- `GET /stations?genre=Rock`
- `GET /stations/{station_id}`