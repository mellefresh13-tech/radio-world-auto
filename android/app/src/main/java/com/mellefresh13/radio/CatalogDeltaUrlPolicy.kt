package com.mellefresh13.radio

/**
 * Catalog manifests are data, not authority to choose arbitrary network targets.
 * Delta downloads accept the canonical latest URL or immutable commit-pinned history URLs.
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
