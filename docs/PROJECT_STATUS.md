# Project status

Обновлено: 2026-10-11

## Основной статус

**main — единственный эталонный источник Android-кода.** Текущий HEAD main — merge-коммит f0bad2b9d96d66d677eec7d236126b574f183f98, продвигающий проверенное состояние исходного коммита a97e19352ece50b2fb2cba6283fcee2a218d8b46.

Пользователь подтвердил внешний вид текущего автомобильного профиля на реальном головном устройстве. Состояние main также успешно прошло Android Release CI: [run #463](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/38159470929), 11 октября 2026. Это подтверждает сборку и CI-проверки; тесты на устройстве остаются отдельной QA-активностью.

## Ветки

- main — Android source, tests, collector/tools и workflow.
- catalog-data — автоматически пересоздаваемый orphan snapshot опубликованного каталога и debug-файлов.

catalog-data не имеет общего предка с main по архитектурному замыслу. Не сливать, не перебазировать и не удалять её.

## UI profiles

- Точный физический профиль 1920×720 landscape направляется в CarMainActivity.
- Другие phone/tablet размеры и ориентации используют MobileMainActivity.
- Автомобильный профиль изолирован; responsive-only правки не должны менять CarMainActivity/CarRadioPlaybackService без отдельной задачи.
- Есть Countries, Genres, Favorites, Recently Played, Search и Player. Station Info скрыт.
- В responsive landscape логотип/название скроллятся вместе с навигацией; mini-player вынесен внизу sidebar и не дублирует Player.
- Search фокусирует поле и показывает совпадения по мере ввода.
- После Shuffle/смены станции обновление состояния не должно вызывать заметное мерцание.

## Playback и metadata

- Экранная Shuffle — случайный выбор станции.
- NEXT/PREV от руля и внешних MediaSession-контроллеров переключают по порядку полного локального каталога; доступны соответствующие Media3 команды в обоих playback-сервисах.
- Последняя станция восстанавливается, если присутствует в актуальном каталоге.
- Для станции поддерживаются альтернативные stream URL, fallback и bounded reconnect.
- ICY StreamTitle и ID3 Artist/Title нормализуются; title без live metadata для внешнего HMI заменяется названием станции.
- При swipe-away из Recents playback service останавливает воспроизведение.
- Cache логотипов и каталога переживает повторные запуски.

## Catalog runtime

Android использует GitHub Raw и ветку catalog-data. Он не требует Railway/API для работы. Snapshot содержит stations.json, manifest, delta, radio.db и статическую диагностику.

Catalog snapshot запускается каждые 6 часов, вручную и после изменений в файлах, от которых зависит генерация. Semantic version игнорирует volatile timestamps last_checked_at/discovered_at; если содержимое станций не изменилось, клиент не должен загружать полный каталог повторно.

Лимит текущей сборки — до 500 станций на страну. Для проверки учитывается до 6 000 обычных уникальных stream URL плюс curated streams. Фактические числа станций меняются при обновлении каталога и не являются константой документации.

## CI / релиз

Workflow Android Release APK использует JDK 17 и Gradle 9.6.0. Этапы: unit tests, lint и upload lint report, release build, подпись/выравнивание, apksigner verification, upload APK. Lint на данный момент non-blocking; остальные release-этапы блокируют публикацию.

Успешный run #463 создал artifact radio-world-auto-release и android-lint-report. Перейти к [run #463 в GitHub Actions](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/38159470929).

Подпись постоянным ключом возможна только при корректно настроенных GitHub Secrets; иначе workflow использует временный key для внутренних тестов.

Отдельные workflow выполняют collector tests, API tests, каталоговую health-проверку и production API smoke. Эти проверки не заменяют Android UI/playback QA на целевом устройстве.

## Повторяемый QA-чеклист

При выпуске Android-сборки или изменении соответствующей логики проверять:
1. Холодный запуск и восстановление последней станции; fallback, если станции больше нет в каталоге.
2. NEXT/PREV с руля, включая переходы через границу каталога и после ручного выбора другой станции.
3. Синхронизацию station, logo, favorite и metadata в приложении и MediaSession/HMI.
4. Станцию без live metadata: название станции на приборке.
5. Ошибку URL, переход к другому stream, reconnect и дальнейший failover.
6. Swipe-away из Recents: звук должен остановиться.
7. Каталог: initial full snapshot, совпавшая manifest version, применимая delta и fallback на полный snapshot.
8. Search с клавиатурой, вводом и live results.
9. Responsive phone portrait/landscape и tablet landscape.
10. Нерегрессию автомобильного профиля 1920×720 после любого мобильного UI-изменения.

## Известные границы

- GitHub нужен для первой загрузки каталога; без сохранённого cache и без сети полный каталог недоступен.
- Непрерывная доступность конкретного stream не гарантируется результатом предыдущей проверки.
- Полноценный Play Store packaging и стратегия пользовательского production update не входят в текущий выполненный объём.
