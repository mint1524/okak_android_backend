package com.example.llm

interface LlmClient {
    suspend fun complete(history: List<LlmMessage>): LlmResult
}

data class LlmMessage(val role: String, val content: String)
data class LlmResult(val content: String, val tokensUsed: Int)

class MockLlmClient : LlmClient {
    override suspend fun complete(history: List<LlmMessage>): LlmResult {
        val last = history.lastOrNull { it.role == "user" }?.content ?: "..."
        val reply = buildString {
            append("Вы написали: \"")
            append(last.take(120))
            append("\". Это ответ заглушки, реальная LLM подключится позже.")
        }
        // грубая оценка токенов
        val tokens = (last.length + reply.length) / 4
        return LlmResult(reply, tokens)
    }
}
