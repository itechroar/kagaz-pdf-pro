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
}
