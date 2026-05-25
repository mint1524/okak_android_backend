package com.example.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface LlmClient {
    suspend fun complete(history: List<LlmMessage>, model: String? = null): LlmResult
    fun stream(history: List<LlmMessage>, model: String? = null): Flow<LlmStreamEvent>
}

data class LlmMessage(val role: String, val content: String)
data class LlmResult(val content: String, val tokensUsed: Int)

sealed class LlmStreamEvent {
    data class Delta(val content: String) : LlmStreamEvent()
    data class Done(val totalTokens: Int) : LlmStreamEvent()
    data class Error(val message: String) : LlmStreamEvent()
}

class MockLlmClient : LlmClient {
    override suspend fun complete(history: List<LlmMessage>, model: String?): LlmResult {
        val last = history.lastOrNull { it.role == "user" }?.content ?: "..."
        val reply = if (isTitleRequest(history)) buildMockTitle(last) else buildMockReply(last)
        val tokens = (last.length + reply.length) / 4
        return LlmResult(reply, tokens)
    }

    override fun stream(history: List<LlmMessage>, model: String?): Flow<LlmStreamEvent> = flow {
        val last = history.lastOrNull { it.role == "user" }?.content ?: "..."
        val reply = buildMockReply(last)
        reply.chunked(4).forEach { chunk ->
            delay(40)
            emit(LlmStreamEvent.Delta(chunk))
        }
        emit(LlmStreamEvent.Done((last.length + reply.length) / 4))
    }

    private fun isTitleRequest(history: List<LlmMessage>): Boolean {
        val sys = history.firstOrNull { it.role == "system" }?.content?.lowercase() ?: return false
        return "заголов" in sys
    }

    private fun buildMockTitle(last: String): String {
        val words = last.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(4)
        val title = words.joinToString(" ").take(40)
        return title.ifBlank { "Новый чат" }
    }

    private fun buildMockReply(last: String) = buildString {
        append("Вы написали: \"")
        append(last.take(120))
        append("\". Это ответ заглушки, реальная LLM подключится позже.\n\n")
        append("```kotlin\n")
        append("fun greet(name: String) = \"Привет, \$name!\"\n")
        append("```")
    }
}
