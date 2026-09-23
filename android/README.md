# Android app

Классическое native Android-приложение на Kotlin + XML Views.

## Current UI

Перенесён основной shell автомобильного дизайна:

- Player;
- Countries;
- Country -> Stations;
- Genres;
- Genre -> Stations;
- Favorites;
- Recently Played;
- Search;
- Station Details dialog;
- large Play/Pause/Previous/Next/Shuffle controls;
- automotive search keypad.

RecyclerView используется для station/country/genre lists, потому что production-каталог будет большим.

## Adaptive layouts

Главный экран не заблокирован в одной ориентации.

- `res/layout/` — portrait;
- `res/layout-land/` — landscape automotive layout.

Landscape остаётся основным сценарием. Portrait имеет отдельную компоновку, а не просто уменьшенную landscape-версию.

## Playback

Аудио воспроизводится через AndroidX Media3/ExoPlayer.

Playback вынесен в `MediaSessionService`, чтобы поддерживать фоновое воспроизведение и дальнейшие автомобильные сценарии.

Для одной станции поддерживается несколько stream URL. При ошибке текущего потока service/client может перейти на следующий fallback.

## Current data source

UI уже подключён к `CatalogRepository`.

- при доступном API используются реальный каталог, страны, жанры и серверный поиск;
- `DemoCatalog` остаётся fallback для development/offline запуска;
- API base URL передаётся через Gradle property `radioApiUrl`;
- без property Android emulator использует `http://10.0.2.2:8000/`.

Для запуска против локального API:

```bash
gradle :app:assembleDebug -PradioApiUrl=http://10.0.2.2:8000/
```

Для реального телефона/head unit нужно передать адрес доступного с устройства API, например:

```bash
gradle :app:assembleDebug -PradioApiUrl=http://192.168.1.10:8000/
```
