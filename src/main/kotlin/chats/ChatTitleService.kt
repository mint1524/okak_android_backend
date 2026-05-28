package com.example.chats

import com.example.llm.LlmClient
import com.example.llm.LlmMessage
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.util.logging.Logger
import kotlinx.coroutines.launch
import java.util.UUID

class ChatTitleService(
    private val llm: LlmClient,
    private val chats: ChatRepository,
    private val log: Logger
) {

    fun scheduleAutoTitle(application: Application, chatId: UUID, userMessage: String) {
        application.launch {
            runCatching { generateAndSet(chatId, userMessage) }
                .onFailure { log.warn("auto-title failed for $chatId: ${it.message}") }
        }
    }

    suspend fun generateAndSet(chatId: UUID, userMessage: String): String {
        val prompt = listOf(
            LlmMessage(
                "system",
                "Ты получаешь первое сообщение пользователя в чате. Придумай для чата короткий заголовок на русском, 2-5 слов, без кавычек, без точки в конце. Отвечай только заголовком, без префиксов."
            ),
            LlmMessage("user", userMessage)
        )
        val raw = runCatching { llm.complete(prompt).content }.getOrDefault("")
        val cleaned = sanitize(raw).ifBlank { fallbackFromUserMessage(userMessage) }
        chats.updateTitle(chatId, cleaned)
        return cleaned
    }

    private fun sanitize(raw: String): String = raw
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.trim('"', '\'', '«', '»', '.', '!', '?')
        ?.take(60)
        .orEmpty()

    private fun fallbackFromUserMessage(text: String): String {
        val cut = text.trim().take(40)
        return if (cut.length < text.trim().length) "$cut..." else cut.ifBlank { "Новый чат" }
    }
}

internal fun Application.titleLog(): Logger = this.log
