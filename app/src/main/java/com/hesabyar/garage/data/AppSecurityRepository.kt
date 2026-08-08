package com.hesabyar.garage.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hesabyar.garage.security.CredentialHasher
import com.hesabyar.garage.security.SecurityAttemptThrottle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.appSecurityDataStore by preferencesDataStore(name = "hesabyar_security")

data class AppSecuritySettings(
    val lockEnabled: Boolean = false,
    val deviceAuthEnabled: Boolean = false,
    val recoveryQuestion: String = "",
    val hasRecoveryCode: Boolean = false,
    val lockTimeoutSeconds: Int = AppSecurityRepository.DEFAULT_LOCK_TIMEOUT_SECONDS,
    val failedAttempts: Int = 0,
    val blockedUntilEpochMillis: Long = 0,
)

sealed interface SecurityResult<out T> {
    data class Success<T>(val value: T) : SecurityResult<T>
    data object Incorrect : SecurityResult<Nothing>
    data class Blocked(
        val retryAtEpochMillis: Long,
        val failedAttempts: Int,
    ) : SecurityResult<Nothing>
}

class AppSecurityRepository(
    context: Context,
    private val credentialHasher: CredentialHasher = CredentialHasher(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val dataStore = context.applicationContext.appSecurityDataStore
    private val operationMutex = Mutex()

    val settings: Flow<AppSecuritySettings> = dataStore.data.map { preferences ->
        val lockEnabled = preferences[Keys.lockEnabled] == true &&
            preferences[Keys.passwordVerifier].isNullOrBlank().not()
        val storedTimeout = preferences[Keys.lockTimeoutSeconds] ?: DEFAULT_LOCK_TIMEOUT_SECONDS
        AppSecuritySettings(
            lockEnabled = lockEnabled,
            deviceAuthEnabled = lockEnabled && (preferences[Keys.deviceAuthEnabled] ?: false),
            recoveryQuestion = preferences[Keys.recoveryQuestion].orEmpty(),
            hasRecoveryCode = preferences[Keys.recoveryCodeVerifier].isNullOrBlank().not(),
            lockTimeoutSeconds = storedTimeout.takeIf(ALLOWED_LOCK_TIMEOUT_SECONDS::contains)
                ?: DEFAULT_LOCK_TIMEOUT_SECONDS,
            failedAttempts = (preferences[Keys.failedAttempts] ?: 0).coerceAtLeast(0),
            blockedUntilEpochMillis = (preferences[Keys.blockedUntilEpochMillis] ?: 0L).coerceAtLeast(0L),
        )
    }

    suspend fun enableLock(
        password: CharArray,
        recoveryQuestion: String,
        recoveryAnswer: CharArray,
        lockTimeoutSeconds: Int = DEFAULT_LOCK_TIMEOUT_SECONDS,
    ): SecurityResult<String> {
        requireValidTimeout(lockTimeoutSeconds)
        val question = validateRecoveryQuestion(recoveryQuestion)
        val passwordVerifier = credentialHasher.hashPassword(password)
        val answerVerifier = credentialHasher.hashRecoveryAnswer(recoveryAnswer)
        val recoveryCode = credentialHasher.generateRecoveryCode()
        val recoveryCodeVerifier = credentialHasher.hashRecoveryCode(recoveryCode)

        return operationMutex.withLock {
            val current = dataStore.data.first()
            require(current[Keys.lockEnabled] != true) { "قفل برنامه قبلاً فعال شده است" }
            dataStore.edit { preferences ->
                preferences.clear()
                preferences[Keys.lockEnabled] = true
                preferences[Keys.deviceAuthEnabled] = false
                preferences[Keys.passwordVerifier] = passwordVerifier
                preferences[Keys.recoveryQuestion] = question
                preferences[Keys.recoveryAnswerVerifier] = answerVerifier
                preferences[Keys.recoveryCodeVerifier] = recoveryCodeVerifier
                preferences[Keys.lockTimeoutSeconds] = lockTimeoutSeconds
                resetThrottle(preferences)
            }
            SecurityResult.Success(recoveryCode)
        }
    }

    suspend fun verifyPassword(password: CharArray): SecurityResult<Unit> = verifyAndApply(
        verifierKey = Keys.passwordVerifier,
        verify = { credentialHasher.verifyPassword(password, it) },
        successValue = Unit,
    )

    suspend fun verifyRecoveryAnswer(answer: CharArray): SecurityResult<Unit> = verifyAndApply(
        verifierKey = Keys.recoveryAnswerVerifier,
        verify = { credentialHasher.verifyRecoveryAnswer(answer, it) },
        successValue = Unit,
    )

    suspend fun verifyRecoveryCode(code: String): SecurityResult<Unit> = verifyAndApply(
        verifierKey = Keys.recoveryCodeVerifier,
        verify = { credentialHasher.verifyRecoveryCode(code, it) },
        successValue = Unit,
    )

    suspend fun changePassword(
        currentPassword: CharArray,
        newPassword: CharArray,
    ): SecurityResult<Unit> {
        val newVerifier = credentialHasher.hashPassword(newPassword)
        return verifyAndApply(
            verifierKey = Keys.passwordVerifier,
            verify = { credentialHasher.verifyPassword(currentPassword, it) },
            successValue = Unit,
        ) { preferences ->
            preferences[Keys.passwordVerifier] = newVerifier
        }
    }

    suspend fun resetPasswordWithRecoveryAnswer(
        recoveryAnswer: CharArray,
        newPassword: CharArray,
    ): SecurityResult<Unit> {
        val newVerifier = credentialHasher.hashPassword(newPassword)
        return verifyAndApply(
            verifierKey = Keys.recoveryAnswerVerifier,
            verify = { credentialHasher.verifyRecoveryAnswer(recoveryAnswer, it) },
            successValue = Unit,
        ) { preferences ->
            preferences[Keys.passwordVerifier] = newVerifier
        }
    }

    suspend fun resetPasswordWithRecoveryCode(
        recoveryCode: String,
        newPassword: CharArray,
    ): SecurityResult<String> {
        val newPasswordVerifier = credentialHasher.hashPassword(newPassword)
        val nextRecoveryCode = credentialHasher.generateRecoveryCode()
        val nextRecoveryCodeVerifier = credentialHasher.hashRecoveryCode(nextRecoveryCode)
        return verifyAndApply(
            verifierKey = Keys.recoveryCodeVerifier,
            verify = { credentialHasher.verifyRecoveryCode(recoveryCode, it) },
            successValue = nextRecoveryCode,
        ) { preferences ->
            preferences[Keys.passwordVerifier] = newPasswordVerifier
            preferences[Keys.recoveryCodeVerifier] = nextRecoveryCodeVerifier
        }
    }

    suspend fun disableLock(password: CharArray): SecurityResult<Unit> = verifyAndApply(
        verifierKey = Keys.passwordVerifier,
        verify = { credentialHasher.verifyPassword(password, it) },
        successValue = Unit,
    ) { preferences ->
        preferences.clear()
    }

    suspend fun updateRecoveryQuestion(
        password: CharArray,
        recoveryQuestion: String,
        recoveryAnswer: CharArray,
    ): SecurityResult<Unit> {
        val question = validateRecoveryQuestion(recoveryQuestion)
        val answerVerifier = credentialHasher.hashRecoveryAnswer(recoveryAnswer)
        return verifyAndApply(
            verifierKey = Keys.passwordVerifier,
            verify = { credentialHasher.verifyPassword(password, it) },
            successValue = Unit,
        ) { preferences ->
            preferences[Keys.recoveryQuestion] = question
            preferences[Keys.recoveryAnswerVerifier] = answerVerifier
        }
    }

    suspend fun regenerateRecoveryCode(password: CharArray): SecurityResult<String> {
        val recoveryCode = credentialHasher.generateRecoveryCode()
        val verifier = credentialHasher.hashRecoveryCode(recoveryCode)
        return verifyAndApply(
            verifierKey = Keys.passwordVerifier,
            verify = { credentialHasher.verifyPassword(password, it) },
            successValue = recoveryCode,
        ) { preferences ->
            preferences[Keys.recoveryCodeVerifier] = verifier
        }
    }

    suspend fun setDeviceAuthEnabled(enabled: Boolean) = operationMutex.withLock {
        dataStore.edit { preferences ->
            val lockEnabled = preferences[Keys.lockEnabled] ?: false
            require(lockEnabled || !enabled) { "برای ورود با قفل گوشی، ابتدا قفل برنامه را فعال کنید" }
            preferences[Keys.deviceAuthEnabled] = lockEnabled && enabled
        }
    }

    suspend fun setLockTimeoutSeconds(seconds: Int) {
        requireValidTimeout(seconds)
        operationMutex.withLock {
            dataStore.edit { preferences -> preferences[Keys.lockTimeoutSeconds] = seconds }
        }
    }

    suspend fun recordDeviceAuthenticationSuccess() = operationMutex.withLock {
        dataStore.edit { preferences ->
            require(preferences[Keys.lockEnabled] == true && preferences[Keys.deviceAuthEnabled] == true) {
                "ورود با قفل گوشی فعال نیست"
            }
            resetThrottle(preferences)
        }
    }

    private suspend fun <T> verifyAndApply(
        verifierKey: Preferences.Key<String>,
        verify: (String) -> Boolean,
        successValue: T,
        onSuccess: (MutablePreferences) -> Unit = {},
    ): SecurityResult<T> = operationMutex.withLock {
        val now = currentTimeMillis()
        val snapshot = dataStore.data.first()
        require(snapshot[Keys.lockEnabled] == true) { "قفل برنامه فعال نیست" }

        val failedAttempts = (snapshot[Keys.failedAttempts] ?: 0).coerceAtLeast(0)
        val blockedUntil = (snapshot[Keys.blockedUntilEpochMillis] ?: 0L).coerceAtLeast(0L)
        if (now < blockedUntil) {
            return@withLock SecurityResult.Blocked(blockedUntil, failedAttempts)
        }

        val storedVerifier = snapshot[verifierKey]
        if (storedVerifier == null || !verify(storedVerifier)) {
            return@withLock recordFailedAttempt(snapshot, now)
        }

        dataStore.edit { preferences ->
            resetThrottle(preferences)
            onSuccess(preferences)
        }
        SecurityResult.Success(successValue)
    }

    private suspend fun recordFailedAttempt(
        snapshot: Preferences,
        now: Long,
    ): SecurityResult<Nothing> {
        val attempts = Math.addExact((snapshot[Keys.failedAttempts] ?: 0).coerceIn(0, MAX_STORED_ATTEMPTS), 1)
        val delaySeconds = SecurityAttemptThrottle.delaySeconds(attempts)
        val blockedUntil = if (delaySeconds == 0L) {
            0L
        } else {
            runCatching { Math.addExact(now, Math.multiplyExact(delaySeconds, 1_000L)) }.getOrDefault(Long.MAX_VALUE)
        }
        dataStore.edit { preferences ->
            preferences[Keys.failedAttempts] = attempts
            preferences[Keys.blockedUntilEpochMillis] = blockedUntil
        }
        return if (blockedUntil > now) {
            SecurityResult.Blocked(blockedUntil, attempts)
        } else {
            SecurityResult.Incorrect
        }
    }

    private fun validateRecoveryQuestion(value: String): String {
        val question = value.trim()
        val codePoints = question.codePointCount(0, question.length)
        require(codePoints in MIN_QUESTION_CODE_POINTS..MAX_QUESTION_CODE_POINTS) {
            "سؤال بازیابی باید بین ۳ تا ۲۰۰ نویسه باشد"
        }
        return question
    }

    private fun requireValidTimeout(seconds: Int) {
        require(seconds in ALLOWED_LOCK_TIMEOUT_SECONDS) { "زمان قفل خودکار معتبر نیست" }
    }

    private fun resetThrottle(preferences: MutablePreferences) {
        preferences[Keys.failedAttempts] = 0
        preferences[Keys.blockedUntilEpochMillis] = 0L
    }

    private object Keys {
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val deviceAuthEnabled = booleanPreferencesKey("device_auth_enabled")
        val passwordVerifier = stringPreferencesKey("password_verifier")
        val recoveryQuestion = stringPreferencesKey("recovery_question")
        val recoveryAnswerVerifier = stringPreferencesKey("recovery_answer_verifier")
        val recoveryCodeVerifier = stringPreferencesKey("recovery_code_verifier")
        val lockTimeoutSeconds = intPreferencesKey("lock_timeout_seconds")
        val failedAttempts = intPreferencesKey("failed_attempts")
        val blockedUntilEpochMillis = longPreferencesKey("blocked_until_epoch_millis")
    }

    companion object {
        const val DEFAULT_LOCK_TIMEOUT_SECONDS = 30
        val ALLOWED_LOCK_TIMEOUT_SECONDS: Set<Int> = setOf(0, 30, 60, 300, 900)

        private const val MIN_QUESTION_CODE_POINTS = 3
        private const val MAX_QUESTION_CODE_POINTS = 200
        private const val MAX_STORED_ATTEMPTS = 1_000
    }
}
