package com.kagazsoftware.pdf

import java.net.URI

/**
 * Validates the user-entered server address. https is always accepted; plain http only for
 * loopback, link-local and RFC 1918 hosts, so credentials never cross the public internet in clear.
 */
object ServerUrl {
    fun normalize(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = (if (isPrivateHost(s.substringBefore('/').substringBefore(':'))) "http://" else "https://") + s
        val uri = try { URI(s) } catch (e: Exception) { return null }
        val host = uri.host ?: return null
        return when (uri.scheme?.lowercase()) {
            "https" -> s.trimEnd('/')
            "http" -> if (isPrivateHost(host)) s.trimEnd('/') else null
            else -> null
        }
    }

    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "10.0.2.2" || h.endsWith(".local")) return true
        val p = h.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) ||
            (p[0] == 172 && p[1] in 16..31) || (p[0] == 169 && p[1] == 254)
    }
}
