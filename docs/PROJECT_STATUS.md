# Текущий статус проекта

Обновлено: 2026-11-10.

## Исходный код и версия

- Основная ветка приложения: [main](https://github.com/mellefresh13-tech/radio-world-auto/tree/main).
- Последний подтверждённый HEAD на момент обновления: `6816d15`.
- Последний релизный тег: **v0.3.11**, versionCode **14**.
- Версия релиза формируется release workflow по тегам; значения в корневом `version.properties` — начальная точка для автоматической нумерации, а не обязательно версия последнего опубликованного релиза.
- Последние обновления зависимостей #65 и #66 включены в main. PR #67 закрыт как заменённый объединённым обновлением в #66.

## Android release

Последний подтверждённый workflow **Android Release APK** — [run #631](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/42821327789), завершился успешно 2026-11-10.

- Тег `v0.3.11` указывает на commit `f914bdc`.
- Этот release run выполнен на commit `f914bdc`, до включения PR #65 и #66 в текущий HEAD.
- Проверки unit tests, lint, release build, постоянного signing certificate и загрузки артефактов прошли успешно.
- Артефакты run #631: `radio-world-auto-release` (APK), `radio-world-auto-release-aab` (AAB), `radio-world-auto-r8-mapping` и `android-lint-report`.

PR #65 и #66 обновляли только GitHub Actions и Python dependency pins; Android-код не менялся. Тем не менее run #631 не следует называть сборкой с точного текущего HEAD `main`. Для нового релиза запусти **Android Release APK** на актуальном `main`.

## Каталог

- Каталог публикуется отдельно от Android APK.
- Android получает опубликованные данные каталога и хранит локальную копию для использования на устройстве.
- Последний подтверждённый scheduled snapshot: [Catalog snapshot #259](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/42774616761), успешно завершился 2026-11-10.
- Каталог и Android APK выпускаются разными workflow.

## CI и сопровождение

Основные обязательные проверки PR:
- Android unit tests;
- API and catalog metadata tests;
- Collector tests.

Последние Dependabot обновления #65 и #66 были синхронизированы с актуальным main и прошли обязательные проверки до слияния. Main остаётся защищённой веткой.

Автоматические проверки не заменяют ручную проверку APK на целевых устройствах. Для релизной проверки используй [QA release checklist](QA_RELEASE_CHECKLIST.md).

## Документация и конфиденциальность

Firebase Analytics и Crashlytics используются в Android-клиенте. Сбор включён в текущей версии; встроенных переключателей отключения этих SDK нет. Подробности и список пользовательских событий:
- [Firebase engineering notes](FIREBASE_ANALYTICS.md)
- [Custom Analytics events](ANALYTICS_EVENTS.md)
- [Privacy policy](PRIVACY_POLICY.md)

Перед публичным распространением необходимо проверить актуальность политики конфиденциальности и Google Play Data safety form с учётом фактического поведения SDK и требований стран распространения.
