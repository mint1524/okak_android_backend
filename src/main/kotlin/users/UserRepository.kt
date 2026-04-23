package com.example.users

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface UserRepository {
    fun findByEmail(email: String): User?
    fun findById(id: UUID): User?
    fun create(email: String, passwordHash: String): User
}

class InMemoryUserRepository : UserRepository {

    private val byId = ConcurrentHashMap<UUID, User>()
    private val byEmail = ConcurrentHashMap<String, UUID>()

    override fun findByEmail(email: String): User? {
        val id = byEmail[email.lowercase()] ?: return null
        return byId[id]
    }

    override fun findById(id: UUID): User? = byId[id]

    override fun create(email: String, passwordHash: String): User {
        val normalizedEmail = email.lowercase()
        if (byEmail.containsKey(normalizedEmail)) {
            error("user already exists")
        }
        val user = User(
            id = UUID.randomUUID(),
            email = normalizedEmail,
            passwordHash = passwordHash,
            createdAt = Instant.now()
        )
        byId[user.id] = user
        byEmail[normalizedEmail] = user.id
        return user
    }
}
