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

## Проверка production API

Workflow .github/workflows/production-smoke.yml проверяет действующий endpoint https://radio-world-auto-production.up.railway.app:
- GET /health;
- GET /countries;
- GET /genres;
- GET /stations?limit=3;
- наличие хотя бы одного URL playable stream у выборки станций.

Smoke workflow доступен вручную и по расписанию каждые 6 часов на 47-й минуте интервала. Последний просмотренный run #65 завершился успешно 11 октября 2026 года. Это подтверждает API smoke contract на момент запуска, а не зависимость Android от Railway.

## Каталог

Catalog snapshot workflow в основном репозитории автоматически публикует SQLite в catalog-data/data/radio.db. Не хранить и не коммитить вручную изменяемую production-копию базы.
