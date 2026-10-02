# Android app

Классическое native Android-приложение на Kotlin + XML Views.

## Актуальная ветка

Текущая Android-разработка ведётся в:

`ui/responsive-mobile`

Она содержит актуальный responsive UI, automotive HMI, playback и catalog-sync логику.

## Runtime architecture

Android не зависит от Railway, FastAPI или другого отдельного сервера.

```text
GitHub catalog-data
        ↓
manifest
   ├─ same version → local cache
   └─ changed
        ↓
   catalog-delta
        ↓
local gzip catalog
        ↓
Media3 / ExoPlayer
        ↓
MediaSession / HMI
```

Первый запуск получает полный `stations.json`. Последующие запуски сравнивают semantic catalog version из `catalog-manifest.json` и при совместимой версии применяют `catalog-delta.json`. При невозможности безопасно применить delta выполняется fallback на полный snapshot.

Навигация, Search, Favorites, Recently Played и playback работают по локальному каталогу. Временная недоступность GitHub после уже выполненной синхронизации не блокирует приложение.

## UI

Актуальный интерфейс:

- Player / Now Playing;
- Countries;
- Country -> Stations;
- Genres;
- Genre -> Stations;
- Favorites;
- Recently Played;
- Search;
- automotive left navigation rail в landscape;
- mini-player закреплён снизу sidebar;
- логотип и название приложения скроллятся вместе с navigation items;
- крупная Play/Pause;
- station artwork + track metadata;
- adaptive portrait / landscape layouts.

Automotive reference: 1920×720 landscape с 96 px OEM safe-area inset.

UI не является build-time generated patch; текущая реализация находится непосредственно в Kotlin/XML исходниках.

## Playback

Playback использует AndroidX Media3 / ExoPlayer и `MediaSessionService`.

Для одной станции поддерживается несколько stream URL.

При проблеме:

```text
current stream
    ↓ error
next stream of same station
    ↓
bounded reconnect
    ↓
next playable station from local catalog
    ↓
OFFLINE
```

Recovery не ограничен первыми 200 station items Media3 playlist: выбор следующей станции выполняется по полному локальному каталогу.

NEXT на руле сохраняет random-station semantics. PREV возвращает одну станцию непосредственно перед последним NEXT согласно текущему transport contract.

При удалении приложения из Recents playback service останавливает радио.

## Metadata / HMI

Поддерживаются:

- ICY `StreamTitle`;
- ID3 Artist/Title;
- разбор `Artist - Track`.

В приложении artist + title могут отображаться вместе. В MediaSession/HMI artist и title передаются раздельно.

Если live metadata отсутствует, для внешнего HMI используется название станции.

## Persisted state

Сохраняются:

- Favorites;
- Recently Played;
- последняя station для startup restore;
- предыдущая station для transport PREV.

Для playback state используется общий `PlaybackStateStore` для Activity/service сценариев.

## Tests / CI

Android Release workflow выполняет:

1. unit tests;
2. Android lint;
3. release build;
4. signing/alignment;
5. `apksigner verify`;
6. artifact upload.

Текущая тестовая база включает отдельные unit tests для:

- station selection;
- track metadata parsing;
- playback recovery policy;
- catalog delta application.

## Legacy / maintenance

Исторические patch/build scripts остаются в репозитории только до отдельной cleanup-фазы. Они не являются частью текущего Android release pipeline и не должны запускаться вручную для обычной сборки.

Основной release workflow использует непосредственно текущий исходный Kotlin/XML код.
