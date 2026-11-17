package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CrowdEntity
import com.example.data.local.CrowdMemberEntity
import com.example.domain.GeofenceEngine
import com.example.ui.components.InviteShareDialog
import com.example.ui.components.StatusPill
import com.example.ui.components.parseColorSafe
import com.example.ui.components.statusPillColorsFor
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricBlue

@Composable
fun CrowdMembersScreen(
    crowd: CrowdEntity,
    selfSessionId: String,
    members: List<CrowdMemberEntity>,
    onRegenerateInvite: () -> Unit,
    onRevokeInvite: () -> Unit,
    onLeaveCrowd: () -> Unit,
    onDeleteCrowd: () -> Unit,
    onMentionInChat: (String) -> Unit,
    onCopiedInviteFeedback: () -> Unit
) {
    val isAdmin = crowd.adminSessionId == selfSessionId
    var showInviteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("${crowd.name} • Members", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${members.size} active member(s) • Shared map visibility is equal for all members",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Invitation Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Crowd Invitation", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (crowd.inviteRevoked) "Invitation link is currently REVOKED."
                            else crowd.inviteUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (crowd.inviteRevoked) CrimsonAlert else ElectricBlue
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showInviteDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("members_share_invite_button")
                            ) {
                                Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("QR & Share")
                            }
                            if (isAdmin) {
                                OutlinedButton(
                                    onClick = onRegenerateInvite,
                                    modifier = Modifier.testTag("members_regenerate_invite_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Regenerate invite", modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("New Link")
                                }
                            }
                        }
                        if (isAdmin && !crowd.inviteRevoked) {
                            TextButton(
                                onClick = onRevokeInvite,
                                modifier = Modifier.testTag("members_revoke_invite_button")
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Revoke Invitation Link", color = CrimsonAlert)
                            }
                        }
                    }
                }
            }

            // Member Cards
            items(members, key = { it.id }) { member ->
                val isSelf = member.sessionId == selfSessionId
                val stale = GeofenceEngine.isStaleLocation(member.lastUpdatedAt)
                val (statusLabel, dotColor, bgColor) = statusPillColorsFor(member.locationStatus, stale)

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(parseColorSafe(member.avatarColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(member.avatarEmoji, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = member.displayName + if (isSelf) " (You)" else "",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (member.isAdmin) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "ADMIN",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricBlue,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            StatusPill(label = statusLabel, dotColor = dotColor, containerColor = bgColor)
                            val metaText = buildString {
                                append(
                                    if (member.lastUpdatedAt > 0) "Updated ${GeofenceEngine.formatRelativeFreshness(member.lastUpdatedAt)}"
                                    else "No GPS fix published yet"
                                )
                                member.distanceFromCenterMeters?.let { dist ->
                                    append(" • ${GeofenceEngine.formatDistance(dist)} (${member.boundaryStatus})")
                                }
                            }
                            Text(
                                text = metaText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isSelf) {
                            TextButton(onClick = { onMentionInChat(member.displayName) }) {
                                Text("@Mention")
                            }
                        }
                    }
                }
            }

            // Leave / Delete Crowd Actions
            item {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showLeaveConfirm = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("leave_crowd_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Leave Crowd")
                    }

                    if (isAdmin) {
                        Button(
                            onClick = { showDeleteConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("delete_crowd_button")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("DELETE CROWD PERMANENTLY", fontWeight = FontWeight.Bold)
                        }
                    }
                }
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

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Crowd?") },
            text = {
                Text("Location sharing will stop immediately and you will leave \"${crowd.name}\".")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        onLeaveCrowd()
                    }
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Crowd Permanently?") },
            text = {
                Text(
                    "All members will immediately lose access. Active background location sharing, invitation links, crowd chat messages, memberships, geofence settings, and boundary logs will be permanently deleted. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCrowd()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    modifier = Modifier.testTag("confirm_delete_crowd_button")
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
