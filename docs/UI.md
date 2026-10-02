# UI — classic automotive Android

## Основной принцип

Приложение должно ощущаться как автомобильное радио, а не как обычный телефонный каталог: крупные touch-targets, минимум второстепенной информации, быстрый доступ к playback и навигации.

Основной эталон — automotive landscape layout **1920×720** для Belgee X50 / Geely Coolray-подобного головного устройства.

## Player

В landscape используется постоянная левая navigation rail. Верхний app title bar не является частью приложения: системная строка состояния ОС уже предоставляет время/связь.

Основная иерархия Player:

- текущая станция и логотип;
- страна / жанр;
- текущий artist / track;
- большой центральный Play/Pause;
- компактные Previous / Next;
- Shuffle и Browse/Source как вторичные действия;
- Favorite доступен непосредственно из блока станции/трека;
- mini-player используется на остальных разделах.

Кнопки должны оставаться крупными и пригодными для управления в автомобиле.

## Navigation

В automotive landscape главный способ навигации — **постоянная левая rail**, а не нижние tabs.

Основные разделы:

- **Player** — текущая станция и playback;
- **Countries** — страна -> список станций;
- **Genres** — жанр -> список станций;
- **Favorites** — сохранённые станции;
- **Recently Played** — последние станции;
- **Search** — поиск по названию, стране и жанру.

На телефонах layout адаптируется под portrait/landscape. Нижняя навигация допустима только как адаптация для маленького portrait-экрана; она не является эталоном automotive UI.

### Sidebar structure

For landscape sidebar:
- the application logo and name scroll together with the navigation items;
- the logo/name are not a fixed or sticky header;
- the mini-player remains pinned to the bottom of the sidebar.

This fixed/scrolling split is intentional.

## Состояние playback

Play/Pause синхронизируется между Player и mini-player.

При смене станции обновляются station name, logo, country/genre, metadata и связанные элементы mini-player.

`Shuffle` выбирает случайную станцию. Физические `NEXT` и `PREV` на руле переключают соседние доступные станции по порядку каталога, идентично экранным Previous / Next.

## Startup

При запуске восстанавливается последняя успешно проигрывавшаяся станция. Если она отсутствует в актуальном каталоге после refresh, сохранённое состояние сбрасывается и выбирается первая доступная станция.

## Ошибка stream

Пользователь не должен разбираться с URL.

```text
primary stream
   ↓ error
fallback stream
   ↓ error
limited reconnect
   ↓
next catalog station
   ↓
OFFLINE
```

Уже проваленные станции не повторяются в рамках одного recovery-цикла.

## Metadata / HMI

Artist и title нормализуются из отдельных полей или `Artist - Track`. В приложении они могут отображаться вместе, а в MediaSession/HMI передаются раздельно.

Если track metadata отсутствует, для внешнего HMI title заменяется названием станции. Обновление metadata не должно перезапускать live stream.

## Lists / visual rules

- Countries and Genres target three items per row where the viewport allows it without harming readability.
- Active navigation state must not produce an unintended green full-screen/tab background.
- Info / Station Details is not shown to the user.

## Design scope

Основной QA-профиль — automotive **1920×720 landscape**. Дополнительно проверяются portrait и landscape мобильные профили. Layout должен адаптироваться без потери критических transport controls и навигации.
