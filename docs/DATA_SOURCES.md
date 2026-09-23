# Data sources

Каталог строится из нескольких независимых источников.

## 1. Radio Browser

API предоставляет список станций и поля name, stream URL, resolved URL, homepage, favicon, tags, country/countrycode, language, codec, bitrate, HLS flag, результаты последней проверки и геоданные.

Поддерживаются выдача всех станций и фильтрация по стране, языку, тегу, codec и другим атрибутам.

Документация: https://docs.radio-browser.info/

Роль: основной стартовый discovery-source.

## 2. IPRD

International Public Radio Directory предоставляет station metadata и список streams, включая direct URL, format, bitrate и reliability.

Документация: https://iprd-org.github.io/iprd/api/

Роль: независимый второй каталог для расширения покрытия и cross-check.

## 3. Icecast / public directories

Используем как дополнительный discovery-layer, особенно для независимых internet stations.

## 4. Official station websites

Используем для:

- станций, которых нет в каталогах;
- обновления старых stream URL;
- нахождения альтернативных потоков;
- подтверждения официального происхождения.

Ищем MP3, AAC/AAC+, OGG/Opus, HLS M3U8, PLS/M3U и ссылки на Icecast/Shoutcast.

## 5. Radio Garden (discovery only)\n\nRadio Garden exposes an internal JSON/search and live-listen API used by third-party projects. The API is undocumented and unofficial, so it is not treated as a stable production dependency. We use it only to discover additional places/stations and possible live-stream references.\n\nReference implementation/spec: https://github.com/jonasrmichel/radio-garden-openapi\n\n## 6. Search discovery

По станции и стране строятся запросы station + live stream, station + m3u, station + mp3, station + aac, station + m3u8 и локализованные варианты.

Поисковый результат — только кандидат, который должен быть проверен.

## Provenance

Каждая station/stream record хранит source provider, source id/URL и timestamps. Это позволит понимать происхождение записи и безопасно обновлять каталог.

Для технического выбора потока приоритет:

official site/direct stream > trusted public catalog > secondary directory > search candidate.

Это приоритет подтверждения источника, а не оценка качества радиостанции.
