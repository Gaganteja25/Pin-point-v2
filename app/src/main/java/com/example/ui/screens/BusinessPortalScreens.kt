package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.BusinessProfileEntity
import com.example.data.local.PaymentTransactionEntity
import com.example.data.local.PromotionCampaignEntity
import com.example.data.local.RefundRequestEntity
import com.example.data.remote.GatewayOrderResult
import com.example.data.remote.NearbyCategory
import com.example.domain.AdPricingEngine
import com.example.domain.CampaignStatus
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberStale
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessDashboardScreen(
    business: BusinessProfileEntity?,
    campaigns: List<PromotionCampaignEntity>,
    transactions: List<PaymentTransactionEntity>,
    refunds: List<RefundRequestEntity>,
    onBack: () -> Unit,
    onOpenRegistration: () -> Unit,
    onOpenCreatePromotion: () -> Unit,
    onOpenCheckout: (String) -> Unit,
    onSubmitRefund: (campaignId: String, transactionId: String, reason: String) -> Unit
) {
    var refundDialogCampaign by remember { mutableStateOf<PromotionCampaignEntity?>(null) }
    var refundReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Advertising") },
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
                        Text(
                            "Privacy Guarantee: Business accounts cannot view private crowd member GPS coordinates, chat rooms, or member directories.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (business == null) {
                            Text("No Business Registered", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Register your local shop, restaurant, hotel, or service to create ₹3/day local promotions.",
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
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = onOpenRegistration, modifier = Modifier.weight(1f)) {
                                    Text("Edit Profile")
                                }
                                Button(
                                    onClick = onOpenCreatePromotion,
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

            if (campaigns.isNotEmpty()) {
                item {
                    Text("Campaigns (${campaigns.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(campaigns, key = { it.campaignId }) { cmp ->
                    val statusEnum = runCatching { CampaignStatus.valueOf(cmp.status) }.getOrDefault(CampaignStatus.DRAFT)
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
                                "Radius: ${cmp.radiusKm} km • Duration: ${cmp.durationDays} days • Total: ₹${cmp.priceInr}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (statusEnum == CampaignStatus.PENDING_PAYMENT || statusEnum == CampaignStatus.DRAFT) {
                                    Button(
                                        onClick = { onOpenCheckout(cmp.campaignId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                    ) {
                                        Icon(Icons.Filled.Payment, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Checkout ₹${cmp.priceInr}")
                                    }
                                }
                                if (statusEnum != CampaignStatus.REFUND_REQUESTED && statusEnum != CampaignStatus.REFUNDED) {
                                    OutlinedButton(onClick = { refundDialogCampaign = cmp }) {
                                        Text("Request Refund")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (transactions.isNotEmpty()) {
                item {
                    Text("Payment History (${transactions.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(transactions, key = { it.transactionId }) { tx ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("₹${tx.amountInr} • ${tx.status}", style = MaterialTheme.typography.titleSmall)
                            Text("TX ID: ${tx.transactionId}", style = MaterialTheme.typography.labelSmall)
                            Text(tx.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (refunds.isNotEmpty()) {
                item {
                    Text("Refund Requests (${refunds.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(refunds, key = { it.refundId }) { ref ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("₹${ref.amountInr} • ${ref.status}", style = MaterialTheme.typography.titleSmall)
                            Text(ref.reason, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    refundDialogCampaign?.let { cmp ->
        AlertDialog(
            onDismissRequest = { refundDialogCampaign = null },
            title = { Text("Request Refund") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Refunds apply to verified duplicate payments or failed campaign activation. Support: breakfreeentertainments0@gmail.com")
                    OutlinedTextField(
                        value = refundReason,
                        onValueChange = { refundReason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val txId = transactions.firstOrNull { it.campaignId == cmp.campaignId }?.transactionId ?: "none"
                        onSubmitRefund(cmp.campaignId, txId, refundReason)
                        refundReason = ""
                        refundDialogCampaign = null
                    },
                    enabled = refundReason.isNotBlank()
                ) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { refundDialogCampaign = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessRegistrationScreen(
    existing: BusinessProfileEntity?,
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
    var email by remember(existing) { mutableStateOf(existing?.contactEmail.orEmpty()) }
    var address by remember(existing) { mutableStateOf(existing?.addressLabel.orEmpty()) }
    var latText by remember(existing) { mutableStateOf((existing?.latitude ?: defaultLat).toString()) }
    var lngText by remember(existing) { mutableStateOf((existing?.longitude ?: defaultLng).toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Business") },
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
                label = { Text("Business Description *") },
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
                enabled = name.isNotBlank() && description.isNotBlank() && email.isNotBlank(),
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
    business: BusinessProfileEntity,
    onBack: () -> Unit,
    onProceedToCheckout: (radiusKm: Int, durationDays: Int) -> Unit
) {
    var radiusKm by remember { mutableIntStateOf(5) }
    var durationDays by remember { mutableIntStateOf(30) }
    val priceInr = AdPricingEngine.calculatePriceInr(durationDays)

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
                        "$durationDays days × ₹${AdPricingEngine.RATE_PER_DAY_INR}/day • Min 7 days (₹21), Max 100 days (₹300). Applicable gateway fees & taxes may apply at checkout.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = { onProceedToCheckout(radiusKm, durationDays) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("promo_checkout_button")
            ) {
                Text("Proceed to Razorpay Checkout (₹$priceInr)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvertisingCheckoutScreen(
    campaign: PromotionCampaignEntity,
    latestResult: GatewayOrderResult?,
    onBack: () -> Unit,
    onInitiatePayment: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Razorpay Checkout") },
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
                    Text("Supported methods (when merchant gateway enabled): UPI, Cards, Net Banking.", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AmberSoft)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Server-Side Verification Security", style = MaterialTheme.typography.titleSmall, color = DarkNavy)
                    Text(
                        "PINPOINT never trusts client-side payment success or stores raw card numbers. Campaigns activate only after verified Razorpay server order creation, webhook signature verification, and business info review.",
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
                Text("Pay ₹${campaign.priceInr} via Razorpay", fontWeight = FontWeight.Bold)
            }

            when (latestResult) {
                is GatewayOrderResult.AwaitingConfiguration -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Awaiting Gateway Configuration", style = MaterialTheme.typography.titleSmall, color = AmberStale)
                            Text(latestResult.reason, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Configure your HTTPS backend URL and Razorpay Key ID in Settings → Configurable Map & Server Settings.",
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
                            Text("Razorpay Order Created", style = MaterialTheme.typography.titleSmall, color = EmeraldLive)
                            Text("Order ID: ${latestResult.order.orderId} • Amount: ₹${latestResult.order.amountPaise / 100}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                null -> Unit
            }
        }
    }
}
