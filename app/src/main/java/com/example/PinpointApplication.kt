package com.example

import android.app.Application
import com.example.data.local.GuestPreferencesDataStore
import com.example.data.local.PinpointDatabase
import com.example.data.remote.FirebaseRemoteDataSource
import com.example.data.repository.PinpointRepository
import com.example.service.PinpointNotificationManager
import com.google.firebase.FirebaseApp

class PinpointApplication : Application() {
    val database: PinpointDatabase by lazy { PinpointDatabase.getInstance(this) }
    val preferences: GuestPreferencesDataStore by lazy { GuestPreferencesDataStore(this) }
    val firebaseRemote: FirebaseRemoteDataSource? by lazy {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                FirebaseRemoteDataSource(this)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
    val repository: PinpointRepository by lazy {
        PinpointRepository(
            db = database,
            preferences = preferences,
            firebaseRemote = firebaseRemote
        )
    }

    override fun onCreate() {
        super.onCreate()
        PinpointNotificationManager.ensureChannels(this)
    }
}
