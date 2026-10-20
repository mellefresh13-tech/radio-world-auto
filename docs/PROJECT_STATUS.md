# Project status

Обновлено: 2026-10-20.

## Источник документации

Документация описывает текущую интеграционную и релизную ветку `main`, а не экспериментальную ветку. Изменения в этой PR затрагивают только документацию.

Текущий HEAD `main` на момент актуализации — [e3d96c5](https://github.com/mellefresh13-tech/radio-world-auto/commit/e3d96c548f0bd815ee58895546e523cdce0f3939), включающий закрытие точечных security-находок в PR #39. Последний подтверждённый Android Release run — [#616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955), успешно завершившийся 20 октября на этом коммите.

## UI profiles

- Точное физическое разрешение 1920×720 в landscape выбирает CarMainActivity.
- Остальные phone/tablet размеры и ориентации используют MobileMainActivity.
- Автомобильный профиль изолирован от responsive UI; мобильные UI-правки не должны менять CarMainActivity/CarRadioPlaybackService без отдельной задачи.
- Доступны Player, Countries, Genres, Favorites, Recently Played и Search; Station Info не показывается как отдельный экран.
- В responsive landscape логотип/название прокручиваются вместе с навигацией; mini-player расположен внизу sidebar там, где предусмотрен.
- На автомобильной и responsive поверхности касание по телу mini-player вне Play/Pause и Favorite открывает Player/Now Playing; кнопки выполняют собственные действия.
- Search фокусирует поле и показывает совпадения по мере ввода.

## Playback и metadata

- Экранная Shuffle выбирает случайную станцию.
- NEXT/PREV от руля и внешних MediaSession-контроллеров выбирают следующую/предыдущую доступную станцию по порядку полного локального каталога, включая циклический переход через границы.
- Последняя станция восстанавливается по сохранённому ID и снимку, когда доступна подходящая локальная запись.
- Для станции поддерживаются альтернативные stream URL и предусмотренные fallback/reconnect-переходы.
- ICY StreamTitle и ID3 Artist/Title нормализуются; при отсутствии live metadata для внешнего HMI используется название станции.
- При swipe-away из Recents playback service останавливает радио.

## Catalog runtime

Android получает каталог напрямую из GitHub Raw, опубликованная ветка — catalog-data. Railway/API не является runtime-зависимостью Android. Catalog snapshot запускается каждые 6 часов, вручную и по заданным изменениям исходников. Лимит публикации — до 500 станций на страну; количество станций и потоков меняется от snapshot к snapshot.

## CI / ограничения проверки

На `main` активны Android Release APK, Catalog snapshot, Collector tests, API/catalog metadata tests и Catalog health check. Workflow `API/catalog metadata tests` проверяет API/catalog tooling в CI; Android runtime при этом по-прежнему не зависит от Railway или hosted API.

Release run [#616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955) для коммита `e3d96c5` прошёл: unit tests, lint, release build, постоянная подпись и проверка SHA-256 сертификата успешны. В run загружены `radio-world-auto-release` и `android-lint-report`. Этот документ обновляется отдельной docs-only PR и не заменяет ручную проверку APK на устройстве.

## Открытые QA-пункты

- [ ] Проверить вручную APK из [run #616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955), включая обновление поверх установленной версии и сохранение локальных данных.
- [ ] Дождаться ближайшей плановой публикации каталога и проверить успешность build/publish, содержимое публикации и обновление `catalog-data`.
- [ ] На реальном head unit проверить NEXT/PREV, включая переходы через границы каталога.
- [ ] Проверить fallback между stream URL, reconnect и длительное отсутствие сети.
- [ ] Проверить восстановление последней станции после обновления каталога и перезапуска.
- [ ] Проверить body tap мини-плеера и независимость кнопок Play/Pause и Favorite.
- [ ] Проверить live metadata и fallback title в MediaSession/HMI.
- [ ] Проверить остановку аудио после swipe-away из Recents.
- [ ] Проверить responsive phone portrait/landscape и tablet portrait/landscape и не-регрессию automotive 1920×720.

Эти пункты — план проверок, а не заявление о подтверждённых дефектах.
