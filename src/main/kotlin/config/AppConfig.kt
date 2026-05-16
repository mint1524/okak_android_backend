package com.example.config

import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val expiresInMinutes: Long
)

data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
    val driver: String
)

data class LlmConfig(
    val provider: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val systemPrompt: String,
    val maxTokens: Int,
    val temperature: Double
)

data class AppConfig(
    val jwt: JwtConfig,
    val db: DbConfig,
    val llm: LlmConfig,
    val useInMemoryDb: Boolean
)

fun Application.loadConfig(): AppConfig {
    val cfg = environment.config
    return AppConfig(
        jwt = JwtConfig(
            secret = cfg.stringOrEnv("jwt.secret", "JWT_SECRET", "change-me-please"),
            issuer = cfg.stringOrEnv("jwt.issuer", "JWT_ISSUER", "okak"),
            audience = cfg.stringOrEnv("jwt.audience", "JWT_AUDIENCE", "okak-users"),
            realm = cfg.stringOrEnv("jwt.realm", "JWT_REALM", "okak"),
            expiresInMinutes = cfg.stringOrEnv("jwt.expiresInMinutes", "JWT_EXPIRES_MIN", "60").toLong()
        ),
        db = DbConfig(
            url = cfg.stringOrEnv("db.url", "DB_URL", "jdbc:postgresql://localhost:5432/okak"),
            user = cfg.stringOrEnv("db.user", "DB_USER", "okak"),
            password = cfg.stringOrEnv("db.password", "DB_PASSWORD", "okak"),
            driver = cfg.stringOrEnv("db.driver", "DB_DRIVER", "org.postgresql.Driver")
        ),
        llm = LlmConfig(
            provider = cfg.stringOrEnv("llm.provider", "LLM_PROVIDER", "mock").lowercase(),
            apiKey = cfg.stringOrEnv("llm.apiKey", "LLM_API_KEY", ""),
            baseUrl = cfg.stringOrEnv("llm.baseUrl", "LLM_BASE_URL", "https://api.groq.com/openai/v1"),
            model = cfg.stringOrEnv("llm.model", "LLM_MODEL", "llama-3.3-70b-versatile"),
            systemPrompt = cfg.stringOrEnv("llm.systemPrompt", "LLM_SYSTEM_PROMPT", "Ты дружелюбный ассистент. Отвечай по-русски, кратко и по делу."),
            maxTokens = cfg.stringOrEnv("llm.maxTokens", "LLM_MAX_TOKENS", "512").toInt(),
            temperature = cfg.stringOrEnv("llm.temperature", "LLM_TEMPERATURE", "0.7").toDouble()
        ),
        useInMemoryDb = cfg.stringOrEnv("app.useInMemoryDb", "USE_IN_MEMORY_DB", "false").toBoolean()
    )
}

private fun ApplicationConfig.stringOrEnv(path: String, env: String, default: String): String {
    val fromEnv = System.getenv(env)
    if (!fromEnv.isNullOrBlank()) return fromEnv
    return propertyOrNull(path)?.getString() ?: default
}
