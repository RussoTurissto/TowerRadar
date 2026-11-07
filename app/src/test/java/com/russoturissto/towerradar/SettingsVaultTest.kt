package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class SettingsVaultTest {
    @Test fun protectedBackupRoundTripsWithoutPlaintext() {
        val data = """{"schema":1,"settings":{"map_key_yandex":"secret-sample-key","manual_lat":"55.7522"}}"""
        val password = "long-passphrase-123".toCharArray()
        val encrypted = SettingsVault.encrypt(data, password)
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("secret-sample-key"))
        assertEquals(data, SettingsVault.decrypt(encrypted, password))
    }

    @Test fun wrongPasswordDoesNotDecryptBackup() {
        val data = """{"schema":1,"settings":{"map_key_google":"sample"}}"""
        val encoded = SettingsVault.encrypt(data, "correct-passphrase-123".toCharArray())
        try {
            SettingsVault.decrypt(encoded, "incorrect-passphrase".toCharArray())
            fail("A wrong password must not reveal encrypted settings")
        } catch (expected: javax.crypto.AEADBadTagException) {
            // A corrupted or malicious ciphertext must also fail authentication.
        }
    }

    @Test fun shortPasswordIsRejected() {
        try {
            SettingsVault.encrypt("{}", "short".toCharArray())
            fail("A weak backup password must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("12"))
        }
    }
}
