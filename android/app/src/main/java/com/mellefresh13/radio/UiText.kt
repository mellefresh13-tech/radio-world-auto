package com.mellefresh13.radio

import android.content.Context

/**
 * Translates fixed interface copy that predates Android string resources.
 * Station, country, genre and live metadata values remain source-provided and unchanged
 * unless they exactly match a known interface label.
 */
internal object UiText {
    private val russian = mapOf(
        "PLAYER" to "ПЛЕЕР",
        "Internet restored" to "Интернет восстановлен",
        "BUFFER TIMEOUT" to "ТАЙМ-АУТ БУФЕРА",
        "STREAM ERROR" to "ОШИБКА ПОТОКА",
        "CONNECTING..." to "ПОДКЛЮЧЕНИЕ...",
        "Restoring last station" to "Восстановление последней станции",
        "COUNTRIES" to "СТРАНЫ",
        "FAVORITES" to "ИЗБРАННОЕ",
        "RECENT" to "НЕДАВНИЕ",
        "SEARCH" to "ПОИСК",
        "GENRES" to "ЖАНРЫ",
        "SETTINGS" to "НАСТРОЙКИ",
        "Ready to play" to "Готово к воспроизведению",
        "Choose a station from Countries, Genres or Search" to "Выберите станцию в разделах «Страны», «Жанры» или «Поиск»",
        "NOW PLAYING" to "СЕЙЧАС ИГРАЕТ",
        "Favorite" to "В избранное",
        "Shuffle" to "Случайная станция",
        "Previous station" to "Предыдущая станция",
        "PAUSE" to "ПАУЗА",
        "PLAY" to "ИГРАТЬ",
        "Next station" to "Следующая станция",
        "BROWSE" to "ОБЗОР",
        "Countries" to "Страны",
        "Genres" to "Жанры",
        "STATIONS" to "СТАНЦИИ",
        "Loading stations…" to "Загрузка станций…",
        "CATALOG ERROR" to "ОШИБКА КАТАЛОГА",
        "Unable to load stations" to "Не удалось загрузить станции",
        "LIBRARY" to "МЕДИАТЕКА",
        "Favorites" to "Избранное",
        "Loading saved stations..." to "Загрузка сохранённых станций…",
        "Your saved stations" to "Сохранённые станции",
        "No favorites yet" to "Пока нет избранных станций",
        "Tap the star on any station to keep it here." to "Нажмите на звёздочку у станции, чтобы добавить её сюда.",
        "Recently played" to "Недавно прослушанные",
        "No stations found" to "Станции не найдены",
        "Back" to "Назад",
        "Load more" to "Загрузить ещё",
        "Recent" to "Недавние",
        "No recently played stations" to "Нет недавно прослушанных станций",
        "Stations you play will appear here." to "Станции, которые вы слушаете, появятся здесь.",
        "Try another country, genre or search." to "Попробуйте другую страну, жанр или поисковый запрос.",
        "FIND" to "ПОИСК",
        "Search" to "Поиск",
        "Station, city, country or genre" to "Станция, город, страна или жанр",
        "Search station, city, country or genre" to "Поиск по станции, городу, стране или жанру",
        "STREAM UNAVAILABLE" to "ПОТОК НЕДОСТУПЕН",
        "No working stream" to "Нет работающего потока",
        "WAITING FOR INTERNET" to "ОЖИДАНИЕ ИНТЕРНЕТА",
        "RECONNECTING..." to "ПОВТОРНОЕ ПОДКЛЮЧЕНИЕ...",
        "Retry" to "Повторить",
        "Switching station" to "Переключение станции",
        "No working station" to "Нет доступной станции",
        "Pause" to "Пауза",
        "Play" to "Воспроизвести",
        "●  OFFLINE" to "●  НЕТ СЕТИ",
        "●  RECONNECTING" to "●  ПЕРЕПОДКЛЮЧЕНИЕ",
        "●  BUFFERING" to "●  БУФЕРИЗАЦИЯ",
        "●  CONNECTING" to "●  ПОДКЛЮЧЕНИЕ",
        "●  PLAYING" to "●  ВОСПРОИЗВЕДЕНИЕ",
        "●  PAUSED" to "●  ПАУЗА",
        "SECTION" to "РАЗДЕЛ",
        "SPACE" to "ПРОБЕЛ",
        "CLEAR" to "ОЧИСТИТЬ",
        "no logo" to "нет логотипа"
    )

    fun resolve(context: Context, value: String): String {
        val language = context.resources.configuration.locales[0]?.language ?: "en"
        if (language != "ru") return value
        russian[value]?.let { return it }
        Regex("^(\\d+) countries with available radio$").matchEntire(value)?.let {
            return "Доступно стран с радио: ${it.groupValues[1]}"
        }
        Regex("^(\\d+) genres in the catalog$").matchEntire(value)?.let {
            return "Жанров в каталоге: ${it.groupValues[1]}"
        }
        Regex("^(\\d+) saved stations$").matchEntire(value)?.let {
            return "Сохранённых станций: ${it.groupValues[1]}"
        }
        Regex("^(\\d+) stations available$").matchEntire(value)?.let {
            return "Доступно станций: ${it.groupValues[1]}"
        }
        return value
    }
}
