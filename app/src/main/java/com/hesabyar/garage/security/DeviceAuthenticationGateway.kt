package com.hesabyar.garage.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class DeviceAuthenticationAvailability {
    AVAILABLE,
    NOT_ENROLLED,
    TEMPORARILY_UNAVAILABLE,
    UNAVAILABLE,
}

sealed interface DeviceAuthenticationResult {
    data object Success : DeviceAuthenticationResult
    data object Canceled : DeviceAuthenticationResult
    data class Error(val message: String) : DeviceAuthenticationResult
}

/**
 * Opens Android's system-owned authentication prompt. No biometric template or
 * device credential is ever exposed to, stored by, or backed up by the app.
 */
class DeviceAuthenticationGateway(
    private val activity: FragmentActivity,
) {
    fun availability(): DeviceAuthenticationAvailability = when (
        BiometricManager.from(activity).canAuthenticate(allowedAuthenticators())
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> DeviceAuthenticationAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> DeviceAuthenticationAvailability.NOT_ENROLLED
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            DeviceAuthenticationAvailability.TEMPORARILY_UNAVAILABLE
        else -> DeviceAuthenticationAvailability.UNAVAILABLE
    }

    fun authenticate(onResult: (DeviceAuthenticationResult) -> Unit) {
        if (availability() != DeviceAuthenticationAvailability.AVAILABLE) {
            onResult(DeviceAuthenticationResult.Error(availabilityMessage(availability())))
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(DeviceAuthenticationResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val canceled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    if (canceled) onResult(DeviceAuthenticationResult.Canceled)
                    else onResult(
                        DeviceAuthenticationResult.Error(
                            errString.toString().ifBlank { "قفل گوشی در دسترس نیست" },
                        ),
                    )
                }
            },
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن حساب‌یار")
            .setSubtitle("از اثر انگشت، چهره یا رمز قفل گوشی استفاده کنید")
            .setAllowedAuthenticators(allowedAuthenticators())
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(promptInfo)
    }

    companion object {
        fun availabilityMessage(availability: DeviceAuthenticationAvailability): String = when (availability) {
            DeviceAuthenticationAvailability.AVAILABLE -> "قفل گوشی آماده است"
            DeviceAuthenticationAvailability.NOT_ENROLLED ->
                "ابتدا اثر انگشت، چهره یا رمز صفحهٔ گوشی را در تنظیمات گوشی فعال کنید"
            DeviceAuthenticationAvailability.TEMPORARILY_UNAVAILABLE ->
                "قفل گوشی موقتاً در دسترس نیست؛ کمی بعد دوباره تلاش کنید"
            DeviceAuthenticationAvailability.UNAVAILABLE ->
                "این گوشی روش امن سازگار برای باز کردن برنامه ندارد"
        }

        private fun allowedAuthenticators(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }
}
