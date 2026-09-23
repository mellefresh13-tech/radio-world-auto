# Project status

Обновлено: 2026-09-23

## Текущий этап

**Сквозной MVP-контур собран:** discovery → normalization → verification → SQLite → REST API → native Android.

Параллельно продолжается доведение каталога и automotive hardening.

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
- [x] CI для collector tests;
- [x] canonical SQLite storage;
- [x] read-only REST API;
- [x] API tests;
- [x] scheduled catalog snapshot workflow;
- [x] catalog coverage report;
- [x] canonical read-only API skeleton;
- [x] paginated station API contract;
- [x] Android API client/repository layer;
- [x] Android native project;
- [x] классический XML Views UI;
- [x] Media3 / ExoPlayer 1.11.1;
- [x] MediaSessionService;
- [x] landscape-first стартовый экран.

## В работе

1. Доведение массового catalog snapshot до стабильного scheduled результата.
2. Расширение discovery: Icecast/public directories, official-site discovery и search candidates.
3. Улучшение fuzzy station/stream deduplication и source confidence.
4. Production deployment публичного API image.
5. Android hardening: Android Auto, реальные head units, network/focus edge cases.
6. Privacy/attribution/Play Store packaging.

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

## Проверка

Автоматические GitHub Actions добавлены для collector и API. Локальный запуск build из текущей среды невозможен из-за отсутствия DNS/сетевого доступа к GitHub и Maven/PyPI.

## Ограничение текущей среды

Массовый collector запускается в GitHub Actions, а не в этой среде. Количество реальных станций считаем подтверждённым только после успешного Catalog workflow и его artifact.


## CI notes — 2026-09-23

Во время реальных GitHub Actions запусков были найдены и исправлены:

1. некорректный YAML path в catalog workflow;
2. неэкранированные MP3 magic bytes в Python verifier;
3. использование dataclasses.replace для Pydantic-модели;
4. строковое "null" в URL полях Radio Browser;
5. строгая URL-валидация, ломавшаяся на отдельных реальных IDN URL логотипов;
6. несовместимый org.jetbrains.kotlin.android при AGP 9.x — Android-проект переведён на встроенный Kotlin;
7. ошибка экранирования API URL в BuildConfig.

Последний завершённый Collector CI run прошёл успешно: 15 тестов на коммите e922cae...; после последующих изменений запущены новые Collector/API/Android/Catalog workflows на актуальной ветке.

Первый реальный catalog artifact пока не считаем готовым результатом до успешного завершения актуального Catalog workflow.
