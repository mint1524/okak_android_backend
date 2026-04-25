package com.example.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.config.JwtConfig
import com.example.users.User
import java.time.Instant
import java.util.Date

class TokenService(private val cfg: JwtConfig) {

    fun issue(user: User): TokenResult {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(cfg.expiresInMinutes * 60)
        val token = JWT.create()
            .withIssuer(cfg.issuer)
            .withAudience(cfg.audience)
            .withClaim("userId", user.id.toString())
            .withClaim("email", user.email)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .sign(Algorithm.HMAC256(cfg.secret))
        return TokenResult(token, expiresAt)
    }
}

data class TokenResult(val token: String, val expiresAt: Instant)
