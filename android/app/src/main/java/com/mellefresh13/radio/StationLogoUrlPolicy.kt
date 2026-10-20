package com.mellefresh13.radio

import java.net.InetAddress
import java.net.Inet4Address
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * Defensive URL policy for station logos supplied by the remote station catalog.
 *
 * DNS answers are checked before each network request and after every redirect.
 * HttpURLConnection resolves hostnames again when connecting, so this reduces
 * SSRF-style exposure but cannot cryptographically pin DNS against rebinding.
 */
internal object StationLogoUrlPolicy {
    const val MAX_REDIRECTS = 5

    private val numericHostPattern = Regex(
        "(?i)(?:(?:0x[0-9a-f]+)|(?:[0-9]+))(?:\\.(?:(?:0x[0-9a-f]+)|(?:[0-9]+)))*"
    )

    fun isSyntacticallyAllowed(rawUrl: String): Boolean {
        val parsed = parseHttpUrl(rawUrl) ?: return false
        val host = parsed.host

        if (isForbiddenHostname(host)) return false

        if (host.contains(':')) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
            return address.address.size == 16 && isPublicAddress(address)
        }

        if (host.all { it.isDigit() || it == '.' }) {
            val address = parseDottedIpv4(host) ?: return false
            return isPublicAddress(address)
        }

        // Avoid alternate numeric IPv4 spellings such as integer and hex hosts.
        if (numericHostPattern.matches(host)) return false

        return isValidDnsHostname(host) && host.contains('.')
    }

    /**
     * Checks syntax and all DNS answers. Every answer must be globally routable;
     * a hostname resolving to a mixture of public and local addresses is rejected.
     */
    fun isAllowedForNetwork(
        rawUrl: String,
        resolveHost: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) }
    ): Boolean {
        if (!isSyntacticallyAllowed(rawUrl)) return false
        val parsed = parseHttpUrl(rawUrl) ?: return false
        val addresses = runCatching { resolveHost(parsed.host) }.getOrNull() ?: return false
        return addresses.isNotEmpty() && addresses.all(::isPublicAddress)
    }

    /**
     * Resolves relative redirects and applies syntax/downgrade checks.
     * The resolved destination must still pass isAllowedForNetwork before use.
     */
    fun resolveRedirectUrl(currentUrl: String, location: String): String? {
        if (location.isBlank()) return null
        val current = parseHttpUrl(currentUrl) ?: return null
        val targetUrl = runCatching {
            URL(URL(currentUrl), location.trim()).toExternalForm()
        }.getOrNull() ?: return null
        val target = parseHttpUrl(targetUrl) ?: return null

        if (current.scheme == "https" && target.scheme == "http") return null
        if (!isSyntacticallyAllowed(targetUrl)) return null
        return targetUrl
    }

    internal fun isPublicAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        val bytes = address.address
        if (bytes.size == 4) {
            return isPublicIpv4(bytes)
        }
        if (bytes.size != 16) return false

        // IPv4-mapped IPv6 addresses must inherit the IPv4 restrictions.
        if (
            bytes.copyOfRange(0, 10).all { it == 0.toByte() } &&
            bytes[10] == 0xff.toByte() &&
            bytes[11] == 0xff.toByte()
        ) {
            return isPublicIpv4(bytes.copyOfRange(12, 16))
        }

        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        val third = bytes[2].toInt() and 0xff
        val fourth = bytes[3].toInt() and 0xff

        // Only IPv6 global-unicast space (2000::/3) is eligible.
        if ((first and 0xe0) != 0x20) return false

        // Documentation, Teredo, 6to4, and ORCHID ranges are not used as public
        // station-logo destinations for this app.
        if (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8) return false
        if (first == 0x20 && second == 0x01 && third == 0x00 && fourth == 0x00) return false
        if (first == 0x20 && second == 0x02) return false
        if (first == 0x20 && second == 0x01 && third == 0x00 && fourth == 0x10) return false
        return true
    }

    private data class ParsedHttpUrl(
        val scheme: String,
        val host: String
    )

    private fun parseHttpUrl(rawUrl: String): ParsedHttpUrl? {
        val value = rawUrl.trim()
        if (value.isEmpty() || value.any { it <= ' ' || it == '\\\\' }) return null

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.isOpaque || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null

        val rawHost = uri.host
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?: return null
        if (rawHost.endsWith("..")) return null
        val host = rawHost.removeSuffix(".").lowercase(Locale.ROOT)
        if (host.isBlank() || host.contains('%') || host.startsWith('.') || host.endsWith('.')) return null

        return ParsedHttpUrl(scheme, host)
    }

    private fun isForbiddenHostname(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost")) return true
        return listOf(
            ".local",
            ".internal",
            ".lan",
            ".home",
            ".home.arpa",
            ".test",
            ".invalid",
            ".example",
            ".onion"
        ).any { suffix -> host.endsWith(suffix) }
    }

    private fun isValidDnsHostname(host: String): Boolean {
        if (host.length > 253) return false
        return host.split('.').all { label ->
            label.isNotEmpty() &&
                label.length <= 63 &&
                label.first().isLetterOrDigit() &&
                label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    private fun parseDottedIpv4(host: String): Inet4Address? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        // Reject octal-looking octets to avoid different URL parsers interpreting
        // the same address differently (for example, 0177.0.0.1).
        if (parts.any { it.length > 1 && it.startsWith('0') }) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return runCatching {
            InetAddress.getByAddress(octets.map(Int::toByte).toByteArray()) as Inet4Address
        }.getOrNull()
    }

    private fun isPublicIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return false
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        val c = bytes[2].toInt() and 0xff
        val d = bytes[3].toInt() and 0xff

        if (a == 0 || a == 10 || a == 127 || a >= 224) return false
        if (a == 169 && b == 254) return false
        if (a == 172 && b in 16..31) return false
        if (a == 192 && b == 168) return false
        if (a == 100 && b in 64..127) return false // Shared address space (RFC 6598).
        if (a == 192 && b == 0 && (c == 0 || c == 2)) return false
        if (a == 192 && b == 88 && c == 99) return false
        if (a == 198 && (b == 18 || b == 19)) return false
        if (a == 198 && b == 51 && c == 100) return false
        if (a == 203 && b == 0 && c == 113) return false
        if (a == 255 && b == 255 && c == 255 && d == 255) return false
        return true
    }
}
