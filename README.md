# Radio World Auto

Android-приложение мирового интернет-радио для автомобилей.

## Текущее состояние

Актуальная рабочая версия находится в `main`.

Последняя проверенная release-сборка: commit `8413233f`.

В плеере:
- экранная кнопка «Назад» отсутствует;
- физическая кнопка Previous/Back на руле переключает на предыдущую станцию;
- Shuffle не вызывает визуального моргания плеера;
- metadata текущей станции/трека сохраняются при переключении станции.

## Android release

Release APK собирается через GitHub Actions workflow `Android Release APK`.

Рабочий процесс проекта:

`правка -> main -> GitHub Actions -> успешный release APK`

APK публикуется как artifact `radio-world-auto-release`.

## Railway

Backend подготовлен для обычного подключения GitHub-репозитория к Railway через Railpack, без Docker и GHCR.

Корневые файлы для автоматического определения Python:
- `requirements.txt` — подключает зависимости API;
- `main.py` — Python entrypoint;
- `.python-version` — Python 3.12;
- `start.sh` и `Procfile` — резервные варианты запуска.

Поэтому Railway может работать как:

`GitHub repository -> Railpack -> Python -> FastAPI`

Root Directory менять не требуется.

При старте API скачивает актуальный `radio.db` из публичной ветки `catalog-data`, затем обновляет его каждые 6 часов.

## API

- `GET /health`
- `GET /countries`
- `GET /genres`
- `GET /stations?q=...`
- `GET /stations?country=DE`
- `GET /stations?genre=Rock`
- `GET /stations/{station_id}`
