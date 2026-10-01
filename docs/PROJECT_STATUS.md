# Project status

Обновлено: 2026-10-02

## Текущий этап

**Основной Android UI и live-radio playback работают. Сейчас закрываются точечные runtime-проблемы реального автомобильного сценария без отката уже работающих функций.**

Старый UI больше не считается эталоном. Новый интерфейс реализуется непосредственно в Android XML/Kotlin, чтобы визуальный дизайн и реальная реализация не расходились.

## UI / responsive contract

- Automotive reference: 1920×720 landscape with 96 px left OEM safe-area inset.
- Landscape uses a permanent left navigation rail; the internal top app title bar is removed.
- Countries / Genres / Favorites / Recently Played use three items per row where the profile width permits it.
- Info / Station Details is not presented to the user.
- The old unintended green full-screen/tab background must not return.
- Shuffle and local navigation must not recreate the player surface or cause visible flicker.

### Sidebar fixed/scrolling split

In landscape sidebar:
- application logo and application name are inside the scrollable navigation content;
- logo/name must scroll away together with navigation items and are not sticky;
- the mini-player is outside that scroll area and remains pinned to the bottom of the sidebar.

This is an explicit non-regression rule for future point fixes.

## Startup / recovery

При запуске приложение восстанавливает последнюю успешно проигрывавшуюся станцию и запускает её после готовности каталога. Если сохранённая станция больше отсутствует в актуальном snapshot каталога, сохранённое состояние сбрасывается и выбирается первая доступная станция каталога.

При ошибке сначала пробуются следующие stream URL текущей станции, затем выполняются ограниченные reconnect attempts. После исчерпания recovery выбирается следующая станция каталога; уже проваленные станции не повторяются в рамках текущего recovery-цикла. Если рабочая станция не найдена — `OFFLINE`.

При удалении приложения из списка последних приложений playback service останавливает радио вместе с приложением.

Локальный cache логотипов сохраняется между запусками и очищается с учётом фактически удалённого размера файлов.

Player volume зафиксирован на unity gain `1.0`; программного занижения или искусственного усиления нет. Если источник всё ещё тише встроенного источника магнитолы, это нужно отдельно сравнивать на одном и том же потоке.

## Steering wheel / transport

- экранный `Shuffle` выбирает случайную станцию;
- физический `NEXT` на руле использует тот же сценарий случайного переключения;
- физический `PREV` возвращает ровно одну станцию, которая играла непосредственно перед последним `NEXT`;
- после возврата назад предыдущая станция забывается, поэтому повторный `PREV` не делает дополнительный шаг назад;
- экранная кнопка `PREV` не является заменой физическому `PREV` на руле;
- при точечных исправлениях логика предыдущих рабочих функций не заменяется целиком.

## Metadata / HMI

- `Artist` и `Title` из раздельных полей используются как отдельные значения;
- `Artist - Track` разбирается на artist/title;
- в приложении artist + title показываются вместе;
- в `MediaSession`/`MediaItem.MediaMetadata` они остаются раздельными для внешнего HMI/приборной панели;
- название станции не используется как исполнитель;
- если track metadata отсутствует, для внешнего HMI `Title` получает название станции;
- при смене трека старые artist/title не переносятся на новую metadata;
- обновление metadata не должно перезапускать текущий live stream;
- статусы UI: `PLAYING`, `BUFFERING`, `CONNECTING`, `PAUSED`, `RECONNECTING`, `OFFLINE`.

Диагностический endpoint live metadata: `GET /debug/metadata`.

## Каталог

Android получает основной каталог из ветки `catalog-data`. Каталог кешируется локально и обновляется не чаще одного раза в 6 часов.

При refresh каталога сохранённая станция проверяется на наличие в новом snapshot. Если её больше нет, она не должна оставаться как невалидная restored station.

## Актуальная архитектура

`catalog/discovery -> SQLite -> REST API -> catalog snapshot -> native Android -> Media3/MediaSession -> release APK`

Android: Kotlin/XML Views, Media3/ExoPlayer 1.11.1, MediaSessionService, landscape-first HMI 1920x720, adaptive portrait/landscape, Countries / Genres / Favorites / Recently Played / Search / Station Details.

## CI / Release APK

`.github/workflows/android.yml` — **Android Release APK**.

Workflow запускается:
- вручную через `workflow_dispatch`;
- автоматически при изменениях внутри `android/**` или самого `.github/workflows/android.yml`;
- на ветке `ui/responsive-mobile` push также является разрешённым триггером для responsive-проверок.

Изменения каталога, коллектора и backend-файлов сами по себе Android Release не запускают.

APK публикуется как artifact **`radio-world-auto-release`**.

Сборка не зависит от старой цепочки build-time patch-скриптов: накопленные UI-изменения материализованы непосредственно в исходном коде.

## Следующая проверка

1. Холодный запуск: последняя доступная станция / fallback на первую станцию после обновления каталога.
2. `NEXT` с руля: случайное переключение.
3. `PREV` с руля: возврат ровно на предыдущую станцию после `NEXT`.
4. Повторный `PREV`: не делать дополнительный шаг назад.
5. Смена трека: приложение + MediaSession/HMI.
6. Станция без metadata: название станции на приборке.
7. Recovery: stream fallback -> reconnect -> следующая станция.
8. Смахивание приложения: playback должен остановиться.
9. Portrait/landscape и marquee.
10. Sidebar: branding скроллится вместе с меню, mini-player остаётся закреплён снизу.
11. После Shuffle нет визуального моргания.
12. Проверка сохранности уже исправленного поведения после каждого точечного патча.

## Подпись

CI использует постоянный release key при наличии соответствующих repository secrets; иначе для конкретного CI-run создаётся временный ключ. Для публичного релиза нужен постоянный signing key.
