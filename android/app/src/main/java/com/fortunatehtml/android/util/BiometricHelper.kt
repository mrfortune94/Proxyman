package com.fortunatehtml.android.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Helper class for biometric authentication.
 * Used to protect access to saved projects when biometric lock is enabled.
 */
object BiometricHelper {

    /**
     * Check if biometric authentication is available on this device.
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Get a human-readable status of biometric availability.
     */
    fun getBiometricStatus(context: Context): String {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Available"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Hardware unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometrics enrolled"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Security update required"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Not supported"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "Status unknown"
            else -> "Not available"
        }
    }

    /**
     * Show biometric authentication prompt.
     *
     * @param activity The FragmentActivity to show the prompt in
     * @param title Title text for the prompt
     * @param subtitle Subtitle text for the prompt
     * @param onSuccess Callback when authentication succeeds
     * @param onError Callback when authentication fails or is cancelled
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authentication Required",
        subtitle: String = "Verify your identity to access protected content",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val errorMessage = when (errorCode) {
                    BiometricPrompt.ERROR_CANCELED,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "Authentication cancelled"
                    BiometricPrompt.ERROR_LOCKOUT -> "Too many attempts. Try again later."
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometric permanently locked. Use device credentials."
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> "No biometrics enrolled"
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> "No device credential set"
                    else -> errString.toString()
                }
                onError(errorMessage)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Don't call onError here - the prompt stays open for retry
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
