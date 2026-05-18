package com.example.chats

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface ChatRepository {
    fun create(userId: UUID, title: String): Chat
    fun findById(id: UUID): Chat?
    fun listByUser(userId: UUID): List<Chat>
    fun delete(id: UUID): Boolean
    fun touch(id: UUID)
    fun updateTitle(id: UUID, title: String)
}

class InMemoryChatRepository : ChatRepository {

    private val chats = ConcurrentHashMap<UUID, Chat>()

    override fun create(userId: UUID, title: String): Chat {
        val now = Instant.now()
        val chat = Chat(
            id = UUID.randomUUID(),
            userId = userId,
            title = title,
            createdAt = now,
            updatedAt = now
        )
        chats[chat.id] = chat
        return chat
    }

    override fun findById(id: UUID): Chat? = chats[id]

    override fun listByUser(userId: UUID): List<Chat> =
        chats.values
            .filter { it.userId == userId }
            .sortedByDescending { it.updatedAt }

    override fun delete(id: UUID): Boolean = chats.remove(id) != null

    override fun touch(id: UUID) {
        val current = chats[id] ?: return
        chats[id] = current.copy(updatedAt = Instant.now())
    }

    override fun updateTitle(id: UUID, title: String) {
        val current = chats[id] ?: return
        chats[id] = current.copy(title = title)
    }
}
