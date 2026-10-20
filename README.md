# Radio World Auto

Native Android-приложение интернет-радио для автомобильных головных устройств и мобильных экранов. Стек Android — Kotlin, XML Views и Media3/ExoPlayer.

## Источник актуального кода

**Основная и эталонная ветка Android-проекта — main.** Именно её нужно использовать для следующих исправлений и релизных сборок. Отдельные feature/fix-ветки были рабочими ветками разработки, а не параллельными источниками истины.

Текущий `main` — [4929042](https://github.com/mellefresh13-tech/radio-world-auto/commit/492904249fd875900dc5af0cd2400917182de107) (20 октября 2026 года). Последняя проверенная release-сборка сделана с коммита [e3d96c5](https://github.com/mellefresh13-tech/radio-world-auto/commit/e3d96c548f0bd815ee58895546e523cdce0f3939) после security PR #39. Последующие PR #30–36 изменили только версии GitHub Actions и Python runtime-зависимостей; Kotlin/XML и поведение приложения не затрагивались.

Последняя подтверждённая сборка APK — [Android Release APK, run #616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955), успешно завершившаяся 20 октября 2026 года. Unit tests, lint, release build, подпись и проверка постоянного SHA-256 сертификата прошли на `e3d96c5`. После конфигурационных обновлений новая APK-сборка ещё не запускалась; workflow запускается вручную.

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

Он запускается вручную или при изменениях в android/** и самом workflow в ветке main. Устаревшая ветка ui/responsive-mobile больше не запускает сборку. В CI используются JDK 17 и Gradle 9.6.0; Gradle Wrapper в репозитории отсутствует, поэтому workflow устанавливает Gradle через setup-gradle.

Этапы: unit tests, Android lint, release build, выравнивание и подпись APK, apksigner verification, upload artifacts. Lint запускается, его отчёт загружается отдельным артефактом, но текущий шаг lint не блокирует выпуск APK. Остальные этапы релиза блокирующие.

APK текущей успешной сборки — artifact **radio-world-auto-release** в [run #616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955). Workflow использует GitHub Environment `android-release-signing`; для постоянного ключа обязательны все пять environment secrets, включая `ANDROID_SIGNING_CERT_SHA256`. Если постоянный ключ не настроен, workflow может собрать отдельный `radio-world-auto-test-build` с временной подписью; такой APK не подходит для обновления установки, подписанной постоянным ключом.

Основные workflow и проверки:
- **Android Release APK** — unit tests, lint, release build, подпись и загрузка APK;
- **Android unit tests** — отдельный быстрый тестовый workflow;
- **API/catalog metadata tests** — Python-тесты API, importer и catalog tooling;
- **Collector tests** — тесты сборщика каталога;
- **Catalog snapshot** — сборка, проверка и публикация каталога;
- **Catalog health check** — ручная проверка опубликованного каталога.

Production/Railway smoke-check отсутствует. Наличие API tests не означает, что установленное Android-приложение обращается к серверному API: runtime каталога остаётся на GitHub Raw. Полный актуальный список workflow находится в `.github/workflows/`.

Изменения каталога сами по себе не должны запускать Android Release APK.

## Railway / API

Android-приложение не зависит от Railway или серверного API. Код api/ пока сохранён, поскольку Catalog snapshot использует api/src/radio_api/importer.py для создания публикуемой SQLite-базы radio.db. Workflow проверки серверного API и периодический Railway smoke-check удалены; это не удаляет importer и не меняет публикацию каталога.

## Структура репозитория

- **android/** — Android application, Kotlin/XML, unit tests.
- **collector/** — сборка, нормализация, фильтрация и проверка источников каталога.
- **tools/** — delta/manifest builder, диагностика и проверка опубликованного каталога.
- **api/** — FastAPI API и SQLite importer.
- **docs/** — архитектура, источники данных, UI, QA и roadmap.
- **.github/workflows/** — Android release, catalog publishing, tests и smoke checks.

При разработке придерживаться точечных исправлений, сохранять проверенное поведение и не запускать повторные сборки без необходимости. После изменений одного логического типа сначала проверять diff, затем выполнять один подходящий workflow.
