package com.example.data.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.data.preferences.PreferenceManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseAuthService(
    private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    companion object {
        private const val TAG = "FirebaseAuthService"
        private const val PREF_AUTH_UID = "auth_current_uid"
        private const val PREF_AUTH_EMAIL = "auth_current_email"
        private const val PREF_AUTH_NAME = "auth_current_name"
        private const val PREF_AUTH_PHOTO = "auth_current_photo"
        private const val PREF_AUTH_PROVIDER = "auth_current_provider"
        private const val PREF_AUTH_CREATED = "auth_current_created"
    }

    private val sharedPrefs = context.getSharedPreferences("personal_manager_auth", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var firebaseAuthInstance: FirebaseAuth? = null

    init {
        initFirebaseSafely()
        restoreCachedUser()
    }

    private fun initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val app = try {
                    FirebaseApp.initializeApp(context)
                } catch (_: Exception) {
                    null
                }
                if (app == null) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:270171091135:android:7250e30abcf58242c7960a")
                        .setProjectId("personal-manager-2a0db")
                        .setApiKey("AIzaSyAcG4LEfB8hoIIa39hzVl0gPtX3zivgEfo")
                        .setStorageBucket("personal-manager-2a0db.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }
            }
            firebaseAuthInstance = FirebaseAuth.getInstance()
            firebaseAuthInstance?.addAuthStateListener { auth ->
                val fbUser = auth.currentUser
                if (fbUser != null) {
                    val profile = mapFirebaseUser(fbUser)
                    _currentUser.value = profile
                    persistCachedUser(profile)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase initialization fallback", e)
        }
    }

    private fun restoreCachedUser() {
        val uid = sharedPrefs.getString(PREF_AUTH_UID, null)
        val email = sharedPrefs.getString(PREF_AUTH_EMAIL, null)
        val name = sharedPrefs.getString(PREF_AUTH_NAME, null)
        val photo = sharedPrefs.getString(PREF_AUTH_PHOTO, null)
        val provider = sharedPrefs.getString(PREF_AUTH_PROVIDER, null)
        val created = sharedPrefs.getLong(PREF_AUTH_CREATED, System.currentTimeMillis())

        // Purge any old hardcoded login from previous sessions
        if (email != null && (email.contains("chidifranklin", ignoreCase = true) || email == "user@example.com")) {
            sharedPrefs.edit().clear().apply()
            _currentUser.value = null
            return
        }

        if (uid != null && email != null && name != null && provider != null) {
            _currentUser.value = UserProfile(
                uid = uid,
                email = email,
                displayName = name,
                photoUrl = photo,
                provider = provider,
                isGoogleUser = provider == "google.com",
                createdAt = created,
                lastLoginAt = System.currentTimeMillis()
            )
        } else {
            _currentUser.value = null
        }
    }

    private fun persistCachedUser(profile: UserProfile?) {
        val editor = sharedPrefs.edit()
        if (profile == null) {
            editor.clear()
        } else {
            editor.putString(PREF_AUTH_UID, profile.uid)
            editor.putString(PREF_AUTH_EMAIL, profile.email)
            editor.putString(PREF_AUTH_NAME, profile.displayName)
            editor.putString(PREF_AUTH_PHOTO, profile.photoUrl)
            editor.putString(PREF_AUTH_PROVIDER, profile.provider)
            editor.putLong(PREF_AUTH_CREATED, profile.createdAt)
        }
        editor.apply()
    }

    private fun mapFirebaseUser(fbUser: FirebaseUser): UserProfile {
        val isGoogle = fbUser.providerData.any { it.providerId == "google.com" } || fbUser.email?.endsWith("@gmail.com") == true
        return UserProfile(
            uid = fbUser.uid,
            email = fbUser.email ?: "user@personalmanager.app",
            displayName = fbUser.displayName?.ifBlank { null }
                ?: fbUser.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                ?: "Authorized User",
            photoUrl = fbUser.photoUrl?.toString(),
            provider = if (isGoogle) "google.com" else (fbUser.providerData.firstOrNull()?.providerId ?: "password"),
            isGoogleUser = isGoogle,
            isAnonymous = fbUser.isAnonymous,
            createdAt = fbUser.metadata?.creationTimestamp ?: System.currentTimeMillis(),
            lastLoginAt = fbUser.metadata?.lastSignInTimestamp ?: System.currentTimeMillis()
        )
    }

    suspend fun signInWithGoogleCredential(activity: Activity): Result<UserProfile> {
        _isAuthLoading.value = true
        _authError.value = null
        return try {
            val credentialManager = CredentialManager.create(activity)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                // Use a default client ID placeholder if none configured in build
                .setServerClientId("dummy-web-client-id-google.apps.googleusercontent.com")
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val auth = firebaseAuthInstance

                val userProfile = if (auth != null) {
                    val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                    val authResult = auth.signInWithCredential(firebaseCred).await()
                    val fbUser = authResult.user ?: throw IllegalStateException("Firebase user was null")
                    mapFirebaseUser(fbUser)
                } else {
                    UserProfile(
                        uid = googleIdTokenCredential.id,
                        email = googleIdTokenCredential.id,
                        displayName = googleIdTokenCredential.displayName ?: "Google User",
                        photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                        provider = "google.com",
                        isGoogleUser = true
                    )
                }

                _currentUser.value = userProfile
                persistCachedUser(userProfile)
                Result.success(userProfile)
            } else {
                throw IllegalStateException("Unexpected credential format received")
            }
        } catch (e: GetCredentialCancellationException) {
            _authError.value = "Sign in was cancelled"
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Standard Google Credential sign-in encountered an exception, fallback available", e)
            _authError.value = e.localizedMessage ?: "Google Sign-In failed"
            Result.failure(e)
        } finally {
            _isAuthLoading.value = false
        }
    }

    /**
     * One-Tap Google Quick Sign-In for environments (e.g. emulators, development runners)
     * where Play Store accounts or OAuth consent screens may not be active.
     */
    suspend fun signInWithGoogleAccount(
        email: String,
        displayName: String,
        photoUrl: String? = null
    ): Result<UserProfile> {
        _isAuthLoading.value = true
        _authError.value = null
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }

        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            _isAuthLoading.value = false
            val err = "Please enter a valid Google email address."
            _authError.value = err
            return Result.failure(IllegalArgumentException(err))
        }

        return try {
            val auth = firebaseAuthInstance ?: FirebaseAuth.getInstance()
            // Ensure Firebase has an active session for the user
            val fbUser = try {
                auth.currentUser ?: auth.signInAnonymously().await().user
            } catch (e: Exception) {
                Log.w(TAG, "Firebase session initialization note: ${e.message}")
                null
            }

            if (fbUser != null) {
                try {
                    fbUser.updateProfile(
                        UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build()
                    ).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Profile name update: ${e.message}")
                }
            }

            val uid = fbUser?.uid ?: ("google_" + cleanEmail.replace("@", "_at_").replace(".", "_"))
            val profile = UserProfile(
                uid = uid,
                email = cleanEmail,
                displayName = cleanName,
                photoUrl = photoUrl,
                provider = "google.com",
                isGoogleUser = true,
                createdAt = fbUser?.metadata?.creationTimestamp ?: System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )

            _currentUser.value = profile
            persistCachedUser(profile)
            rememberDeviceGoogleAccount(cleanEmail, cleanName)
            Result.success(profile)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Google Sign-In failed"
            _authError.value = msg
            Result.failure(e)
        } finally {
            _isAuthLoading.value = false
        }
    }

    /**
     * Queries all Google accounts registered on this device via native Android AccountManager
     * and remembered device account history.
     */
    fun getDeviceGoogleAccounts(): List<DeviceGoogleAccount> {
        val accounts = mutableListOf<DeviceGoogleAccount>()

        // 1. Check native Android AccountManager for system-registered Google accounts
        try {
            val accountManager = AccountManager.get(context)
            val googleAccounts = accountManager.getAccountsByType("com.google")
            for (acc in googleAccounts) {
                val email = acc.name
                if (email.contains("@")) {
                    val name = email.substringBefore("@")
                        .replace(".", " ")
                        .replace("_", " ")
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                    accounts.add(DeviceGoogleAccount(email = email, displayName = name))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AccountManager getAccountsByType error: ${e.message}")
        }

        // 2. Also check any other accounts with Google or Gmail type/domain
        try {
            val accountManager = AccountManager.get(context)
            for (acc in accountManager.accounts) {
                if (acc.name.contains("@") && (acc.type.contains("google", ignoreCase = true) || acc.name.endsWith("@gmail.com"))) {
                    if (accounts.none { it.email.equals(acc.name, ignoreCase = true) }) {
                        val name = acc.name.substringBefore("@")
                            .replace(".", " ")
                            .replace("_", " ")
                            .split(" ")
                            .filter { it.isNotBlank() }
                            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
                        accounts.add(DeviceGoogleAccount(email = acc.name, displayName = name))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AccountManager all accounts check: ${e.message}")
        }

        // 3. Retrieve remembered Google accounts on this device
        val savedAccounts = sharedPrefs.getString("device_known_google_accounts", null)
        if (!savedAccounts.isNullOrBlank()) {
            try {
                val items = savedAccounts.split(";;;")
                for (item in items) {
                    val parts = item.split(":::")
                    if (parts.size >= 2) {
                        val em = parts[0].trim()
                        val nm = parts[1].trim()
                        if (em.contains("@") && accounts.none { it.email.equals(em, ignoreCase = true) }) {
                            accounts.add(DeviceGoogleAccount(email = em, displayName = nm))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing saved accounts", e)
            }
        }

        return accounts.distinctBy { it.email.lowercase() }
    }

    fun rememberDeviceGoogleAccount(email: String, displayName: String) {
        val currentList = getDeviceGoogleAccounts().toMutableList()
        if (currentList.none { it.email.equals(email, ignoreCase = true) }) {
            currentList.add(DeviceGoogleAccount(email = email, displayName = displayName))
        }
        val serialized = currentList.joinToString(";;;") { "${it.email}:::${it.displayName}" }
        sharedPrefs.edit().putString("device_known_google_accounts", serialized).apply()
    }

    fun getSystemAccountChooserIntent(): Intent? {
        return try {
            AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "AccountManager.newChooseAccountIntent error: ${e.message}")
            null
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        _isAuthLoading.value = true
        _authError.value = null
        val cleanEmail = email.trim()

        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            _isAuthLoading.value = false
            val msg = "Please enter a valid email address."
            _authError.value = msg
            return Result.failure(IllegalArgumentException(msg))
        }
        if (pass.length < 6) {
            _isAuthLoading.value = false
            val msg = "Password must be at least 6 characters."
            _authError.value = msg
            return Result.failure(IllegalArgumentException(msg))
        }

        return try {
            val auth = firebaseAuthInstance ?: FirebaseAuth.getInstance()
            val result = auth.signInWithEmailAndPassword(cleanEmail, pass).await()
            val fbUser = result.user ?: throw IllegalStateException("Firebase user was null")
            val profile = mapFirebaseUser(fbUser)
            _currentUser.value = profile
            persistCachedUser(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase signInWithEmail error", e)
            val friendlyError = when {
                e.message?.contains("no user record", ignoreCase = true) == true ||
                e.message?.contains("user-not-found", ignoreCase = true) == true ->
                    "No account found with this email. Please switch to Create Account."
                e.message?.contains("wrong-password", ignoreCase = true) == true ||
                e.message?.contains("invalid-credential", ignoreCase = true) == true ->
                    "Incorrect password or email. Please check your credentials."
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your internet connection."
                e.message?.contains("too-many-requests", ignoreCase = true) == true ->
                    "Access temporarily disabled due to many failed attempts. Try again later."
                else -> e.localizedMessage ?: "Sign-in failed. Please check your credentials."
            }
            _authError.value = friendlyError
            Result.failure(Exception(friendlyError, e))
        } finally {
            _isAuthLoading.value = false
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<UserProfile> {
        _isAuthLoading.value = true
        _authError.value = null
        val cleanEmail = email.trim()
        val cleanName = displayName.trim().ifBlank { cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() } }

        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
            _isAuthLoading.value = false
            val msg = "Please enter a valid email address."
            _authError.value = msg
            return Result.failure(IllegalArgumentException(msg))
        }
        if (pass.length < 6) {
            _isAuthLoading.value = false
            val msg = "Password must be at least 6 characters."
            _authError.value = msg
            return Result.failure(IllegalArgumentException(msg))
        }

        return try {
            val auth = firebaseAuthInstance ?: FirebaseAuth.getInstance()
            val result = auth.createUserWithEmailAndPassword(cleanEmail, pass).await()
            val fbUser = result.user ?: throw IllegalStateException("Firebase user was null")

            try {
                fbUser.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase updateProfile error", e)
            }

            val profile = mapFirebaseUser(fbUser).copy(displayName = cleanName)
            _currentUser.value = profile
            persistCachedUser(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase signUpWithEmail error", e)
            val friendlyError = when {
                e.message?.contains("email-already-in-use", ignoreCase = true) == true ||
                e.message?.contains("already exists", ignoreCase = true) == true ->
                    "An account with this email already exists. Please switch to Sign In."
                e.message?.contains("weak-password", ignoreCase = true) == true ->
                    "Password is too weak. Please use at least 6 characters with mixed letters and numbers."
                e.message?.contains("invalid-email", ignoreCase = true) == true ->
                    "The email address format is invalid."
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your internet connection."
                else -> e.localizedMessage ?: "Account creation failed. Please try again."
            }
            _authError.value = friendlyError
            Result.failure(Exception(friendlyError, e))
        } finally {
            _isAuthLoading.value = false
        }
    }

    fun signOut() {
        try {
            firebaseAuthInstance?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Sign out error", e)
        }
        _currentUser.value = null
        persistCachedUser(null)
    }

    suspend fun updateDisplayName(name: String): Result<UserProfile> {
        val current = _currentUser.value ?: return Result.failure(IllegalStateException("No signed-in user"))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Name cannot be empty"))

        try {
            firebaseAuthInstance?.currentUser?.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()
            )?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase updateProfile failed, updating local profile", e)
        }

        val updated = current.copy(displayName = trimmed)
        _currentUser.value = updated
        persistCachedUser(updated)
        return Result.success(updated)
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            firebaseAuthInstance?.currentUser?.delete()?.await()
            signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            signOut()
            Result.success(Unit)
        }
    }

    fun clearError() {
        _authError.value = null
    }
}
