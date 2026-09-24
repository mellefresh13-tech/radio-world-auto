# Project status

Обновлено: 2026-09-24

## Текущий этап

Сквозной MVP-контур собран: discovery -> normalization -> verification -> SQLite -> REST API -> native Android.

Текущий этап — production deployment API через обычный GitHub source deployment в Railway.

## Railway

Репозиторий специально подготовлен для варианта «подключить GitHub и нажать Deploy»:

1. GitHub repository — `mellefresh13-tech/radio-world-auto`.
2. Root Directory — `/`.
3. Docker — не используется.
4. Build Command — не нужен.
5. Railpack определяет Python по корневому `requirements.txt`.
6. Корневой `main.py` запускает FastAPI.
7. API получает `PORT` от Railway.
8. Каталог SQLite скачивается из ветки `catalog-data` сразу при старте и обновляется каждые 6 часов.

## В работе

1. Проверка реального deployment в Railway.
2. Расширение discovery и качества каталога.
3. Android hardening и тестирование на реальных head units.
4. Monitoring, rate limits, privacy/attribution и Play Store packaging.