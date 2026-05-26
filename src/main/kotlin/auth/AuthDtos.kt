package com.example.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val email: String,
    val password: String,
    val verificationCode: String? = null
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class EmailCodeRequest(
    val email: String
)

@Serializable
data class EmailCodeResponse(
    val sent: Boolean,
    val expiresInMinutes: Long,
    val debugCode: String? = null
)
