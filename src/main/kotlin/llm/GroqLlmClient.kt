package com.example.llm

import com.example.config.LlmConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class GroqLlmClient(private val cfg: LlmConfig) : LlmClient {
    private val log = LoggerFactory.getLogger(GroqLlmClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 60_000
        }
        expectSuccess = false
    }

    override suspend fun complete(history: List<LlmMessage>): LlmResult {
        val req = buildRequest(history, stream = false)
        return try {
            val resp = http.post("${cfg.baseUrl.trimEnd('/')}/chat/completions") {
                headers { append(HttpHeaders.Authorization, "Bearer ${cfg.apiKey}") }
                contentType(ContentType.Application.Json)
                setBody(req)
            }
            if (!resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                log.warn("groq returned ${resp.status}: ${body.take(500)}")
                return fallback(history, "LLM сервис недоступен (HTTP ${resp.status.value})")
            }
            val body = resp.body<GroqChatResponse>()
            val content = body.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            val tokens = body.usage?.totalTokens ?: estimateTokens(history, content)
            if (content.isBlank()) fallback(history, "пустой ответ от модели")
            else LlmResult(content, tokens)
        } catch (e: Exception) {
            log.warn("groq call failed: ${e.message}")
            fallback(history, "ошибка обращения к LLM")
        }
    }

    override fun stream(history: List<LlmMessage>): Flow<LlmStreamEvent> = flow {
        val req = buildRequest(history, stream = true)
        var totalTokens = 0
        val acc = StringBuilder()
        try {
            http.preparePost("${cfg.baseUrl.trimEnd('/')}/chat/completions") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer ${cfg.apiKey}")
                    append(HttpHeaders.Accept, "text/event-stream")
                }
                contentType(ContentType.Application.Json)
                setBody(req)
            }.execute { resp ->
                if (!resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    log.warn("groq stream returned ${resp.status}: ${body.take(500)}")
                    emit(LlmStreamEvent.Error("HTTP ${resp.status.value}"))
                    return@execute
                }
                val channel = resp.bodyAsChannel()
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    if (line.isBlank() || !line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    val chunk = runCatching { json.decodeFromString<GroqStreamChunk>(payload) }.getOrNull() ?: continue
                    val delta = chunk.choices.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) {
                        acc.append(delta)
                        emit(LlmStreamEvent.Delta(delta))
                    }
                    chunk.usage?.totalTokens?.let { totalTokens = it }
                }
            }
            val tokens = if (totalTokens > 0) totalTokens else estimateTokens(history, acc.toString())
            emit(LlmStreamEvent.Done(tokens))
        } catch (e: Exception) {
            log.warn("groq stream failed: ${e.message}")
            if (acc.isNotEmpty()) {
                emit(LlmStreamEvent.Done(estimateTokens(history, acc.toString())))
            } else {
                emit(LlmStreamEvent.Error("ошибка обращения к LLM"))
            }
        }
    }

    private fun buildRequest(history: List<LlmMessage>, stream: Boolean): GroqChatRequest {
        val msgs = buildList {
            if (cfg.systemPrompt.isNotBlank()) add(GroqMessage("system", cfg.systemPrompt))
            history.forEach { add(GroqMessage(normalizeRole(it.role), it.content)) }
        }
        return GroqChatRequest(
            model = cfg.model,
            messages = msgs,
            temperature = cfg.temperature,
            maxTokens = cfg.maxTokens,
            stream = stream
        )
    }

    private fun fallback(history: List<LlmMessage>, hint: String): LlmResult {
        val last = history.lastOrNull { it.role == "user" }?.content ?: "..."
        val text = "Не получилось ответить (${hint}). Сказали: \"${last.take(120)}\""
        return LlmResult(text, estimateTokens(history, text))
    }

    private fun normalizeRole(role: String) = when (role) {
        "user", "assistant", "system" -> role
        else -> "user"
    }

    private fun estimateTokens(history: List<LlmMessage>, reply: String): Int {
        val input = history.sumOf { it.content.length }
        return (input + reply.length) / 4
    }
}

@Serializable
private data class GroqMessage(val role: String, val content: String)

@Serializable
private data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double,
    @kotlinx.serialization.SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = false
)

@Serializable
private data class GroqChatResponse(
    val choices: List<GroqChoice> = emptyList(),
    val usage: GroqUsage? = null
)

@Serializable
private data class GroqChoice(val message: GroqMessage)

@Serializable
private data class GroqStreamChunk(
    val choices: List<GroqStreamChoice> = emptyList(),
    val usage: GroqUsage? = null
)

@Serializable
private data class GroqStreamChoice(
    val delta: GroqStreamDelta = GroqStreamDelta(),
    @kotlinx.serialization.SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
private data class GroqStreamDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
private data class GroqUsage(
    @kotlinx.serialization.SerialName("prompt_tokens") val promptTokens: Int = 0,
    @kotlinx.serialization.SerialName("completion_tokens") val completionTokens: Int = 0,
    @kotlinx.serialization.SerialName("total_tokens") val totalTokens: Int = 0
)
