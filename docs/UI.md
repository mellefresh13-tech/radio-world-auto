# UI — classic automotive Android

## Основной принцип

Это приложение должно ощущаться как автомобильное радио, а не как обычный телефонный каталог.

### Первый экран

```
┌───────────────────────────────────────────────┐
│ RADIO WORLD                                  │
│                                               │
│                STATION NAME                   │
│             COUNTRY · GENRE                  │
│                                               │
│                   ▶ / ❚❚                     │
│                                               │
│      ◀ previous        next ▶                 │
│                                               │
├───────────────────────────────────────────────┤
│ COUNTRIES │ GENRES │ FAVORITES │ SEARCH       │
└───────────────────────────────────────────────┘
```

## Navigation

Главные разделы всегда доступны из нижней панели.

**Countries**

Country -> station list -> player.

**Genres**

Genre -> station list -> player.

**Favorites**

Список сохранённых станций без промежуточных экранов.

**Search**

Поиск по названию, стране и жанру.

## Размер элементов

Для головных устройств используем крупные кнопки и увеличенные интервалы. Нельзя строить критические действия вокруг маленьких icon-only controls.

## Playback

Главное действие — Play/Pause.

Дополнительные:

- previous station;
- next station;
- favorite;
- открыть список станций.

## Startup

Планируем запуск последней выбранной станции без обязательного ручного поиска.

## Ошибка stream

Пользователь не должен разбираться с URL.

```
primary stream
   ↓ error
fallback stream
   ↓ error
next fallback
   ↓
"No working stream"
```

При успешном fallback станция продолжает воспроизводиться.

## Design scope

Первый production UI будет сначала отлаживаться в landscape. Portrait не является основным режимом первой версии.
