package com.example

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthTest {

    @Test
    fun `register and login flow`() = testApplication {
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
}
