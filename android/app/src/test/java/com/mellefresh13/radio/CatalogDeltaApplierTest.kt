package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogDeltaApplierTest {

    private data class Item(val id: String, val name: String)

    @Test
    fun delta_updates_adds_and_removesItems() {
        val result = CatalogDeltaApplier.apply(
            localItems = listOf(Item("a", "A"), Item("b", "B")),
            updatedItems = listOf(Item("b", "B updated"), Item("c", "C")),
            removedIds = setOf("a"),
            idOf = { it.id }
        )

        assertEquals(listOf("B updated", "C"), result.map { it.name })
        assertTrue(result.none { it.id == "a" })
    }

    @Test
    fun delta_keepsUnchangedItems() {
        val result = CatalogDeltaApplier.apply(
            localItems = listOf(Item("a", "A"), Item("b", "B")),
            updatedItems = emptyList(),
            removedIds = emptySet(),
            idOf = { it.id }
        )

        assertEquals(listOf("a", "b"), result.map { it.id })
    }
}
