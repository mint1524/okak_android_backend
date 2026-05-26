package com.example

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthTest {

    @Test
    fun `register and login flow`() = testApplication {
        withInMemoryDb()
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val regResp = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"qwerty123"}""")
        }
        assertEquals(HttpStatusCode.Created, regResp.status)
        assertTrue(regResp.bodyAsText().contains("accessToken"))

        val dup = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"qwerty123"}""")
        }
        assertEquals(HttpStatusCode.Conflict, dup.status)

        val login = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"qwerty123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)

        val badLogin = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"wrong"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, badLogin.status)

        val badEmail = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"notemail","password":"qwerty123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badEmail.status)
    }

    @Test
    fun `me endpoint returns user when token is provided`() = testApplication {
        withInMemoryDb()
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val regResp = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"me@example.com","password":"qwerty123"}""")
        }
        val token = Json.parseToJsonElement(regResp.bodyAsText())
            .let { (it as kotlinx.serialization.json.JsonObject)["accessToken"]!!.jsonPrimitive.content }

        val meResp = client.get("/user/me") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, meResp.status)
        assertTrue(meResp.bodyAsText().contains("me@example.com"))

        val noToken = client.get("/user/me")
        assertEquals(HttpStatusCode.Unauthorized, noToken.status)
    }

    @Test
    fun `register can require email verification code`() = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig(
                "app.useInMemoryDb" to "true",
                "mail.verificationRequired" to "true",
                "mail.devReturnCode" to "true"
            )
        }
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val codeResp = client.post("/auth/register/code") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"coded@example.com"}""")
        }
        assertEquals(HttpStatusCode.OK, codeResp.status)
        val code = Json.parseToJsonElement(codeResp.bodyAsText())
            .let { (it as kotlinx.serialization.json.JsonObject)["debugCode"]!!.jsonPrimitive.content }

        val noCode = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"coded@example.com","password":"qwerty123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, noCode.status)

        val regResp = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"coded@example.com","password":"qwerty123","verificationCode":"$code"}""")
        }
        assertEquals(HttpStatusCode.Created, regResp.status)
    }
}
