package com.hesabyar.garage.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialHasherTest {
    private val hasher = CredentialHasher(iterations = 1_000)

    @Test
    fun `password verifier is versioned salted and preserves exact Unicode`() {
        val password = "رمز🙂۱۲۳".toCharArray()

        val first = hasher.hashPassword(password)
        val second = hasher.hashPassword(password)

        assertTrue(first.startsWith("HYS1|PASSWORD|1000|"))
        assertNotEquals(first, second)
        assertFalse(first.contains("رمز"))
        assertTrue(hasher.verifyPassword(password, first))
        assertFalse(hasher.verifyPassword("رمز🙂123".toCharArray(), first))

        val composed = "éabcde".toCharArray()
        val decomposed = "e\u0301abcde".toCharArray()
        val composedVerifier = hasher.hashPassword(composed)
        assertTrue(hasher.verifyPassword(composed, composedVerifier))
        assertFalse(hasher.verifyPassword(decomposed, composedVerifier))
    }

    @Test
    fun `password validation counts code points and rejects edge whitespace`() {
        val sixEmoji = "🙂🙂🙂🙂🙂🙂".toCharArray()
        assertTrue(hasher.verifyPassword(sixEmoji, hasher.hashPassword(sixEmoji)))

        assertThrows(IllegalArgumentException::class.java) {
            hasher.hashPassword("پنجتا".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            hasher.hashPassword(" رمزامن".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            hasher.hashPassword("رمزامن\u00A0".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            hasher.hashPassword("ا".repeat(129).toCharArray())
        }
    }

    @Test
    fun `Persian recovery answer normalization handles Arabic letters digits and spacing`() {
        val entered = "  علي\u200c \tكاظمي ۱۲٣  "
        val canonical = "علی کاظمی 123"

        assertEquals(canonical, hasher.normalizeRecoveryAnswer(entered))
        val verifier = hasher.hashRecoveryAnswer(entered.toCharArray())
        assertTrue(hasher.verifyRecoveryAnswer(canonical.toCharArray(), verifier))
        assertTrue(hasher.verifyRecoveryAnswer("علي   کاظمی ١٢٣".toCharArray(), verifier))
        assertFalse(hasher.verifyRecoveryAnswer("علی کاظمی 124".toCharArray(), verifier))
        assertFalse(hasher.verifyPassword(canonical.toCharArray(), verifier))
    }

    @Test
    fun `recovery code contains twenty digits and accepts grouped localized input`() {
        val code = hasher.generateRecoveryCode()
        assertEquals(CredentialHasher.RECOVERY_CODE_LENGTH, code.length)
        assertTrue(code.all { it in '0'..'9' })

        val verifier = hasher.hashRecoveryCode(code)
        val localizedAndGrouped = code
            .map { digit -> ('۰'.code + digit.digitToInt()).toChar() }
            .chunked(5)
            .joinToString("-") { it.joinToString("") }

        assertTrue(verifier.startsWith("HYS1|RECOVERY_CODE|1000|"))
        assertTrue(hasher.verifyRecoveryCode(code, verifier))
        assertTrue(hasher.verifyRecoveryCode(localizedAndGrouped, verifier))
        assertFalse(hasher.verifyRecoveryCode(code.dropLast(1), verifier))
        assertThrows(IllegalArgumentException::class.java) {
            hasher.hashRecoveryCode("1".repeat(19))
        }
    }

    @Test
    fun `malformed or cross-purpose verifiers never authenticate`() {
        val password = "رمزایمن".toCharArray()
        val passwordVerifier = hasher.hashPassword(password)
        val answerVerifier = hasher.hashRecoveryAnswer(password)

        assertFalse(hasher.verifyPassword(password, "not-a-verifier"))
        assertFalse(hasher.verifyPassword(password, answerVerifier))
        assertFalse(hasher.verifyRecoveryAnswer(password, passwordVerifier))
    }

    @Test
    fun `failed attempt delay starts at five and grows to thirty minutes`() {
        assertEquals(0L, SecurityAttemptThrottle.delaySeconds(4))
        assertEquals(30L, SecurityAttemptThrottle.delaySeconds(5))
        assertEquals(60L, SecurityAttemptThrottle.delaySeconds(6))
        assertEquals(960L, SecurityAttemptThrottle.delaySeconds(10))
        assertEquals(1_800L, SecurityAttemptThrottle.delaySeconds(11))
        assertEquals(1_800L, SecurityAttemptThrottle.delaySeconds(100))
    }
}
