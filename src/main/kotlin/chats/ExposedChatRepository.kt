package com.example.chats

import com.example.database.ChatsTable
import com.example.database.MessagesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

class ExposedChatRepository : ChatRepository {

    override fun create(userId: UUID, title: String): Chat = transaction {
        val now = Instant.now()
        val newId = UUID.randomUUID()
        ChatsTable.insert {
            it[id] = newId
            it[ChatsTable.userId] = userId
            it[ChatsTable.title] = title
            it[createdAt] = now
            it[updatedAt] = now
        }
        Chat(newId, userId, title, now, now)
    }

    override fun findById(id: UUID): Chat? = transaction {
        ChatsTable.selectAll()
            .where { ChatsTable.id eq id }
            .firstOrNull()
            ?.toChat()
    }

    override fun listByUser(userId: UUID): List<Chat> = transaction {
        ChatsTable.selectAll()
            .where { ChatsTable.userId eq userId }
            .orderBy(ChatsTable.updatedAt to SortOrder.DESC)
            .map { it.toChat() }
    }

    override fun delete(id: UUID): Boolean = transaction {
        MessagesTable.deleteWhere { chatId eq id }
        ChatsTable.deleteWhere { ChatsTable.id eq id } > 0
    }

    override fun touch(id: UUID) {
        transaction {
            ChatsTable.update({ ChatsTable.id eq id }) {
                it[updatedAt] = Instant.now()
            }
        }
    }

    private fun ResultRow.toChat() = Chat(
        id = this[ChatsTable.id],
        userId = this[ChatsTable.userId],
        title = this[ChatsTable.title],
        createdAt = this[ChatsTable.createdAt],
        updatedAt = this[ChatsTable.updatedAt]
    )
}

class ExposedMessageRepository : MessageRepository {

    override fun add(chatId: UUID, role: MessageRole, content: String, tokensUsed: Int): Message = transaction {
        val newId = UUID.randomUUID()
        val now = Instant.now()
        MessagesTable.insert {
            it[id] = newId
            it[MessagesTable.chatId] = chatId
            it[MessagesTable.role] = role.name.lowercase()
            it[MessagesTable.content] = content
            it[MessagesTable.tokensUsed] = tokensUsed
            it[createdAt] = now
        }
        Message(newId, chatId, role, content, tokensUsed, now)
    }

    override fun listByChat(chatId: UUID): List<Message> = transaction {
        MessagesTable.selectAll()
            .where { MessagesTable.chatId eq chatId }
            .orderBy(MessagesTable.createdAt to SortOrder.ASC)
            .map { row ->
                Message(
                    id = row[MessagesTable.id],
                    chatId = row[MessagesTable.chatId],
                    role = MessageRole.valueOf(row[MessagesTable.role].uppercase()),
                    content = row[MessagesTable.content],
                    tokensUsed = row[MessagesTable.tokensUsed],
                    createdAt = row[MessagesTable.createdAt]
                )
            }
    }

    override fun deleteByChat(chatId: UUID) {
        transaction {
            MessagesTable.deleteWhere { MessagesTable.chatId eq chatId }
        }
    }
}
