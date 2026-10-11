# Architecture

## Общая схема

Источники → collector → normalization/deduplication → stream verification и FM-oriented filtering → canonical snapshot → ветка GitHub catalog-data → Android local cache → Media3/ExoPlayer → MediaSession → car HMI или системные медиаконтроллеры.

## Репозиторий и ветки

- main — единственный источник истины для Android-кода, collector, tools, tests и workflow.
- catalog-data — отдельная ветка публикации с generated data и статическими debug-файлами.

Catalog snapshot workflow переключается на orphan branch, формирует дерево публикации и делает force-push в catalog-data. Поэтому отсутствие общего предка с main ожидаемо: это не ветка разработки и её нельзя merge/rebase в main.

## Каталог и публикация

В текущем scheduled pipeline активные источники — Radio Browser, IPRD и collector/src/radio_catalog/sources/curated.json. Каталог нормализуется, дубликаты объединяются, потоки проверяются, применяются FM-признаки/курируемые исключения и лимит до 500 станций на страну. Верификатор проверяет до 6 000 обычных уникальных stream URL плюс приоритетные curated streams.

Workflow .github/workflows/catalog.yml запускается каждые 6 часов, вручную и при изменениях в файлах, влияющих на построение каталога. Он создаёт candidate JSON, импортирует SQLite, строит semantic manifest/delta и диагностические файлы, после чего публикует их в catalog-data.

Публикуемый набор:
- data/stations.json — основной полный каталог;
- data/catalog-manifest.json — semantic version, base_version, количества и статистика;
- data/catalog-delta.json — updated и removed_ids;
- data/radio.db — SQLite для вспомогательного API;
- debug/ — статическая диагностика;
- .nojekyll — служебный файл публикации GitHub Pages.

Semantic version не меняется только из-за volatile timestamps last_checked_at/discovered_at. Это предотвращает полную синхронизацию Android при каждом обновлении проверки потоков, если сами station records не изменились.

## Android catalog runtime

Android читает stations.json, manifest и delta напрямую из GitHub Raw; Railway и API не входят в runtime critical path.

- Первый запуск без cache: manifest и полный stations.json.
- При наличии cache: сравнение catalog version с manifest.
- Одинаковая версия: cache остаётся в использовании.
- Совместимая base_version: скачать и применить delta, включая updated и removed_ids.
- Неприменимая/несовместимая delta: полный snapshot как fallback.
- Сохранение — локальный gzip cache; навигация, поиск, фильтрация, Favorites/Recent и playback используют локальные данные.

При временной недоступности GitHub уже загруженный каталог продолжает работать. Без предварительно сохранённого каталога первый запуск не может получить полный список станций из сети, если GitHub недоступен.

## UI profiles

MainActivity dispatches по UiProfile:
- точный экран 1920×720 physical pixels в landscape выбирает CarMainActivity;
- остальные размеры и ориентации выбирают MobileMainActivity.

CarMainActivity и CarRadioPlaybackService изолируют automotive HMI. MobileMainActivity и RadioPlaybackService реализуют responsive phone/tablet experience. UI-профили используют Kotlin/XML Views; Compose и generated build patches не являются частью текущей реализации.

## Playback и MediaSession

Оба playback service используют Media3/ExoPlayer, MediaSessionService и CatalogNavigationPlayer. ForwardingPlayer объявляет варианты NEXT/PREV доступными и перенаправляет их в общий selector по полному локальному каталогу, даже если очередь ExoPlayer содержит только текущую станцию.

- Экранная Shuffle выбирает случайную станцию.
- NEXT/PREV внешнего HMI переключают на следующую/предыдущую доступную станцию по порядку каталога.
- Для станции можно выбрать несколько stream URL; применяется stream fallback/reconnect.
- ICY StreamTitle и ID3 Artist/Title нормализуются; внешнему HMI передаются раздельные artist/title, а при отсутствии live metadata title заменяется именем станции.
- Последняя станция сохраняется и восстанавливается при наличии в актуальном каталоге.
- onTaskRemoved останавливает playback service после swipe-away из Recents.

Точечные изменения навигации нужно проверять в обоих сервисах, поскольку только unit test selector не доказывает, что автомобильное или внешнее устройство посылает ожидаемую команду MediaSession.

## Вспомогательный API

api/ содержит FastAPI read-only API и SQLite importer. Railway service остаётся отдельным диагностическим/служебным компонентом. Его smoke workflow проверяет health, countries, genres и поиск, но Android от него не зависит.

## CI

- Android Release APK: JDK 17, Gradle 9.6, unit tests, lint report, release APK, signing/apksigner verification, artifact upload.
- Catalog snapshot: формирование, проверка и публикация каталога.
- Collector tests и API tests: отдельные Python tests.
- Catalog health check: ручная проверка опубликованной станции/потоков.
- Production API smoke: периодическая проверка отдельного Railway API.
