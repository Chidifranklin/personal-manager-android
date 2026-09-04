package com.example

import android.app.Application
import com.example.data.ai.GeminiAssistantService
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.PersonalManagerRepository
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

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = PersonalManagerRepository(database)
        preferenceManager = PreferenceManager(this)
        alarmScheduler = AlarmScheduler(this)
        aiService = GeminiAssistantService(repository, preferenceManager)

        // Seed default initial state if brand new DB
        CoroutineScope(Dispatchers.IO).launch {
            repository.seedInitialDataIfEmpty()
        }
    }
}
