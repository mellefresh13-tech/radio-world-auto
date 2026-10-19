package com.mellefresh13.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class CountryFlagPolicyTest {

    @Test
    fun convertsValidUppercaseAndLowercaseAsciiCountryCodes() {
        assertEquals("🇺🇸", CountryFlagPolicy.flagFor("US"))
        assertEquals("🇩🇪", CountryFlagPolicy.flagFor("de"))
    }

    @Test
    fun usesGlobeFallbackForMalformedCodes() {
        assertEquals("🌐", CountryFlagPolicy.flagFor(""))
        assertEquals("🌐", CountryFlagPolicy.flagFor("U"))
        assertEquals("🌐", CountryFlagPolicy.flagFor("USA"))
        assertEquals("🌐", CountryFlagPolicy.flagFor("1S"))
        assertEquals("🌐", CountryFlagPolicy.flagFor("ÄE"))
        assertEquals("🌐", CountryFlagPolicy.flagFor("!@"))
    }

    @Test
    fun preservesUnknownCountryCodeFlagBehavior() {
        assertEquals("🇿🇿", CountryFlagPolicy.flagFor("ZZ"))
    }
}
