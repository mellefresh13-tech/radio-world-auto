# Radio World Auto

Native Android-приложение интернет-радио для автомобильных головных устройств и мобильных экранов. Стек Android — Kotlin, XML Views и Media3/ExoPlayer.

## Источник актуального кода

**Основная и эталонная ветка Android-проекта — main.** Именно её нужно использовать для следующих исправлений и релизных сборок. Отдельные feature/fix-ветки были рабочими ветками разработки, а не параллельными источниками истины.

Текущий HEAD main: [f0bad2b](https://github.com/mellefresh13-tech/radio-world-auto/commit/f0bad2b9d96d66d677eec7d236126b574f183f98). Он продвигает в main проверенное состояние исходного коммита [a97e193](https://github.com/mellefresh13-tech/radio-world-auto/commit/a97e19352ece50b2fb2cba6283fcee2a218d8b46). Визуальное состояние автомобильного профиля на этой версии проверено пользователем на реальном головном устройстве.

Последняя проверенная CI-сборка main — [Android Release APK, run #463](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/38159470929), завершилась успешно 11 октября 2026 года.

### Две ветки с разными назначениями

- **main** — исходный код приложения, тесты, инструменты и workflow.
- **catalog-data** — только сгенерированная публикация каталога и отладочная веб-страница.

Ветка catalog-data создаётся как orphan snapshot и обновляется workflow с force-push. Поэтому у неё намеренно нет общего предка с main. Не сливать её с main, не перебазировать и не удалять: это рабочая ветка опубликованных данных, а не ветка исходного кода.

## Android: актуальное поведение

- Главный экран — Player; доступны Countries, Genres, Favorites, Recently Played и Search.
- Ровно профиль 1920×720 в landscape направляется в изолированный автомобильный интерфейс. Остальные разрешения используют responsive phone/tablet интерфейс.
- Поиск поддерживает ввод с клавиатуры и обновление подходящих станций по мере набора текста.
- Экранная Shuffle выбирает случайную станцию. NEXT/PREV с руля и внешних MediaSession-контроллеров выбирают следующую/предыдущую доступную станцию по порядку полного локального каталога.
- Последняя выбранная станция восстанавливается, если она есть в локальном каталоге. Навигация и списки используют локальный snapshot.
- Для станции могут быть сохранены альтернативные stream URL; приложение использует fallback/reconnect при ошибках.
- ICY StreamTitle и ID3 Artist/Title приводятся к общей metadata-модели. Когда live metadata нет, внешнему HMI передаётся название станции.
- При удалении приложения из Recents playback service останавливает радио.
- Логотипы и каталог кешируются локально.

Автомобильный профиль и responsive phone/tablet интерфейс намеренно изолированы. Исправления мобильного UI не должны случайно менять проверенный экран автомобиля.

## Каталог радиостанций

**Android runtime получает каталог напрямую из GitHub Raw. Railway/API не требуется для работы установленного приложения.**

Workflow **Catalog snapshot** собирает и публикует данные в catalog-data:
- расписание — каждые 6 часов, в 17 минут каждого шестичасового интервала;
- ручной запуск через GitHub Actions;
- запуск при изменениях в collector, связанных API importer/model файлах, delta builder или самом workflow.

Активный snapshot pipeline объединяет Radio Browser, IPRD и курируемый список в collector/sources/curated.json. Каталог нормализуется, дедуплицируется, проходит проверку потоков и фильтрацию FM-признаков. В одном snapshot ограничение — до 500 станций на страну; потоки проверяются параллельно, с приоритетом для курируемых записей.

В catalog-data публикуются:
- **data/stations.json** — основной каталог для Android;
- **data/catalog-manifest.json** — semantic version, статистика и ссылки на snapshot/delta;
- **data/catalog-delta.json** — изменившиеся станции и удалённые ID;
- **data/radio.db** — SQLite для служебного API;
- **debug/** — статическая страница и данные диагностики каталога.

### Синхронизация на устройстве

1. При отсутствии локальной копии приложение загружает полный stations.json.
2. При следующей синхронизации сначала читает manifest.
3. Если semantic version совпадает, повторно скачивать станции не нужно.
4. Если версия изменилась и локальная версия совпадает с base_version, применяется delta.
5. Если delta нельзя безопасно применить, используется полный snapshot.
6. Каталог сохраняется в локальный gzip cache. Если GitHub временно недоступен, уже сохранённые данные доступны для поиска, фильтрации и playback.

При расчёте semantic version игнорируются изменяемые timestamps проверки и обнаружения, чтобы обычное обновление времени проверки потоков не заставляло приложение скачивать весь каталог.

## GitHub Actions

### Android Release APK

Workflow: [.github/workflows/android.yml](.github/workflows/android.yml).

Он запускается вручную или при изменениях в android/** и самом workflow. В CI используются JDK 17 и Gradle 9.6.0; Gradle Wrapper в репозитории отсутствует, поэтому workflow устанавливает Gradle через setup-gradle.

Этапы: unit tests, Android lint, release build, выравнивание и подпись APK, apksigner verification, upload artifacts. Lint запускается, его отчёт загружается отдельным артефактом, но текущий шаг lint не блокирует выпуск APK. Остальные этапы релиза блокирующие.

APK — artifact **radio-world-auto-release**. Его можно получить во вкладке Artifacts соответствующего [GitHub Actions run](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/38159470929). Постоянная подпись требует настроенных GitHub Secrets; без них workflow генерирует временный ключ для внутреннего тестирования.

Другие workflow:
- Catalog snapshot — сборка и публикация каталога;
- Collector tests и API tests — отдельные Python-тесты;
- Catalog health check — ручной поиск записи в опубликованном каталоге, при желании с проверкой URL потоков;
- Production API smoke — периодические проверки отдельного Railway API.

Изменения каталога сами по себе не должны запускать Android Release APK.

## Railway / API

В репозитории сохранён отдельный FastAPI/SQLite API, а production smoke workflow проверяет доступный Railway endpoint. Это вспомогательный/служебный путь для диагностики и работы с SQLite, но **не runtime-зависимость Android-приложения**.

## Структура репозитория

- **android/** — Android application, Kotlin/XML, unit tests.
- **collector/** — сборка, нормализация, фильтрация и проверка источников каталога.
- **tools/** — delta/manifest builder, диагностика и проверка опубликованного каталога.
- **api/** — FastAPI API и SQLite importer.
- **docs/** — архитектура, источники данных, UI, QA и roadmap.
- **.github/workflows/** — Android release, catalog publishing, tests и smoke checks.

При разработке придерживаться точечных исправлений, сохранять проверенное поведение и не запускать повторные сборки без необходимости. После изменений одного логического типа сначала проверять diff, затем выполнять один подходящий workflow.
