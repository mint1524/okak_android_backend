package com.example.chats

import java.time.Instant
import java.util.UUID

data class Chat(
    val id: UUID,
    val userId: UUID,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class Message(
    val id: UUID,
    val chatId: UUID,
    val role: MessageRole,
    val content: String,
    val tokensUsed: Int,
    val createdAt: Instant
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }
