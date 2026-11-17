package com.example

import android.app.Application
import com.example.data.local.GuestPreferencesDataStore
import com.example.data.local.PinpointDatabase
import com.example.data.remote.FirebaseRemoteDataSource
import com.example.data.repository.PinpointRepository
import com.example.service.PinpointNotificationManager

class PinpointApplication : Application() {
    lateinit var database: PinpointDatabase
        private set
    lateinit var preferences: GuestPreferencesDataStore
        private set
    lateinit var firebaseRemote: FirebaseRemoteDataSource
        private set
    lateinit var repository: PinpointRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = PinpointDatabase.getInstance(this)
        preferences = GuestPreferencesDataStore(this)
        firebaseRemote = FirebaseRemoteDataSource(this)
        repository = PinpointRepository(
            db = database,
            preferences = preferences,
            firebaseRemote = firebaseRemote
        )
        PinpointNotificationManager.ensureChannels(this)
    }
}
