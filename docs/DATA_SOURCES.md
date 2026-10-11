# Data sources

Каталог строится из нескольких источников, но важно различать источники, которые реально вызываются текущим scheduled snapshot pipeline, и модули для дополнительного discovery.

## Активные источники опубликованного snapshot

### Radio Browser

Публичный каталог станций с названием, stream URL, homepage/favicon, tags, страной, языком, codec/bitrate, HLS-флагом и результатами проверок.

Документация: https://docs.radio-browser.info/

В текущем collector CLI используется как основной discovery-вход; запрашивается до 20 000 исходных записей с параметром hidebroken.

### IPRD

International Public Radio Directory — независимый источник station metadata и stream URL.

Документация: https://iprd-org.github.io/iprd/api/

Текущий CLI использует IPRD для расширения покрытия и cross-check, после чего записи проходят общий merge/normalization.

### Curated station list

Файл collector/src/radio_catalog/sources/curated.json содержит поддерживаемые вручную станции и aliases. Текущая сборка всегда загружает его. Курируемым станциям и их stream URL отдаётся приоритет при отборе на проверку; для важных curated records есть специальные правила защиты при фильтрации.

## Нормализация и фильтры

После ingestion записи проходят нормализацию, merge/deduplication, нормализацию жанров и проверку stream URL.

Финальная фильтрация:
- сохраняет записи с FM-признаком в названии, aliases или tags;
- разрешает curated-family исключения;
- удаляет неактивные не-curated записи;
- ограничивает публикацию до 500 станций на страну;
- при распределении лимита страны отдаёт приоритет прямым curated records.

Stream verification в текущем scheduled workflow использует до 32 workers, timeout 6 секунд, до 6 000 обычных уникальных URL, плюс curated URL поверх лимита. Статус может устареть между проверкой и фактическим запуском радио.

## Provenance

Каждая station/stream record сохраняет источники. Source provenance помогает понять, откуда пришла запись, и безопаснее обновлять сведения. Приоритет источника используется для доверия к происхождению, а не как рейтинг качества радиостанции.
