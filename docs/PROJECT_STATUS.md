# Project status

Обновлено: 2026-09-23

## Текущий этап

**Этап 2 — сборка и нормализация мирового каталога + первый API-слой.**

Параллельно развивается **Этап 1 Android MVP**: классический native UI и аудиодвижок уже заложены.

## Сделано

- [x] GitHub-репозиторий;
- [x] документация архитектуры, модели, UI и roadmap;
- [x] модель Station / Stream / SourceRecord;
- [x] адаптер Radio Browser;
- [x] адаптер IPRD;
- [x] discovery-адаптер Radio Garden;
- [x] первичная нормализация источников в единую модель;
- [x] resolver стран в ISO 3166-1 alpha-2;
- [x] нормализованный список базовых жанров;
- [x] базовый stream verifier;
- [x] shared HTTP/retry layer;
- [x] snapshot writer;
- [x] station merge layer;
- [x] CI для collector tests;\n- [x] canonical SQLite storage;\n- [x] read-only REST API;\n- [x] API tests;\n- [x] scheduled catalog snapshot workflow;\n- [x] canonical read-only API skeleton;
- [x] Android native project;
- [x] классический XML Views UI;
- [x] Media3 / ExoPlayer 1.11.1;
- [x] MediaSessionService;
- [x] landscape-first стартовый экран.

## В работе

1. Массовый ingestion всех доступных записей.
2. Подтверждение структуры и полей IPRD в текущих snapshot-файлах.
3. Icecast/public-directory adapter.
4. Улучшенный stream verifier с распознаванием HLS/audio Content-Type.
5. Web crawler: HTML -> JS -> JSON -> player/network discovery.
6. Search-based discovery.
7. Более надёжная дедупликация станций.
8. Формирование первого canonical candidate snapshot.
9. Backend/API.
10. Подключение Android к API.

## Важное решение по источникам

Radio Browser — основной стартовый discovery-source; API поддерживает получение всех станций и фильтры по стране, языку, тегам и другим атрибутам. citeturn278172search5

IPRD — независимый второй источник; предоставляет catalog JSON и M3U-плейлисты, в том числе по странам. citeturn278172search0turn278172search2

Icecast YP — дополнительный источник публичных Icecast-потоков. citeturn278172search1

Radio Garden — только discovery/fallback-кандидаты. Используем осторожно, потому что найденный API является внутренним/неофициальным интерфейсом. citeturn637961search0turn637961search12

## Android

Стек на 23 сентября 2026:

- AGP 9.4.0;
- Kotlin 2.3.21;
- JDK 17;
- compile/target SDK 37;
- AppCompat 1.8.0;
- Activity 1.13.0;
- Media3 1.11.1.

AGP 9.4 поддерживает API 37 и JDK 17; Media3 1.11.1 выпущен 10 сентября 2026. citeturn213705search0turn213705search1

Это именно **классическое native Android-приложение на XML Views**, а не WebView.

## Правило каталога

Название станции без подтверждённого stream URL — это discovery candidate, а не готовая станция.

Один station может иметь несколько streams. Рабочий primary stream не означает, что альтернативные ссылки нужно удалять.

## Проверка\n\nАвтоматические GitHub Actions добавлены для collector и API. Локальный запуск build из текущей среды невозможен из-за отсутствия DNS/сетевого доступа к GitHub и Maven/PyPI.\n\n## Ограничение текущей среды

Код ingestion написан и отправлен в GitHub, но массовый сетевой запуск самого collector из текущей среды пока не выполняется напрямую. Поэтому количество собранных реальных станций не выдаём за готовый результат до первого фактического snapshot.
