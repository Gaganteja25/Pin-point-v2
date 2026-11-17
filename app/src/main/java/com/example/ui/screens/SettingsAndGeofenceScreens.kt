package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppNotificationEntity
import com.example.data.local.BoundaryEventEntity
import com.example.data.local.CrowdEntity
import com.example.data.local.CrowdMemberEntity
import com.example.data.local.GuestSessionState
import com.example.data.remote.RuntimeAppConfig
import com.example.domain.GeofenceEngine
import com.example.domain.RadiusValidationResult
import com.example.ui.components.ProfileCustomizeDialog
import com.example.ui.components.parseColorSafe
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun SettingsHubScreen(
    session: GuestSessionState,
    appConfig: RuntimeAppConfig,
    crowd: CrowdEntity?,
    selfMember: CrowdMemberEntity?,
    unreadNotifications: Int,
    onUpdateProfile: (String, String, String) -> Unit,
    onOpenGeofence: () -> Unit,
    onOpenBoundaryAlerts: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenPrivacyAndHelp: () -> Unit,
    onOpenBusinessPortal: () -> Unit,
    onSubmitFeedback: (category: String, message: String, contactEmail: String) -> Unit
) {
    var showProfileDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings & Safety", style = MaterialTheme.typography.headlineMedium)

        // Guest Profile Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(parseColorSafe(session.avatarColorHex)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(session.avatarEmoji, fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.displayName.ifBlank { "Guest Traveller" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Session: ${session.sessionId.take(14)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showProfileDialog = true },
                    modifier = Modifier.testTag("settings_edit_profile_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit display name and avatar", tint = ElectricBlue)
                }
            }
        }

        if (crowd != null) {
            SettingsRowCard(
                icon = Icons.Filled.Radar,
                title = "Geofence Settings",
                subtitle = "Current radius: ${GeofenceEngine.formatRadius(crowd.geofenceRadiusMeters)} (10 m – 100 km)",
                onClick = onOpenGeofence,
                testTag = "settings_geofence_row"
            )
            SettingsRowCard(
                icon = Icons.Filled.Notifications,
                title = "Boundary Alerts & Log",
                subtitle = if (selfMember?.boundaryAlertsOptIn == true) "Alerts enabled for crowd crossings" else "Alerts muted",
                onClick = onOpenBoundaryAlerts,
                testTag = "settings_boundary_row"
            )
        }

        SettingsRowCard(
            icon = Icons.Filled.Notifications,
            title = "Notifications Inbox",
            subtitle = if (unreadNotifications > 0) "$unreadNotifications unread notification(s)" else "Mentions & boundary events",
            onClick = onOpenNotifications,
            testTag = "settings_notifications_row"
        )

        if (appConfig.enableBusinessAds) {
            SettingsRowCard(
                icon = Icons.Filled.Storefront,
                title = "Business Advertising Portal",
                subtitle = "₹${appConfig.dailyAdPriceInr}/day local promotions • Owner-approved accounts",
                onClick = onOpenBusinessPortal,
                testTag = "settings_business_row"
            )
        }

        SettingsRowCard(
            icon = Icons.Filled.Feedback,
            title = "Send Feedback or Report Issue",
            subtitle = "Share bugs, feature ideas, or safety feedback directly with the team",
            onClick = { showFeedbackDialog = true },
            testTag = "settings_feedback_row"
        )

        SettingsRowCard(
            icon = Icons.Filled.PrivacyTip,
            title = "Privacy, Battery & Support",
            subtitle = "Background GPS guide, DPDP Act 2023, child safety, email support",
            onClick = onOpenPrivacyAndHelp,
            testTag = "settings_privacy_row"
        )

        Spacer(Modifier.height(16.dp))
    }

    if (showProfileDialog) {
        ProfileCustomizeDialog(
            initialName = session.displayName,
            initialEmoji = session.avatarEmoji,
            initialColor = session.avatarColorHex,
            onDismiss = { showProfileDialog = false },
            onSave = { name, emoji, color ->
                onUpdateProfile(name, emoji, color)
                showProfileDialog = false
            }
        )
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { cat, msg, email ->
                onSubmitFeedback(cat, msg, email)
                showFeedbackDialog = false
            }
        )
    }
}

@Composable
private fun FeedbackDialog(
    onDismiss: () -> Unit,
    onSubmit: (category: String, message: String, contactEmail: String) -> Unit
) {
    val categories = listOf("GENERAL", "BUG", "FEATURE", "SAFETY")
    var category by remember { mutableStateOf("GENERAL") }
    var message by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Feedback") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Your feedback or report *") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    label = { Text("Reply email (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(category, message, contactEmail) },
                enabled = message.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SettingsRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ElectricBlue)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceSettingsScreen(
    crowd: CrowdEntity,
    isAdmin: Boolean,
    selfMember: CrowdMemberEntity?,
    onBack: () -> Unit,
    onPickCenterOnMap: () -> Unit,
    onSaveGeofence: (lat: Double, lng: Double, radiusMeters: Int) -> Unit
) {
    var radiusText by remember(crowd.geofenceRadiusMeters) { mutableStateOf(crowd.geofenceRadiusMeters.toString()) }
    // Logarithmic slider mapped 0f..1f -> 10m..100,000m
    var sliderPos by remember(crowd.geofenceRadiusMeters) {
        val ratio = (kotlin.math.log10(crowd.geofenceRadiusMeters.coerceIn(10, 100_000).toDouble()) - 1.0) / 4.0
        mutableFloatStateOf(ratio.toFloat().coerceIn(0f, 1f))
    }
    var centerLat by remember(crowd.geofenceLat) { mutableStateOf(crowd.geofenceLat) }
    var centerLng by remember(crowd.geofenceLng) { mutableStateOf(crowd.geofenceLng) }

    val validation = remember(radiusText) { GeofenceEngine.validateRadius(radiusText) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geofence Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Configured Boundary", style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                        Text(
                            text = GeofenceEngine.formatRadius(crowd.geofenceRadiusMeters),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Centre: ${String.format(Locale.US, "%.5f, %.5f", centerLat, centerLng)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (!isAdmin) {
                    Text(
                        "Only the Crowd Admin can edit geofence centre and radius settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = radiusText,
                        onValueChange = { input ->
                            radiusText = input
                            val v = GeofenceEngine.validateRadius(input)
                            if (v is RadiusValidationResult.Valid) {
                                val r = (kotlin.math.log10(v.radiusMeters.toDouble()) - 1.0) / 4.0
                                sliderPos = r.toFloat().coerceIn(0f, 1f)
                            }
                        },
                        label = { Text("Custom Radius in Metres (10 – 100,000)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = validation is RadiusValidationResult.Invalid,
                        supportingText = {
                            when (validation) {
                                is RadiusValidationResult.Valid ->
                                    Text("Formatted: ${GeofenceEngine.formatRadius(validation.radiusMeters)}")
                                is RadiusValidationResult.Invalid ->
                                    Text(validation.message, color = CrimsonAlert)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("geofence_radius_input")
                    )

                    Text("Adjust Radius (10 m to 100 km)", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = sliderPos,
                        onValueChange = { pos ->
                            sliderPos = pos
                            val meters = 10.0.pow(1.0 + pos * 4.0).roundToInt().coerceIn(10, 100_000)
                            radiusText = meters.toString()
                        },
                        modifier = Modifier.testTag("geofence_radius_slider")
                    )

                    Text("Geofence Centre", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                if (selfMember?.latitude != null && selfMember.longitude != null) {
                                    centerLat = selfMember.latitude
                                    centerLng = selfMember.longitude
                                }
                            },
                            enabled = selfMember?.latitude != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use My Location")
                        }
                        OutlinedButton(
                            onClick = onPickCenterOnMap,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pick on Map")
                        }
                    }

                    Button(
                        onClick = {
                            if (validation is RadiusValidationResult.Valid) {
                                onSaveGeofence(centerLat, centerLng, validation.radiusMeters)
                            }
                        },
                        enabled = validation is RadiusValidationResult.Valid,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_geofence_button")
                    ) {
                        Text("Save Geofence Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoundaryAlertsScreen(
    events: List<BoundaryEventEntity>,
    alertsOptIn: Boolean,
    onToggleOptIn: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boundary Alerts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Other Members' Boundary Alerts", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Admin and affected members are always notified. Toggle notifications for other members.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alertsOptIn,
                            onCheckedChange = onToggleOptIn
                        )
                    }
                }
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        "No boundary crossings recorded yet. GPS smoothing & hysteresis prevent false jitter alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            items(events, key = { it.eventId }) { ev ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${ev.memberName} • ${ev.eventType}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (ev.eventType == "EXITED") CrimsonAlert else EmeraldLive
                        )
                        Text(
                            text = "Distance: ${GeofenceEngine.formatDistance(ev.distanceMeters)} • Accuracy ±${ev.accuracyMeters.toInt()} m",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = timeFormatter.format(Date(ev.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notifications: List<AppNotificationEntity>,
    onMarkAllRead: () -> Unit,
    onBack: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    LaunchedEffect(Unit) { onMarkAllRead() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (notifications.isEmpty()) {
                item {
                    Text(
                        "No notifications yet. @Mentions and geofence boundary alerts appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(notifications, key = { it.id }) { item ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(item.body, style = MaterialTheme.typography.bodySmall)
                        Text(
                            formatter.format(Date(item.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAndHelpScreen(
    supportEmail: String = "breakfreeentertainments0@gmail.com",
    onSubmitFeedback: (category: String, message: String, contactEmail: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showFeedbackDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy, Battery & Help") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoSectionCard(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Background Location & Battery Optimization",
                body = "PINPOINT uses a native Android foreground location service with a persistent notification and STOP SHARING action. Swiping PINPOINT from recent apps will not intentionally stop tracking. However, force-stopping the app in Android Settings, revoking permissions, shutting down the device, or aggressive OEM battery savers (Xiaomi MIUI, Realme, Vivo, Oppo, Samsung) can pause background GPS. Set Battery Saver to 'Unrestricted' for best reliability."
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Android App & Battery Settings")
            }

            InfoSectionCard(
                icon = Icons.Filled.PrivacyTip,
                title = "India Privacy & DPDP Act, 2023 Disclosures",
                body = "• Explicit Consent: Location sharing starts only after your consent and stops immediately when you tap STOP SHARING.\n• No Secret Tracking: A visible system notification always shows while GPS sharing is active.\n• Business Isolation: Local business advertisers NEVER receive member GPS coordinates, chat messages, or member lists.\n• Crowd Deletion: When the Admin deletes a crowd, active sharing stops, invites are invalidated, and crowd-specific records are purged immediately from active storage (backup deletion cycles and legally required payment tax records are retained separately as required by law).\n• Children's Safeguard: Organisers/teachers must obtain verifiable parental/guardian consent before sharing any child's location, in line with the Digital Personal Data Protection Act, 2023 (subject to applicable rules and commencement provisions)."
            )

            InfoSectionCard(
                icon = Icons.Filled.Help,
                title = "Developer & Support",
                body = "Developer: Gurugubelli Gagan Teja (Individual Developer, India)\nSupport & Privacy Email: $supportEmail\nMap Data: OpenStreetMap contributors & Overpass API.\nPayments: Server-verified payment gateway configured by PINPOINT administrator."
            )

            OutlinedButton(
                onClick = { showFeedbackDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Feedback, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send In-App Feedback")
            }

            Button(
                onClick = {
                    val mail = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:$supportEmail")
                        putExtra(Intent.EXTRA_SUBJECT, "PINPOINT Support Request")
                    }
                    context.startActivity(Intent.createChooser(mail, "Email Support"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Email Support")
            }
        }
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onDismiss = { showFeedbackDialog = false },
            onSubmit = { cat, msg, email ->
                onSubmitFeedback(cat, msg, email)
                showFeedbackDialog = false
            }
        )
    }
}

@Composable
private fun InfoSectionCard(icon: ImageVector, title: String, body: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = ElectricBlue)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
