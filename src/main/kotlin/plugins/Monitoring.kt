package com.example.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(DefaultHeaders)
    install(CallLogging) {
        level = Level.INFO
    }
}
