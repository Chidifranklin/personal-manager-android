package com.example.presentation.landing

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.DeviceGoogleAccount
import com.example.data.auth.FirebaseAuthService
import com.example.data.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LandingViewModel(
    private val authService: FirebaseAuthService
) : ViewModel() {

    val currentUser: StateFlow<UserProfile?> = authService.currentUser
    val isAuthLoading: StateFlow<Boolean> = authService.isAuthLoading
    val authError: StateFlow<String?> = authService.authError

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun getDeviceGoogleAccounts(): List<DeviceGoogleAccount> {
        return authService.getDeviceGoogleAccounts()
    }

    fun getSystemAccountChooserIntent(): Intent? {
        return authService.getSystemAccountChooserIntent()
    }

    fun signInWithGoogle(
        activity: Activity,
        onRequireManualGoogleAccount: () -> Unit,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = authService.signInWithGoogleCredential(activity)
            result.onSuccess {
                _userMessage.value = "Signed in as ${it.displayName}"
                onSuccess()
            }.onFailure { err ->
                if (err is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    _userMessage.value = "Google Sign-In was cancelled."
                } else {
                    // Provide fallback for devices/emulators where Google Play Services accounts are not registered
                    onRequireManualGoogleAccount()
                }
            }
        }
    }

    fun signInWithGoogleAccount(
        email: String,
        displayName: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = authService.signInWithGoogleAccount(email, displayName)
            result.onSuccess {
                _userMessage.value = "Signed in as ${it.displayName}"
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = err.localizedMessage ?: "Google Sign-In failed"
            }
        }
    }

    fun signInWithEmail(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = authService.signInWithEmail(email, pass)
            result.onSuccess {
                _userMessage.value = "Signed in successfully"
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = "Email sign-in failed: ${err.localizedMessage}"
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String, name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = authService.signUpWithEmail(email, pass, name)
            result.onSuccess {
                _userMessage.value = "Account created successfully"
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = "Sign-up failed: ${err.localizedMessage}"
            }
        }
    }

    fun signOut() {
        authService.signOut()
        _userMessage.value = "Signed out"
    }

    fun clearMessage() {
        _userMessage.value = null
        authService.clearError()
    }
}
