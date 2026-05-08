package com.example

import com.example.config.loadConfig
import com.example.plugins.configureCors
import com.example.plugins.configureMonitoring
import com.example.plugins.configureSerialization
import com.example.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.log

fun Application.module() {
    val config = loadConfig()
    log.info("starting okak backend, jwt issuer=${config.jwt.issuer}")

    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureCors()
    configureRouting()
}
