# Project status

Обновлено: 2026-09-26

## Текущий этап

**Основной Android UI и live-radio playback работают. Текущий этап — финальная проверка metadata Artist/Title и stream status на реальном устройстве и проверка внешнего HMI/приборной панели.**

Старый UI больше не считается эталоном. Новый интерфейс проектируется непосредственно в Android XML/Kotlin, чтобы визуальный дизайн и реальная реализация не расходились.

Текущая цепочка:

`catalog/discovery -> SQLite -> REST API -> Railway -> native Android -> Media3/MediaSession -> release APK`

Основные решения из HMI-ветки, native redesign и последующих patch-правок сведены в `main`.

## Что сейчас считается актуальным

- `main` — актуальная ветка Android-кода.
- Landscape HMI 1920x720 для автомобильного сценария.
- Adaptive UI для portrait/landscape через UI profiles.
- Native Kotlin/XML Views.
- Media3/ExoPlayer + MediaSessionService.
- Countries / Genres / Favorites / Recently Played / Search / Station Details.
- Новый главный radio player с крупным station/now-playing блоком и крупными touch controls.
- Dark automotive visual system: графитовые поверхности, тонкие borders, cyan accent, крупные touch targets.
- Несколько stream URL с fallback/retry.
- ICY/HLS ID3 metadata.
- Реальный live metadata probe backend для проверки того, что конкретный stream действительно отдаёт `StreamTitle`.
- Catalog cache и cache I/O вне UI-потока.
- Production API: `https://radio-world-auto-production.up.railway.app/`.

## Live metadata / player status

Проверено на реальных stream URL: часть станций действительно отдаёт ICY `StreamTitle`, например формат `Artist - Track`; часть станций поддерживает ICY, но присылает пустой `StreamTitle`.

Диагностический endpoint:

`GET /debug/metadata`

Он подключается к реальному stream URL с `Icy-MetaData: 1` и показывает `icy_metaint`, `stream_title`, `has_track_metadata`, `raw_metadata` и ошибки подключения.

В Android используется единая логика нормализации metadata:

- если источник отдаёт отдельные `Artist` и `Title`, используются эти поля;
- если приходит одна строка `Artist - Track`, она разбирается на `artist` и `songTitle`;
- в приложении artist и title показываются вместе как единая now-playing информация;
- в `MediaSession`/`MediaItem.MediaMetadata` artist и title сохраняются отдельными полями для внешнего HMI/приборной панели;
- название станции не используется как fallback для исполнителя;
- если источник передал только title, artist остаётся пустым, а не подменяется названием станции;
- при смене трека старые artist/title не должны переноситься на новую композицию;
- обновление metadata не должно заново запускать поток.

Строка под названием трека в UI используется для состояния потока, а не для технического сообщения `Waiting for track metadata`.

Состояния UI:

- `PLAYING` — воспроизведение;
- `BUFFERING` — буферизация;
- `CONNECTING` — подключение;
- `PAUSED` — пауза;
- `RECONNECTING` — восстановление соединения;
- `OFFLINE` — поток недоступен.

Для статусов используются существующие цвета `auto_success`, `auto_warning`, `auto_danger`, `auto_text_muted`, без изменения текущего шрифта и его размера.

Важно: приборная панель должна получать metadata через `MediaSession`; UI приложения и UI приборной панели не обязаны визуально совпадать. В приложении artist/title могут быть представлены одной строкой, а для внешнего HMI передаются отдельные поля Artist и Title.

## CI / Release APK

Для Android оставлен один workflow:

`.github/workflows/android.yml` — **Android Release APK**.

Android Release workflow запускается **только вручную (`workflow_dispatch`)**. Push в `main` не должен автоматически запускать release-сборку.

Workflow:
1. собирает `assembleRelease`;
2. создаёт временный release keystore для CI;
3. выравнивает и подписывает APK;
4. выполняет `apksigner verify`;
5. публикует artifact `radio-world-auto-release`.

Текущая правка metadata/status находится в воспроизводимом скрипте:

`scripts/apply_player_metadata_ui_patch.py`

Gradle применяет его перед `preBuild`, чтобы release-сборка и локальная сборка использовали одинаковую правку.

Последняя release-сборка после исправления Artist/Title + stream status успешно собрана и подписана. Её необходимо отдельно проверить на реальном устройстве, особенно по отображению metadata на внешнем HMI.

## Ветки

Актуальной для Android считается `main`.

Старые Android/HMI-ветки не являются источником текущего Android UI:

- `android-final`;
- `feature/android-html-hmi-1920x720`;
- `feature/native-hmi-redesign-from-html-reference`;
- `native-hmi-redesign-from-html-reference`.

`catalog-data` — отдельная ветка с данными каталога; она не является Android-веткой и имеет отдельную историю.

## История последних этапов

1. Собран базовый Android MVP и подключён production API.
2. Добавлены cache, metadata, fallback/retry и adaptive orientation.
3. Перенесён автомобильный HMI 1920x720.
4. Сведены player controls, catalogs и визуальные решения reference HMI.
5. Выполнен полный native dark automotive UI redesign.
6. Исправлены ошибки XML/UI и cache I/O.
7. CI очищен до одного Android release workflow; автоматический release на push в `main` отключён.
8. Release APK успешно собран, подписан и проверен на реальном устройстве.
9. Добавлен live metadata probe backend и подтверждено, что реальные станции могут отдавать `StreamTitle`.
10. Исправлена обработка dynamic metadata: Artist/Title нормализуются из раздельных или объединённых данных.
11. Artist и Title передаются в `MediaSession` раздельно для внешнего HMI, а в приложении показываются вместе.
12. `Waiting for track metadata` заменён на понятный статус live-потока.

## Что осталось после текущей сборки

1. Установить последнюю release-сборку на реальный head unit/планшет.
2. Проверить станцию с подтверждённым ICY `StreamTitle`.
3. Проверить смену трека: artist/title должны обновляться одновременно в приложении и MediaSession.
4. Проверить, что внешний HMI/приборка получает Artist и Title отдельными полями.
5. Проверить статусы `PLAYING`, `BUFFERING`, `PAUSED`, `RECONNECTING`, `OFFLINE`.
6. Проверить portrait/landscape и сохранение станции.
7. Проверить бегущую строку во всех обновляемых полях.
8. После физического теста зафиксировать следующий набор проблем, если он останется.

## Ограничение текущей подписи

CI сейчас использует временный keystore. Это подходит для тестового установщика, но **не является постоянным release signing key**.

Перед публичным релизом/обновлением установленного приложения нужен постоянный keystore и сохранённая схема подписи.
