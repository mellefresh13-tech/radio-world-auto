# Android app

Классическое native Android-приложение на Kotlin + XML Views.

## Current UI

Актуальная версия содержит новый dark automotive UI:

- Player / Now Playing;
- Countries;
- Country -> Stations;
- Genres;
- Genre -> Stations;
- Favorites;
- Recently Played;
- Search;
- Station Details dialog;
- крупная Play/Pause и Shuffle;
- station artwork + track metadata;
- адаптивный landscape automotive layout и отдельный portrait layout.

Countries — только навигация по странам; поиск станций находится на отдельной вкладке Search.

## UI stability

Playback и metadata callbacks не должны менять текущую вкладку пользователя. События буферизации, reconnect и смены metadata обновляют данные проигрывателя без принудительного перехода на Now Playing.

Shuffle и Favorite обновляют существующие элементы интерфейса без полного перерисовывания Player. Cross-fade между состояниями Player отключён, чтобы исключить визуальные дёргания при смене станции и metadata.

Для release-сборки эти точечные изменения применяются скриптом `tools/fix_ui_stability.py` перед компиляцией. Это сделано как минимальная правка поверх текущей рабочей версии без переписывания playback-слоя.

## Adaptive layouts

Главный экран не заблокирован в одной ориентации.

- `res/layout/` — portrait;
- `res/layout-land/` — landscape automotive layout.

Landscape остаётся основным сценарием. Portrait имеет отдельную компоновку, а не просто уменьшенную landscape-версию.

## Playback

Аудио воспроизводится через AndroidX Media3/ExoPlayer.

Playback вынесен в `MediaSessionService`, чтобы поддерживать фоновое воспроизведение и дальнейшие автомобильные сценарии.

Для одной станции поддерживается несколько stream URL. При ошибке текущего потока service/client может перейти на следующий fallback.

Metadata обрабатывается из ICY/ID3 и передаётся отдельно как Artist + Title для MediaSession/приборки. В приложении Artist + Title показываются вместе в блоке текущего трека.

## Current data source

UI уже подключён к `CatalogRepository`.

- при доступном API используются реальный каталог, страны, жанры и серверный поиск;
- `DemoCatalog` остаётся fallback для development/offline запуска;
- API base URL передаётся через Gradle property `radioApiUrl`.

Для запуска против локального API:

```bash
gradle :app:assembleDebug -PradioApiUrl=http://10.0.2.2:8000/
```

Для реального телефона/head unit нужно передать адрес доступного с устройства API, например:

```bash
gradle :app:assembleDebug -PradioApiUrl=http://192.168.1.10:8000/
```

## Release status — 2026-09-26

Последняя проверенная release-сборка после UI stability fixes:

- Release APK собран успешно;
- APK выровнен и подписан;
- `apksigner verify` прошёл;
- workflow был запущен единоразово и после получения APK временный workflow/trigger удалены;
- постоянный `.github/workflows/android.yml` остаётся только с `workflow_dispatch`, поэтому push сам по себе сборку не запускает.
