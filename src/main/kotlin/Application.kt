package com.example

import com.example.auth.AuthRateLimiter
import com.example.auth.EmailVerificationService
import com.example.auth.ExposedRefreshTokenRepository
import com.example.auth.InMemoryRefreshTokenRepository
import com.example.auth.MailServiceClient
import com.example.auth.RefreshTokenRepository
import com.example.auth.TokenService
import com.example.chats.ChatRepository
import com.example.chats.ChatTitleService
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
import com.example.subscriptions.GooglePlayVerifier
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
    val refreshes: RefreshTokenRepository
    val healthCheck: suspend () -> Boolean

    if (config.useInMemoryDb) {
        users = InMemoryUserRepository()
        chats = InMemoryChatRepository()
        messages = InMemoryMessageRepository()
        subs = InMemorySubscriptionRepository()
        refreshes = InMemoryRefreshTokenRepository()
        healthCheck = { true }
    } else {
        DatabaseFactory.init(config.db)
        users = ExposedUserRepository()
        chats = ExposedChatRepository()
        messages = ExposedMessageRepository()
        subs = ExposedSubscriptionRepository()
        refreshes = ExposedRefreshTokenRepository()
        healthCheck = { DatabaseFactory.isHealthy() }
    }

    val tokens = TokenService(config.jwt, refreshes)
    val rateLimiter = AuthRateLimiter()
    val llm: LlmClient = when (config.llm.provider) {
        "openai", "groq" -> {
            if (config.llm.apiKey.isBlank()) {
                log.warn("LLM_PROVIDER=${config.llm.provider}, но LLM_API_KEY пустой — откатываюсь на mock")
                MockLlmClient()
            } else {
                log.info("LLM provider=${config.llm.provider}, baseUrl=${config.llm.baseUrl}, model=${config.llm.model}")
                GroqLlmClient(config.llm)
            }
        }
        else -> {
            log.info("LLM provider=mock")
            MockLlmClient()
        }
    }

    val verifier = GooglePlayVerifier(config.billing.packageName, config.billing.credentialsPath)
    val mailClient = MailServiceClient(config.mail)
    val emailVerification = EmailVerificationService(config.mail, mailClient)

    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureCors()
    configureSecurity(config.jwt)
    val titleService = ChatTitleService(llm, chats, log)

    configureRouting(users, tokens, refreshes, rateLimiter, chats, messages, llm, subs, titleService, verifier, emailVerification, healthCheck)
}
