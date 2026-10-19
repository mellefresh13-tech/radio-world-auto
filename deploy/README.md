# Optional API deployment

Это инструкции для вспомогательного FastAPI/SQLite API. API не используется Android-приложением как runtime backend: установленное приложение получает каталог напрямую из GitHub Raw.

## Текущая root-level конфигурация Railway

Репозиторий: mellefresh13-tech/radio-world-auto.

Для описанного здесь режима Root Directory Railway должен быть корнем репозитория. В корне находятся requirements.txt и Procfile; Procfile запускает python main.py, а main.py добавляет api/src в Python path и запускает radio_api.app:app. Отдельный Dockerfile и ручной Build Command не требуются для этой конфигурации.

При старте API загружает SQLite из ветки catalog-data. Базовые значения:
- CATALOG_DB_URL — GitHub Raw URL на catalog-data/data/radio.db;
- RADIO_DB_PATH — data/radio.db;
- CATALOG_REFRESH_SECONDS — 21600 (6 часов).

Если конфигурация вашего сервиса Railway использует Root Directory /api, это другой режим: используйте api/start.sh и инструкции в api/README.md, не команды root-level deployment.

## Ручная проверка API

В репозитории нет активного workflow .github/workflows/production-smoke.yml и нет автоматического расписания для проверки Railway endpoint. Наличие кода API и инструкции по его развёртыванию не означает, что endpoint сейчас доступен или мониторится GitHub Actions.

Если API развёрнут, проверьте его отдельно вручную: GET /health, /countries, /genres и /stations?limit=3. Отказ API не означает отказ Android-каталога: установленное приложение использует GitHub Raw напрямую.

## Каталог

Catalog snapshot workflow в основном репозитории автоматически публикует SQLite в catalog-data/data/radio.db. Не хранить и не коммитить вручную изменяемую production-копию базы.
