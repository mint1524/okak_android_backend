package com.example

import com.example.auth.TokenService
import com.example.config.loadConfig
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

    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureCors()
    configureSecurity(config.jwt)
    configureRouting(users, tokens)
}
