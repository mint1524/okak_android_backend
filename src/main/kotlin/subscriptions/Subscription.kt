package com.example.subscriptions

import java.time.Instant
import java.util.UUID

data class Subscription(
    val id: UUID,
    val userId: UUID,
    val planId: String,
    val status: Status,
    val startedAt: Instant,
    val expiresAt: Instant,
    val purchaseToken: String?,
    val requestsUsed: Int,
    val tokensUsed: Int
) {
    enum class Status { ACTIVE, EXPIRED, CANCELLED }

    fun isActive(now: Instant = Instant.now()): Boolean =
        status == Status.ACTIVE && expiresAt.isAfter(now)
}
