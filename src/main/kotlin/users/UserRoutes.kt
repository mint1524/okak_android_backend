package com.example.users

import com.example.plugins.ErrorResponse
import com.example.plugins.JWT_AUTH_NAME
import com.example.subscriptions.SubscriptionRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val subscriptionStatus: String
)

fun Route.userRoutes(users: UserRepository, subs: SubscriptionRepository) {
    authenticate(JWT_AUTH_NAME) {
        route("/user") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "no token"))
                    return@get
                }
                val uuid = UUID.fromString(userId)
                val user = users.findById(uuid)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("USER_NOT_FOUND", "user not found"))
                    return@get
                }
                val status = if (subs.findActive(uuid) != null) "active" else "inactive"
                call.respond(UserDto(user.id.toString(), user.email, status))
            }
        }
    }
}
