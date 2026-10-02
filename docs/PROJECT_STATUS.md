# Project status

Обновлено: 2026-10-02

## Текущий этап

**Основной Android UI и live-radio playback работают. Сейчас закрываются точечные runtime-проблемы реального автомобильного сценария без отката уже работающих функций.**

Актуальная Android-ветка: `ui/responsive-mobile`.

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

При ошибке stream сначала пробуются другие URL той же станции, которые ещё не были помечены как неуспешные в текущей playback-сессии. Затем остаётся существующий ограниченный reconnect/failover сценарий. Это не добавляет все streams станции одновременно в ExoPlayer.

При удалении приложения из списка последних приложений playback service останавливает радио вместе с приложением.

Локальный cache логотипов сохраняется между запусками.

Player volume зафиксирован на unity gain `1.0`; программного занижения или искусственного усиления нет.

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

## Каталог

**Android runtime не использует Railway или REST API.** Основной источник — ветка GitHub `catalog-data`.

Публикуемый snapshot содержит:

```text
catalog-data/data/
├── stations.json
├── radio.db
├── catalog-manifest.json
└── catalog-delta.json
```

Первый запуск скачивает полный `stations.json` и сохраняет локальный gzip cache вместе с `catalog_version`.

При последующих запусках Android сначала проверяет `catalog-manifest.json`. Если версия не изменилась, полный каталог не скачивается. Если локальная версия совпадает с `base_version`, скачивается и применяется `catalog-delta.json` (`updated` + `removed_ids`). При невозможности безопасно применить delta выполняется fallback на полный snapshot.

Локальный каталог используется для навигации, поиска, Favorites/Recent и playback. Поэтому уже загруженное приложение продолжает работать при временной недоступности GitHub.

Collector workflow обновляет каталог каждые 6 часов.

## Актуальная архитектура

```text
external sources
      ↓
collector / normalization / filtering / verification
      ↓
GitHub catalog-data
      ↓
manifest + delta/full snapshot
      ↓
Android local catalog cache
      ↓
Media3/ExoPlayer + MediaSession
      ↓
car HMI / mobile UI
```

`api/` остаётся в репозитории как отдельная служебная/диагностическая часть и не является runtime dependency Android.

Android: Kotlin/XML Views, Media3/ExoPlayer 1.11.1, MediaSessionService, landscape-first HMI 1920x720, adaptive portrait/landscape, Countries / Genres / Favorites / Recently Played / Search.

## CI / Release APK

`.github/workflows/android.yml` — **Android Release APK**.

Workflow запускается:
- вручную через `workflow_dispatch`;
- автоматически при изменениях внутри `android/**` или самого `.github/workflows/android.yml`.

Перед release build выполняются:
1. `:app:test`;
2. `:app:lintRelease`;
3. `:app:assembleRelease`;
4. alignment/signing;
5. `apksigner verify`;
6. upload release artifact.

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
7. Recovery: следующий stream текущей станции -> reconnect -> следующая станция.
8. Смахивание приложения: playback должен остановиться.
9. Portrait/landscape и marquee.
10. Sidebar: branding скроллится вместе с меню, mini-player остаётся закреплён снизу.
11. После Shuffle нет визуального моргания.
12. Первый запуск / повторный запуск: full catalog → manifest check → delta → local cache.
13. Проверка сохранности уже исправленного поведения после каждого точечного патча.

## Подпись

CI использует постоянный release key при наличии соответствующих repository secrets; иначе для конкретного CI-run создаётся временный ключ. Для публичного релиза нужен постоянный signing key.


## Catalog delta

Semantic catalog changes ignore volatile verification timestamps (`last_checked_at`, `discovered_at`). Therefore a six-hour verification refresh should not cause the Android client to redownload thousands of station records when the actual station data did not change.
