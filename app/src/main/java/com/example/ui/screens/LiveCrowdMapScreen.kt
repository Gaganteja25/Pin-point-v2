package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CrowdEntity
import com.example.data.local.CrowdMemberEntity
import com.example.data.local.GuestSessionState
import com.example.domain.GeofenceEngine
import com.example.domain.PlaceListing
import com.example.ui.components.InviteShareDialog
import com.example.ui.components.LeafletMapView
import com.example.ui.components.MapRecenterTrigger
import com.example.ui.components.StatusPill
import com.example.ui.components.parseColorSafe
import com.example.ui.components.statusPillColorsFor
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldLive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCrowdMapScreen(
    crowd: CrowdEntity,
    session: GuestSessionState,
    members: List<CrowdMemberEntity>,
    nearbyPlaces: List<PlaceListing>,
    unreadNotifications: Int,
    isPickingGeofenceCenter: Boolean,
    onRequestStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenGeofenceSettings: () -> Unit,
    onCancelPickCenter: () -> Unit,
    onCenterPicked: (Double, Double) -> Unit,
    onMentionMemberInChat: (String) -> Unit,
    onCopiedInviteFeedback: () -> Unit
) {
    var recenterTrigger by remember { mutableStateOf<MapRecenterTrigger?>(null) }
    var selectedMember by remember { mutableStateOf<CrowdMemberEntity?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }

    val selfMember = remember(members, session.sessionId) {
        members.find { it.sessionId == session.sessionId }
    }
    val sharingCount = remember(members) {
        members.count { it.isSharingLocation && it.locationStatus != "STOPPED" }
    }
    val selfIsStale = selfMember?.let { GeofenceEngine.isStaleLocation(it.lastUpdatedAt) } ?: false
    val (statusLabel, dotColor, containerColor) = statusPillColorsFor(
        status = selfMember?.locationStatus ?: if (session.isSharingEnabled) "CONNECTING" else "STOPPED",
        isStale = selfIsStale
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LeafletMapView(
            crowd = crowd,
            members = members,
            places = nearbyPlaces,
            selfSessionId = session.sessionId,
            tileUrl = session.tileUrl,
            tileAttribution = session.tileAttribution,
            pickCenterMode = isPickingGeofenceCenter,
            recenterTrigger = recenterTrigger,
            onMemberTapped = { tappedSessionId ->
                selectedMember = members.find { it.sessionId == tappedSessionId }
            },
            onPlaceTapped = { _, _ -> },
            onMapTappedForCenter = { lat, lng ->
                onCenterPicked(lat, lng)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Floating Crowd HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkNavy.copy(alpha = 0.90f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = crowd.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$sharingCount/${members.size} sharing live • Geofence ${GeofenceEngine.formatRadius(crowd.geofenceRadiusMeters)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                    IconButton(
                        onClick = { showInviteDialog = true },
                        modifier = Modifier.testTag("map_invite_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Invite", tint = Color.White)
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("map_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge { Text(unreadNotifications.coerceAtMost(99).toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Color.White)
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    label = "$statusLabel • ${
                        if (selfMember != null && selfMember.lastUpdatedAt > 0)
                            GeofenceEngine.formatRelativeFreshness(selfMember.lastUpdatedAt)
                        else "Waiting"
                    }",
                    dotColor = dotColor,
                    containerColor = containerColor
                )

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    modifier = Modifier
                        .clickable { onOpenGeofenceSettings() }
                        .testTag("map_geofence_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Radar,
                            contentDescription = "Geofence settings",
                            tint = ElectricBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            GeofenceEngine.formatRadius(crowd.geofenceRadiusMeters),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (isPickingGeofenceCenter) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ElectricBlue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Tap anywhere on the map to set the geofence centre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onCancelPickCenter,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }

        // Bottom Map Controls: Prominent START/STOP SHARING & Recenter FAB
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (session.isSharingEnabled) {
                Button(
                    onClick = onStopSharing,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("stop_sharing_button")
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("STOP SHARING LOCATION", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onRequestStartSharing,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("start_sharing_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("START SHARING LOCATION", fontWeight = FontWeight.Bold)
                }
            }

            FloatingActionButton(
                onClick = {
                    val targetLat = selfMember?.latitude ?: crowd.geofenceLat
                    val targetLng = selfMember?.longitude ?: crowd.geofenceLng
                    recenterTrigger = MapRecenterTrigger(targetLat, targetLng, 16)
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = ElectricBlue,
                modifier = Modifier.testTag("recenter_map_fab")
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Recenter map")
            }
        }
    }

    selectedMember?.let { member ->
        ModalBottomSheet(onDismissRequest = { selectedMember = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(parseColorSafe(member.avatarColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(member.avatarEmoji, fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = member.displayName + if (member.sessionId == session.sessionId) " (You)" else "",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (member.isAdmin) "Crowd Admin" else "Crowd Member",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricBlue
                        )
                    }
                }

                val stale = GeofenceEngine.isStaleLocation(member.lastUpdatedAt)
                val (label, dot, bg) = statusPillColorsFor(member.locationStatus, stale)
                StatusPill(label = label, dotColor = dot, containerColor = bg)

                Text(
                    text = if (member.lastUpdatedAt > 0L) {
                        "Last updated: ${GeofenceEngine.formatRelativeFreshness(member.lastUpdatedAt)}"
                    } else {
                        "Location not published yet"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                member.distanceFromCenterMeters?.let { dist ->
                    Text(
                        text = "Boundary status: ${member.boundaryStatus} • ${GeofenceEngine.formatDistance(dist)} from geofence centre",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (member.latitude != null && member.longitude != null) {
                        OutlinedButton(
                            onClick = {
                                recenterTrigger = MapRecenterTrigger(member.latitude, member.longitude, 17)
                                selectedMember = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Focus Pin")
                        }
                    }
                    Button(
                        onClick = {
                            val name = member.displayName
                            selectedMember = null
                            onMentionMemberInChat(name)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("@Mention")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showInviteDialog) {
        InviteShareDialog(
            crowdName = crowd.name,
            inviteUrl = crowd.inviteUrl,
            inviteToken = crowd.inviteToken,
            isRevoked = crowd.inviteRevoked,
            onDismiss = { showInviteDialog = false },
            onCopyFeedback = onCopiedInviteFeedback
        )
    }
}
