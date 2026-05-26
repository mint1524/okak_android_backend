package com.example.auth

import com.example.config.MailConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MailServiceClient(
    private val cfg: MailConfig
) {
    private val json = Json { encodeDefaults = true }
    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
        expectSuccess = false
    }

    val isConfigured: Boolean
        get() = cfg.baseUrl.isNotBlank() && cfg.token.isNotBlank() && cfg.hmacSecret.isNotBlank()

    suspend fun sendVerificationCode(email: String, code: String, expiresInMinutes: Long): Boolean {
        if (!isConfigured) return false
        val body = json.encodeToString(
            MailSendRequest(
                to = listOf(email),
                subject = "OKAK email verification code",
                template = "verification-code",
                category = "transactional",
                metadata = mapOf("kind" to "email_verification"),
                variables = mapOf(
                    "code" to code,
                    "expiresInMinutes" to expiresInMinutes.toString(),
                    "headline" to "Подтвердите почту",
                    "eyebrow" to "OKAK security",
                    "preheader" to "Ваш код подтверждения OKAK: $code"
                )
            )
        )
        val timestamp = System.currentTimeMillis().toString()
        val signature = hmacSha256Hex(cfg.hmacSecret, "$timestamp.$body")
        val response = http.post("${cfg.baseUrl.trimEnd('/')}/internal/send") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${cfg.token}")
            header("X-OKAK-Timestamp", timestamp)
            header("X-OKAK-Signature", signature)
            setBody(body)
        }
        return response.status.value in 200..299 && response.bodyAsText().contains("\"ok\":true")
    }

    private fun hmacSha256Hex(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

@Serializable
private data class MailSendRequest(
    val to: List<String>,
    val subject: String,
    val template: String,
    val category: String,
    val metadata: Map<String, String>,
    val variables: Map<String, String>
)
