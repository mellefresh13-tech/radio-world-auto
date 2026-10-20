# Android app

Нативное Android-приложение на Kotlin + XML Views с Media3/ExoPlayer. `main` — единственная актуальная ветка интеграции и релизов. Документ сверяется с текущим `main`; экспериментальные и refactor-ветки — история разработки, не источник релизного состояния.

## Точка входа и UI-профили

MainActivity выбирает экран через UiProfile:
- точный физический landscape-профиль 1920×720 — CarMainActivity;
- другие размеры и ориентации — MobileMainActivity.

CarMainActivity и CarRadioPlaybackService — изолированная автомобильная поверхность. MobileMainActivity и RadioPlaybackService — responsive phone/tablet поверхность. Не переносить мобильные UI-изменения в автомобильные классы без отдельной задачи и проверки.

Исторический визуальный baseline автомобиля был исходно зафиксирован в `a97e193` и продвинут в `main` коммитом `f0bad2b`. Текущий `main` — [e3d96c5](https://github.com/mellefresh13-tech/radio-world-auto/commit/e3d96c548f0bd815ee58895546e523cdce0f3939). Release APK для этого коммита собран в [run #616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955); прохождение CI не заменяет ручной device QA.

## Интерфейс

Основные разделы: Player, Countries, Genres, Favorites, Recently Played и Search. Экран Station Info не показывается пользователю.

Автомобильный профиль рассчитан на 1920×720 landscape, с левой навигационной rail и 96 px OEM safe inset. В нём крупные transport controls и крупная кнопка Play/Pause. Верхний дублирующий app title bar не используется.

В responsive-интерфейсе используются отдельные размеры и навигация для phone portrait, phone landscape и tablet landscape. Ширина списка определяет число колонок: станции — одна колонка в portrait и до трёх в landscape; страны и жанры адаптируются под доступную ширину.

В landscape sidebar логотип и название приложения находятся в прокручиваемом блоке навигации, а не закреплены сверху. Mini-player располагается снизу sidebar вне прокручиваемой части, когда он нужен на текущем разделе; на самом Player его не следует дублировать. Касание по телу мини-плеера вне Play/Pause и Favorite открывает Player/Now Playing; эти кнопки остаются самостоятельными действиями.

Search фокусирует строку ввода и обновляет совпадения во время печати. На обычном телефоне используется системная клавиатура; в автомобильном профиле поддерживается экранная клавиатура.

## Catalog runtime

Приложение не зависит от Railway, FastAPI или отдельного сервера. Удалённый источник каталога — GitHub Raw, ветка catalog-data.

Публикуемые файлы:
- data/stations.json;
- data/catalog-manifest.json;
- data/catalog-delta.json;
- data/radio.db (служебный/резервный формат, не основной формат Android).

Первый запуск загружает полный snapshot. Следующие синхронизации проверяют semantic version в manifest, пропускают загрузку при совпадении версии, применяют delta при совпадении local version и base_version или безопасно переходят на полный snapshot. Локальная копия хранится в gzip cache. Browse, Search, Countries, Genres, Favorites, Recently Played и обычный выбор станции работают по локальному snapshot.

## Playback и внешнее HMI

Playback использует Media3/ExoPlayer, MediaSessionService и MediaSession. Оба сервиса используют CatalogNavigationPlayer — ForwardingPlayer, который объявляет NEXT/PREV доступными и направляет варианты seekToNext/seekToPrevious к выбору станции из полного локального каталога. Это необходимо, потому что очередь ExoPlayer может содержать только текущий stream, а не весь каталог.

Экранная Shuffle выбирает случайную станцию. NEXT/PREV с руля переключают по порядку каталога с циклическим переходом на его границах.

Для одной станции поддерживается несколько stream URL. При ошибке потоков применяется fallback/reconnect и предусмотренный выбор следующей доступной станции. Не добавлять все варианты одного потока в очередь одновременно.

Поддерживаются ICY StreamTitle, ID3 Artist/Title и разбор Artist - Track. Artist и title в MediaSession/HMI хранятся раздельно. Если live metadata отсутствует, title для внешнего HMI заменяется названием станции. Изменение metadata не должно перезапускать эфир.

Последняя выбранная станция сохраняется и восстанавливается, если присутствует в актуальном локальном каталоге. При удалении задачи приложения из Recents сервис вызывает остановку playback.

## CI / release APK

Workflow находится в `.github/workflows/android.yml` и называется **Android Release APK**. Последняя подтверждённая сборка текущего `main` — [run #616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955) на `e3d96c5`, успешно завершившаяся 20 октября 2026 года. Unit tests, lint, release build, подписание и проверка постоянного сертификата завершились успешно.

CI использует JDK 17 и Gradle 9.6.0. Gradle Wrapper в репозитории отсутствует. Workflow выполняет:
1. unit tests через Gradle;
2. Android lint и загрузку отчёта (lint пока не блокирует APK);
3. release build;
4. подписание и выравнивание APK;
5. apksigner verify;
6. upload artifact.

Артефакт APK называется **radio-world-auto-release**. Откройте Actions run и скачайте его в разделе Artifacts. Отдельно загружается android-lint-report.

Подпись: релизный job запускается только на `main` и использует GitHub Environment `android-release-signing`. Постоянная подпись требует environment secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` и `ANDROID_SIGNING_CERT_SHA256`. Для постоянного ключа отпечаток обязателен и проверяется до подписи. Если используются временные тестовые ключи, artifact получает отдельное имя `radio-world-auto-test-build`.

Локально из папки android доступны Gradle-задачи :app:test, :app:lintRelease и :app:assembleRelease, если установлены JDK 17 и Gradle 9.6.0.

## Точечные изменения и регрессии

Перед изменением playback проверять оба сервиса, текущую и соседние станции, NEXT/PREV, metadata в приложении и MediaSession, восстановление последней станции, ошибки stream и остановку после swipe-away. Перед мобильным UI-изменением отдельно убеждаться, что точный 1920×720 профиль и автомобильный playback не изменены.
