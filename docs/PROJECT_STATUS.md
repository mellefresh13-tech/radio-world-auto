# Project status

Обновлено: 2026-09-23

## Текущий этап

**Этап 2 — сборка и нормализация мирового каталога.**

Параллельно ведётся **Этап 1 Android MVP**: классический native UI и аудиодвижок уже заложены, пока без подключения production API.

### Сделано

- [x] GitHub-репозиторий;
- [x] документация архитектуры и roadmap;
- [x] модель Station / Stream / SourceRecord;
- [x] адаптер Radio Browser;
- [x] адаптер IPRD;
- [x] первичная нормализация двух источников в единую модель;
- [x] базовый stream verifier;
- [x] snapshot writer;
- [x] CLI-заготовка сборки raw-normalized snapshot;
- [x] Android native project;
- [x] классический XML Views UI;
- [x] Media3 / ExoPlayer;
- [x] MediaSessionService для playback;
- [x] landscape-first стартовый экран;
- [x] описан многослойный поиск stream URL.

### Сейчас делаем

1. Полный ingestion Radio Browser.
2. Полный ingestion IPRD.
3. Реестр стран вне зависимости от покрытия конкретного каталога.
4. Нормализация жанров и языков.
5. Дедупликация станций и потоков.
6. Более строгая проверка реального аудиопотока.
7. Icecast/public-directory адаптер.
8. Официальный website crawler.
9. Первый большой snapshot каталога.
10. Read-only API для Android.

### Android

Сейчас это именно **классическое Android-приложение**, а не web-приложение и не WebView.

Стек:

- Kotlin;
- XML Views;
- AppCompat;
- AndroidX Media3 / ExoPlayer 1.11.1;
- MediaSessionService;
- landscape-first для головных устройств.

После появления API экран будет развиваться от текущего каркаса в сторону:

`Player -> Countries -> Stations -> Player`

и

`Player -> Genres -> Stations -> Player`.

### Ещё не сделано

- production backend;
- scheduled production crawl;
- полноценный search/web discovery;
- JavaScript/network extraction;
- production design всех экранов;
- offline cache каталога;
- Android Auto integration;
- Play Store release.

## Правило каталога

Название станции без подтверждённого stream URL — это **discovery candidate**, а не готовая станция.

Один station может иметь несколько рабочих streams. Не выбрасываем альтернативы только потому, что один URL уже найден.

## Проверка проекта

Collector написан с расчётом на Python 3.11+ и pytest. Android-проект предназначен для открытия в Android Studio с JDK 17.
