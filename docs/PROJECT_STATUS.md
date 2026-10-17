# Project status

Обновлено: 2026-10-17.

## Источник документации

Этот файл и связанные документы актуализированы под кодовый baseline ветки [experiment/lava-lamp-player](https://github.com/mellefresh13-tech/radio-world-auto/tree/experiment/lava-lamp-player) — [86d3e2c0f9765deac1bf714dac906e427d09e87d](https://github.com/mellefresh13-tech/radio-world-auto/commit/86d3e2c0f9765deac1bf714dac906e427d09e87d). Документационные коммиты не меняют Kotlin-код, ресурсы или workflow.

main остаётся веткой интеграции и релизов; на 17 октября 2026 года её HEAD — [537b73d9e6bd3d69fda0eb7808b070f63eb5c8fa](https://github.com/mellefresh13-tech/radio-world-auto/commit/537b73d9e6bd3d69fda0eb7808b070f63eb5c8fa). Историческая проверка автомобильного UI относится к a97e193/f0bad2b. Последний подтверждённый Android Release run — [#463](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/38159470929), успешно завершившийся 11 октября на f0bad2b; это не новая сборка текущего main.

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

На main активны Android Release APK, Catalog snapshot, Collector tests и Catalog health check. Отдельных API tests и Production API smoke workflow в репозитории нет.

Успешная release-сборка #463 относится к историческому main-коммиту f0bad2b. Документационная правка не запускала APK, unit tests или lint и не заменяет проверку на целевом устройстве.

## Открытые QA-пункты

- [ ] На реальном head unit проверить NEXT/PREV, включая переходы через границы каталога.
- [ ] Проверить fallback между stream URL, reconnect и длительное отсутствие сети.
- [ ] Проверить восстановление последней станции после обновления каталога и перезапуска.
- [ ] Проверить body tap мини-плеера и независимость кнопок Play/Pause и Favorite.
- [ ] Проверить live metadata и fallback title в MediaSession/HMI.
- [ ] Проверить остановку аудио после swipe-away из Recents.
- [ ] Проверить responsive phone portrait/landscape и tablet portrait/landscape и не-регрессию automotive 1920×720.

Эти пункты — план проверок, а не заявление о подтверждённых дефектах.
