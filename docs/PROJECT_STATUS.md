# Project status

Обновлено: 2026-09-25

## Текущий этап

**Android MVP собран в единую актуальную версию на `main`. Release APK успешно собирается и подписывается в GitHub Actions.**

Текущая цепочка:

`catalog/discovery -> SQLite -> REST API -> Railway -> native Android -> release APK`

Основные решения из HMI-ветки, native redesign и последующих patch-правок сведены в `main`.

## Что сейчас считается актуальным

- `main` — актуальная ветка Android-кода.
- Landscape HMI 1920x720 для автомобильного сценария.
- Adaptive UI для portrait/landscape через UI profiles.
- Native Kotlin/XML Views.
- Media3/ExoPlayer + MediaSessionService.
- Countries / Genres / Favorites / Recently Played / Search / Station Details.
- Большая центральная Play/Pause-кнопка и автомобильные Prev/Next/Shuffle/Source controls.
- Несколько stream URL с fallback/retry.
- ICY/HLS ID3 metadata.
- Локальный catalog cache.
- Cache I/O вынесен с UI-потока, чтобы не блокировать главный поток при старте/сохранении каталога.
- Production API: `https://radio-world-auto-production.up.railway.app/`.

## CI / Release APK

Для Android оставлен один workflow:

`.github/workflows/android.yml` — **Android Release APK**.

Он:
1. собирает `assembleRelease`;
2. создаёт временный release keystore для CI;
3. выравнивает и подписывает APK;
4. выполняет `apksigner verify`;
5. публикует artifact `radio-world-auto-release`.

Последняя проверка:

- workflow run: **#92**
- commit: `b1122b4`
- результат: **success**
- release artifact: **5.46 MB**
- SHA-256 artifact: `67d7774fd45e6bff18c23d2d584558e3457324babea9220735494185dc7c947e`

Это подтверждает корректную CI-сборку и подпись установочного APK. Реальное поведение на физической магнитоле ещё требует отдельной проверки.

## Ветки

Актуальной для Android считается `main`.

Старые Android/HMI-ветки не содержат изменений поверх текущего `main`:

- `android-final` — отстаёт на 10 коммитов;
- `feature/android-html-hmi-1920x720` — отстаёт на 18 коммитов;
- `feature/native-hmi-redesign-from-html-reference` — отстаёт на 20 коммитов;
- `native-hmi-redesign-from-html-reference` — отстаёт на 20 коммитов.

`catalog-data` — отдельная ветка с данными каталога; она не является Android-веткой и имеет отдельную историю.

## История последних этапов

1. Собран базовый Android MVP и подключён production API.
2. Добавлены cache, metadata, fallback/retry и adaptive orientation.
3. Перенесён автомобильный HMI 1920x720.
4. Сведены player controls, 3-column catalogs и визуальные решения reference HMI.
5. Добавлены vector icons, details/info UI и единые transitions.
6. Исправлены ошибки XML/UI, возникшие при объединении изменений.
7. Cache file I/O вынесен из UI thread.
8. CI очищен до одного Android release workflow.
9. Release APK успешно собран, подписан и проверен.

## Что осталось

1. Установить APK на реальный head unit и проверить запуск/переключение экранов.
2. Проверить реальное воспроизведение нескольких типов потоков, fallback и reconnect.
3. Проверить metadata и поведение при плохом/отсутствующем интернете.
4. Проверить portrait и landscape отдельно.
5. После физического теста зафиксировать найденные проблемы и сделать следующий release.

## Ограничение текущей подписи

CI сейчас использует временный keystore. Это подходит для тестового установщика, но **не является постоянным release signing key**.

Перед публичным релизом/обновлением установленного приложения нужен постоянный keystore и сохранённая схема подписи.
