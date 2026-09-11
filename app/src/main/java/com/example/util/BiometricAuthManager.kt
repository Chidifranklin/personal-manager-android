package com.example.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Result of checking device biometric hardware & enrollment capabilities.
 */
sealed class BiometricCapability {
    data class Available(val description: String) : BiometricCapability()
    data class NotEnrolled(val message: String) : BiometricCapability()
    data class NoHardware(val message: String) : BiometricCapability()
    data class HardwareUnavailable(val message: String) : BiometricCapability()
    data class SecurityUpdateRequired(val message: String) : BiometricCapability()
    data class Unsupported(val message: String) : BiometricCapability()

    val isAvailable: Boolean
        get() = this is Available
}

object BiometricAuthManager {

    /**
     * Checks if biometric authentication (fingerprint, face, or device PIN/pattern) is supported and enrolled.
     */
    fun checkBiometricCapability(context: Context): BiometricCapability {
        val biometricManager = BiometricManager.from(context)
        val authenticators = Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL

        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                BiometricCapability.Available("Fingerprint, Face Unlock, or Device Credential ready")
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                BiometricCapability.NotEnrolled("Biometric hardware available, but no biometric or lock credentials are enrolled in device Settings.")
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                BiometricCapability.NoHardware("No biometric hardware detected on this device.")
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                BiometricCapability.HardwareUnavailable("Biometric sensors are currently busy or unavailable.")
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                BiometricCapability.SecurityUpdateRequired("Security update required for biometric sensors.")
            }
            else -> {
                // Fallback check for strong biometrics only
                val strongCheck = biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
                if (strongCheck == BiometricManager.BIOMETRIC_SUCCESS) {
                    BiometricCapability.Available("Strong Biometrics ready")
                } else {
                    BiometricCapability.Unsupported("Biometric authentication is not supported or ready on this device.")
                }
            }
        }
    }

    /**
     * Launches the AndroidX BiometricPrompt for authentication.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Personal Manager Security",
        subtitle: String = "Confirm your identity",
        description: String = "Unlock to access protected financial data, records, and vault.",
        allowDeviceCredential: Boolean = true,
        negativeButtonText: String = "Cancel",
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)

        if (allowDeviceCredential) {
            // When DEVICE_CREDENTIAL is used, setNegativeButtonText MUST NOT be called
            promptInfoBuilder.setAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            promptInfoBuilder.setAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK
            )
            promptInfoBuilder.setNegativeButtonText(negativeButtonText)
        }

        val promptInfo = try {
            promptInfoBuilder.build()
        } catch (_: Exception) {
            // Fallback for older OEM frameworks where DEVICE_CREDENTIAL may throw
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDescription(description)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
                .build()
        }

        prompt.authenticate(promptInfo)
    }
}
