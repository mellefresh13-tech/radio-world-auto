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
    fun acceptsOnlyImmutableCommitPinnedHistoryUrls() {
        val historical = "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/0123456789abcdef0123456789abcdef01234567/data/catalog-delta.json"
        assertEquals(historical, CatalogDeltaUrlPolicy.canonicalOrFallback(historical, canonical))
        val branchUrl = "https://raw.githubusercontent.com/mellefresh13-tech/radio-world-auto/main/data/catalog-delta.json"
        assertEquals(canonical, CatalogDeltaUrlPolicy.canonicalOrFallback(branchUrl, canonical))
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
