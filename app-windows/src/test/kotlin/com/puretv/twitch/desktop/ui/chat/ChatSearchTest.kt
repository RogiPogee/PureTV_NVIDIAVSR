package com.puretv.twitch.desktop.ui.chat

import com.puretv.twitch.core.model.ChatMessage
import com.puretv.twitch.core.model.MessagePart
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatSearchTest {
    private fun msg(
        id: String,
        user: String,
        body: String,
        deleted: Boolean = false,
        reply: Boolean = false,
        mention: Boolean = false,
        parts: List<MessagePart> = listOf(MessagePart.Text(body)),
    ) = ChatMessage(
        id = id,
        channel = "test",
        username = user,
        displayName = user,
        color = "",
        message = body,
        parsedParts = parts,
        badges = emptyList(),
        timestamp = 0,
        isSubscriber = false,
        isModerator = false,
        isBroadcaster = false,
        replyParentDisplayName = if (reply) "parent" else null,
        replyParentBody = if (reply) "original line" else null,
        deleted = deleted,
        mentionsSelf = mention,
    )

    @Test fun matchesTextAndSenderFilters() {
        val messages = listOf(
            msg("1", "Alice", "hello world"),
            msg("2", "Bob", "hello there"),
        )
        assertEquals(listOf("1"), filterChatMessages(messages, "from:ali hello").map { it.id })
    }

    @Test fun supportsStructuralFilters() {
        val messages = listOf(
            msg("1", "A", "gone", deleted = true),
            msg("2", "B", "reply", reply = true),
            msg("3", "C", "ping", mention = true),
            msg("4", "D", "https://example.com"),
        )
        assertEquals(listOf("1"), filterChatMessages(messages, "is:deleted").map { it.id })
        assertEquals(listOf("2"), filterChatMessages(messages, "is:reply").map { it.id })
        assertEquals(listOf("3"), filterChatMessages(messages, "is:mention").map { it.id })
        assertEquals(listOf("4"), filterChatMessages(messages, "has:link").map { it.id })
    }

    @Test fun emoteCodesAreSearchable() {
        val messages = listOf(
            msg(
                "1",
                "A",
                "look",
                parts = listOf(
                    MessagePart.Text("look"),
                    MessagePart.ThirdPartyEmote(
                        url = "https://cdn",
                        name = "OMEGALUL",
                        provider = com.puretv.twitch.core.model.EmoteProvider.SEVENTV,
                    ),
                ),
            ),
        )
        assertEquals(listOf("1"), filterChatMessages(messages, "omegalul").map { it.id })
    }
}
