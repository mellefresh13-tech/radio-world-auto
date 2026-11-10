# Текущий статус проекта

Обновлено: 2026-11-10.

## Исходный код и версия

- Основная ветка приложения: [main](https://github.com/mellefresh13-tech/radio-world-auto/tree/main).
- Последний релизный тег: **v0.3.11**, versionCode **14**.
- Автоматическая нумерация релизов использует Git-теги; значения в `version.properties` служат начальными значениями, а не обязательно текущей версией последнего тега.
- В `main` включены расширенные Firebase Analytics events и обновления GitHub Actions/Python-зависимостей.

## Release APK

Последняя подтверждённая release-сборка: [Android Release APK #631](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/42821327789), версия **0.3.11**, versionCode **14**, собрана на commit `f914bdc`.

Workflow успешно выполнил unit tests, Android lint, release APK/AAB build, проверку постоянного сертификата подписи и загрузку APK, AAB и R8 mapping artifact. Этот запуск предшествует последним dependency-only PR #65 и #66, которые не меняли Android-исходники. Для новой раздачи после текущих изменений можно запустить **Android Release APK** вручную на актуальном `main`; использовать artifact `radio-world-auto-release`.

## Каталог

- Каталог публикуется отдельно от Android APK через ветку `catalog-data`.
- Android использует опубликованные данные и локальную копию каталога.
- Последний проверенный scheduled snapshot: [run #259](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/42774616761), успешно завершившийся 10 ноября 2026 года.
- Сборка APK и публикация каталога — независимые процессы.

## Проверки и CI

Обязательные проверки pull request:
- Android unit tests;
- API and catalog metadata tests;
- Collector tests.

Изменения следует проводить небольшими PR. Успешный CI подтверждает автоматические проверки, но не заменяет ручное тестирование APK на целевых устройствах. Ручные проверки фиксируются отдельно по [QA release checklist](QA_RELEASE_CHECKLIST.md).

## Аналитика и конфиденциальность

- Firebase Analytics используется для событий запуска, воспроизведения, прослушивания, поиска, избранного, навигации и ошибок воспроизведения.
- Firebase Crashlytics используется для автоматической диагностики фатальных сбоев и поддерживаемых ANR.
- Сбор Analytics и Crashlytics включён в текущей версии; отдельного переключателя отключения в приложении нет.
- Политика конфиденциальности и документация по Firebase должны обновляться вместе с изменениями SDK и событий.

Подробности: [Analytics events](ANALYTICS_EVENTS.md), [Firebase Analytics and Crashlytics](FIREBASE_ANALYTICS.md), [Privacy policy](PRIVACY_POLICY.md).

## Принципы сопровождения

- Не объединять несвязанные изменения в один PR.
- Не менять Android-код ради документационного обновления.
- Не запускать release APK при каждом обычном merge в `main`.
- Поддерживать каталог отдельно от исходников приложения.
- Считать этот файл снимком подтверждённого состояния на дату обновления, а не историей всех экспериментов.
