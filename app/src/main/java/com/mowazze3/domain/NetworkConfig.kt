package com.mowazze3.domain

import java.security.SecureRandom

data class NetworkConfig(val ssid: String, val password: String) {
    /** Keeps the password out of logs and crash reports. */
    override fun toString() = "NetworkConfig(ssid=$ssid, password=***)"
}

enum class NetworkError { SSID_EMPTY, SSID_TOO_LONG, PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_NOT_ASCII }

private const val SSID_MAX_BYTES = 32
private val PASSWORD_LENGTH = 8..63
private val PRINTABLE_ASCII = ' '..'~'

/** Limits from HANDOFF section 5. WPA2 passphrases are also printable ASCII only, whatever the UI language is. */
fun NetworkConfig.validate(): Set<NetworkError> = buildSet {
    if (ssid.isBlank()) add(NetworkError.SSID_EMPTY)
    if (ssid.toByteArray(Charsets.UTF_8).size > SSID_MAX_BYTES) add(NetworkError.SSID_TOO_LONG)
    if (password.length < PASSWORD_LENGTH.first) add(NetworkError.PASSWORD_TOO_SHORT)
    if (password.length > PASSWORD_LENGTH.last) add(NetworkError.PASSWORD_TOO_LONG)
    if (password.any { it !in PRINTABLE_ASCII }) add(NetworkError.PASSWORD_NOT_ASCII)
}

/**
 * Standard Wi-Fi QR format. The escaped characters are the ones the format reserves. An empty password
 * means an open hotspot, which only happens when the details are read from the system.
 */
fun NetworkConfig.qrPayload(): String =
    if (password.isEmpty()) {
        "WIFI:T:nopass;S:${ssid.qrEscaped()};;"
    } else {
        "WIFI:T:WPA;S:${ssid.qrEscaped()};P:${password.qrEscaped()};H:false;;"
    }

private fun String.qrEscaped() = replace(Regex("""[\\;,:"]""")) { "\\${it.value}" }

// Leaves out look-alikes (0/O, 1/l/I) because people type this into a second device by hand.
private const val PASSWORD_ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val GENERATED_PASSWORD_LENGTH = 16

fun generatePassword(random: SecureRandom = SecureRandom()): String =
    String(CharArray(GENERATED_PASSWORD_LENGTH) { PASSWORD_ALPHABET[random.nextInt(PASSWORD_ALPHABET.length)] })

fun defaultNetwork(random: SecureRandom = SecureRandom()) =
    NetworkConfig(ssid = "Mowazze3-${1000 + random.nextInt(9000)}", password = generatePassword(random))
