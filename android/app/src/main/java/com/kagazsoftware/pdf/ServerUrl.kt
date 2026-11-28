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

    /** The address built into this app variant, or null when none was set or it is not acceptable. */
    fun builtIn(configured: String): String? = if (configured.isBlank()) null else normalize(configured)

    /** The page this app variant opens first: the user app opens the tools, the admin app the console. */
    fun start(server: String, path: String): String = server.trimEnd('/') + path

    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h == "10.0.2.2" || h.endsWith(".local")) return true
        val p = h.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        return p[0] == 10 || p[0] == 127 || (p[0] == 192 && p[1] == 168) ||
            (p[0] == 172 && p[1] in 16..31) || (p[0] == 169 && p[1] == 254)
    }

    /** True when [url] has the same scheme, host and port as [server]; prefix matching would accept pdf.example.com.evil.com. */
    fun sameOrigin(server: String, url: String): Boolean =
        try {
            val a = URI(server); val b = URI(url)
            a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && port(a) == port(b)
        } catch (e: Exception) {
            false
        }

    private fun port(u: URI): Int = if (u.port != -1) u.port else if (u.scheme.equals("https", true)) 443 else 80
}
