package com.example.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val email: String,
    val password: String
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
