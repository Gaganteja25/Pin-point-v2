package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.remote.BusinessApprovalStatus
import com.example.data.remote.FirestoreBusinessAccount
import com.example.data.remote.FirestorePaymentRecord
import com.example.data.remote.FirestorePromotionCampaign
import com.example.data.remote.GatewayOrderResult
import com.example.data.remote.NearbyCategory
import com.example.data.remote.RuntimeAppConfig
import com.example.domain.AdPricingEngine
import com.example.domain.CampaignStatus
import com.example.ui.BusinessUiState
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberStale
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive
import com.google.firebase.auth.FirebaseUser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessAuthSignInScreen(
    onBack: () -> Unit,
    onAttemptAutoSignIn: () -> Unit,
    onGoogleSignIn: (onDone: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onAttemptAutoSignIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Account") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AddBusiness, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(32.dp))
                    Text("Persistent Business Accounts", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Businesses sign in with a Google account so your business profile, owner approval status, ad campaigns, and payment receipts stay saved permanently — even after logging out.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How Owner Approval Works", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "1. Sign in with your business Google account.\n" +
                            "2. Submit your shop/service profile (starts as PENDING).\n" +
                            "3. Once approved by the PINPOINT owner, create and pay for local Nearby promotions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (authError != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = authError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonAlert,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    authError = null
                    isSigningIn = true
                    onGoogleSignIn(
                        { isSigningIn = false },
                        { msg ->
                            isSigningIn = false
                            authError = msg
                        }
                    )
                },
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("biz_google_signin_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDashboardScreen(
    authUser: FirebaseUser,
    appConfig: RuntimeAppConfig,
    businessState: BusinessUiState<FirestoreBusinessAccount?>,
    campaigns: List<FirestorePromotionCampaign>,
    payments: List<FirestorePaymentRecord>,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onOpenRegistration: () -> Unit,
    onOpenCreatePromotion: () -> Unit,
    onOpenCheckout: (String) -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Advertising") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("biz_signout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out of business account")
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
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = ElectricBlue)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Signed in: ${authUser.email ?: authUser.uid}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                "Privacy Guarantee: Businesses cannot view crowd GPS locations, chats, or member lists.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (!appConfig.enableBusinessAds) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AmberSoft)
                    ) {
                        Text(
                            "Local business promotions are currently paused by the administrator.",
                            color = DarkNavy,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            when (businessState) {
                is BusinessUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is BusinessUiState.Error -> {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = businessState.message,
                                color = CrimsonAlert,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                is BusinessUiState.Success -> {
                    val business = businessState.data
                    item {
                        Card(shape = RoundedCornerShape(18.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (business == null) {
                                    Text("No Business Profile Registered Yet", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Register your local shop, restaurant, hotel, or service for ₹${appConfig.dailyAdPriceInr}/day local promotions. All new registrations start PENDING until owner approval.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = onOpenRegistration,
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("biz_register_button")
                                    ) {
                                        Icon(Icons.Filled.AddBusiness, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Register Business")
                                    }
                                } else {
                                    Text(business.name, style = MaterialTheme.typography.titleLarge)
                                    Text("${business.category} • ${business.addressLabel}", style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                                    Text(business.description, style = MaterialTheme.typography.bodySmall)

                                    ApprovalStatusBanner(business)

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(onClick = onOpenRegistration, modifier = Modifier.weight(1f)) {
                                            Text("Edit Profile")
                                        }
                                        Button(
                                            onClick = onOpenCreatePromotion,
                                            enabled = business.approvalEnum == BusinessApprovalStatus.APPROVED && appConfig.enableBusinessAds,
                                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("biz_create_promo_button")
                                        ) {
                                            Icon(Icons.Filled.Campaign, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("New Ad")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (campaigns.isNotEmpty()) {
                item {
                    Text("Your Campaigns (${campaigns.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(campaigns, key = { it.id.ifBlank { it.businessName + it.durationDays } }) { cmp ->
                    val statusEnum = enumValues<CampaignStatus>()
                        .firstOrNull { it.name == cmp.status } ?: CampaignStatus.DRAFT
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cmp.businessName, style = MaterialTheme.typography.titleSmall)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (statusEnum) {
                                        CampaignStatus.ACTIVE -> EmeraldLive.copy(alpha = 0.16f)
                                        CampaignStatus.PENDING_PAYMENT -> AmberSoft
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = statusEnum.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkNavy,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                "Radius: ${cmp.radiusKm} km • Duration: ${cmp.durationDays} days • Total: ₹${cmp.priceInr} (₹${cmp.dailyRateInr}/day)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (statusEnum == CampaignStatus.ACTIVE && cmp.expiresAtMs > 0L) {
                                Text(
                                    "Remaining: ${cmp.remainingDays()} day(s) • Expires ${dateFormatter.format(Date(cmp.expiresAtMs))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldLive,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if ((statusEnum == CampaignStatus.PENDING_PAYMENT || statusEnum == CampaignStatus.DRAFT) &&
                                businessState is BusinessUiState.Success &&
                                businessState.data?.approvalEnum == BusinessApprovalStatus.APPROVED
                            ) {
                                Button(
                                    onClick = { onOpenCheckout(cmp.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Icon(Icons.Filled.Payment, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pay ₹${cmp.priceInr}")
                                }
                            }
                        }
                    }
                }
            }

            if (payments.isNotEmpty()) {
                item {
                    Text("Payment History (${payments.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(payments, key = { it.id.ifBlank { it.campaignId + it.status } }) { tx ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("₹${tx.amountInr} • ${tx.durationDays} days • ${tx.status}", style = MaterialTheme.typography.titleSmall)
                            Text("Provider: ${tx.provider.uppercase()} • Ref: ${tx.id}", style = MaterialTheme.typography.labelSmall)
                            if (tx.expiryDateMs > 0L) {
                                Text(
                                    "Valid until: ${dateFormatter.format(Date(tx.expiryDateMs))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlue
                                )
                            }
                            Text(tx.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalStatusBanner(business: FirestoreBusinessAccount) {
    when (business.approvalEnum) {
        BusinessApprovalStatus.APPROVED -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldLive.copy(alpha = 0.14f))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldLive)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Owner Approved — You can create and publish local ads anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        BusinessApprovalStatus.PENDING -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AmberSoft)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.HourglassTop, contentDescription = null, tint = DarkNavy)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Pending Owner Approval — Your business profile is saved permanently. Publishing ads is locked until approved by the PINPOINT owner.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkNavy
                    )
                }
            }
        }
        BusinessApprovalStatus.SUSPENDED, BusinessApprovalStatus.REJECTED -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = CrimsonAlert)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Account ${business.approvalStatus}: ${business.rejectionReason.ifBlank { "Contact PINPOINT support for review." }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonAlert
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessRegistrationScreen(
    existing: FirestoreBusinessAccount?,
    defaultEmail: String,
    defaultLat: Double,
    defaultLng: Double,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String, String, String, Double, Double) -> Unit
) {
    val categories = remember { NearbyCategory.entries.filter { it != NearbyCategory.ALL }.map { it.label } }
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember(existing) { mutableStateOf(existing?.category ?: categories.first()) }
    var description by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }
    var website by remember(existing) { mutableStateOf(existing?.website.orEmpty()) }
    var phone by remember(existing) { mutableStateOf(existing?.contactPhone.orEmpty()) }
    var email by remember(existing, defaultEmail) { mutableStateOf(existing?.contactEmail?.ifBlank { defaultEmail } ?: defaultEmail) }
    var address by remember(existing) { mutableStateOf(existing?.addressLabel.orEmpty()) }
    var latText by remember(existing) { mutableStateOf((existing?.latitude?.takeIf { it != 0.0 } ?: defaultLat).toString()) }
    var lngText by remember(existing) { mutableStateOf((existing?.longitude?.takeIf { it != 0.0 } ?: defaultLng).toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Register Business" else "Edit Business Profile") },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Business Name *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biz_name_input")
            )
            Text("Category", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                categories.chunked(3).forEach { rowCats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowCats.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Business Description (min 5 chars) *") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Website (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Contact Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Contact Email *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address / City Label") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it },
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull() ?: defaultLat
                    val lng = lngText.toDoubleOrNull() ?: defaultLng
                    onSave(name, category, description, website, phone, email, address, lat, lng)
                },
                enabled = name.trim().length >= 2 && description.trim().length >= 5 && email.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("biz_save_profile_button")
            ) {
                Text("Save Business Profile", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePromotionScreen(
    business: FirestoreBusinessAccount,
    dailyRateInr: Int,
    onBack: () -> Unit,
    onProceedToCheckout: (radiusKm: Int, durationDays: Int) -> Unit
) {
    var radiusKm by remember { mutableIntStateOf(5) }
    var durationDays by remember { mutableIntStateOf(30) }
    val safeRate = dailyRateInr.coerceAtLeast(1)
    val priceInr = AdPricingEngine.calculatePriceInr(durationDays, safeRate)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Promotion") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Promoting: ${business.name}", style = MaterialTheme.typography.titleMedium)
            Text("Advertising Radius", style = MaterialTheme.typography.titleMedium)
            Text(
                "Eligible for any crowd that enters this radius during your active campaign window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdPricingEngine.ALLOWED_RADII_KM.forEach { km ->
                    FilterChip(
                        selected = radiusKm == km,
                        onClick = { radiusKm = km },
                        label = { Text("$km km") }
                    )
                }
            }

            Text("Campaign Duration: $durationDays days", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = durationDays.toFloat(),
                onValueChange = { durationDays = it.toInt().coerceIn(7, 100) },
                valueRange = 7f..100f,
                modifier = Modifier.testTag("promo_duration_slider")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 15, 30, 50, 75, 100).forEach { preset ->
                    FilterChip(
                        selected = durationDays == preset,
                        onClick = { durationDays = preset },
                        label = { Text("${preset}d") }
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Automatic Price Calculator", style = MaterialTheme.typography.labelLarge, color = ElectricBlue)
                    Text("₹$priceInr INR", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$durationDays days × ₹$safeRate/day • Min 7 days (₹${safeRate * 7}), Max 100 days (₹${safeRate * 100}).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = { onProceedToCheckout(radiusKm, durationDays) },
                enabled = business.approvalEnum == BusinessApprovalStatus.APPROVED,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("promo_checkout_button")
            ) {
                Text("Proceed to Payment (₹$priceInr)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvertisingCheckoutScreen(
    campaign: FirestorePromotionCampaign,
    appConfig: RuntimeAppConfig,
    latestResult: GatewayOrderResult?,
    onBack: () -> Unit,
    onInitiatePayment: () -> Unit
) {
    val providerLabel = when (appConfig.paymentProvider) {
        "razorpay" -> "Razorpay"
        "custom" -> "Configured Payment Gateway"
        else -> "Payment Gateway"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$providerLabel Checkout") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(campaign.businessName, style = MaterialTheme.typography.titleLarge)
                    Text("Promotion Radius: ${campaign.radiusKm} km • Duration: ${campaign.durationDays} days")
                    Text("Amount Due: ₹${campaign.priceInr} INR", style = MaterialTheme.typography.titleMedium, color = ElectricBlue)
                    Text("Supported methods (when gateway active): UPI, Cards, Net Banking.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AmberSoft)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Server-Side Verification Security", style = MaterialTheme.typography.titleSmall, color = DarkNavy)
                    Text(
                        "PINPOINT verifies all payments server-side via Firebase Cloud Functions & payment webhooks. Secret API keys never reside inside the mobile app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkNavy
                    )
                }
            }

            Button(
                onClick = onInitiatePayment,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("razorpay_pay_button")
            ) {
                Text("Pay ₹${campaign.priceInr} via $providerLabel", fontWeight = FontWeight.Bold)
            }

            when (latestResult) {
                is GatewayOrderResult.AwaitingConfiguration -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Gateway Pending Owner Setup", style = MaterialTheme.typography.titleSmall, color = AmberStale)
                            Text(latestResult.reason, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "This checkout is recorded safely in your account. Once the PINPOINT owner enables live payment keys on the admin panel, your campaign can complete activation.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is GatewayOrderResult.Failed -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Payment Order Failed", style = MaterialTheme.typography.titleSmall, color = CrimsonAlert)
                            Text(latestResult.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is GatewayOrderResult.Created -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Payment Order Created", style = MaterialTheme.typography.titleSmall, color = EmeraldLive)
                            Text("Order ID: ${latestResult.order.orderId} • Amount: ₹${latestResult.order.amountPaise / 100}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                null -> Unit
            }
        }
    }
}
