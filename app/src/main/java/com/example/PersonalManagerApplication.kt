package com.example

import android.app.Application
import com.example.data.ai.GeminiAssistantService
import com.example.data.auth.FirebaseAuthService
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.PersonalManagerRepository
import com.example.data.sync.FirestoreSyncService
import com.example.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PersonalManagerApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: PersonalManagerRepository
        private set

    lateinit var preferenceManager: PreferenceManager
        private set

    lateinit var alarmScheduler: AlarmScheduler
        private set

    lateinit var aiService: GeminiAssistantService
        private set

    lateinit var authService: FirebaseAuthService
        private set

    lateinit var firestoreSyncService: FirestoreSyncService
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = PersonalManagerRepository(database)
        preferenceManager = PreferenceManager(this)
        alarmScheduler = AlarmScheduler(this)
        aiService = GeminiAssistantService(repository, preferenceManager)
        authService = FirebaseAuthService(this, preferenceManager)
        firestoreSyncService = FirestoreSyncService(this, database, repository)

        CoroutineScope(Dispatchers.IO).launch {
            authService.currentUser.collect { user ->
                if (user != null) {
                    repository.setCurrentUser(user.uid)
                    repository.seedInitialUserDataIfEmpty(user.uid, user.displayName)
                    firestoreSyncService.initForUser(user.uid)
                } else {
                    repository.setCurrentUser("local_default_user")
                    repository.seedInitialDataIfEmpty()
                }
            }
        }
    }
}
