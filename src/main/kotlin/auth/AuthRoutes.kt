package com.example.auth

import com.example.plugins.ErrorResponse
import com.example.users.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(users: UserRepository, tokens: TokenService) {
    route("/auth") {
        post("/register") {
            val req = call.receive<AuthRequest>()
            val email = req.email.trim()
            val password = req.password

            if (!isEmailValid(email)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_EMAIL", "email is not valid"))
                return@post
            }
            if (password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_PASSWORD", "password is too short"))
                return@post
            }

            val existing = users.findByEmail(email)
            if (existing != null) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("EMAIL_TAKEN", "email already registered"))
                return@post
            }

            val user = users.create(email, Passwords.hash(password))
            val token = tokens.issue(user).token
            call.respond(HttpStatusCode.Created, AuthResponse(token))
        }

        post("/login") {
            val req = call.receive<AuthRequest>()
            val email = req.email.trim().lowercase()
            val user = users.findByEmail(email)
            if (user == null || !Passwords.verify(req.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("BAD_CREDENTIALS", "wrong email or password"))
                return@post
            }
            val token = tokens.issue(user).token
            call.respond(AuthResponse(token))
        }
    }
}

private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

private fun isEmailValid(email: String): Boolean = emailRegex.matches(email)
