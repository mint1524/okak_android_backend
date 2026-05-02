package com.example

import com.example.auth.TokenService
import com.example.auth.authRoutes
import com.example.chats.ChatRepository
import com.example.chats.MessageRepository
import com.example.chats.chatRoutes
import com.example.llm.LlmClient
import com.example.users.UserRepository
import com.example.users.userRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Application.configureRouting(
    users: UserRepository,
    tokens: TokenService,
    chats: ChatRepository,
    messages: MessageRepository,
    llm: LlmClient
) {
    routing {
        get("/health") {
            call.respond(HealthResponse("ok"))
        }
        authRoutes(users, tokens)
        userRoutes(users)
        chatRoutes(chats, messages, llm)
    }
}
