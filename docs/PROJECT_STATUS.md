# Текущий статус проекта

Обновлено: 2026-11-18.

## Исходный код и версия

- Основная ветка приложения: [main](https://github.com/mellefresh13-tech/radio-world-auto/tree/main).
- Последний подтверждённый HEAD на момент обновления: `ceac53a`.
- Последний релизный тег: **v0.3.12**, versionCode **15**.
- Версия релиза формируется release workflow по тегам; значения в корневом `version.properties` — начальная точка для автоматической нумерации, а не обязательно версия последнего опубликованного релиза.
- Последние изменения #76–#78 добавили инструменты диагностики метаданных и тематические потоки Radio ZET в curated-каталог. Они не изменяли Android-код.

## Android release

Последний подтверждённый workflow **Android Release APK** — [run #632](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/42989272009), завершился успешно.

- Тег `v0.3.12` указывает на commit `4763c41`.
- Release run #632 выполнен на commit `4763c41`, который отстаёт от текущего HEAD `main` на три коммита.
- Эти три коммита относятся к диагностическим инструментам и curated-каталогу; Android-код после релизной сборки не менялся.
- Проверки unit tests, lint, release build, постоянного signing certificate и загрузки артефактов прошли успешно.
- Артефакты run #632: APK, AAB, R8 mapping и lint report.

Текущая сборка успешна, однако её commit не совпадает с HEAD `main`. При необходимости выпуска нового APK запусти **Android Release APK** на актуальном `main`.

## Каталог

- Каталог публикуется отдельно от Android APK.
- Android получает опубликованные данные каталога и хранит локальную копию для использования на устройстве.
- Последний подтверждённый scheduled snapshot: [Catalog snapshot #275](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/44042559200), успешно завершился 2026-11-17.
- Снимок опубликован через GitHub Pages; deployment [#129](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/44043269386) завершился успешно.
- На момент snapshot: 5 587 станций, 5 581 станция с потоками, 5 825 потоков, 5 781 поток со статусом online.
- Уменьшение каталога составило 22 станции (около 0,39%); shrink guard прошёл без разрешения на намеренное уменьшение.
- Каталог и Android APK выпускаются разными workflow.

## CI и сопровождение

Основные обязательные проверки PR:
- Android unit tests;
- API and catalog metadata tests;
- Collector tests.

Последние подтверждённые запуски этих проверок успешны. Отменённые Android unit tests при последовательных обновлениях веток могут быть ожидаемым результатом настройки `cancel-in-progress`; отмена сама по себе не означает провал тестов.

Main остаётся защищённой веткой. Автоматические проверки не заменяют ручную проверку APK на целевых устройствах. Для релизной проверки используй [QA release checklist](QA_RELEASE_CHECKLIST.md).

## Документация и конфиденциальность

Firebase Analytics и Crashlytics используются в Android-клиенте. Сбор включён в текущей версии; встроенных переключателей отключения этих SDK нет. Подробности и список пользовательских событий:
- [Firebase engineering notes](FIREBASE_ANALYTICS.md)
- [Custom Analytics events](ANALYTICS_EVENTS.md)
- [Privacy policy](PRIVACY_POLICY.md)

Публичная политика конфиденциальности и Google Play Data safety form должны соответствовать фактическому поведению SDK и требованиям стран распространения. Сверяй их с текущими инженерными документами перед публичным распространением.

