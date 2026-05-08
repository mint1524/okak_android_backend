package com.example

import com.example.plugins.configureCors
import com.example.plugins.configureMonitoring
import com.example.plugins.configureSerialization
import com.example.plugins.configureStatusPages
import io.ktor.server.application.Application

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureCors()
    configureRouting()
}
