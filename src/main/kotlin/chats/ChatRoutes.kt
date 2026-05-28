package com.example.chats

import com.example.llm.LlmClient
import com.example.llm.LlmMessage
import com.example.llm.LlmStreamEvent
import com.example.plugins.ErrorResponse
import com.example.plugins.JWT_AUTH_NAME
import com.example.subscriptions.Plans
import com.example.subscriptions.SubscriptionRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

fun Route.chatRoutes(
    chats: ChatRepository,
    messages: MessageRepository,
    llm: LlmClient,
    subs: SubscriptionRepository,
    titleService: ChatTitleService
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
                val before = call.request.queryParameters["before"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: MessageRepository.DEFAULT_PAGE
                val page = messages.listByChatPaged(chatId, before, limit)
                call.respond(page.map { it.toDto() })
            }

            patch("/{chatId}") {
                val userId = call.userId() ?: return@patch call.unauthorized()
                val chatId = call.chatId() ?: return@patch call.badId()
                val chat = chats.findById(chatId)
                if (chat == null || chat.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("CHAT_NOT_FOUND", "chat not found"))
                    return@patch
                }
                val req = runCatching { call.receive<UpdateChatRequest>() }.getOrNull()
                val title = req?.title?.trim()?.take(120)
                if (title.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_TITLE", "title is empty"))
                    return@patch
                }
                chats.updateTitle(chatId, title)
                val updated = chats.findById(chatId)!!
                call.respond(updated.toDto())
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

                val sub = subs.findActive(userId)
                if (sub == null) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse("SUBSCRIPTION_REQUIRED", "active subscription required")
                    )
                    return@post
                }
                val plan = Plans.byId(sub.planId)
                if (plan == null) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("BAD_PLAN", "plan not found"))
                    return@post
                }
                if (sub.requestsUsed >= plan.requestLimit || sub.tokensUsed >= plan.tokenLimit) {
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("LIMIT_EXCEEDED", "request or token limit exceeded")
                    )
                    return@post
                }

                val req = call.receive<SendMessageRequest>()
                val text = req.content.trim()
                if (text.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("EMPTY_MESSAGE", "message is empty"))
                    return@post
                }
                if (text.length > MAX_MESSAGE_LEN) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("MESSAGE_TOO_LONG", "максимум $MAX_MESSAGE_LEN символов"))
                    return@post
                }

                val userMsg = messages.add(chatId, MessageRole.USER, text)
                val isFirstMessage = messages.countByChat(chatId) == 1
                val history = messages.listByChat(chatId).map { LlmMessage(it.role.name.lowercase(), it.content) }
                val result = llm.complete(history, plan.modelName)
                val assistantMsg = messages.add(chatId, MessageRole.ASSISTANT, result.content, result.tokensUsed)
                chats.touch(chatId)
                subs.incrementUsage(userId, requests = 1, tokens = result.tokensUsed)

                if (isFirstMessage) titleService.generateAndSet(chatId, text)

                call.respond(
                    SendMessageResponse(
                        userMessage = userMsg.toDto(),
                        assistantMessage = assistantMsg.toDto(),
                        tokensUsed = result.tokensUsed
                    )
                )
            }

            post("/{chatId}/messages/stream") {
                val userId = call.userId() ?: return@post call.unauthorized()
                val chatId = call.chatId() ?: return@post call.badId()
                val chat = chats.findById(chatId)
                if (chat == null || chat.userId != userId) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("CHAT_NOT_FOUND", "chat not found"))
                    return@post
                }
                val sub = subs.findActive(userId)
                if (sub == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("SUBSCRIPTION_REQUIRED", "active subscription required"))
                    return@post
                }
                val plan = Plans.byId(sub.planId)
                if (plan == null) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("BAD_PLAN", "plan not found"))
                    return@post
                }
                if (sub.requestsUsed >= plan.requestLimit || sub.tokensUsed >= plan.tokenLimit) {
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("LIMIT_EXCEEDED", "request or token limit exceeded"))
                    return@post
                }
                val req = call.receive<SendMessageRequest>()
                val text = req.content.trim()
                if (text.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("EMPTY_MESSAGE", "message is empty"))
                    return@post
                }
                if (text.length > MAX_MESSAGE_LEN) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("MESSAGE_TOO_LONG", "максимум $MAX_MESSAGE_LEN символов"))
                    return@post
                }

                val userMsg = messages.add(chatId, MessageRole.USER, text)
                val isFirstMessage = messages.countByChat(chatId) == 1
                val history = messages.listByChat(chatId).map { LlmMessage(it.role.name.lowercase(), it.content) }
                val planModel = plan.modelName

                call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                    suspend fun send(type: String, payload: JsonObject?) {
                        val frame = buildJsonObject {
                            put("type", type)
                            payload?.let { put("payload", it) }
                        }
                        write("data: ${Json.encodeToString(JsonObject.serializer(), frame)}\n\n")
                        flush()
                    }

                    send("user_message", buildJsonObject {
                        put("id", userMsg.id.toString())
                        put("role", "user")
                        put("content", userMsg.content)
                        put("createdAt", userMsg.createdAt.toString())
                    })

                    val acc = StringBuilder()
                    var totalTokens = 0
                    var hadError = false
                    llm.stream(history, planModel).collect { event ->
                        when (event) {
                            is LlmStreamEvent.Delta -> {
                                acc.append(event.content)
                                send("delta", buildJsonObject { put("content", event.content) })
                            }
                            is LlmStreamEvent.Done -> {
                                totalTokens = event.totalTokens
                            }
                            is LlmStreamEvent.Error -> {
                                hadError = true
                                send("error", buildJsonObject { put("message", event.message) })
                            }
                        }
                    }

                    val finalText = acc.toString().ifBlank {
                        if (hadError) "не удалось получить ответ от LLM" else "пустой ответ"
                    }
                    val tokens = if (totalTokens > 0) totalTokens else (finalText.length + text.length) / 4
                    val assistantMsg = messages.add(chatId, MessageRole.ASSISTANT, finalText, tokens)
                    chats.touch(chatId)
                    subs.incrementUsage(userId, requests = 1, tokens = tokens)

                    send("assistant_message", buildJsonObject {
                        put("id", assistantMsg.id.toString())
                        put("role", "assistant")
                        put("content", assistantMsg.content)
                        put("createdAt", assistantMsg.createdAt.toString())
                        put("tokensUsed", tokens)
                    })
                    if (isFirstMessage) {
                        val title = titleService.generateAndSet(chatId, text)
                        send("chat_title", buildJsonObject {
                            put("chatId", chatId.toString())
                            put("title", title)
                        })
                    }
                    send("done", null)
                }
            }
        }
    }
}

private const val MAX_MESSAGE_LEN = 8000

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
