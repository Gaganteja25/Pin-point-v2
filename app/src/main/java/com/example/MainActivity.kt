package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.TokenAndQrUtils
import com.example.ui.BusinessUiState
import com.example.ui.PinpointViewModel
import com.example.ui.screens.AdvertisingCheckoutScreen
import com.example.ui.screens.BoundaryAlertsScreen
import com.example.ui.screens.BusinessAuthSignInScreen
import com.example.ui.screens.BusinessDashboardScreen
import com.example.ui.screens.BusinessRegistrationScreen
import com.example.ui.screens.CreateCrowdScreen
import com.example.ui.screens.CreatePromotionScreen
import com.example.ui.screens.CrowdChatScreen
import com.example.ui.screens.CrowdMembersScreen
import com.example.ui.screens.GeofenceSettingsScreen
import com.example.ui.screens.JoinCrowdScreen
import com.example.ui.screens.LiveCrowdMapScreen
import com.example.ui.screens.NearbyPlacesScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PrivacyAndHelpScreen
import com.example.ui.screens.SettingsHubScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.PinpointTheme

private object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val CREATE_CROWD = "create_crowd"
    const val JOIN_CROWD = "join_crowd"
    const val CROWD_SHELL = "crowd_shell"
    const val GEOFENCE_SETTINGS = "geofence_settings"
    const val BOUNDARY_ALERTS = "boundary_alerts"
    const val NOTIFICATIONS = "notifications"
    const val PRIVACY_HELP = "privacy_help"
    const val BUSINESS_DASHBOARD = "business_dashboard"
    const val BUSINESS_REGISTER = "business_register"
    const val BUSINESS_CREATE_PROMO = "business_create_promo"
    const val BUSINESS_CHECKOUT = "business_checkout/{campaignId}"
    fun businessCheckout(campaignId: String) = "business_checkout/$campaignId"
}

private enum class CrowdTab(val label: String, val icon: ImageVector, val tag: String) {
    MAP("Map", Icons.Filled.Map, "nav_tab_map"),
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat, "nav_tab_chat"),
    MEMBERS("Members", Icons.Filled.Groups, "nav_tab_members"),
    NEARBY("Nearby", Icons.Filled.Place, "nav_tab_nearby"),
    SETTINGS("Settings", Icons.Filled.Settings, "nav_tab_settings")
}

class MainActivity : ComponentActivity() {
    private val viewModel: PinpointViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractInviteToken(intent)

        setContent {
            PinpointTheme {
                PinpointAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractInviteToken(intent)
    }

    private fun extractInviteToken(intent: Intent?) {
        val dataString = intent?.dataString ?: return
        val token = TokenAndQrUtils.parseInviteTokenFromInput(dataString)
        if (!token.isNullOrBlank()) {
            viewModel.setPendingInviteToken(token)
        }
    }
}

@Composable
private fun PinpointAppRoot(viewModel: PinpointViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
    val session by viewModel.guestSession.collectAsStateWithLifecycle()
    val activeCrowd by viewModel.activeCrowd.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val boundaryEvents by viewModel.boundaryEvents.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val nearbyPlaces by viewModel.nearbyPlaces.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isLoadingNearby by viewModel.isLoadingNearby.collectAsStateWithLifecycle()
    val nearbyError by viewModel.nearbyError.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val pendingInviteToken by viewModel.pendingInviteToken.collectAsStateWithLifecycle()
    val isPickingCenter by viewModel.isPickingGeofenceCenter.collectAsStateWithLifecycle()

    val businessAuthUser by viewModel.businessAuthUser.collectAsStateWithLifecycle()
    val remoteBusinessState by viewModel.remoteBusinessState.collectAsStateWithLifecycle()
    val remotePromotions by viewModel.remotePromotions.collectAsStateWithLifecycle()
    val remotePayments by viewModel.remotePayments.collectAsStateWithLifecycle()
    val latestPaymentResult by viewModel.latestPaymentResult.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(CrowdTab.MAP) }
    var prefillMentionName by remember { mutableStateOf<String?>(null) }
    var showBackgroundRationale by remember { mutableStateOf(false) }

    val visibleTabs = remember(appConfig.enableChat, appConfig.enableNearbyPlaces) {
        CrowdTab.entries.filter { tab ->
            when (tab) {
                CrowdTab.CHAT -> appConfig.enableChat
                CrowdTab.NEARBY -> appConfig.enableNearbyPlaces
                else -> true
            }
        }
    }

    LaunchedEffect(visibleTabs) {
        if (currentTab !in visibleTabs) {
            currentTab = CrowdTab.MAP
        }
    }

    val bgPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startLocationSharing(context)
    }

    val fgPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                showBackgroundRationale = true
            } else {
                viewModel.startLocationSharing(context)
            }
        } else {
            viewModel.onPermissionDenied()
        }
    }

    fun requestStartSharingFlow() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                showBackgroundRationale = true
            } else {
                viewModel.startLocationSharing(context)
            }
        } else {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            fgPermissionsLauncher.launch(perms)
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUiMessage()
        }
    }

    LaunchedEffect(pendingInviteToken, session) {
        if (!pendingInviteToken.isNullOrBlank() && session != null) {
            navController.navigate(Routes.JOIN_CROWD) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH,
                modifier = Modifier.padding(bottom = 0.dp)
            ) {
                composable(Routes.SPLASH) {
                    val isLoaded = session != null
                    val hasCrowd = session?.activeCrowdId != null && activeCrowd != null
                    SplashScreen(
                        hasActiveCrowd = hasCrowd,
                        isLoaded = isLoaded,
                        onContinueToWelcome = {
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        },
                        onContinueToActiveCrowd = {
                            navController.navigate(Routes.CROWD_SHELL) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        initialDisplayName = session?.displayName.orEmpty(),
                        onCreateCrowdClick = { navController.navigate(Routes.CREATE_CROWD) },
                        onJoinCrowdClick = { navController.navigate(Routes.JOIN_CROWD) },
                        onOpenBusinessPortal = { navController.navigate(Routes.BUSINESS_DASHBOARD) },
                        onOpenPrivacyAndHelp = { navController.navigate(Routes.PRIVACY_HELP) }
                    )
                }

                composable(Routes.CREATE_CROWD) {
                    CreateCrowdScreen(
                        initialDisplayName = session?.displayName.orEmpty(),
                        onBack = { navController.popBackStack() },
                        onCreateCrowd = { displayName, crowdName ->
                            viewModel.createCrowd(displayName, crowdName) {
                                currentTab = CrowdTab.MAP
                                navController.navigate(Routes.CROWD_SHELL) {
                                    popUpTo(Routes.WELCOME) { inclusive = false }
                                }
                            }
                        }
                    )
                }

                composable(Routes.JOIN_CROWD) {
                    JoinCrowdScreen(
                        initialToken = pendingInviteToken,
                        initialDisplayName = session?.displayName.orEmpty(),
                        onBack = {
                            viewModel.setPendingInviteToken(null)
                            navController.popBackStack()
                        },
                        onLookupCrowd = { input, onFound ->
                            viewModel.lookupInvite(input, onFound)
                        },
                        onJoinCrowd = { tokenInput, displayName, consentAccepted ->
                            viewModel.joinCrowd(tokenInput, displayName, consentAccepted) {
                                currentTab = CrowdTab.MAP
                                navController.navigate(Routes.CROWD_SHELL) {
                                    popUpTo(Routes.WELCOME) { inclusive = false }
                                }
                            }
                        }
                    )
                }

                composable(Routes.CROWD_SHELL) {
                    val currentSession = session
                    val crowd = activeCrowd
                    if (currentSession == null || crowd == null) {
                        LaunchedEffect(Unit) {
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(Routes.CROWD_SHELL) { inclusive = true }
                            }
                        }
                    } else {
                        val selfMember = members.find { it.sessionId == currentSession.sessionId }
                        val refLat = selfMember?.latitude ?: crowd.geofenceLat
                        val refLng = selfMember?.longitude ?: crowd.geofenceLng

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val useRail = maxWidth >= 600.dp

                            Scaffold(
                                bottomBar = {
                                    if (!useRail) {
                                        NavigationBar {
                                            visibleTabs.forEach { tab ->
                                                NavigationBarItem(
                                                    selected = currentTab == tab,
                                                    onClick = { currentTab = tab },
                                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                                    label = { Text(tab.label) },
                                                    modifier = Modifier.testTag(tab.tag)
                                                )
                                            }
                                        }
                                    }
                                }
                            ) { innerPadding ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    if (useRail) {
                                        NavigationRail {
                                            visibleTabs.forEach { tab ->
                                                NavigationRailItem(
                                                    selected = currentTab == tab,
                                                    onClick = { currentTab = tab },
                                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                                    label = { Text(tab.label) },
                                                    modifier = Modifier.testTag(tab.tag)
                                                )
                                            }
                                        }
                                    }

                                    Box(modifier = Modifier.weight(1f)) {
                                        when (currentTab) {
                                            CrowdTab.MAP -> LiveCrowdMapScreen(
                                                crowd = crowd,
                                                session = currentSession,
                                                appConfig = appConfig,
                                                members = members,
                                                nearbyPlaces = nearbyPlaces,
                                                unreadNotifications = unreadNotifications,
                                                isPickingGeofenceCenter = isPickingCenter,
                                                onRequestStartSharing = { requestStartSharingFlow() },
                                                onStopSharing = { viewModel.stopLocationSharing(context) },
                                                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                                                onOpenGeofenceSettings = { navController.navigate(Routes.GEOFENCE_SETTINGS) },
                                                onCancelPickCenter = { viewModel.setPickingGeofenceCenter(false) },
                                                onCenterPicked = { lat, lng ->
                                                    viewModel.updateGeofence(crowd.crowdId, lat, lng, crowd.geofenceRadiusMeters)
                                                },
                                                onMentionMemberInChat = { name ->
                                                    prefillMentionName = name
                                                    currentTab = if (appConfig.enableChat) CrowdTab.CHAT else CrowdTab.MEMBERS
                                                },
                                                onCopiedInviteFeedback = {
                                                    viewModel.showMessage("Invitation link copied to clipboard.")
                                                }
                                            )

                                            CrowdTab.CHAT -> CrowdChatScreen(
                                                crowd = crowd,
                                                selfSessionId = currentSession.sessionId,
                                                members = members,
                                                messages = messages,
                                                prefillMentionName = prefillMentionName,
                                                onPrefillConsumed = { prefillMentionName = null },
                                                onSendMessage = { text -> viewModel.sendMessage(crowd.crowdId, text) },
                                                onReportMessage = { msgId, targetId, reason ->
                                                    viewModel.reportMessage(crowd.crowdId, msgId, targetId, reason)
                                                }
                                            )

                                            CrowdTab.MEMBERS -> CrowdMembersScreen(
                                                crowd = crowd,
                                                selfSessionId = currentSession.sessionId,
                                                members = members,
                                                onRegenerateInvite = { viewModel.regenerateInviteLink(crowd.crowdId) },
                                                onRevokeInvite = { viewModel.revokeInviteLink(crowd.crowdId) },
                                                onLeaveCrowd = {
                                                    viewModel.leaveCrowd(crowd.crowdId, context) {
                                                        navController.navigate(Routes.WELCOME) {
                                                            popUpTo(Routes.CROWD_SHELL) { inclusive = true }
                                                        }
                                                    }
                                                },
                                                onDeleteCrowd = {
                                                    viewModel.deleteCrowd(crowd.crowdId, context) {
                                                        navController.navigate(Routes.WELCOME) {
                                                            popUpTo(Routes.CROWD_SHELL) { inclusive = true }
                                                        }
                                                    }
                                                },
                                                onMentionInChat = { name ->
                                                    prefillMentionName = name
                                                    currentTab = if (appConfig.enableChat) CrowdTab.CHAT else CrowdTab.MEMBERS
                                                },
                                                onCopiedInviteFeedback = {
                                                    viewModel.showMessage("Invitation link copied to clipboard.")
                                                }
                                            )

                                            CrowdTab.NEARBY -> NearbyPlacesScreen(
                                                referenceLat = refLat,
                                                referenceLng = refLng,
                                                selectedCategory = selectedCategory,
                                                places = nearbyPlaces,
                                                isLoading = isLoadingNearby,
                                                errorMessage = nearbyError,
                                                dailyAdPriceInr = appConfig.dailyAdPriceInr,
                                                enableBusinessAds = appConfig.enableBusinessAds,
                                                onSelectCategory = { cat ->
                                                    viewModel.refreshNearbyPlaces(refLat, refLng, cat)
                                                },
                                                onRefresh = {
                                                    viewModel.refreshNearbyPlaces(refLat, refLng, selectedCategory)
                                                },
                                                onOpenBusinessPortal = { navController.navigate(Routes.BUSINESS_DASHBOARD) }
                                            )

                                            CrowdTab.SETTINGS -> SettingsHubScreen(
                                                session = currentSession,
                                                appConfig = appConfig,
                                                crowd = crowd,
                                                selfMember = selfMember,
                                                unreadNotifications = unreadNotifications,
                                                onUpdateProfile = { name, emoji, color ->
                                                    viewModel.updateProfile(name, emoji, color)
                                                },
                                                onOpenGeofence = { navController.navigate(Routes.GEOFENCE_SETTINGS) },
                                                onOpenBoundaryAlerts = { navController.navigate(Routes.BOUNDARY_ALERTS) },
                                                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                                                onOpenPrivacyAndHelp = { navController.navigate(Routes.PRIVACY_HELP) },
                                                onOpenBusinessPortal = { navController.navigate(Routes.BUSINESS_DASHBOARD) },
                                                onSubmitFeedback = { cat, msg, email ->
                                                    viewModel.submitUserFeedback(cat, msg, email)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                composable(Routes.GEOFENCE_SETTINGS) {
                    val crowd = activeCrowd
                    val currentSession = session
                    if (crowd != null && currentSession != null) {
                        GeofenceSettingsScreen(
                            crowd = crowd,
                            isAdmin = crowd.adminSessionId == currentSession.sessionId,
                            selfMember = members.find { it.sessionId == currentSession.sessionId },
                            onBack = { navController.popBackStack() },
                            onPickCenterOnMap = {
                                viewModel.setPickingGeofenceCenter(true)
                                currentTab = CrowdTab.MAP
                                navController.popBackStack()
                            },
                            onSaveGeofence = { lat, lng, radiusMeters ->
                                viewModel.updateGeofence(crowd.crowdId, lat, lng, radiusMeters)
                                navController.popBackStack()
                            }
                        )
                    }
                }

                composable(Routes.BOUNDARY_ALERTS) {
                    val crowd = activeCrowd
                    val currentSession = session
                    val selfMember = members.find { it.sessionId == currentSession?.sessionId }
                    BoundaryAlertsScreen(
                        events = boundaryEvents,
                        alertsOptIn = selfMember?.boundaryAlertsOptIn ?: true,
                        onToggleOptIn = { optIn ->
                            if (crowd != null) viewModel.setBoundaryAlertsOptIn(crowd.crowdId, optIn)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(
                        notifications = notifications,
                        onMarkAllRead = { viewModel.markNotificationsRead() },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.PRIVACY_HELP) {
                    PrivacyAndHelpScreen(
                        supportEmail = appConfig.supportEmail,
                        onSubmitFeedback = { cat, msg, email ->
                            viewModel.submitUserFeedback(cat, msg, email)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.BUSINESS_DASHBOARD) {
                    val signedInUser = businessAuthUser
                    if (signedInUser == null) {
                        BusinessAuthSignInScreen(
                            onBack = { navController.popBackStack() },
                            onAttemptAutoSignIn = { viewModel.attemptBusinessAutoSignIn(context) },
                            onGoogleSignIn = { onDone, onError ->
                                viewModel.onBusinessGoogleSignIn(context, onDone, onError)
                            }
                        )
                    } else {
                        key(signedInUser.uid) {
                            BusinessDashboardScreen(
                                authUser = signedInUser,
                                appConfig = appConfig,
                                businessState = remoteBusinessState,
                                campaigns = remotePromotions,
                                payments = remotePayments,
                                onBack = { navController.popBackStack() },
                                onSignOut = { viewModel.signOutBusiness(context) },
                                onOpenRegistration = { navController.navigate(Routes.BUSINESS_REGISTER) },
                                onOpenCreatePromotion = { navController.navigate(Routes.BUSINESS_CREATE_PROMO) },
                                onOpenCheckout = { campaignId ->
                                    navController.navigate(Routes.businessCheckout(campaignId))
                                }
                            )
                        }
                    }
                }

                composable(Routes.BUSINESS_REGISTER) {
                    val signedInUser = businessAuthUser
                    if (signedInUser == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val existingBiz = (remoteBusinessState as? BusinessUiState.Success)?.data
                        val selfMember = members.find { it.sessionId == session?.sessionId }
                        BusinessRegistrationScreen(
                            existing = existingBiz,
                            defaultEmail = signedInUser.email.orEmpty(),
                            defaultLat = selfMember?.latitude ?: activeCrowd?.geofenceLat ?: 17.3850,
                            defaultLng = selfMember?.longitude ?: activeCrowd?.geofenceLng ?: 78.4867,
                            onBack = { navController.popBackStack() },
                            onSave = { name, cat, desc, web, phone, email, addr, lat, lng ->
                                viewModel.saveRemoteBusinessProfile(name, cat, desc, web, phone, email, addr, lat, lng) {
                                    navController.popBackStack()
                                }
                            }
                        )
                    }
                }

                composable(Routes.BUSINESS_CREATE_PROMO) {
                    val biz = (remoteBusinessState as? BusinessUiState.Success)?.data
                    if (biz != null) {
                        CreatePromotionScreen(
                            business = biz,
                            dailyRateInr = appConfig.dailyAdPriceInr,
                            onBack = { navController.popBackStack() },
                            onProceedToCheckout = { radiusKm, durationDays ->
                                viewModel.createRemotePromotion(biz, radiusKm, durationDays) { cmp ->
                                    navController.navigate(Routes.businessCheckout(cmp.id)) {
                                        popUpTo(Routes.BUSINESS_CREATE_PROMO) { inclusive = true }
                                    }
                                }
                            }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Business profile required.", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                            }
                        }
                    }
                }

                composable(
                    route = Routes.BUSINESS_CHECKOUT,
                    arguments = listOf(navArgument("campaignId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val cid = backStackEntry.arguments?.getString("campaignId")
                    val cmp = remotePromotions.find { it.id == cid }
                    val biz = (remoteBusinessState as? BusinessUiState.Success)?.data
                    if (cmp != null && biz != null) {
                        AdvertisingCheckoutScreen(
                            campaign = cmp,
                            appConfig = appConfig,
                            latestResult = latestPaymentResult,
                            onBack = { navController.popBackStack() },
                            onInitiatePayment = {
                                viewModel.startRemoteCheckout(biz, cmp)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showBackgroundRationale) {
        AlertDialog(
            onDismissRequest = { showBackgroundRationale = false },
            title = { Text("Enable Background Location?") },
            text = {
                Text(
                    "To keep sharing your live location with your crowd when the screen is locked or when switching to WhatsApp/other apps, select 'Allow all the time' on the next Android prompt. You can stop sharing anytime from the notification or in-app button."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackgroundRationale = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            viewModel.startLocationSharing(context)
                        }
                    }
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBackgroundRationale = false
                        viewModel.startLocationSharing(context)
                    }
                ) { Text("Foreground Only") }
            }
        )
    }
}
