package com.mellefresh13.radio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCacheSnapshotPolicyTest {

    @Test
    fun acceptsOnlyCompleteNonEmptySnapshots() {
        assertTrue(CatalogCacheSnapshotPolicy.isComplete(3, 3, 3))
        assertFalse(CatalogCacheSnapshotPolicy.isComplete(3, 2, 2))
        assertFalse(CatalogCacheSnapshotPolicy.isComplete(3, 3, 2))
        assertFalse(CatalogCacheSnapshotPolicy.isComplete(0, 0, 0))
    }
}
