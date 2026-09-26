# Project status

Обновлено: 2026-09-26

## Текущий этап

**Основной Android UI и live-radio playback работают. Сейчас закрываем runtime-проблемы реального автомобильного сценария: startup restore, dynamic metadata/HMI, stream failover и lifecycle.**

Старый UI больше не считается эталоном. Новый интерфейс проектируется непосредственно в Android XML/Kotlin, чтобы визуальный дизайн и реальная реализация не расходились.

## Startup / recovery

При запуске: последняя проигрывавшаяся станция автоматически восстанавливается и запускается; если истории нет — запускается первая доступная станция каталога. Metadata после запуска снова поступает через Media3 и обновляет UI + MediaSession/HMI.

При ошибке: сначала пробуются следующие stream URL станции, затем ограниченные reconnect attempts, после чего выбирается следующая станция каталога. Проваленные станции не повторяются в рамках текущего recovery-цикла. Если рабочая станция не найдена — `OFFLINE`.

При удалении приложения из списка последних приложений playback service теперь останавливает радио вместе с приложением.

Player volume зафиксирован на unity gain `1.0`; программного занижения или искусственного усиления нет. Если источник всё ещё тише встроенного источника магнитолы, это нужно отдельно сравнивать на одном и том же потоке, поскольку уровень записи самого stream может отличаться.

## Metadata / HMI

- `Artist` и `Title` из раздельных полей используются как отдельные значения.
- `Artist - Track` разбирается на artist/title.
- В приложении artist + title показываются вместе.
- В `MediaSession`/`MediaItem.MediaMetadata` они остаются раздельными для внешнего HMI/приборной панели.
- Название станции не используется как исполнитель.
- Если track metadata отсутствует, для внешнего HMI `Title` получает название станции.
- Статусы UI: `PLAYING`, `BUFFERING`, `CONNECTING`, `PAUSED`, `RECONNECTING`, `OFFLINE`.

Диагностический endpoint live metadata: `GET /debug/metadata`.

## Актуальная архитектура

`catalog/discovery -> SQLite -> REST API -> Railway -> native Android -> Media3/MediaSession -> release APK`

Android: Kotlin/XML Views, Media3/ExoPlayer 1.11.1, MediaSessionService, landscape-first HMI 1920x720, adaptive portrait/landscape, Countries / Genres / Favorites / Recently Played / Search / Station Details.

## CI / Release APK

`.github/workflows/android.yml` — **Android Release APK**. Workflow запускается только вручную (`workflow_dispatch`); push в `main` release-сборку не запускает.

Runtime-правка воспроизводится через `scripts/apply_player_metadata_ui_patch.py`, который Gradle применяет перед `preBuild`.

## Ветки

Актуальная Android-ветка — `main`. Старые Android/HMI-ветки не являются источником текущего UI: `android-final`, `feature/android-html-hmi-1920x720`, `feature/native-hmi-redesign-from-html-reference`, `native-hmi-redesign-from-html-reference`.

## Следующая проверка

1. Холодный запуск: последняя станция / первая станция.
2. Смена трека: приложение + MediaSession/HMI.
3. Станция без metadata: название станции на приборке.
4. Recovery: stream fallback -> reconnect -> следующая станция.
5. Смахивание приложения: playback должен остановиться.
6. Сравнение громкости с встроенной музыкой магнитолы.
7. Portrait/landscape и marquee.

## Подпись

CI использует временный release keystore. Для публичного релиза нужен постоянный signing key.
