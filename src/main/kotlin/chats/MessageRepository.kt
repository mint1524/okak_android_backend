package com.example.chats

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface MessageRepository {
    fun add(chatId: UUID, role: MessageRole, content: String, tokensUsed: Int = 0): Message
    fun listByChat(chatId: UUID): List<Message>
    fun deleteByChat(chatId: UUID)
}

class InMemoryMessageRepository : MessageRepository {

    private val byChat = ConcurrentHashMap<UUID, MutableList<Message>>()

    override fun add(chatId: UUID, role: MessageRole, content: String, tokensUsed: Int): Message {
        val msg = Message(
            id = UUID.randomUUID(),
            chatId = chatId,
            role = role,
            content = content,
            tokensUsed = tokensUsed,
            createdAt = Instant.now()
        )
        byChat.computeIfAbsent(chatId) { mutableListOf() }.add(msg)
        return msg
    }

    override fun listByChat(chatId: UUID): List<Message> =
        byChat[chatId]?.sortedBy { it.createdAt } ?: emptyList()

    override fun deleteByChat(chatId: UUID) {
        byChat.remove(chatId)
    }
}
