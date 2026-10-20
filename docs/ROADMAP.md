# Roadmap

Обновлено: 2026-10-20

## Выполненная основная работа

- [x] main остаётся веткой интеграции и релизов; experiment/lava-lamp-player предназначена для разработки и не переносится в main автоматически.
- [x] Изолированный автомобильный UI-профиль 1920×720 и responsive phone/tablet UI.
- [x] Native Android UI на Kotlin/XML Views.
- [x] Media3 playback services, MediaSession и управление с внешних устройств.
- [x] Полный локальный каталог для browse/search/filter и навигации, gzip cache.
- [x] GitHub manifest/delta synchronization и fallback на полный snapshot.
- [x] Альтернативные stream URL, reconnect/failover и stream recovery policy.
- [x] ICY/ID3 metadata parser и отдельные artist/title для MediaSession.
- [x] Сохранение последней станции, Favorites и Recently Played.
- [x] Live search с фокусом строки и обновлением результатов во время ввода.
- [x] Тело mini-player открывает Player/Now Playing; Play/Pause и Favorite остаются отдельными действиями.
- [x] Catalog debug page и ручная команда проверки опубликованной станции.
- [x] Лимит до 500 станций на страну.
- [x] Автоматическая публикация catalog snapshot каждые 6 часов.
- [x] Android release signing/alignment/apksigner validation и artifact upload в CI.
- [x] Визуальный baseline текущего автомобильного профиля проверен пользователем на реальном head unit.
- [x] Последняя release-сборка — run [#616](https://github.com/mellefresh13-tech/radio-world-auto/actions/runs/39417285955) на `e3d96c5`; unit tests, lint, подпись и fingerprint check прошли 2026-10-20. После неё PR #30–36 обновили только конфигурацию CI и Python runtime pins; их проверки прошли, свежая APK-сборка на актуальном [`main`](https://github.com/mellefresh13-tech/radio-world-auto/tree/main) ещё не запускалась.

## Текущие QA-задачи

- [ ] Запустить Android Release APK на текущем `main` (`4929042`) и проверить успешную сборку, постоянную подпись/fingerprint и загрузку `radio-world-auto-release`.
- [ ] Установить APK из нового run и проверить обычную работу плюс обновление поверх предыдущей версии.
- [ ] После ближайшего планового запуска проверить автоматическую сборку и публикацию каталога: оба job успешны, артефакты и `catalog-data` обновлены.
- [ ] На телефоне, планшете и head unit проверить переход в Player/Now Playing нажатием по телу mini-player и независимость кнопок Play/Pause и Favorite.

- [ ] Повторяемая проверка steering-wheel NEXT/PREV на целевом устройстве, включая manual station change и переход через конец/начало каталога.
- [ ] Проверка на реальном head unit всех playback edge cases: fallback между URL, reconnect, обновление metadata и восстановление после обновления каталога.
- [ ] Проверка восстановления стартовой станции, если сохранённая станция удалена из следующего snapshot.
- [ ] Проверка responsive tablet landscape на физическом устройстве и на дополнительных разрешениях.
- [ ] Повторить визуальную проверку после любых будущих изменений automotive-specific Kotlin/XML.
- [ ] Рассмотреть дополнительные автоматизированные тесты интеграции MediaSession команд; unit test selector не проверяет доставку команды головным устройством.

Эти пункты — регрессионная проверка поведения на устройстве, а не утверждение, что текущая сборка уже содержит известную поломку.

## Каталог

- [x] Активные источники: Radio Browser, IPRD, curated records.
- [x] Нормализация, merge, проверка stream URL и FM-oriented filtering.
- [x] Ограничение 500 станций на страну.
- [x] Сборка SQLite, manifest, delta и debug snapshot.
- [x] Публикация в отдельную orphan-ветку catalog-data.
- [x] Semantic version игнорирует last_checked_at/discovered_at.
- [x] Ручной catalog health workflow и автоматический шестичасовой snapshot.
- [ ] При добавлении нового discovery adapter включить его в scheduled CLI, покрыть тестами и только после этого считать активным источником.

## Вспомогательный API

- [x] Read-only FastAPI/SQLite API.
- [x] Endpoint smoke checks для health, countries, genres и station search.
- [x] Периодическое обновление отдельной SQLite из catalog-data.
- [ ] Дополнительное мониторирование и резервное копирование API при необходимости.

API остаётся отдельным служебным компонентом и не является runtime dependency Android.

## Позже / вне текущей цели

- [ ] Дополнительные автомобильные разрешения кроме текущего эталона 1920×720.
- [ ] Android Auto integration.
- [ ] Play Store packaging.
- [ ] Окончательная политика production signing и обновления APK для внешних пользователей.

## История актуализации

### 2026-10-20 — Release/security status refreshed
- Зафиксированы merge security PR #39 и актуальный `main` (`e3d96c5`).
- Обновлена ссылка на Android Release APK run #616: unit tests, lint, подпись и fingerprint check прошли.
- Ожидают внешней проверки плановая публикация каталога и ручной smoke test на устройстве.

### 2026-10-17 — Documentation aligned to branch baseline
- Обновлены ссылки на актуальный HEAD main и отделены факты исторической CI-сборки от текущего состояния ветки.
- Зафиксировано открытие Player/Now Playing касанием по телу мини-плеера.
- Исправлены устаревшие описания отсутствующего workflow Production API smoke.

### 2026-10-11 — Tested baseline and repository docs
- Проверенное состояние автомобильного UI из a97e193 продвинуто в main (merge commit f0bad2b).
- Android Release APK run #463 для main завершился успешно; artifact radio-world-auto-release загружен.
- Актуализированы сведения о ветках, изоляции UI, steering-wheel navigation, catalog-data orphan publication и workflow.

### 2026-10-02 — Catalog and playback reliability
- Android runtime switched to GitHub catalog-data as its only remote catalog source.
- Full snapshot at first launch; manifest/delta sync on subsequent refreshes.
- Playback failover uses the full local station catalog.
- Shared station selector and track metadata parser have unit tests.

### 2026-09-25 — Native Android release candidate
- Automotive/responsive UI was implemented directly in Kotlin/XML.
- Cache disk I/O, metadata handling and playback recovery were hardened.
