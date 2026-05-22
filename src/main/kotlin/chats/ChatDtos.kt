package com.example.chats

import kotlinx.serialization.Serializable

@Serializable
data class ChatDto(
    val id: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateChatRequest(
    val title: String? = null
)

@Serializable
data class UpdateChatRequest(
    val title: String
)

@Serializable
data class MessageDto(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: String
)

@Serializable
data class SendMessageRequest(
    val content: String
)

@Serializable
data class SendMessageResponse(
    val userMessage: MessageDto,
    val assistantMessage: MessageDto,
    val tokensUsed: Int
)

@Serializable
data class DeleteChatResponse(val success: Boolean)

internal fun Chat.toDto() = ChatDto(
    id = id.toString(),
    title = title,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)

internal fun Message.toDto() = MessageDto(
    id = id.toString(),
    role = role.name.lowercase(),
    content = content,
    createdAt = createdAt.toString()
)
