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
- крупная Play/Pause;
- Back — возврат к станции, которая играла непосредственно перед текущей;
- Forward/Shuffle — случайный переход на другую станцию;
- station artwork + track metadata;
- адаптивный landscape automotive layout и отдельный portrait layout.

Countries — только навигация по странам; поиск станций находится на отдельной вкладке Search.

## Station navigation

При переходе на новую станцию запоминается предыдущая. Нажатие Back возвращает именно эту станцию, используя её актуальное состояние из каталога: логотип, название, текущие сохранённые Artist/Title и остальные данные станции.

Вместо отдельной кнопки Next используется Shuffle: она выбирает другую станцию случайным образом. Это не является переходом к следующему элементу каталога.

## UI stability

Playback и metadata callbacks не должны менять текущую вкладку пользователя. События буферизации, reconnect и смены metadata обновляют данные проигрывателя без принудительного перехода на Now Playing.

Shuffle и Favorite обновляют существующие элементы интерфейса без полного перерисовывания Player. Cross-fade между состояниями Player отключён, чтобы исключить визуальные дёргания при смене станции и metadata.

Для release-сборки эти точечные изменения применяются скриптом `tools/fix_ui_stability.py` перед компиляцией.

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

## Release status — 2026-09-26

В репозитории подготовлена точечная правка station navigation: Back возвращает предыдущую станцию, Forward заменён на Shuffle. Для release-сборки изменения применяются через `scripts/apply_player_metadata_ui_patch.py` перед компиляцией.

Последняя проверенная release-сборка до этой правки была успешно собрана, выровнена, подписана и прошла `apksigner verify`.
