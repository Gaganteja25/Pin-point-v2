package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.NearbyCategory
import com.example.domain.GeofenceEngine
import com.example.domain.PlaceListing
import com.example.ui.components.openOsmDirectionsIntent
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberStale
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.ElectricBlue

@Composable
fun NearbyPlacesScreen(
    referenceLat: Double,
    referenceLng: Double,
    selectedCategory: NearbyCategory,
    places: List<PlaceListing>,
    isLoading: Boolean,
    errorMessage: String?,
    dailyAdPriceInr: Int = 3,
    enableBusinessAds: Boolean = true,
    onSelectCategory: (NearbyCategory) -> Unit,
    onRefresh: () -> Unit,
    onOpenBusinessPortal: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(referenceLat, referenceLng) {
        if (places.isEmpty() && !isLoading) {
            onRefresh()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nearby Places", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Powered by OpenStreetMap Overpass • Paid listings labeled SPONSORED",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("nearby_refresh_button")) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh nearby places", tint = ElectricBlue)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NearbyCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { onSelectCategory(cat) },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (enableBusinessAds) {
                item {
                    Card(
                        onClick = onOpenBusinessPortal,
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Storefront, contentDescription = null, tint = ElectricBlue)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Promote Your Local Business", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "₹$dailyAdPriceInr/day • Min ₹${dailyAdPriceInr * 7} (7 days) • No private member GPS shared",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text("Open", color = ElectricBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Querying OpenStreetMap nearby places...")
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("OpenStreetMap Notice", style = MaterialTheme.typography.titleSmall)
                            Text(errorMessage, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (!isLoading && places.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No nearby places found yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Start sharing live location or set your crowd's geofence centre to search OpenStreetMap places around you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = onRefresh) {
                                Text("Search OpenStreetMap Again")
                            }
                        }
                    }
                }
            }

            items(places, key = { it.id }) { place ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (place.isSponsored) AmberSoft.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (place.isSponsored) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AmberStale
                                ) {
                                    Text(
                                        text = "SPONSORED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkNavy,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${place.category} • ${GeofenceEngine.formatDistance(place.distanceMeters)} away",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricBlue
                        )
                        Text(
                            text = place.addressOrDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    openOsmDirectionsIntent(context, place.latitude, place.longitude, place.name)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Map")
                            }
                            if (!place.website.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val uri = Uri.parse(
                                            if (place.website.startsWith("http")) place.website
                                            else "https://${place.website}"
                                        )
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Website")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
