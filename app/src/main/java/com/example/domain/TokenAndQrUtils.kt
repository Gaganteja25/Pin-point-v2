package com.example.domain

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom

data class MentionExtractionResult(
    val mentionedSessionIds: List<String>,
    val mentionedDisplayNames: List<String>
)

data class MemberMentionTarget(
    val sessionId: String,
    val displayName: String
)

object TokenAndQrUtils {
    private val secureRandom = SecureRandom()
    private val urlSafeChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generateSecureSessionId(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return "sess_" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateCrowdId(): String {
        val bytes = ByteArray(12)
        secureRandom.nextBytes(bytes)
        return "crd_" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateInviteToken(): String {
        val sb = StringBuilder("PIN-")
        repeat(3) { group ->
            if (group > 0) sb.append('-')
            repeat(4) {
                sb.append(codeChars[secureRandom.nextInt(codeChars.length)])
            }
        }
        sb.append('-')
        repeat(8) {
            sb.append(urlSafeChars[secureRandom.nextInt(urlSafeChars.length)])
        }
        return sb.toString()
    }

    fun generateUniqueId(prefix: String): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return "${prefix}_" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun buildInviteUrl(inviteToken: String): String {
        return "https://pinpoint.app/join/$inviteToken"
    }

    fun parseInviteTokenFromInput(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val afterJoin = when {
            trimmed.contains("/join/") -> trimmed.substringAfterLast("/join/")
            trimmed.startsWith("pinpoint://join/") -> trimmed.removePrefix("pinpoint://join/")
            else -> trimmed
        }
        val normalized = afterJoin
            .substringBefore("?")
            .substringBefore("#")
            .trim()
        return normalized.takeIf { it.length >= 6 }
    }

    /**
     * Validates @mentions strictly against current crowd membership.
     * Supports multi-word display names (e.g. @Best Friend) and multiple mentions.
     */
    fun extractValidMentions(
        messageText: String,
        currentMembers: List<MemberMentionTarget>
    ): MentionExtractionResult {
        if (!messageText.contains('@') || currentMembers.isEmpty()) {
            return MentionExtractionResult(emptyList(), emptyList())
        }
        val matchedIds = linkedSetOf<String>()
        val matchedNames = linkedSetOf<String>()

        // Sort longest display names first so "@Best Friend" matches before "@Best"
        val sortedMembers = currentMembers.sortedByDescending { it.displayName.length }
        for (member in sortedMembers) {
            val name = member.displayName.trim()
            if (name.isEmpty()) continue
            val pattern = Regex("(?i)(?:^|\\s)@${Regex.escape(name)}(?=$|\\s|[.,!?:;])")
            if (pattern.containsMatchIn(messageText)) {
                matchedIds.add(member.sessionId)
                matchedNames.add(name)
            }
        }
        return MentionExtractionResult(matchedIds.toList(), matchedNames.toList())
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.parseColor("#0B132B") else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
