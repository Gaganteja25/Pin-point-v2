package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CrowdEntity
import com.example.data.local.CrowdMemberEntity
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrowdChatScreen(
    crowd: CrowdEntity,
    selfSessionId: String,
    members: List<CrowdMemberEntity>,
    messages: List<ChatMessageEntity>,
    prefillMentionName: String?,
    onPrefillConsumed: () -> Unit,
    onSendMessage: (String) -> Unit,
    onReportMessage: (messageId: String, targetSessionId: String, reason: String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var reportTarget by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var reportReason by remember { mutableStateOf("Spam or inappropriate message") }
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    LaunchedEffect(prefillMentionName) {
        if (!prefillMentionName.isNullOrBlank()) {
            draft = "@$prefillMentionName $draft"
            onPrefillConsumed()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Extract active @query at the end of draft for autocomplete
    val mentionQuery = remember(draft) {
        val lastAt = draft.lastIndexOf('@')
        if (lastAt >= 0) {
            val after = draft.substring(lastAt + 1)
            if (!after.contains('\n') && after.length <= 24) after else null
        } else null
    }
    val mentionSuggestions = remember(mentionQuery, members) {
        if (mentionQuery == null) emptyList()
        else members.filter {
            it.displayName.contains(mentionQuery, ignoreCase = true)
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
                Text(
                    text = "${crowd.name} • Crowd Chat",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Shared room for all ${members.size} members • Use @Name to mention",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.messageId }) { msg ->
                val isSystem = msg.senderSessionId == "system"
                val isSelf = msg.senderSessionId == selfSessionId
                if (isSystem) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelf) ElectricBlue else MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(0.84f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(msg.senderAvatar, fontSize = 15.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isSelf) "${msg.senderName} (You)" else msg.senderName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelf) Color.White else ElectricBlue
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = timeFormatter.format(Date(msg.createdAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelf) Color(0xFFDBEAFE) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!isSelf && !msg.isReported) {
                                            IconButton(
                                                onClick = { reportTarget = msg },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Flag,
                                                    contentDescription = "Report message",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                val annotated = remember(msg.text, msg.mentionedNamesCsv, isSelf) {
                                    buildHighlightedMentionText(
                                        text = msg.text,
                                        mentionedNames = msg.mentionedNamesCsv.split(",").filter { it.isNotBlank() },
                                        highlightColor = if (isSelf) Color(0xFFFDE68A) else ElectricBlue
                                    )
                                }
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelf) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (msg.isReported) {
                                    Text(
                                        text = "Reported for review",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CrimsonAlert
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // @Mention Autocomplete Strip
        if (mentionSuggestions.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mentionSuggestions.forEach { member ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ElectricBlue,
                            modifier = Modifier.clickable {
                                val lastAt = draft.lastIndexOf('@')
                                if (lastAt >= 0) {
                                    draft = draft.substring(0, lastAt) + "@${member.displayName} "
                                }
                            }
                        ) {
                            Text(
                                text = "@${member.displayName}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Emoji Bar + Composer
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("👍", "📍", "🚗", "⏳", "☕", "🆘", "🎉").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { draft += emoji }
                                .padding(4.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { draft += "@" },
                        modifier = Modifier.testTag("chat_mention_button")
                    ) {
                        Icon(Icons.Filled.AlternateEmail, contentDescription = "Insert mention", tint = ElectricBlue)
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Message everyone or @mention...") },
                        maxLines = 3,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    IconButton(
                        onClick = {
                            if (draft.isNotBlank()) {
                                onSendMessage(draft)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (draft.isNotBlank()) ElectricBlue else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    reportTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("Report Message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Report message from ${target.senderName}?")
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReportMessage(target.messageId, target.senderSessionId, reportReason)
                        reportTarget = null
                    }
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text("Cancel") }
            }
        )
    }
}

private fun buildHighlightedMentionText(
    text: String,
    mentionedNames: List<String>,
    highlightColor: Color
) = buildAnnotatedString {
    if (mentionedNames.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var cursor = 0
    val lower = text.lowercase()
    val tokens = mentionedNames.map { "@${it.lowercase()}" }
    while (cursor < text.length) {
        val nextMatch = tokens
            .mapNotNull { token ->
                val idx = lower.indexOf(token, cursor)
                if (idx >= 0) idx to token.length else null
            }
            .minByOrNull { it.first }

        if (nextMatch == null) {
            append(text.substring(cursor))
            break
        } else {
            val (idx, len) = nextMatch
            if (idx > cursor) append(text.substring(cursor, idx))
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(idx, idx + len))
            }
            cursor = idx + len
        }
    }
}
