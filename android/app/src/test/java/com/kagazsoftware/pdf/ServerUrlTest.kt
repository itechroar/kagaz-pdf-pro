package com.kagazsoftware.pdf

import org.junit.Assert.*
import org.junit.Test

class ServerUrlTest {
    @Test fun httpsAccepted() = assertEquals("https://pdf.example.com", ServerUrl.normalize("https://pdf.example.com/"))
    @Test fun bareHostGetsHttps() = assertEquals("https://pdf.example.com", ServerUrl.normalize("pdf.example.com"))
    @Test fun publicHttpRejected() = assertNull(ServerUrl.normalize("http://pdf.example.com"))
    @Test fun lanHttpAllowed() = assertEquals("http://192.168.1.5:8080", ServerUrl.normalize("192.168.1.5:8080"))
    @Test fun emulatorHostAllowed() = assertEquals("http://10.0.2.2:8080", ServerUrl.normalize("http://10.0.2.2:8080"))
    @Test fun garbageRejected() { assertNull(ServerUrl.normalize("")); assertNull(ServerUrl.normalize("ftp://x.com")) }
    @Test fun publicIpNotPrivate() = assertFalse(ServerUrl.isPrivateHost("172.32.0.1"))
    @Test fun sameOriginMatches() = assertTrue(ServerUrl.sameOrigin("https://pdf.example.com", "https://pdf.example.com/api/x"))
    @Test fun lookalikeHostRejected() = assertFalse(ServerUrl.sameOrigin("https://pdf.example.com", "https://pdf.example.com.evil.com/"))
    @Test fun otherPortRejected() = assertFalse(ServerUrl.sameOrigin("http://192.168.1.5:8080", "http://192.168.1.5:9090/"))
    @Test fun schemeDowngradeRejected() = assertFalse(ServerUrl.sameOrigin("https://pdf.example.com", "http://pdf.example.com/"))
    @Test fun startJoinsServerAndPath() {
        assertEquals("https://pdf.example.com/admin-dashboard.html", ServerUrl.start("https://pdf.example.com/", "/admin-dashboard.html"))
        assertEquals("http://192.168.1.5:8081/", ServerUrl.start("http://192.168.1.5:8081", "/"))
    }
}
