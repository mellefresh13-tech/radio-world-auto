package com.mellefresh13.radio

import java.util.Locale

/**
 * Pure station filtering shared by the repository's background processing path.
 * Filtering must happen before offset/limit so paging is stable on filtered results.
 */
internal object StationCatalogFilter {

    fun filter(
        source: List<Station>,
        query: String?,
        country: String?,
        genre: String?,
        limit: Int,
        offset: Int
    ): List<Station> {
        val normalizedQuery = query?.trim()?.lowercase(Locale.ROOT) ?: ""
        val normalizedCountry = country?.trim()?.lowercase(Locale.ROOT) ?: ""
        val normalizedGenre = genre?.trim()?.lowercase(Locale.ROOT) ?: ""

        return source.asSequence()
            .filter {
                normalizedCountry.isBlank() ||
                    it.countryCode.lowercase(Locale.ROOT) == normalizedCountry
            }
            .filter {
                normalizedGenre.isBlank() ||
                    it.genre.lowercase(Locale.ROOT) == normalizedGenre
            }
            .filter {
                normalizedQuery.isBlank() || listOf(
                    it.name,
                    it.country,
                    it.countryCode,
                    it.city,
                    it.genre,
                    it.language,
                    it.website.orEmpty()
                ).any { value -> value.lowercase(Locale.ROOT).contains(normalizedQuery) }
            }
            .drop(offset)
            .take(limit)
            .toList()
    }
}
