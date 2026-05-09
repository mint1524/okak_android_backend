package com.example

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatTest {

    @Test
    fun `chat lifecycle and llm reply`() = testApplication {
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val reg = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"chat@example.com","password":"qwerty123"}""")
        }
        val token = (Json.parseToJsonElement(reg.bodyAsText()) as JsonObject)["accessToken"]!!.jsonPrimitive.content

        val createResp = client.post("/chats") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("""{"title":"hello"}""")
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val chatId = (Json.parseToJsonElement(createResp.bodyAsText()) as JsonObject)["id"]!!.jsonPrimitive.content

        val list = client.get("/chats") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, list.status)
        val arr = Json.parseToJsonElement(list.bodyAsText()).jsonArray
        assertEquals(1, arr.size)

        val noSub = client.post("/chats/$chatId/messages") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("""{"content":"привет"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, noSub.status)

        val verify = client.post("/subscriptions/verify") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("""{"productId":"basic_monthly","purchaseToken":"fake-token"}""")
        }
        assertEquals(HttpStatusCode.OK, verify.status)

        val send = client.post("/chats/$chatId/messages") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody("""{"content":"привет"}""")
        }
        assertEquals(HttpStatusCode.OK, send.status)
        assertTrue(send.bodyAsText().contains("assistantMessage"))

        val msgs = client.get("/chats/$chatId/messages") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, msgs.status)
        assertEquals(2, Json.parseToJsonElement(msgs.bodyAsText()).jsonArray.size)

        val del = client.delete("/chats/$chatId") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, del.status)

        val listAfter = client.get("/chats") { bearerAuth(token) }
        assertEquals(0, Json.parseToJsonElement(listAfter.bodyAsText()).jsonArray.size)
    }

    @Test
    fun `cannot access another users chat`() = testApplication {
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val tokenA = registerAndGetToken(client, "a@example.com")
        val tokenB = registerAndGetToken(client, "b@example.com")

        val createResp = client.post("/chats") {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenA)
            setBody("""{"title":"private"}""")
        }
        val chatId = (Json.parseToJsonElement(createResp.bodyAsText()) as JsonObject)["id"]!!.jsonPrimitive.content

        val resp = client.get("/chats/$chatId/messages") { bearerAuth(tokenB) }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    private suspend fun registerAndGetToken(client: io.ktor.client.HttpClient, email: String): String {
        val resp = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"qwerty123"}""")
        }
        return (Json.parseToJsonElement(resp.bodyAsText()) as JsonObject)["accessToken"]!!.jsonPrimitive.content
    }
}
