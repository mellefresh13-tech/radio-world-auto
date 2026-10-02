package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMetadataParserTest {

    @Test
    fun parsesArtistAndTitle() {
        val result = TrackMetadataParser.parse("Artist - Track")
        assertEquals("Artist", result.artist)
        assertEquals("Track", result.title)
    }

    @Test
    fun parsesEnDashSeparator() {
        val result = TrackMetadataParser.parse("Artist – Track")
        assertEquals("Artist", result.artist)
        assertEquals("Track", result.title)
    }

    @Test
    fun titleOnlyLeavesArtistEmpty() {
        val result = TrackMetadataParser.parse("Track only")
        assertNull(result.artist)
        assertEquals("Track only", result.title)
    }

    @Test
    fun blankMetadataIsEmpty() {
        val result = TrackMetadataParser.parse("   ")
        assertNull(result.artist)
        assertNull(result.title)
    }
}
