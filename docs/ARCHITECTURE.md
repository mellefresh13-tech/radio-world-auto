# Architecture

## Общая схема

```text
External sources
    |
    v
Collectors -> Raw records -> Normalization -> Deduplication
                                             |
                                             v
                                      Stream verification
                                             |
                                             v
                                      Canonical catalog
                                             |
                                             v
                                      GitHub catalog-data
                                             |
                              +--------------+--------------+
                              |                             |
                              v                             v
                         stations.json              manifest + delta
                              |                             |
                              +--------------+--------------+
                                             |
                                             v
                                      Android local cache
                                             |
                                             v
                                      Media3 / ExoPlayer
                                             |
                                             v
                                         MediaSession
                                             |
                                             v
                                        external HMI
```

## Runtime принцип

Android **не зависит от Railway или отдельного API-сервера**. Runtime-источник каталога — публичная ветка `catalog-data` репозитория GitHub.

Backend/API-код, присутствующий в репозитории, используется для служебных/диагностических задач и импорта, но не находится в критическом runtime path установленного APK.

## Каталог

Каждый внешний источник подключается отдельным адаптером. Адаптер только получает данные и приводит их к raw-модели. Каноническая модель формируется после normalization/deduplication/verification.

Источники не считаются взаимозаменяемыми: для каждой записи сохраняется provenance.

Публикация snapshot создаёт:

```text
catalog-data/data/
├── stations.json
├── radio.db
├── catalog-manifest.json
└── catalog-delta.json
```

`stations.json` — основной источник для Android. `radio.db` сохраняется как резервный/служебный формат.

## Синхронизация Android

### Первый запуск

Если локального snapshot нет:

```text
GitHub manifest
      ↓
полный stations.json
      ↓
gzip local cache
```

В cache сохраняется `catalog_version`.

### Последующие запуски

```text
local catalog_version
        ↓
manifest
        |
        +-- same version → use cache
        |
        +-- changed + local == base_version
        |       ↓
        |   download delta
        |       ↓
        |   apply updated / removed_ids
        |
        +-- delta cannot be applied
                ↓
          download full catalog
```

После успешной синхронизации новый snapshot атомарно записывается в локальный cache.

Навигация, поиск, Favorites/Recent и playback используют локальный каталог. Поэтому временная недоступность GitHub после уже выполненной первоначальной загрузки не блокирует приложение.

## Stream discovery

Уровни обнаружения:

1. официальный сайт станции;
2. прямой stream URL на официальном сайте;
3. HTML/JS/JSON;
4. web-player network requests;
5. публичные каталоги;
6. поисковое обнаружение.

Найденные потоки проходят единый verifier.

## Android

Приложение — обычный native Android project:

- Kotlin;
- XML Views;
- AppCompat;
- Media3 / ExoPlayer;
- playback service;
- MediaSession;
- responsive phone/tablet UI;
- landscape-first automotive profile;
- большие touch targets.

Compose на первом этапе не используется: нужен классический Android UI, простой для поддержки и хорошо подходящий для головных устройств.

## Playback и failover

Для станции может быть сохранено несколько stream URL. Android не загружает все варианты в ExoPlayer одновременно.

При ошибке текущего stream:

```text
current stream
     ↓ error
next unused stream of same station
     ↓
if no streams remain → существующий reconnect/failover сценарий
```

Использованные неуспешные URL запоминаются только на время текущей playback-сессии станции, чтобы не уйти в бесконечный цикл.

## Live metadata

Источник может передать metadata двумя способами:

```text
Artist + Title отдельно
        или
StreamTitle = "Artist - Track"
```

Android нормализует оба варианта в единое состояние:

```text
artist
songTitle
```

Правила:

- отдельные Artist/Title имеют приоритет, если они реально переданы источником;
- строка `Artist - Track` разбирается, если отдельные поля отсутствуют;
- если передан только title, artist остаётся пустым;
- название станции не используется как artist fallback;
- при смене трека старые artist/title не переносятся на новую metadata;
- обновление metadata не должно перезапускать текущий live stream.

Для собственного UI artist/title могут отображаться вместе. Для внешнего HMI они сохраняются раздельно в `MediaItem.MediaMetadata` и доступны через `MediaSession`.

## Player status

UI использует состояние playback для отображения статуса:

- `PLAYING`;
- `BUFFERING`;
- `CONNECTING`;
- `PAUSED`;
- `RECONNECTING`;
- `OFFLINE`.

## Основные экраны

**Player** — текущая станция, metadata и большие кнопки управления.

**Countries** — страна -> список станций.

**Genres** — жанр -> список станций.

**Favorites** — быстрый доступ к сохранённым станциям.

**Search** — поиск по названию, стране, жанру.

## Автомобильный сценарий

```text
Запуск
  -> локальный каталог
  -> последняя доступная станция
  -> Play
  -> смена станции
  -> обновление Artist/Title
  -> MediaSession -> внешний HMI
  -> избранное
```

NEXT выполняет random station selection, PREV возвращает одну непосредственно предыдущую станцию согласно текущему контракту управления.

Не перегружаем экран настройками и второстепенной информацией.
