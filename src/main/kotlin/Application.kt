package com.example

import com.example.auth.TokenService
import com.example.chats.InMemoryChatRepository
import com.example.chats.InMemoryMessageRepository
import com.example.config.loadConfig
import com.example.llm.MockLlmClient
import com.example.subscriptions.InMemorySubscriptionRepository
import com.example.plugins.configureCors
import com.example.plugins.configureMonitoring
import com.example.plugins.configureSecurity
import com.example.plugins.configureSerialization
import com.example.plugins.configureStatusPages
import com.example.users.InMemoryUserRepository
import io.ktor.server.application.Application
import io.ktor.server.application.log

fun Application.module() {
    val config = loadConfig()
    log.info("starting okak backend, jwt issuer=${config.jwt.issuer}")

    val users = InMemoryUserRepository()
    val tokens = TokenService(config.jwt)
    val chats = InMemoryChatRepository()
    val messages = InMemoryMessageRepository()
    val llm = MockLlmClient()
    val subs = InMemorySubscriptionRepository()

    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureCors()
    configureSecurity(config.jwt)
    configureRouting(users, tokens, chats, messages, llm, subs)
}
