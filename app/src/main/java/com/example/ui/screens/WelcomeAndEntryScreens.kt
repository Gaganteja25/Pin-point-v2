package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CrowdEntity
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricBlueBright
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NavySurface
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    hasActiveCrowd: Boolean,
    isLoaded: Boolean,
    onContinueToWelcome: () -> Unit,
    onContinueToActiveCrowd: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    LaunchedEffect(isLoaded, hasActiveCrowd) {
        if (isLoaded) {
            delay(850)
            if (hasActiveCrowd) onContinueToActiveCrowd() else onContinueToWelcome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DarkNavy, NavySurface, Color(0xFF102A6B))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(ElectricBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "PINPOINT emblem",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "PINPOINT",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Everyone Together. Every Location. Real Time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                CircularProgressIndicator(
                    color = ElectricBlueBright,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    initialDisplayName: String,
    onCreateCrowdClick: () -> Unit,
    onJoinCrowdClick: () -> Unit,
    onOpenBusinessPortal: () -> Unit,
    onOpenPrivacyAndHelp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Banner Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_welcome_hero),
                    contentDescription = "PINPOINT live crowd map illustration",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x550B132B), Color(0xDD0B132B))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Surface(
                            color = ElectricBlue,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "NO SIGN-IN REQUIRED",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "PINPOINT",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Everyone Together. Every Location. Real Time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            if (initialDisplayName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = ElectricBlue
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Guest session active as $initialDisplayName. Ready to create or join a crowd.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Two Primary Actions
            Button(
                onClick = onCreateCrowdClick,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("welcome_create_crowd_button")
            ) {
                Icon(Icons.Filled.AddLocationAlt, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        "CREATE A CROWD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Start a live group & share invite link",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFDBEAFE)
                    )
                }
            }

            OutlinedButton(
                onClick = onJoinCrowdClick,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("welcome_join_crowd_button")
            ) {
                Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = ElectricBlue)
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        "JOIN A CROWD",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Paste an invitation link or code",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Transparency Note about Guest Session
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Instant Guest Privacy",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "No Google account, email, or phone number required. Your random guest session is stored securely on this device. Note: Clearing app data or uninstalling removes your guest session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Business Portal & Privacy Shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    onClick = onOpenBusinessPortal,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_business_button")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = ElectricBlue)
                        Spacer(Modifier.height(6.dp))
                        Text("Business Ads", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "From ₹3/day • ₹21 min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    onClick = onOpenPrivacyAndHelp,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("welcome_privacy_button")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = EmeraldLive)
                        Spacer(Modifier.height(6.dp))
                        Text("Privacy & Help", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "DPDP 2023 & support",
                            style = MaterialTheme.typography.bodySmall,
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
fun CreateCrowdScreen(
    initialDisplayName: String,
    onBack: () -> Unit,
    onCreateCrowd: (displayName: String, crowdName: String) -> Unit
) {
    var displayName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var crowdName by remember { mutableStateOf("") }
    var consentChecked by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create a Crowd") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_back_button")) {
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
                Text(
                    "Set up your crowd in seconds. You become Crowd Admin automatically with full control over invitations, geofence boundaries, and crowd deletion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your Display Name *") },
                    placeholder = { Text("e.g., Gagan, Team Leader, Traveller 01") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_display_name_input")
                )

                OutlinedTextField(
                    value = crowdName,
                    onValueChange = { crowdName = it },
                    label = { Text("Crowd Name (Optional)") },
                    placeholder = { Text("e.g., Weekend Trip, College Fest") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_crowd_name_input")
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it },
                            modifier = Modifier.testTag("create_consent_checkbox")
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "I understand that when I turn on location sharing, my live GPS position will be shared with members of this crowd until I press STOP SHARING.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(
                    onClick = { onCreateCrowd(displayName, crowdName) },
                    enabled = displayName.trim().isNotEmpty() && consentChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("confirm_create_crowd_button")
                ) {
                    Text("Create Crowd & Open Live Map", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinCrowdScreen(
    initialToken: String?,
    initialDisplayName: String,
    onBack: () -> Unit,
    onLookupCrowd: (String, (CrowdEntity) -> Unit) -> Unit,
    onJoinCrowd: (tokenInput: String, displayName: String, consentAccepted: Boolean) -> Unit
) {
    var inviteInput by remember(initialToken) { mutableStateOf(initialToken.orEmpty()) }
    var displayName by remember(initialDisplayName) { mutableStateOf(initialDisplayName) }
    var previewCrowd by remember { mutableStateOf<CrowdEntity?>(null) }
    var consentChecked by remember { mutableStateOf(false) }

    LaunchedEffect(initialToken) {
        if (!initialToken.isNullOrBlank()) {
            onLookupCrowd(initialToken) { found -> previewCrowd = found }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join a Crowd") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("join_back_button")) {
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = inviteInput,
                    onValueChange = {
                        inviteInput = it
                        previewCrowd = null
                    },
                    label = { Text("Invitation Link or Code *") },
                    placeholder = { Text("https://pinpoint.app/join/PIN-... or code") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_invite_input")
                )

                TextButton(
                    onClick = { onLookupCrowd(inviteInput) { previewCrowd = it } },
                    enabled = inviteInput.isNotBlank(),
                    modifier = Modifier.testTag("verify_invite_button")
                ) {
                    Text("Verify Invitation Details")
                }

                previewCrowd?.let { crowd ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Invitation Verified", style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                            Text(crowd.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Members cannot see your location until you join and turn on sharing.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your Display Name *") },
                    placeholder = { Text("e.g., Rahul, Best Friend") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_display_name_input")
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it },
                            modifier = Modifier.testTag("join_consent_checkbox")
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "I consent to join this crowd. When I start sharing, my live GPS location will be visible to all crowd members until I press STOP SHARING or leave.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Button(
                    onClick = { onJoinCrowd(inviteInput, displayName, consentChecked) },
                    enabled = inviteInput.isNotBlank() && displayName.trim().isNotEmpty() && consentChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("confirm_join_crowd_button")
                ) {
                    Text("Join Crowd & Open Map", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
