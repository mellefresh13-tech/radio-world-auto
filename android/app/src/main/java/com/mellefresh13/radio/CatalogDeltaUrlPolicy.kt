package com.mellefresh13.radio

/**
 * Catalog manifests are data, not authority to choose arbitrary network targets.
 * Delta downloads are restricted to the one URL shipped with the application.
 */
internal object CatalogDeltaUrlPolicy {
    fun canonicalOrFallback(candidate: String?, canonicalUrl: String): String {
        val value = candidate?.trim()
        return if (!value.isNullOrEmpty() && value == canonicalUrl) value else canonicalUrl
    }
}
