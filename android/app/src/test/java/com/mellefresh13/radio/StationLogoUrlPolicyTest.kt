package com.mellefresh13.radio

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationLogoUrlPolicyTest {

    @Test
    fun allowsHttpAndHttpsUrlsWithPublicDnsNames() {
        assertTrue(StationLogoUrlPolicy.isSyntacticallyAllowed("https://cdn.example.org/logo.png"))
        assertTrue(StationLogoUrlPolicy.isSyntacticallyAllowed("http://radio-images.example.net/logo.jpg"))
    }

    @Test
    fun rejectsUnsupportedSchemesCredentialsMalformedUrlsAndSingleLabelHosts() {
        listOf(
            "file:///etc/passwd",
            "ftp://cdn.example.org/logo.png",
            "data:image/png;base64,AAAA",
            "https://user:password@cdn.example.org/logo.png",
            "https:///missing-host/logo.png",
            "https://printer/logo.png",
            "https://cdn.example.org:70000/logo.png",
            "https://cdn.example.org\\logo.png"
        ).forEach { url ->
            assertFalse("Expected rejection for $url", StationLogoUrlPolicy.isSyntacticallyAllowed(url))
        }
    }

    @Test
    fun rejectsLocalAndNonPublicLiteralAddresses() {
        listOf(
            "http://localhost/logo.png",
            "http://printer.local/logo.png",
            "http://127.0.0.1/logo.png",
            "http://10.2.3.4/logo.png",
            "http://172.16.0.1/logo.png",
            "http://192.168.1.2/logo.png",
            "http://169.254.1.1/logo.png",
            "http://100.64.0.1/logo.png",
            "http://224.0.0.1/logo.png",
            "http://0177.0.0.1/logo.png",
            "http://127.1/logo.png",
            "http://2130706433/logo.png",
            "http://0x7f000001/logo.png",
            "http://[::1]/logo.png",
            "http://[fc00::1]/logo.png",
            "http://[fe80::1]/logo.png"
        ).forEach { url ->
            assertFalse("Expected local/private destination rejection for $url", StationLogoUrlPolicy.isSyntacticallyAllowed(url))
        }
    }

    @Test
    fun allowsPublicLiteralAddresses() {
        assertTrue(StationLogoUrlPolicy.isSyntacticallyAllowed("http://8.8.8.8/logo.png"))
        assertTrue(StationLogoUrlPolicy.isSyntacticallyAllowed("https://[2606:4700:4700::1111]/logo.png"))
    }

    @Test
    fun rejectsDnsAnswersThatContainAnyPrivateAddress() {
        val url = "https://cdn.example.org/logo.png"
        val public = InetAddress.getByName("8.8.8.8")
        val private = InetAddress.getByName("192.168.1.10")

        assertTrue(StationLogoUrlPolicy.isAllowedForNetwork(url) { arrayOf(public) })
        assertFalse(StationLogoUrlPolicy.isAllowedForNetwork(url) { arrayOf(public, private) })
        assertFalse(StationLogoUrlPolicy.isAllowedForNetwork(url) { emptyArray() })
        assertFalse(StationLogoUrlPolicy.isAllowedForNetwork(url) { throw java.io.IOException("DNS failure") })
    }

    @Test
    fun resolvesRelativeRedirectsAndRejectsUnsafeOrDowngradedTargets() {
        assertEquals(
            "https://cdn.example.org/new-logo.png",
            StationLogoUrlPolicy.resolveRedirectUrl("https://cdn.example.org/logo.png", "/new-logo.png")
        )
        assertEquals(
            "https://other.example.net/logo.png",
            StationLogoUrlPolicy.resolveRedirectUrl("https://cdn.example.org/logo.png", "https://other.example.net/logo.png")
        )
        assertNull(StationLogoUrlPolicy.resolveRedirectUrl("https://cdn.example.org/logo.png", "http://other.example.net/logo.png"))
        assertNull(StationLogoUrlPolicy.resolveRedirectUrl("https://cdn.example.org/logo.png", "file:///etc/passwd"))
        assertNull(StationLogoUrlPolicy.resolveRedirectUrl("https://cdn.example.org/logo.png", "http://127.0.0.1/private"))
        assertNull(StationLogoUrlPolicy.resolveRedirectUrl("https://cdn.example.org/logo.png", ""))
    }

    @Test
    fun publicAddressPolicyRejectsSpecialUseRanges() {
        listOf(
            "0.1.2.3",
            "10.1.2.3",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.1.1",
            "172.31.255.254",
            "192.0.0.1",
            "192.0.2.1",
            "192.168.1.1",
            "198.18.0.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1"
        ).forEach { address ->
            assertFalse(address, StationLogoUrlPolicy.isPublicAddress(InetAddress.getByName(address)))
        }
        assertTrue(StationLogoUrlPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")))
    }
}
