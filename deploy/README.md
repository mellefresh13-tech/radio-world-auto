# API deployment

Backend использует обычный Railway source deployment из GitHub.

## Railway

Подключить репозиторий:

`mellefresh13-tech/radio-world-auto`

Использовать корень репозитория. Docker Image и Dockerfile не нужны.

Railpack видит корневой `requirements.txt`, определяет Python, а `main.py` запускает FastAPI на Railway `PORT`.

Build Command вручную задавать не нужно.

После deployment сгенерировать Public Domain в Networking.

## Catalog data

GitHub Actions собирает SQLite и публикует актуальную базу в ветку `catalog-data`.

API скачивает:

`https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/radio.db`

Скачивание происходит сразу при старте, затем раз в 6 часов.