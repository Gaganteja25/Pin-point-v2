package com.example.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.PinpointApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PinpointBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        val app = context.applicationContext as? PinpointApplication ?: run {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = app.preferences.guestSessionFlow.first()
                if (session.isSharingEnabled && session.activeCrowdId != null) {
                    val hasFine = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasBg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true

                    if (hasFine && hasBg) {
                        try {
                            PinpointLocationService.start(context)
                        } catch (_: Exception) {
                            // Android 14+ restricts starting location FGS from background without foreground launch.
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class LocationRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as? PinpointApplication ?: run {
            pendingResult.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = app.preferences.guestSessionFlow.first()
                if (session.isSharingEnabled && session.activeCrowdId != null) {
                    try {
                        PinpointLocationService.start(context)
                    } catch (_: Exception) {
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
