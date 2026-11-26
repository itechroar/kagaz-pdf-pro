package com.kagazsoftware.pdf

import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException

/** One-shot reachability probe used before a server address is saved. Blocking: call off the main thread. */
object ServerCheck {
    enum class Result { OK, TLS_FAILED, UNREACHABLE }

    /** Any HTTP answer below 500 proves a Kagaz server (or at least a web server) is there; login-protected paths answer 401/302. */
    fun probe(base: String): Result =
        try {
            val c = URL("$base/api/v1/auth/status").openConnection() as HttpURLConnection
            c.connectTimeout = 7000
            c.readTimeout = 7000
            c.instanceFollowRedirects = false
            val code = c.responseCode
            c.disconnect()
            if (code in 100..499) Result.OK else Result.UNREACHABLE
        } catch (e: SSLException) {
            Result.TLS_FAILED
        } catch (e: Exception) {
            Result.UNREACHABLE
        }
}
