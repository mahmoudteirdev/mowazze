package com.mowazze3.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkConfigTest {
    private val validPassword = "abcd1234"

    @Test
    fun `a normal network has no errors`() {
        assertEquals(emptySet<NetworkError>(), NetworkConfig("شبكتي", validPassword).validate())
    }

    @Test
    fun `an empty or blank name is rejected`() {
        assertTrue(NetworkError.SSID_EMPTY in NetworkConfig("", validPassword).validate())
        assertTrue(NetworkError.SSID_EMPTY in NetworkConfig("   ", validPassword).validate())
    }

    @Test
    fun `the name limit counts UTF-8 bytes, so 17 Arabic letters are too long`() {
        assertFalse(NetworkError.SSID_TOO_LONG in NetworkConfig("a".repeat(32), validPassword).validate())
        assertTrue(NetworkError.SSID_TOO_LONG in NetworkConfig("a".repeat(33), validPassword).validate())
        assertFalse(NetworkError.SSID_TOO_LONG in NetworkConfig("م".repeat(16), validPassword).validate())
        assertTrue(NetworkError.SSID_TOO_LONG in NetworkConfig("م".repeat(17), validPassword).validate())
    }

    @Test
    fun `the password must be 8 to 63 characters`() {
        assertTrue(NetworkError.PASSWORD_TOO_SHORT in NetworkConfig("net", "a".repeat(7)).validate())
        assertEquals(emptySet<NetworkError>(), NetworkConfig("net", "a".repeat(8)).validate())
        assertEquals(emptySet<NetworkError>(), NetworkConfig("net", "a".repeat(63)).validate())
        assertTrue(NetworkError.PASSWORD_TOO_LONG in NetworkConfig("net", "a".repeat(64)).validate())
    }

    @Test
    fun `an Arabic password is rejected because WPA2 is ASCII only`() {
        assertTrue(NetworkError.PASSWORD_NOT_ASCII in NetworkConfig("net", "كلمةسرطويلة").validate())
    }

    @Test
    fun `the QR payload escapes every reserved character`() {
        assertEquals(
            """WIFI:T:WPA;S:a\;b\,c\:d\\e\"f;P:p\;ss\:word;H:false;;""",
            NetworkConfig("""a;b,c:d\e"f""", "p;ss:word").qrPayload(),
        )
    }

    @Test
    fun `the QR payload keeps Arabic names as they are`() {
        assertEquals("WIFI:T:WPA;S:شبكتي;P:abcd1234;H:false;;", NetworkConfig("شبكتي", "abcd1234").qrPayload())
    }

    @Test
    fun `an open hotspot gets a QR without a password`() {
        assertEquals("WIFI:T:nopass;S:cafe\\;1;;", NetworkConfig("cafe;1", "").qrPayload())
    }

    @Test
    fun `toString never shows the password`() {
        assertFalse(NetworkConfig("net", "secret-value").toString().contains("secret-value"))
    }

    @Test
    fun `generated defaults always pass validation`() {
        repeat(200) { assertEquals(emptySet<NetworkError>(), defaultNetwork().validate()) }
    }

    @Test
    fun `generated passwords avoid look-alike characters`() {
        repeat(200) { assertFalse(generatePassword().any { it in "0O1lI" }) }
    }
}
