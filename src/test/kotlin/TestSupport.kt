package com.example

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder

fun ApplicationTestBuilder.withInMemoryDb() {
    environment {
        config = MapApplicationConfig("app.useInMemoryDb" to "true")
    }
}
