# Project status

Обновлено: 2026-09-26

## Текущий этап

**Android UI полностью переведён на новый dark automotive radio design. Сейчас закрывается этап реальной проверки live-radio metadata, статусов потока и передачи Artist/Title во внешний MediaSession/HMI.**

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
- Новый главный radio player с крупным station/now-playing блоком и центрированными touch controls.
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

В Android на текущем этапе:

- `StreamTitle` разбирается на `artist` и `songTitle`, когда источник использует `Artist - Track`;
- исходные `Artist`/`Title` metadata Media3 используются, если источник отдаёт их отдельно;
- metadata текущего трека синхронизируется с `MediaItem.MediaMetadata`, чтобы `MediaSession` мог передавать отдельные Artist/Title внешнему HMI/приборной панели;
- обновление metadata не должно заново запускать поток;
- строка под названием трека в UI используется для состояния потока, а не для технического сообщения `Waiting for track metadata`;
- состояния UI: `PLAYING`, `BUFFERING`, `CONNECTING`, `PAUSED`, `RECONNECTING`, `OFFLINE`;
- для статусов используются существующие цвета `auto_success`, `auto_warning`, `auto_danger`, `auto_text_muted`, без изменения текущего шрифта и его размера.

Важно: приборная панель должна получать metadata через `MediaSession`; UI приложения и UI приборной панели не обязаны визуально совпадать. В приложении artist/title могут быть представлены как одна строка источника или текущим player layout, а для внешнего HMI передаются отдельные поля Artist и Title.

## CI / Release APK

Для Android оставлен один workflow:

`.github/workflows/android.yml` — **Android Release APK**.

Он:
1. собирает `assembleRelease`;
2. создаёт временный release keystore для CI;
3. выравнивает и подписывает APK;
4. выполняет `apksigner verify`;
5. публикует artifact `radio-world-auto-release`.

Текущая правка metadata/status находится в воспроизводимом скрипте:

`scripts/apply_player_metadata_ui_patch.py`

Gradle применяет его перед `preBuild`, чтобы release-сборка и локальная сборка использовали одинаковую правку.

Последний подтверждённый физически проверенный APK до этой правки:

- release APK успешно устанавливается;
- воспроизведение радио работает;
- получение названия трека на подтверждённых metadata-enabled станциях работает в UI.

Новый APK после правки Artist/Title + stream status должен быть отдельно проверен на реальном устройстве.

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
7. CI очищен до одного Android release workflow.
8. Release APK успешно собран, подписан и проверен на реальном устройстве.
9. Добавлен live metadata probe backend и подтверждено, что реальные станции могут отдавать `StreamTitle`.
10. Найдена и исправляется Android-цепочка dynamic metadata: split Artist/Title + MediaSession metadata.
11. Строка `Waiting for track metadata` заменяется на понятный статус live-потока.

## Что осталось после текущей сборки

1. Установить новый APK на реальный head unit/планшет.
2. Проверить станцию с подтверждённым ICY `StreamTitle`.
3. Проверить, что Artist и Title отдельно видны через Android media controls / целевой HMI.
4. Проверить статусы `PLAYING`, `BUFFERING`, `PAUSED`, `RECONNECTING`, `OFFLINE`.
5. Проверить portrait/landscape и сохранение станции.
6. Проверить бегущую строку во всех обновляемых полях.
7. После физического теста зафиксировать следующий набор проблем, если он останется.

## Ограничение текущей подписи

CI сейчас использует временный keystore. Это подходит для тестового установщика, но **не является постоянным release signing key**.

Перед публичным релизом/обновлением установленного приложения нужен постоянный keystore и сохранённая схема подписи.
