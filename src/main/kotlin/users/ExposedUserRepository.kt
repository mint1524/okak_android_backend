package com.example.users

import com.example.database.UsersTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ExposedUserRepository : UserRepository {

    override fun findByEmail(email: String): User? = transaction {
        UsersTable.selectAll()
            .where { UsersTable.email eq email.lowercase() }
            .firstOrNull()
            ?.toUser()
    }

    override fun findById(id: UUID): User? = transaction {
        UsersTable.selectAll()
            .where { UsersTable.id eq id }
            .firstOrNull()
            ?.toUser()
    }

    override fun create(email: String, passwordHash: String): User = transaction {
        val normalized = email.lowercase()
        val existing = UsersTable.selectAll()
            .where { UsersTable.email eq normalized }
            .firstOrNull()
        if (existing != null) error("user already exists")
        val now = Instant.now()
        val newId = UUID.randomUUID()
        UsersTable.insert {
            it[id] = newId
            it[this.email] = normalized
            it[this.passwordHash] = passwordHash
            it[createdAt] = now
        }
        User(newId, normalized, passwordHash, now)
    }

    private fun ResultRow.toUser() = User(
        id = this[UsersTable.id],
        email = this[UsersTable.email],
        passwordHash = this[UsersTable.passwordHash],
        createdAt = this[UsersTable.createdAt]
    )
}
