package com.mellefresh13.radio

object CatalogDeltaApplier {

    fun <T> apply(
        localItems: List<T>,
        updatedItems: List<T>,
        removedIds: Set<String>,
        idOf: (T) -> String
    ): List<T> {
        val byId = linkedMapOf<String, T>()
        localItems.forEach { item ->
            val id = idOf(item).trim()
            if (id.isNotEmpty()) byId[id] = item
        }
        updatedItems.forEach { item ->
            val id = idOf(item).trim()
            if (id.isNotEmpty()) byId[id] = item
        }
        removedIds.forEach { id ->
            val normalizedId = id.trim()
            if (normalizedId.isNotEmpty()) byId.remove(normalizedId)
        }
        return byId.values.toList()
    }
}
