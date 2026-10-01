# Architecture

## Общая схема

```
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
                              +--------------+--------------+
                              |                             |
                              v                             v
                             API                       snapshots
                              |
                              v
                        Android app
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

## Каталог

Каждый внешний источник подключается отдельным адаптером. Адаптер только получает данные и приводит их к raw-модели. Каноническая модель формируется после normalization/deduplication/verification.

Источники не считаются взаимозаменяемыми: для каждой записи сохраняется provenance.

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
- landscape-first;
- большие touch targets.

Compose на первом этапе не используется: нужен классический Android UI, простой для поддержки и хорошо подходящий для головных устройств.

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

Диагностика реальных stream metadata выполняется backend endpoint `GET /debug/metadata`, который проверяет ICY `StreamTitle` непосредственно на stream URL.

## Player status

UI использует состояние playback для отображения статуса:

- `PLAYING`;
- `BUFFERING`;
- `CONNECTING`;
- `PAUSED`;
- `RECONNECTING`;
- `OFFLINE`.

Технический placeholder `Waiting for track metadata` не используется как пользовательский статус.

## Основные экраны

**Player** — текущая станция, metadata и большие кнопки управления.

**Countries** — страна -> список станций.

**Genres** — жанр -> список станций.

**Favorites** — быстрый доступ к сохранённым станциям.

**Search** — поиск по названию, стране, жанру.

## Автомобильный сценарий

```
Запуск
  -> последняя станция
  -> Play
  -> смена станции
  -> обновление Artist/Title
  -> MediaSession -> внешний HMI
  -> избранное
```

Не перегружаем экран настройками и второстепенной информацией.
