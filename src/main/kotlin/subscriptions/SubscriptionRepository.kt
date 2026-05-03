package com.example.subscriptions

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface SubscriptionRepository {
    fun findActive(userId: UUID): Subscription?
    fun upsertFromPurchase(userId: UUID, plan: Plan, purchaseToken: String): Subscription
    fun incrementUsage(userId: UUID, requests: Int, tokens: Int)
}

class InMemorySubscriptionRepository : SubscriptionRepository {

    private val byUser = ConcurrentHashMap<UUID, Subscription>()

    override fun findActive(userId: UUID): Subscription? {
        val s = byUser[userId] ?: return null
        return if (s.isActive()) s else null
    }

    override fun upsertFromPurchase(userId: UUID, plan: Plan, purchaseToken: String): Subscription {
        val now = Instant.now()
        val s = Subscription(
            id = UUID.randomUUID(),
            userId = userId,
            planId = plan.id,
            status = Subscription.Status.ACTIVE,
            startedAt = now,
            expiresAt = now.plus(30, ChronoUnit.DAYS),
            purchaseToken = purchaseToken,
            requestsUsed = 0,
            tokensUsed = 0
        )
        byUser[userId] = s
        return s
    }

    override fun incrementUsage(userId: UUID, requests: Int, tokens: Int) {
        val current = byUser[userId] ?: return
        byUser[userId] = current.copy(
            requestsUsed = current.requestsUsed + requests,
            tokensUsed = current.tokensUsed + tokens
        )
    }
}
