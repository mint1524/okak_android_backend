package com.example.auth

import com.example.database.RefreshTokensTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class RefreshTokenRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val revoked: Boolean
)

interface RefreshTokenRepository {
    fun save(userId: UUID, rawToken: String, expiresAt: Instant): RefreshTokenRecord
    fun findActiveByRawToken(rawToken: String): RefreshTokenRecord?
    fun revoke(id: UUID)
    fun revokeAllForUser(userId: UUID)

    fun hash(rawToken: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(rawToken.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

class InMemoryRefreshTokenRepository : RefreshTokenRepository {
    private val byHash = ConcurrentHashMap<String, RefreshTokenRecord>()

    override fun save(userId: UUID, rawToken: String, expiresAt: Instant): RefreshTokenRecord {
        val rec = RefreshTokenRecord(UUID.randomUUID(), userId, hash(rawToken), expiresAt, false)
        byHash[rec.tokenHash] = rec
        return rec
    }

    override fun findActiveByRawToken(rawToken: String): RefreshTokenRecord? {
        val rec = byHash[hash(rawToken)] ?: return null
        return if (!rec.revoked && rec.expiresAt.isAfter(Instant.now())) rec else null
    }

    override fun revoke(id: UUID) {
        byHash.entries.firstOrNull { it.value.id == id }?.let {
            byHash[it.key] = it.value.copy(revoked = true)
        }
    }

    override fun revokeAllForUser(userId: UUID) {
        byHash.entries.toList().forEach {
            if (it.value.userId == userId) byHash[it.key] = it.value.copy(revoked = true)
        }
    }
}

class ExposedRefreshTokenRepository : RefreshTokenRepository {

    override fun save(userId: UUID, rawToken: String, expiresAt: Instant): RefreshTokenRecord = transaction {
        val id = UUID.randomUUID()
        val tokenHash = hash(rawToken)
        RefreshTokensTable.insert {
            it[RefreshTokensTable.id] = id
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.tokenHash] = tokenHash
            it[RefreshTokensTable.expiresAt] = expiresAt
            it[revoked] = false
            it[createdAt] = Instant.now()
        }
        RefreshTokenRecord(id, userId, tokenHash, expiresAt, false)
    }

    override fun findActiveByRawToken(rawToken: String): RefreshTokenRecord? = transaction {
        val tokenHash = hash(rawToken)
        RefreshTokensTable.selectAll()
            .where { (RefreshTokensTable.tokenHash eq tokenHash) and (RefreshTokensTable.revoked eq false) }
            .singleOrNull()
            ?.let {
                RefreshTokenRecord(
                    id = it[RefreshTokensTable.id],
                    userId = it[RefreshTokensTable.userId],
                    tokenHash = it[RefreshTokensTable.tokenHash],
                    expiresAt = it[RefreshTokensTable.expiresAt],
                    revoked = it[RefreshTokensTable.revoked]
                )
            }
            ?.takeIf { it.expiresAt.isAfter(Instant.now()) }
    }

    override fun revoke(id: UUID) {
        transaction {
            RefreshTokensTable.update({ RefreshTokensTable.id eq id }) {
                it[revoked] = true
            }
        }
    }

    override fun revokeAllForUser(userId: UUID) {
        transaction {
            RefreshTokensTable.update({ RefreshTokensTable.userId eq userId }) {
                it[revoked] = true
            }
        }
    }
}
