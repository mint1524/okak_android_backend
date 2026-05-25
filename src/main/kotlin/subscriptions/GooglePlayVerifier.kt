package com.example.subscriptions

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

class GooglePlayVerifier(
    private val packageName: String,
    private val credentialsPath: String?
) {
    private val log = LoggerFactory.getLogger(GooglePlayVerifier::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 15_000 }
        expectSuccess = false
    }

    sealed class VerificationResult {
        object Valid : VerificationResult()
        data class Invalid(val reason: String) : VerificationResult()
        data class Skipped(val reason: String) : VerificationResult()
    }

    suspend fun verifySubscription(productId: String, purchaseToken: String): VerificationResult {
        if (credentialsPath.isNullOrBlank()) {
            log.warn("GOOGLE_PLAY_CREDENTIALS_PATH не задан — верификация покупки пропущена (dev mode)")
            return VerificationResult.Skipped("credentials not configured")
        }

        val credFile = File(credentialsPath)
        if (!credFile.exists()) {
            log.warn("Файл credentials не найден: $credentialsPath — пропускаю верификацию")
            return VerificationResult.Skipped("credentials file not found")
        }

        return try {
            val accessToken = obtainAccessToken(credFile.readText()) ?: return VerificationResult.Invalid("failed to obtain access token")
            checkSubscription(accessToken, productId, purchaseToken)
        } catch (e: Exception) {
            log.error("Google Play verification error: ${e.message}")
            VerificationResult.Invalid("verification error: ${e.message}")
        }
    }

    private suspend fun checkSubscription(accessToken: String, productId: String, purchaseToken: String): VerificationResult {
        val url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
            "$packageName/purchases/subscriptions/$productId/tokens/$purchaseToken"

        val resp = http.get(url) {
            headers { append(HttpHeaders.Authorization, "Bearer $accessToken") }
        }

        if (!resp.status.isSuccess()) {
            val body = resp.bodyAsText()
            log.warn("Google Play API returned ${resp.status}: ${body.take(300)}")
            return VerificationResult.Invalid("Google Play API error: ${resp.status.value}")
        }

        val body = resp.bodyAsText()
        val purchase = runCatching { json.decodeFromString<SubscriptionPurchaseDto>(body) }.getOrNull()
            ?: return VerificationResult.Invalid("cannot parse Google Play response")

        return when (purchase.paymentState) {
            1, 2 -> VerificationResult.Valid  // 1=received, 2=free trial
            0 -> VerificationResult.Invalid("payment pending")
            else -> VerificationResult.Invalid("unexpected payment state: ${purchase.paymentState}")
        }
    }

    private suspend fun obtainAccessToken(credentialsJson: String): String? {
        return try {
            val creds = json.decodeFromString<ServiceAccountCredentials>(credentialsJson)
            val jwt = buildJwt(creds)
            exchangeJwtForToken(jwt)
        } catch (e: Exception) {
            log.error("Failed to parse service account credentials: ${e.message}")
            null
        }
    }

    private fun buildJwt(creds: ServiceAccountCredentials): String {
        val now = System.currentTimeMillis() / 1000
        val header = base64url("""{"alg":"RS256","typ":"JWT"}""")
        val payload = base64url("""{"iss":"${creds.clientEmail}","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","iat":$now,"exp":${now + 3600}}""")
        val unsigned = "$header.$payload"
        val privateKeyBytes = parsePem(creds.privateKey)
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
        val privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        val sig = java.security.Signature.getInstance("SHA256withRSA").also {
            it.initSign(privateKey)
            it.update(unsigned.toByteArray())
        }.sign()
        return "$unsigned.${java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sig)}"
    }

    private suspend fun exchangeJwtForToken(jwt: String): String? {
        val resp = http.get("https://oauth2.googleapis.com/token?grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt")
        if (!resp.status.isSuccess()) return null
        val body = resp.bodyAsText()
        return runCatching { json.decodeFromString<TokenResponse>(body).accessToken }.getOrNull()
    }

    private fun base64url(s: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    private fun parsePem(pem: String): ByteArray {
        val clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()
        return java.util.Base64.getDecoder().decode(clean)
    }
}

@Serializable
private data class SubscriptionPurchaseDto(
    val paymentState: Int? = null,
    val expiryTimeMillis: String? = null,
    val autoRenewing: Boolean? = null
)

@Serializable
private data class ServiceAccountCredentials(
    val type: String = "",
    @kotlinx.serialization.SerialName("client_email") val clientEmail: String = "",
    @kotlinx.serialization.SerialName("private_key") val privateKey: String = "",
    @kotlinx.serialization.SerialName("token_uri") val tokenUri: String = ""
)

@Serializable
private data class TokenResponse(
    @kotlinx.serialization.SerialName("access_token") val accessToken: String = ""
)
