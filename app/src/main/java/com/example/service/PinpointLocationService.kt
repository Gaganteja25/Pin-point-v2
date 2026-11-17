package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.PinpointApplication
import com.example.data.repository.LocationSharingStatus
import com.example.domain.BoundaryEventType
import com.example.domain.GeofenceEngine
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PinpointLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationManager: LocationManager? = null
    private var activeCrowdName: String = "Active Crowd"
    private var isTrackingStarted = false

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            handleLocationUpdate(location)
        }
    }

    private val fallbackListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocationUpdate(location)
        }

        @Deprecated("Deprecated in Android API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {
            serviceScope.launch {
                val repo = (application as PinpointApplication).repository
                repo.setLocationStatus(LocationSharingStatus.UNAVAILABLE, sharing = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SHARING -> {
                stopSharingAndTerminate()
                return START_NOT_STICKY
            }
            ACTION_START_SHARING, null -> {
                startForegroundAndTrack()
            }
        }
        return START_STICKY
    }

    private fun startForegroundAndTrack() {
        val app = application as PinpointApplication
        serviceScope.launch {
            val session = app.preferences.guestSessionFlow.first()
            val crowdId = session.activeCrowdId
            if (!session.isSharingEnabled || crowdId == null) {
                stopForegroundCompat()
                stopSelf()
                return@launch
            }
            val crowd = app.database.crowdDao().getCrowdById(crowdId)
            activeCrowdName = crowd?.name ?: "Active Crowd"

            val notification = PinpointNotificationManager.buildTrackingNotification(
                context = this@PinpointLocationService,
                crowdName = activeCrowdName,
                statusText = "Sharing live GPS location • Tap STOP SHARING to pause"
            )
            startForegroundCompat(notification)

            if (!hasLocationPermission()) {
                app.repository.setLocationStatus(LocationSharingStatus.PERMISSION_DENIED, sharing = false)
                stopForegroundCompat()
                stopSelf()
                return@launch
            }

            app.repository.setLocationStatus(LocationSharingStatus.CONNECTING, sharing = true)
            if (!isTrackingStarted) {
                beginOsLocationUpdates()
            }
        }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PinpointNotificationManager.NOTIFICATION_ID_TRACKING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(PinpointNotificationManager.NOTIFICATION_ID_TRACKING, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun beginOsLocationUpdates() {
        isTrackingStarted = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 8_000L)
            .setMinUpdateIntervalMillis(4_000L)
            .setMaxUpdateDelayMillis(15_000L)
            .build()

        try {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) handleLocationUpdate(loc)
            }
            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    startLocationManagerFallback()
                }
        } catch (_: Exception) {
            startLocationManagerFallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationManagerFallback() {
        val lm = locationManager ?: return
        try {
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                serviceScope.launch {
                    (application as PinpointApplication).repository
                        .setLocationStatus(LocationSharingStatus.UNAVAILABLE, sharing = true)
                }
                return
            }
            lm.getLastKnownLocation(provider)?.let { handleLocationUpdate(it) }
            lm.requestLocationUpdates(provider, 8_000L, 5f, fallbackListener, Looper.getMainLooper())
        } catch (_: Exception) {
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val app = application as PinpointApplication
        serviceScope.launch {
            val session = app.preferences.guestSessionFlow.first()
            if (!session.isSharingEnabled) {
                stopSharingAndTerminate()
                return@launch
            }
            val event = app.repository.processLiveGpsFix(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else 20f
            )

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val updatedNotif = PinpointNotificationManager.buildTrackingNotification(
                context = this@PinpointLocationService,
                crowdName = activeCrowdName,
                statusText = "Live GPS active (±${location.accuracy.toInt()}m) • Tap STOP SHARING to end"
            )
            nm.notify(PinpointNotificationManager.NOTIFICATION_ID_TRACKING, updatedNotif)

            if (event != null) {
                val action = if (event.eventType == BoundaryEventType.EXITED.name) {
                    "exited the geofence boundary"
                } else {
                    "returned inside the geofence boundary"
                }
                PinpointNotificationManager.postAlertNotification(
                    context = this@PinpointLocationService,
                    notificationId = (System.currentTimeMillis() % 100_000).toInt() + 5000,
                    title = "PINPOINT Boundary Alert",
                    body = "${event.memberName} $action (${GeofenceEngine.formatDistance(event.distanceMeters)} from centre)."
                )
            }
        }
    }

    private fun stopSharingAndTerminate() {
        isTrackingStarted = false
        try {
            fusedClient.removeLocationUpdates(fusedCallback)
        } catch (_: Exception) {
        }
        try {
            locationManager?.removeUpdates(fallbackListener)
        } catch (_: Exception) {
        }
        val app = application as PinpointApplication
        serviceScope.launch {
            app.repository.stopLocationSharingManually()
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Design service to survive ordinary recent-apps swipe when user has NOT manually stopped sharing.
        val app = application as PinpointApplication
        serviceScope.launch {
            val session = app.preferences.guestSessionFlow.first()
            if (session.isSharingEnabled && session.activeCrowdId != null && hasLocationPermission()) {
                val restartIntent = Intent(applicationContext, LocationRestartReceiver::class.java)
                val pending = PendingIntent.getBroadcast(
                    applicationContext,
                    202,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarm.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1500L,
                    pending
                )
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isTrackingStarted = false
        try {
            fusedClient.removeLocationUpdates(fusedCallback)
            locationManager?.removeUpdates(fallbackListener)
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        const val ACTION_START_SHARING = "com.example.pinpoint.ACTION_START_SHARING"
        const val ACTION_STOP_SHARING = "com.example.pinpoint.ACTION_STOP_SHARING"

        fun start(context: Context) {
            val intent = Intent(context, PinpointLocationService::class.java).apply {
                action = ACTION_START_SHARING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PinpointLocationService::class.java).apply {
                action = ACTION_STOP_SHARING
            }
            context.startService(intent)
        }
    }
}
