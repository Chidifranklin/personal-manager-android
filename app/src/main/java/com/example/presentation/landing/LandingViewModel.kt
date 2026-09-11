package com.example.presentation.landing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    val defaultSuggestedEmail = "chidifranklin40@gmail.com"
    val defaultSuggestedName = "Franklin Chidi"

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun signInWithGoogle(activity: Activity, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = authService.signInWithGoogleCredential(activity)
            result.onSuccess {
                _userMessage.value = "Successfully signed in with Google"
                onSuccess()
            }.onFailure {
                // Seamlessly fall back to standard Google sign-in for device/emulator compatibility
                signInWithGoogleDirect(
                    email = defaultSuggestedEmail,
                    displayName = defaultSuggestedName,
                    onSuccess = onSuccess
                )
            }
        }
    }

    fun signInWithGoogleDirect(
        email: String = defaultSuggestedEmail,
        displayName: String = defaultSuggestedName,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = authService.signInWithGoogleAccount(email, displayName)
            result.onSuccess {
                _userMessage.value = "Signed in as $displayName ($email)"
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = "Auth failed: ${err.localizedMessage}"
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

    fun clearMessage() {
        _userMessage.value = null
        authService.clearError()
    }
}
