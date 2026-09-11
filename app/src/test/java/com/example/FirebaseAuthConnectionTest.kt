package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.FirebaseAuthService
import com.example.data.preferences.PreferenceManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAuthConnectionTest {

    @Test
    fun `firebase auth dependency and class are resolvable`() {
        val authClass = FirebaseAuth::class.java
        assertNotNull(authClass)
    }

    @Test
    fun `firebase app and auth initialize with google-services project configuration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:270171091135:android:7250e30abcf58242c7960a")
                .setProjectId("personal-manager-2a0db")
                .setApiKey("AIzaSyAcG4LEfB8hoIIa39hzVl0gPtX3zivgEfo")
                .setStorageBucket("personal-manager-2a0db.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(context, options, "test-auth-app")
        } else {
            FirebaseApp.getInstance()
        }

        assertNotNull("FirebaseApp should be initialized", app)
        val auth = FirebaseAuth.getInstance(app)
        assertNotNull("FirebaseAuth instance should not be null", auth)
        assertEquals("personal-manager-2a0db", app.options.projectId)
    }

    @Test
    fun `firebase auth service initializes successfully without throwing exceptions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferenceManager = PreferenceManager(context)
        val authService = FirebaseAuthService(context, preferenceManager)

        assertNotNull(authService)
        assertNotNull(authService.currentUser)
        assertNotNull(authService.isAuthLoading)
        assertNotNull(authService.authError)
    }
}
