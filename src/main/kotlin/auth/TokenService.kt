package com.example.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.config.JwtConfig
import com.example.users.User
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date

class TokenService(
    private val cfg: JwtConfig,
    private val refreshRepo: RefreshTokenRepository
) {
    private val random = SecureRandom()

    fun issuePair(user: User): TokenPair {
        val now = Instant.now()
        val accessExpiresAt = now.plusSeconds(cfg.expiresInMinutes * 60)
        val accessToken = JWT.create()
            .withIssuer(cfg.issuer)
            .withAudience(cfg.audience)
            .withClaim("userId", user.id.toString())
            .withClaim("email", user.email)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(accessExpiresAt))
            .sign(Algorithm.HMAC256(cfg.secret))

        val refreshRaw = randomRefresh()
        val refreshExpiresAt = now.plusSeconds(REFRESH_TTL_DAYS * 86400L)
        refreshRepo.save(user.id, refreshRaw, refreshExpiresAt)
        return TokenPair(accessToken, accessExpiresAt, refreshRaw, refreshExpiresAt)
    }

    fun rotate(user: User, oldRecord: RefreshTokenRecord): TokenPair {
        refreshRepo.revoke(oldRecord.id)
        return issuePair(user)
    }

    private fun randomRefresh(): String {
        val bytes = ByteArray(48)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val REFRESH_TTL_DAYS = 30L
    }
}

data class TokenPair(
    val accessToken: String,
    val accessExpiresAt: Instant,
    val refreshToken: String,
    val refreshExpiresAt: Instant
)
