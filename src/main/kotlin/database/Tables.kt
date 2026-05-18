package com.example.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = text("password_hash")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ChatsTable : Table("chats") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val title = varchar("title", 255)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object MessagesTable : Table("messages") {
    val id = uuid("id")
    val chatId = uuid("chat_id").references(ChatsTable.id)
    val role = varchar("role", 20)
    val content = text("content")
    val tokensUsed = integer("tokens_used")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokensTable : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val revoked = bool("revoked")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object SubscriptionsTable : Table("subscriptions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id).uniqueIndex()
    val planId = varchar("plan_id", 50)
    val status = varchar("status", 20)
    val startedAt = timestamp("started_at")
    val expiresAt = timestamp("expires_at")
    val googlePurchaseToken = text("google_purchase_token").nullable()
    val requestsUsed = integer("requests_used")
    val tokensUsed = integer("tokens_used")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
