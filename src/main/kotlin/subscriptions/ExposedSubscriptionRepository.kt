package com.example.subscriptions

import com.example.database.SubscriptionsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExposedSubscriptionRepository : SubscriptionRepository {

    override fun findActive(userId: UUID): Subscription? = transaction {
        val row = SubscriptionsTable.selectAll()
            .where { SubscriptionsTable.userId eq userId }
            .firstOrNull() ?: return@transaction null
        val sub = row.toSubscription()
        if (sub.isActive()) sub else null
    }

    override fun upsertFromPurchase(userId: UUID, plan: Plan, purchaseToken: String): Subscription = transaction {
        val now = Instant.now()
        val expires = now.plus(30, ChronoUnit.DAYS)
        SubscriptionsTable.deleteWhere { SubscriptionsTable.userId eq userId }
        val newId = UUID.randomUUID()
        SubscriptionsTable.insert {
            it[id] = newId
            it[SubscriptionsTable.userId] = userId
            it[planId] = plan.id
            it[status] = Subscription.Status.ACTIVE.name
            it[startedAt] = now
            it[expiresAt] = expires
            it[googlePurchaseToken] = purchaseToken
            it[requestsUsed] = 0
            it[tokensUsed] = 0
            it[createdAt] = now
        }
        Subscription(
            id = newId,
            userId = userId,
            planId = plan.id,
            status = Subscription.Status.ACTIVE,
            startedAt = now,
            expiresAt = expires,
            purchaseToken = purchaseToken,
            requestsUsed = 0,
            tokensUsed = 0
        )
    }

    override fun incrementUsage(userId: UUID, requests: Int, tokens: Int) {
        transaction {
            val row = SubscriptionsTable.selectAll()
                .where { SubscriptionsTable.userId eq userId }
                .firstOrNull() ?: return@transaction
            val r = row[SubscriptionsTable.requestsUsed]
            val t = row[SubscriptionsTable.tokensUsed]
            SubscriptionsTable.update({ SubscriptionsTable.userId eq userId }) {
                it[requestsUsed] = r + requests
                it[tokensUsed] = t + tokens
            }
        }
    }

    private fun ResultRow.toSubscription() = Subscription(
        id = this[SubscriptionsTable.id],
        userId = this[SubscriptionsTable.userId],
        planId = this[SubscriptionsTable.planId],
        status = Subscription.Status.valueOf(this[SubscriptionsTable.status]),
        startedAt = this[SubscriptionsTable.startedAt],
        expiresAt = this[SubscriptionsTable.expiresAt],
        purchaseToken = this[SubscriptionsTable.googlePurchaseToken],
        requestsUsed = this[SubscriptionsTable.requestsUsed],
        tokensUsed = this[SubscriptionsTable.tokensUsed]
    )
}
