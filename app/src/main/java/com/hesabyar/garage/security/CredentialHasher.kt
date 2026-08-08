package com.hesabyar.garage.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object SecurityAttemptThrottle {
    fun delaySeconds(failedAttempts: Int): Long {
        require(failedAttempts >= 0) { "تعداد تلاش ناموفق نمی‌تواند منفی باشد" }
        if (failedAttempts < FAILURE_COUNT_BEFORE_BLOCK) return 0L
        val exponent = (failedAttempts - FAILURE_COUNT_BEFORE_BLOCK).coerceIn(0, MAX_BACKOFF_EXPONENT)
        return (INITIAL_BLOCK_SECONDS shl exponent).coerceAtMost(MAX_BLOCK_SECONDS)
    }

    private const val FAILURE_COUNT_BEFORE_BLOCK = 5
    private const val INITIAL_BLOCK_SECONDS = 30L
    private const val MAX_BLOCK_SECONDS = 30L * 60L
    private const val MAX_BACKOFF_EXPONENT = 6
}

class CredentialHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    init {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "تعداد دورهای مشتق‌سازی رمز معتبر نیست" }
    }

    fun hashPassword(password: CharArray): String {
        requireValidPassword(password)
        return hash(CredentialPurpose.PASSWORD, password)
    }

    fun verifyPassword(password: CharArray, verifier: String): Boolean {
        if (!isValidPassword(password)) return false
        return verify(CredentialPurpose.PASSWORD, password, verifier)
    }

    fun hashRecoveryAnswer(answer: CharArray): String {
        val normalized = normalizedRecoveryAnswerChars(answer)
        return try {
            require(normalized.isNotEmpty()) { "پاسخ بازیابی نمی‌تواند خالی باشد" }
            require(codePointCount(normalized) <= MAX_RECOVERY_ANSWER_CODE_POINTS) { "پاسخ بازیابی بیش از حد طولانی است" }
            hash(CredentialPurpose.RECOVERY_ANSWER, normalized)
        } finally {
            normalized.fill('\u0000')
        }
    }

    fun verifyRecoveryAnswer(answer: CharArray, verifier: String): Boolean {
        val normalized = normalizedRecoveryAnswerChars(answer)
        return try {
            normalized.isNotEmpty() &&
                codePointCount(normalized) <= MAX_RECOVERY_ANSWER_CODE_POINTS &&
                verify(CredentialPurpose.RECOVERY_ANSWER, normalized, verifier)
        } finally {
            normalized.fill('\u0000')
        }
    }

    fun generateRecoveryCode(): String = buildString(RECOVERY_CODE_LENGTH) {
        repeat(RECOVERY_CODE_LENGTH) { append(secureRandom.nextInt(10)) }
    }

    fun hashRecoveryCode(code: String): String {
        val normalized = normalizeRecoveryCode(code)
        require(isValidRecoveryCode(normalized)) { "کد بازیابی باید دقیقاً ۲۰ رقم باشد" }
        val chars = normalized.toCharArray()
        return try {
            hash(CredentialPurpose.RECOVERY_CODE, chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun verifyRecoveryCode(code: String, verifier: String): Boolean {
        val normalized = normalizeRecoveryCode(code)
        if (!isValidRecoveryCode(normalized)) return false
        val chars = normalized.toCharArray()
        return try {
            verify(CredentialPurpose.RECOVERY_CODE, chars, verifier)
        } finally {
            chars.fill('\u0000')
        }
    }

    internal fun normalizeRecoveryAnswer(answer: CharSequence): String {
        val result = StringBuilder(answer.length)
        var pendingSpace = false
        var offset = 0
        while (offset < answer.length) {
            val codePoint = Character.codePointAt(answer, offset)
            offset += Character.charCount(codePoint)
            if (isAnswerSeparator(codePoint)) {
                if (result.isNotEmpty()) pendingSpace = true
                continue
            }
            if (pendingSpace) {
                result.append(' ')
                pendingSpace = false
            }
            result.appendCodePoint(normalizePersianCodePoint(codePoint))
        }
        return Normalizer.normalize(result.toString(), Normalizer.Form.NFC)
    }

    internal fun normalizeRecoveryCode(code: CharSequence): String = buildString(code.length) {
        var offset = 0
        while (offset < code.length) {
            val codePoint = Character.codePointAt(code, offset)
            offset += Character.charCount(codePoint)
            when {
                codePoint in '0'.code..'9'.code -> appendCodePoint(codePoint)
                codePoint in PERSIAN_ZERO..PERSIAN_NINE -> append(('0'.code + codePoint - PERSIAN_ZERO).toChar())
                codePoint in ARABIC_ZERO..ARABIC_NINE -> append(('0'.code + codePoint - ARABIC_ZERO).toChar())
                isAnswerSeparator(codePoint) || codePoint == '-'.code -> Unit
                else -> appendCodePoint(codePoint)
            }
        }
    }

    private fun normalizedRecoveryAnswerChars(answer: CharArray): CharArray =
        normalizeRecoveryAnswer(String(answer)).toCharArray()

    private fun hash(purpose: CredentialPurpose, secret: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val derived = derive(purpose, secret, salt, iterations)
        return try {
            listOf(
                FORMAT_VERSION,
                purpose.serializedName,
                iterations.toString(),
                ENCODER.encodeToString(salt),
                ENCODER.encodeToString(derived),
            ).joinToString(SEPARATOR)
        } finally {
            salt.fill(0)
            derived.fill(0)
        }
    }

    private fun verify(purpose: CredentialPurpose, secret: CharArray, encoded: String): Boolean {
        val parsed = parse(encoded) ?: return false
        if (parsed.purpose != purpose) {
            parsed.salt.fill(0)
            parsed.expected.fill(0)
            return false
        }
        val actual = derive(purpose, secret, parsed.salt, parsed.iterations)
        return try {
            MessageDigest.isEqual(actual, parsed.expected)
        } finally {
            actual.fill(0)
            parsed.salt.fill(0)
            parsed.expected.fill(0)
        }
    }

    private fun parse(encoded: String): ParsedVerifier? = runCatching {
        val parts = encoded.split(SEPARATOR)
        require(parts.size == 5 && parts[0] == FORMAT_VERSION)
        val purpose = CredentialPurpose.entries.first { it.serializedName == parts[1] }
        val storedIterations = parts[2].toInt()
        require(storedIterations in MIN_ITERATIONS..MAX_ITERATIONS)
        val salt = DECODER.decode(parts[3])
        val expected = DECODER.decode(parts[4])
        require(salt.size == SALT_BYTES && expected.size == KEY_BYTES)
        ParsedVerifier(purpose, storedIterations, salt, expected)
    }.getOrNull()

    private fun derive(
        purpose: CredentialPurpose,
        secret: CharArray,
        salt: ByteArray,
        storedIterations: Int,
    ): ByteArray {
        val prefix = "hesabyar-app-lock|$FORMAT_VERSION|${purpose.serializedName}|".toCharArray()
        val material = CharArray(prefix.size + secret.size)
        prefix.copyInto(material)
        secret.copyInto(material, destinationOffset = prefix.size)
        prefix.fill('\u0000')
        val spec = PBEKeySpec(material, salt, storedIterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            material.fill('\u0000')
        }
    }

    private fun requireValidPassword(password: CharArray) {
        require(isValidPassword(password)) {
            "رمز باید بین ۶ تا ۱۲۸ نویسه باشد و در ابتدا یا انتهای آن فاصله نباشد"
        }
    }

    private fun isValidPassword(password: CharArray): Boolean {
        if (password.isEmpty()) return false
        val codePoints = codePointCount(password)
        if (codePoints !in MIN_PASSWORD_CODE_POINTS..MAX_PASSWORD_CODE_POINTS) return false
        val first = Character.codePointAt(password, 0)
        val last = Character.codePointBefore(password, password.size)
        return !isUnicodeWhitespace(first) && !isUnicodeWhitespace(last)
    }

    private fun codePointCount(value: CharArray): Int = Character.codePointCount(value, 0, value.size)

    private fun isValidRecoveryCode(value: String): Boolean =
        value.length == RECOVERY_CODE_LENGTH && value.all { it in '0'..'9' }

    private fun isAnswerSeparator(codePoint: Int): Boolean =
        codePoint == ZERO_WIDTH_NON_JOINER || isUnicodeWhitespace(codePoint)

    private fun isUnicodeWhitespace(codePoint: Int): Boolean =
        Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)

    private fun normalizePersianCodePoint(codePoint: Int): Int = when (codePoint) {
        ARABIC_YEH -> PERSIAN_YEH
        ARABIC_KAF -> PERSIAN_KEHEH
        in PERSIAN_ZERO..PERSIAN_NINE -> '0'.code + codePoint - PERSIAN_ZERO
        in ARABIC_ZERO..ARABIC_NINE -> '0'.code + codePoint - ARABIC_ZERO
        else -> codePoint
    }

    private enum class CredentialPurpose(val serializedName: String) {
        PASSWORD("PASSWORD"),
        RECOVERY_ANSWER("RECOVERY_ANSWER"),
        RECOVERY_CODE("RECOVERY_CODE"),
    }

    private data class ParsedVerifier(
        val purpose: CredentialPurpose,
        val iterations: Int,
        val salt: ByteArray,
        val expected: ByteArray,
    )

    companion object {
        const val DEFAULT_ITERATIONS = 210_000
        const val RECOVERY_CODE_LENGTH = 20

        private const val MIN_ITERATIONS = 1_000
        private const val MAX_ITERATIONS = 2_000_000
        private const val MIN_PASSWORD_CODE_POINTS = 6
        private const val MAX_PASSWORD_CODE_POINTS = 128
        private const val MAX_RECOVERY_ANSWER_CODE_POINTS = 256
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private const val KEY_BYTES = KEY_BITS / 8
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val FORMAT_VERSION = "HYS1"
        private const val SEPARATOR = "|"
        private const val ZERO_WIDTH_NON_JOINER = 0x200C
        private const val ARABIC_YEH = 0x064A
        private const val PERSIAN_YEH = 0x06CC
        private const val ARABIC_KAF = 0x0643
        private const val PERSIAN_KEHEH = 0x06A9
        private const val ARABIC_ZERO = 0x0660
        private const val ARABIC_NINE = 0x0669
        private const val PERSIAN_ZERO = 0x06F0
        private const val PERSIAN_NINE = 0x06F9

        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()
    }
}
