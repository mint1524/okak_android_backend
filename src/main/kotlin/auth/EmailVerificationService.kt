package com.example.auth

import com.example.config.MailConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class EmailVerificationService(
    private val cfg: MailConfig,
    private val mail: MailServiceClient
) {
    private val random = SecureRandom()
    private val codes = ConcurrentHashMap<String, CodeRecord>()

    val verificationRequired: Boolean
        get() = cfg.verificationRequired

    suspend fun issue(email: String): CodeIssueResult {
        cleanupExpired()
        val code = randomCode()
        val expiresAt = Instant.now().plusSeconds(cfg.codeTtlMinutes * 60)
        codes[email] = CodeRecord(hash(code), expiresAt)
        val sent = mail.sendVerificationCode(email, code, cfg.codeTtlMinutes)
        return CodeIssueResult(
            sent = sent,
            debugCode = if (cfg.devReturnCode) code else null,
            expiresInMinutes = cfg.codeTtlMinutes
        )
    }

    fun verify(email: String, code: String?): Boolean {
        if (!verificationRequired) return true
        val normalizedCode = code?.trim().orEmpty()
        if (normalizedCode.length != CODE_LENGTH) return false
        val record = codes[email] ?: return false
        if (record.expiresAt.isBefore(Instant.now())) {
            codes.remove(email)
            return false
        }
        val ok = MessageDigest.isEqual(record.codeHash.toByteArray(), hash(normalizedCode).toByteArray())
        if (ok) codes.remove(email)
        return ok
    }

    private fun randomCode(): String = buildString {
        repeat(CODE_LENGTH) {
            append(random.nextInt(10))
        }
    }

    private fun cleanupExpired() {
        val now = Instant.now()
        codes.entries.removeIf { it.value.expiresAt.isBefore(now) }
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val CODE_LENGTH = 6
    }
}

data class CodeIssueResult(
    val sent: Boolean,
    val debugCode: String?,
    val expiresInMinutes: Long
)

private data class CodeRecord(
    val codeHash: String,
    val expiresAt: Instant
)
