package com.example.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface LlmClient {
    suspend fun complete(history: List<LlmMessage>): LlmResult
    fun stream(history: List<LlmMessage>): Flow<LlmStreamEvent>
}

data class LlmMessage(val role: String, val content: String)
data class LlmResult(val content: String, val tokensUsed: Int)

sealed class LlmStreamEvent {
    data class Delta(val content: String) : LlmStreamEvent()
    data class Done(val totalTokens: Int) : LlmStreamEvent()
    data class Error(val message: String) : LlmStreamEvent()
}

class MockLlmClient : LlmClient {
    override suspend fun complete(history: List<LlmMessage>): LlmResult {
        val last = history.lastOrNull { it.role == "user" }?.content ?: "..."
        val reply = buildMockReply(last)
        val tokens = (last.length + reply.length) / 4
        return LlmResult(reply, tokens)
    }

    override fun stream(history: List<LlmMessage>): Flow<LlmStreamEvent> = flow {
        val last = history.lastOrNull { it.role == "user" }?.content ?: "..."
        val reply = buildMockReply(last)
        reply.chunked(4).forEach { chunk ->
            delay(40)
            emit(LlmStreamEvent.Delta(chunk))
        }
        emit(LlmStreamEvent.Done((last.length + reply.length) / 4))
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
