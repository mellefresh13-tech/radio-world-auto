# Radio World Auto

Android-приложение мирового интернет-радио для автомобилей.

## Актуальная ветка

**`ui/responsive-mobile` — актуальная рабочая ветка Android-проекта.**

Она содержит текущий responsive UI для phone/tablet/automotive, playback-логику и исправления каталога.

## Интерфейс и управление

Приложение использует automotive-oriented UI: крупная типографика, увеличенные карточки и touch-targets, выделенная кнопка Play/Pause и компактный mini-player на остальных вкладках.

Текущее поведение плеера:
- Play/Pause синхронизируется во всех местах интерфейса;
- избранное синхронизировано между экраном плеера, списками и mini-player;
- при смене станции обновляются название станции, логотип, страна/жанр, текущий трек и связанные элементы mini-player;
- экранная кнопка **Shuffle** выбирает случайную станцию;
- физическая кнопка **NEXT** на руле переключает на следующую доступную станцию по порядку каталога;
- физическая кнопка **PREV** на руле переключает на предыдущую доступную станцию по порядку каталога;
- при каждом переключении сохраняется актуальное состояние избранного и metadata;
- для станции перебираются доступные stream URL при ошибке воспроизведения;
- автоматический reconnect для временных ошибок остаётся включённым;
- при сворачивании/удалении приложения из списка задач воспроизведение останавливается через playback service.

### Навигация и состояние

- страны и жанры сохраняют выбранный раздел и позицию прокрутки;
- город станции не выводится в карточке станции;
- служебная надпись Catalog Online не используется;
- индикатор Sync показывается только во время фактической синхронизации каталога;
- каталог кешируется локально и доступен без сети;
- последняя выбранная станция восстанавливается при запуске, если она присутствует в актуальном локальном каталоге.

## Android release

Рабочий процесс:

`правка -> ui/responsive-mobile -> GitHub Actions -> release APK`

APK публикуется как artifact **`radio-world-auto-release`**.

Android Release workflow запускается:
- вручную через `workflow_dispatch`;
- автоматически при изменениях внутри `android/**` или самого `.github/workflows/android.yml`.

Перед release-сборкой workflow выполняет unit tests и Android lint.

Изменения каталога/collector/backend сами по себе Android Release не запускают.

### Подпись APK

Текущая release-сборка использует постоянный signing key, если соответствующие GitHub Secrets настроены; иначе workflow использует временный ключ для внутреннего тестирования. Постоянный production signing key следует хранить в GitHub Secrets.

## GitHub catalog — единственный runtime-источник

**Android runtime не использует Railway или отдельный сервер/API.**

Каталог формируется collector workflow и публикуется в отдельную ветку **`catalog-data`**. Android читает данные напрямую из GitHub Raw и хранит локальную копию.

Схема:

```text
External sources
      ↓
collector / normalization / filtering / verification
      ↓
GitHub catalog-data
      ├── stations.json
      ├── catalog-manifest.json
      └── catalog-delta.json
               ↓
          Android cache
               ↓
        Media3 / ExoPlayer
```

### Синхронизация каталога

**Первый запуск:**

```text
нет локального каталога
        ↓
полный stations.json
        ↓
локальный gzip cache
```

**Последующие запуски:**

```text
локальный catalog_version
        ↓
manifest.json
        ↓
version совпала → ничего не скачивать
        ↓
version изменилась
        ↓
если local version == base_version
        → скачать catalog-delta.json
        → применить updated + removed_ids
        ↓
если delta неприменима
        → безопасный fallback на полный stations.json
```

Локальный каталог используется для навигации, поиска, Favorites/Recent и playback. Если GitHub временно недоступен, уже сохранённый каталог продолжает работать.

Каталог обновляется collector workflow каждые 6 часов. Android не скачивает полный каталог при каждом запуске.

### Публикуемые данные

Каждый успешный snapshot формирует:
- `stations.json` — основной источник станций для Android;
- `radio.db` — резервный/служебный вариант;
- `catalog-manifest.json` — версия и статистика snapshot;
- `catalog-delta.json` — изменения относительно предыдущего snapshot.

Публикация:

```text
catalog-data/
└── data/
    ├── stations.json
    ├── radio.db
    ├── catalog-manifest.json
    └── catalog-delta.json
```

Android использует основной каталог напрямую из GitHub Raw.

### Источники и фильтрация

Сборщик объединяет Radio Browser, IPRD и curated-источники. Каталог проходит normalization, deduplication, stream verification и FM-oriented filtering. Curated-семейства и важные станции защищены от удаления из-за временной недоступности источника.

## Railway / API

В репозитории сохраняется backend/API-код как отдельная историческая/служебная часть проекта и возможный инструмент диагностики/импорта.

**Он не является зависимостью Android runtime и не нужен для работы установленного приложения.**

## Структура

```text
android/     → Android application
collector/   → catalog collection and filtering
api/         → backend/import/diagnostic code, not Android runtime dependency
tools/       → catalog build and validation tools
docs/        → architecture, UI, data and project documentation
.github/     → Android and catalog workflows
```
