# Responsive UI contract

## Источник истины и разделение интерфейсов

main — текущая продуктовая и релизная ветка. Каталог публикации находится отдельно в catalog-data; это не UI-ветка.

MainActivity использует UiProfile и запускает CarMainActivity только на точном физическом разрешении 1920×720 в landscape. Все остальные варианты направляются в MobileMainActivity.

- CarMainActivity.kt и CarRadioPlaybackService.kt — изолированная automotive surface.
- MobileMainActivity.kt и RadioPlaybackService.kt — responsive phone/tablet surface.
- MainActivity.kt — только выбор поверхности и запуск нужной Activity.
- Автомобильный profile сохраняет 96 px OEM safe-area inset.
- Мобильные UI-изменения не должны попадать в автомобильную Activity/service без отдельной явной задачи.

## Responsive-профили

- Phone portrait: вертикальный Player, компактная навигация; список станций — одна колонка.
- Phone landscape: компактный horizontal Player и sidebar; число колонок зависит от ширины.
- Tablet landscape: расширенная область контента, три колонки станций и крупные читаемые карточки.
- Точный automotive 1920×720: проверенная геометрия автомобильного интерфейса; responsive tablet redesign на него не распространяется.

## Правила sidebar

В responsive landscape логотип и название приложения размещены внутри прокручиваемого списка навигации, а не закреплены как отдельный sticky header. Mini-player находится вне scroll container внизу sidebar на разделах, где он нужен, и не дублирует Player/Now Playing.

Это намеренное разделение: не переносить mini-player внутрь scroll container и не закреплять branding сверху при точечных UI-изменениях.

## Search и controls

- Search должен фокусировать строку ввода и показывать совпадения во время набора.
- На phone используется системная клавиатура; автомобильный профиль использует адаптированную экранную клавиатуру.
- Иконки transport buttons должны быть центрированы внутри фиксированного viewport, без смещения из-за внутренних отступов вектора.
- Shuffle остаётся отдельным действием случайного выбора станции.
- NEXT/PREV с руля переключают последовательные станции каталога; их не заменять экранной Shuffle-логикой.

## Нерегрессионные правила

- Не менять playback/reconnect/fallback при чисто UI-правках.
- Не менять metadata и startup station restoration.
- Не менять сохранение Favorites/Recently Played.
- Не менять управление с руля.
- Не изменять вид и геометрию CarMainActivity для responsive-only задач.
- Не добавлять полноэкранный app title bar, дублирующий системную строку состояния.
- Проверять, что локальное нажатие не перезапускает live stream и не вызывает заметное мерцание.
- Если общая модель данных или playback service всё же меняется, явно проверять обе поверхности.

## Текущий статус

- Phone portrait/landscape и tablet landscape responsive implementation присутствуют в main.
- По словам пользователя, последний визуальный baseline корректно выглядит на автомобильном head unit; эталонный исходный commit — a97e193, текущий main merge — f0bad2b.
- Android Release workflow run #463 для main завершился успешно. Это compile/CI result, а не автоматическое доказательство каждого сценария на каждом физическом размере экрана.

Тестовая матрица остаётся ориентиром для последующей QA: 360×640, 360×800, 390×844, 412×915, 430×932 для portrait; 800×360, 854×480, 915×412 и 1280×720 для landscape; 1920×720 для автомобиля.
