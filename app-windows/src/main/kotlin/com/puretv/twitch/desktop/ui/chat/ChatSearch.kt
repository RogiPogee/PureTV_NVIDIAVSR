package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart

/**
 * Small Chatterino-style search grammar for the in-memory chat buffer.
 *
 * Supported filters:
 *   from:<name>   username/display-name contains <name>
 *   is:deleted    only deleted messages
 *   is:reply      only replies
 *   is:mention    only messages mentioning the local viewer
 *   has:link      only messages containing http:// or https://
 *
 * Remaining words are matched case-insensitively against username, display name,
 * message text, reply body and rendered emote codes. All terms must match.
 */
internal fun filterChatMessages(messages: List<ChatMessage>, query: String): List<ChatMessage> {
    val rawTokens = query.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (rawTokens.isEmpty()) return messages

    val from = rawTokens.firstOrNull { it.startsWith("from:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.takeIf { it.isNotBlank() }
    val deletedOnly = rawTokens.any { it.equals("is:deleted", ignoreCase = true) }
    val repliesOnly = rawTokens.any { it.equals("is:reply", ignoreCase = true) }
    val mentionsOnly = rawTokens.any { it.equals("is:mention", ignoreCase = true) }
    val linksOnly = rawTokens.any { it.equals("has:link", ignoreCase = true) }
    val terms = rawTokens.filterNot {
        it.startsWith("from:", ignoreCase = true) ||
            it.equals("is:deleted", ignoreCase = true) ||
            it.equals("is:reply", ignoreCase = true) ||
            it.equals("is:mention", ignoreCase = true) ||
            it.equals("has:link", ignoreCase = true)
    }

    return messages.filter { message ->
        if (from != null &&
            !message.username.contains(from, ignoreCase = true) &&
            !message.displayName.contains(from, ignoreCase = true)
        ) return@filter false
        if (deletedOnly && !message.deleted) return@filter false
        if (repliesOnly && message.replyParentDisplayName == null) return@filter false
        if (mentionsOnly && !message.mentionsSelf) return@filter false
        if (linksOnly &&
            !message.message.contains("http://", ignoreCase = true) &&
            !message.message.contains("https://", ignoreCase = true)
        ) return@filter false

        if (terms.isEmpty()) return@filter true
        val searchable = buildString {
            append(message.username).append(' ')
            append(message.displayName).append(' ')
            append(message.message).append(' ')
            message.replyParentBody?.let { append(it).append(' ') }
            message.parsedParts.forEach { part ->
                when (part) {
                    is MessagePart.Text -> append(part.content).append(' ')
                    is MessagePart.TwitchEmote -> {
                        append(part.name).append(' ')
                        part.overlays.forEach { append(it.name).append(' ') }
                    }
                    is MessagePart.ThirdPartyEmote -> {
                        append(part.name).append(' ')
                        part.overlays.forEach { append(it.name).append(' ') }
                    }
                }
            }
        }
        terms.all { searchable.contains(it, ignoreCase = true) }
    }
}
