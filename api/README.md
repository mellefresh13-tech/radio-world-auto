# Radio World Auto API

Вспомогательный read-only FastAPI API поверх SQLite-каталога. Он остаётся в репозитории и может использоваться для диагностики, но **не является runtime-зависимостью Android-приложения**: Android получает каталог напрямую из GitHub Raw.

## Как работает каталог API

При старте API проверяет, есть ли пригодная локальная SQLite-база, затем скачивает и валидирует актуальную базу по CATALOG_DB_URL с атомарной заменой файла. Если обновление недоступно, API продолжает работу с последней пригодной базой; если такой базы нет, запуск завершается ошибкой. После успешного старта запускается периодическое обновление. По умолчанию URL указывает на data/radio.db в ветке catalog-data; интервал по умолчанию — 21600 секунд (6 часов).

Можно переопределить:
- RADIO_DB_PATH — путь к SQLite-файлу;
- CATALOG_DB_URL — адрес опубликованной базы;
- CATALOG_REFRESH_SECONDS — период обновления в секундах.

## Локальный запуск из корня репозитория

Установите зависимости из корневого requirements.txt и запустите python main.py. Корневой entrypoint добавляет api/src в Python path и поднимает radio_api.app:app на PORT (по умолчанию 8000).

## Отдельный запуск из папки api

Установите зависимости из api/requirements.txt и выполните sh start.sh. Скрипт запускает Uvicorn; загрузкой, проверкой, fallback и периодическим обновлением каталога управляет lifecycle FastAPI. Это исключает двойную загрузку на старте. Альтернативный вариант разработки — PYTHONPATH=src uvicorn radio_api.app:app --reload; API сам попытается получить каталог через CATALOG_DB_URL или использует существующую пригодную базу.

Инструкции для текущей root-level конфигурации Railway находятся в ../deploy/README.md. Если Root Directory сервиса Railway настроен на /api, используйте start.sh и согласованные переменные окружения. Не смешивайте команды двух режимов деплоя.

## Endpoints

- GET /health — состояние сервиса, число станций и online streams;
- GET /countries — страны и счётчики;
- GET /genres — жанры и счётчики;
- GET /stations?q=... — поиск по названию и полям каталога;
- GET /stations?country=DE — станции страны;
- GET /stations?genre=Rock — станции жанра;
- GET /stations/{station_id} — одна станция;
- GET /debug/metadata — диагностика live metadata потоков.

API отдаёт потоки, помеченные online в SQLite. У /stations есть limit/offset; фактический limit ограничен 200. Endpoint debug/metadata имеет ограничение на число результатов и время проверки, поэтому не использовать его как массовый crawler.

## Импорт каталога

После того как collector создал merged candidate snapshot, из папки api можно выполнить importer с входным JSON и целевым SQLite-путём. В текущем Catalog snapshot workflow импорт и проверка SQLite выполняются автоматически перед публикацией.
