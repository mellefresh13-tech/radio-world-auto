package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogDeltaUrlPolicyTest {
    private val canonical = "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta.json"

    @Test
    fun acceptsOnlyExactCanonicalDeltaUrl() {
        assertEquals(canonical, CatalogDeltaUrlPolicy.canonicalOrFallback(canonical, canonical))
        assertEquals(canonical, CatalogDeltaUrlPolicy.canonicalOrFallback("  $canonical  ", canonical))
    }

    @Test
    fun fallsBackForMissingOrUntrustedManifestUrl() {
        listOf(
            null,
            "",
            " ",
            "https://attacker.example/delta.json",
            "http://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta.json",
            "https://raw.githubusercontent.com.attacker.example/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta.json",
            "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/catalog-data/data/catalog-delta.json?redirect=https://attacker.example",
            "file:///data/local/tmp/catalog-delta.json"
        ).forEach { candidate ->
            assertEquals(
                "Candidate should fall back: $candidate",
                canonical,
                CatalogDeltaUrlPolicy.canonicalOrFallback(candidate, canonical)
            )
        }
    }
}
