# Architecture

## Общая схема

```
External sources
    |
    v
Collectors -> Raw records -> Normalization -> Deduplication
                                             |
                                             v
                                      Stream verification
                                             |
                                             v
                                      Canonical catalog
                                             |
                              +--------------+--------------+
                              |                             |
                              v                             v
                             API                       snapshots
                              |
                              v
                        Android app
                              |
                              v
                         Media3 player
```

## Каталог

Каждый внешний источник подключается отдельным адаптером. Адаптер только получает данные и приводит их к raw-модели. Каноническая модель формируется после normalization/deduplication/verification.

Источники не считаются взаимозаменяемыми: для каждой записи сохраняется provenance.

## Stream discovery

Уровни обнаружения:

1. официальный сайт станции;
2. прямой stream URL на официальном сайте;
3. HTML/JS/JSON;
4. web-player network requests;
5. публичные каталоги;
6. поисковое обнаружение.

Найденные потоки проходят единый verifier.

## Android

Приложение — обычный native Android project:

- Kotlin;
- XML Views;
- AppCompat;
- Media3 / ExoPlayer;
- playback service;
- landscape-first;
- большие touch targets.

Compose на первом этапе не используется: нужен классический Android UI, простой для поддержки и хорошо подходящий для головных устройств.

## Основные экраны

**Player** — текущая станция и большие кнопки управления.

**Countries** — страна -> список станций.

**Genres** — жанр -> список станций.

**Favorites** — быстрый доступ к сохранённым станциям.

**Search** — поиск по названию, стране, жанру.

## Автомобильный сценарий

```
Запуск
  -> последняя станция
  -> Play
  -> смена станции
  -> избранное
```

Не перегружаем экран настройками и второстепенной информацией.
