package com.mellefresh13.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteStatePolicyTest {

    @Test
    fun toggleAddsMissingFavorite() {
        assertTrue(FavoriteStatePolicy.toggle(emptySet(), "station"))
    }

    @Test
    fun toggleRemovesExistingFavorite() {
        assertFalse(FavoriteStatePolicy.toggle(setOf("station"), "station"))
    }
}
