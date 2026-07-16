package com.hesabyar.garage.security

import org.junit.Assert.*
import org.junit.Test

class BackupCryptoTest {
    @Test fun `encrypted backup round trips`() {
        val payload = "پرسنل، انعام، کارت 5892107047168045".toByteArray()
        val envelope = BackupCrypto.encrypt(payload, "secret-123".toCharArray())
        assertFalse(envelope.contains("589210"))
        assertArrayEquals(payload, BackupCrypto.decrypt(envelope, "secret-123".toCharArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong password is rejected`() {
        val envelope = BackupCrypto.encrypt("data".toByteArray(), "correct".toCharArray())
        BackupCrypto.decrypt(envelope, "wrong-password".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tampered backup is rejected`() {
        val envelope = BackupCrypto.encrypt("data".toByteArray(), "correct".toCharArray())
        val tampered = envelope.dropLast(2) + "AA"
        BackupCrypto.decrypt(tampered, "correct".toCharArray())
    }
}
