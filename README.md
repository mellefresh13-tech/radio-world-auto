# Radio World Auto

Android-приложение мирового интернет-радио для автомобилей и сенсорных головных устройств.

## Идея

Собственный постоянно обновляемый каталог радиостанций со всех доступных стран.

Мы не привязываемся к одному стороннему каталогу. Внешние каталоги и интернет используются для discovery, затем записи нормализуются, объединяются, проверяются и попадают в собственный canonical catalog.

## Android

Это **классическое native Android-приложение**:

- Kotlin
- XML Views
- AppCompat
- AndroidX Media3 / ExoPlayer
- MediaSessionService
- landscape-first UI
- крупные touch controls

Никакого WebView как основы приложения.

## Пользовательские разделы

- **Player**
- **Countries**
- **Genres**
- **Favorites**
- **Search**

Основной сценарий рассчитан на минимальное количество действий во время поездки.

## Каталог

Пайплайн:

```
Radio Browser
IPRD
Icecast / public directories
Official station websites
Search discovery
       |
       v
Raw records
       |
       v
Normalization
       |
       v
Deduplication
       |
       v
Stream verification
       |
       v
Canonical catalog
       |
       v
Own API
       |
       v
Android
```

У одной станции может быть несколько рабочих потоков. Приложение сможет использовать fallback, если основной stream перестал работать.

## Документация

- [Project status](docs/PROJECT_STATUS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data sources](docs/DATA_SOURCES.md)
- [Data model](docs/DATA_MODEL.md)
- [Roadmap](docs/ROADMAP.md)

## Текущий статус

Сейчас готов фундамент каталога и Android-приложения. Следующая крупная цель — получить первый реальный объединённый snapshot станций и рабочих потоков, а затем подключить к нему API.

## Текущие технологические версии

На 23 сентября 2026 года Android-часть ориентирована на AGP 9.4.0, Kotlin 2.3.21, AppCompat 1.8.0, Activity 1.13.0 и Media3 1.11.1.
