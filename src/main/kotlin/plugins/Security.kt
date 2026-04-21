package com.example.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.example.config.JwtConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt

const val JWT_AUTH_NAME = "auth-jwt"

fun Application.configureSecurity(jwtConfig: JwtConfig) {
    install(Authentication) {
        jwt(JWT_AUTH_NAME) {
            realm = jwtConfig.realm
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtConfig.secret))
                    .withIssuer(jwtConfig.issuer)
                    .withAudience(jwtConfig.audience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asString().isNullOrBlank()) null
                else JWTPrincipal(credential.payload)
            }
        }
    }
}
