package com.hesabyar.garage.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {
    private const val PREFIX = "HESABYAR-BACKUP-V1"
    private const val ITERATIONS = 180_000
    private const val KEY_BITS = 256

    fun encrypt(payload: ByteArray, password: CharArray, random: SecureRandom = SecureRandom()): String {
        require(password.size >= 6) { "رمز فایل پشتیبان باید حداقل ۶ نویسه باشد" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(PREFIX.toByteArray())
        val encrypted = cipher.doFinal(payload)
        val encoder = Base64.getEncoder()
        password.fill('\u0000')
        return listOf(PREFIX, encoder.encodeToString(salt), encoder.encodeToString(iv), encoder.encodeToString(encrypted)).joinToString("\n")
    }

    fun decrypt(envelope: String, password: CharArray): ByteArray {
        val parts = envelope.trim().split('\n')
        require(parts.size == 4 && parts[0] == PREFIX) { "فرمت فایل پشتیبان معتبر نیست" }
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[1])
        val iv = decoder.decode(parts[2])
        val encrypted = decoder.decode(parts[3])
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(PREFIX.toByteArray())
            cipher.doFinal(encrypted)
        } catch (error: Exception) {
            throw IllegalArgumentException("رمز اشتباه است یا فایل آسیب دیده", error)
        } finally {
            password.fill('\u0000')
        }
    }

    fun checksum(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
