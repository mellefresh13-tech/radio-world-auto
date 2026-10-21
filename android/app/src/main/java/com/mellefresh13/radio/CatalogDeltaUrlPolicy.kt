package com.mellefresh13.radio

/**
 * Catalog manifests are data, not authority to choose arbitrary network targets.
 * Delta downloads are restricted to the one URL shipped with the application.
 */
internal object CatalogDeltaUrlPolicy {
    fun canonicalOrFallback(candidate: String?, canonicalUrl: String): String {
        val value = candidate?.trim()
        val immutableHistoryUrl = value?.matches(
            Regex("https://raw\\.githubusercontent\\.com/mellefresh13-tech/radio-world-auto/[0-9a-f]{40}/data/catalog-delta\\.json")
        ) == true
        return if (!value.isNullOrEmpty() && (value == canonicalUrl || immutableHistoryUrl)) {
            value
        } else {
            canonicalUrl
        }
    }
}
