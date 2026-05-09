package com.example.chats

import com.example.llm.LlmClient
import com.example.llm.LlmMessage
import com.example.plugins.ErrorResponse
import com.example.plugins.JWT_AUTH_NAME
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.chatRoutes(
    chats: ChatRepository,
    messages: MessageRepository,
    llm: LlmClient
) {
    authenticate(JWT_AUTH_NAME) {
        route("/chats") {

            get {
                val userId = call.userId() ?: return@get call.unauthorized()
                val list = chats.listByUser(userId).map { it.toDto() }
                call.respond(list)
            }

            post {
                val userId = call.userId() ?: return@post call.unauthorized()
                val req = runCatching { call.receive<CreateChatRequest>() }.getOrNull()
                val title = req?.title?.trim().takeUnless { it.isNullOrBlank() } ?: "Новый чат"
                val chat = chats.create(userId, title)
                call.respond(HttpStatusCode.Created, chat.toDto())
            }

            get("/{chatId}/messages") {
                val userId = call.userId() ?: return@get call.unauthorized()
                val chatId = call.chatId() ?: return@get call.badId()
                val chat = chats.findById(chatId)
                if (chat == null || chat.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("CHAT_NOT_FOUND", "chat not found"))
                    return@get
                }
                call.respond(messages.listByChat(chatId).map { it.toDto() })
            }

            delete("/{chatId}") {
                val userId = call.userId() ?: return@delete call.unauthorized()
                val chatId = call.chatId() ?: return@delete call.badId()
                val chat = chats.findById(chatId)
                if (chat == null || chat.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("CHAT_NOT_FOUND", "chat not found"))
                    return@delete
                }
                chats.delete(chatId)
                messages.deleteByChat(chatId)
                call.respond(DeleteChatResponse(true))
            }

            post("/{chatId}/messages") {
                val userId = call.userId() ?: return@post call.unauthorized()
                val chatId = call.chatId() ?: return@post call.badId()
                val chat = chats.findById(chatId)
                if (chat == null || chat.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("CHAT_NOT_FOUND", "chat not found"))
                    return@post
                }
                val req = call.receive<SendMessageRequest>()
                val text = req.content.trim()
                if (text.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("EMPTY_MESSAGE", "message is empty"))
                    return@post
                }

                val userMsg = messages.add(chatId, MessageRole.USER, text)
                val history = messages.listByChat(chatId).map { LlmMessage(it.role.name.lowercase(), it.content) }
                val result = llm.complete(history)
                val assistantMsg = messages.add(chatId, MessageRole.ASSISTANT, result.content, result.tokensUsed)
                chats.touch(chatId)

                call.respond(
                    SendMessageResponse(
                        userMessage = userMsg.toDto(),
                        assistantMessage = assistantMsg.toDto(),
                        tokensUsed = result.tokensUsed
                    )
                )
            }
        }
    }
}

internal fun io.ktor.server.application.ApplicationCall.userId(): UUID? {
    val principal = principal<JWTPrincipal>() ?: return null
    val raw = principal.payload.getClaim("userId").asString() ?: return null
    return runCatching { UUID.fromString(raw) }.getOrNull()
}

internal fun io.ktor.server.application.ApplicationCall.chatId(): UUID? {
    val raw = parameters["chatId"] ?: return null
    return runCatching { UUID.fromString(raw) }.getOrNull()
}

internal suspend fun io.ktor.server.application.ApplicationCall.unauthorized() {
    respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "no token"))
}

internal suspend fun io.ktor.server.application.ApplicationCall.badId() {
    respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_ID", "invalid id"))
}
