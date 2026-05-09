package com.example

import com.example.auth.TokenService
import com.example.chats.ChatRepository
import com.example.chats.ExposedChatRepository
import com.example.chats.ExposedMessageRepository
import com.example.chats.InMemoryChatRepository
import com.example.chats.InMemoryMessageRepository
import com.example.chats.MessageRepository
import com.example.config.loadConfig
import com.example.database.DatabaseFactory
import com.example.llm.GroqLlmClient
import com.example.llm.LlmClient
import com.example.llm.MockLlmClient
import com.example.plugins.configureCors
import com.example.plugins.configureMonitoring
import com.example.plugins.configureSecurity
import com.example.plugins.configureSerialization
import com.example.plugins.configureStatusPages
import com.example.subscriptions.ExposedSubscriptionRepository
import com.example.subscriptions.InMemorySubscriptionRepository
import com.example.subscriptions.SubscriptionRepository
import com.example.users.ExposedUserRepository
import com.example.users.InMemoryUserRepository
import com.example.users.UserRepository
import io.ktor.server.application.Application
import io.ktor.server.application.log

fun Application.module() {
    val config = loadConfig()
    log.info("starting okak backend, jwt issuer=${config.jwt.issuer}, inMemoryDb=${config.useInMemoryDb}")

    val users: UserRepository
    val chats: ChatRepository
    val messages: MessageRepository
    val subs: SubscriptionRepository

    if (config.useInMemoryDb) {
        users = InMemoryUserRepository()
        chats = InMemoryChatRepository()
        messages = InMemoryMessageRepository()
        subs = InMemorySubscriptionRepository()
    } else {
        DatabaseFactory.init(config.db)
        users = ExposedUserRepository()
        chats = ExposedChatRepository()
        messages = ExposedMessageRepository()
        subs = ExposedSubscriptionRepository()
    }

    val tokens = TokenService(config.jwt)
    val llm: LlmClient = when (config.llm.provider) {
        "groq" -> {
            if (config.llm.apiKey.isBlank()) {
                log.warn("LLM_PROVIDER=groq, но LLM_API_KEY пустой — откатываюсь на mock")
                MockLlmClient()
            } else {
                log.info("LLM provider=groq, model=${config.llm.model}")
                GroqLlmClient(config.llm)
            }
        }
        else -> {
            log.info("LLM provider=mock")
            MockLlmClient()
        }
    }

    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureCors()
    configureSecurity(config.jwt)
    configureRouting(users, tokens, chats, messages, llm, subs)
}
