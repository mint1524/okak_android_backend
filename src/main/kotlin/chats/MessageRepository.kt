package com.example.chats

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface MessageRepository {
    fun add(chatId: UUID, role: MessageRole, content: String, tokensUsed: Int = 0): Message
    fun listByChat(chatId: UUID): List<Message>
    fun listByChatPaged(chatId: UUID, before: UUID? = null, limit: Int = DEFAULT_PAGE): List<Message>
    fun countByChat(chatId: UUID): Int
    fun deleteByChat(chatId: UUID)

    companion object {
        const val DEFAULT_PAGE = 100
        const val MAX_PAGE = 200
    }
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

    override fun listByChatPaged(chatId: UUID, before: UUID?, limit: Int): List<Message> {
        val all = listByChat(chatId)
        val cutoff = before?.let { id -> all.firstOrNull { it.id == id }?.createdAt }
        val window = if (cutoff != null) all.filter { it.createdAt.isBefore(cutoff) } else all
        return window.takeLast(limit.coerceAtMost(MessageRepository.MAX_PAGE))
    }

    override fun countByChat(chatId: UUID): Int = byChat[chatId]?.size ?: 0

    override fun deleteByChat(chatId: UUID) {
        byChat.remove(chatId)
    }
}
