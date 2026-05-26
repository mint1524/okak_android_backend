package com.example.auth

import com.example.plugins.ErrorResponse
import com.example.users.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(
    users: UserRepository,
    tokens: TokenService,
    refreshRepo: RefreshTokenRepository,
    rateLimiter: AuthRateLimiter,
    emailVerification: EmailVerificationService
) {
    route("/auth") {
        post("/register/code") {
            if (rateLimiter.deny(call.remoteAddress(), "register-code")) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("RATE_LIMITED", "too many requests"))
                return@post
            }
            val req = call.receive<EmailCodeRequest>()
            val email = req.email.trim().lowercase()

            if (!isEmailValid(email)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_EMAIL", "email is not valid"))
                return@post
            }
            if (users.findByEmail(email) != null) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("EMAIL_TAKEN", "email already registered"))
                return@post
            }

            val result = emailVerification.issue(email)
            if (!result.sent && emailVerification.verificationRequired && result.debugCode == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("MAIL_UNAVAILABLE", "email service unavailable"))
                return@post
            }

            call.respond(
                EmailCodeResponse(
                    sent = result.sent,
                    expiresInMinutes = result.expiresInMinutes,
                    debugCode = result.debugCode
                )
            )
        }

        post("/register") {
            if (rateLimiter.deny(call.remoteAddress(), "register")) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("RATE_LIMITED", "too many requests"))
                return@post
            }
            val req = call.receive<AuthRequest>()
            val email = req.email.trim().lowercase()
            val password = req.password

            if (!isEmailValid(email)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_EMAIL", "email is not valid"))
                return@post
            }
            if (password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_PASSWORD", "password is too short"))
                return@post
            }
            if (password.length > 200) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_PASSWORD", "password is too long"))
                return@post
            }

            val existing = users.findByEmail(email)
            if (existing != null) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("EMAIL_TAKEN", "email already registered"))
                return@post
            }
            if (!emailVerification.verify(email, req.verificationCode)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_VERIFICATION_CODE", "verification code is invalid or expired"))
                return@post
            }

            val user = users.create(email, Passwords.hash(password))
            val pair = tokens.issuePair(user)
            call.respond(HttpStatusCode.Created, pair.toResponse())
        }

        post("/login") {
            if (rateLimiter.deny(call.remoteAddress(), "login")) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("RATE_LIMITED", "too many requests"))
                return@post
            }
            val req = call.receive<AuthRequest>()
            val email = req.email.trim().lowercase()
            val user = users.findByEmail(email)
            if (user == null || !Passwords.verify(req.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("BAD_CREDENTIALS", "wrong email or password"))
                return@post
            }
            refreshRepo.revokeAllForUser(user.id)
            val pair = tokens.issuePair(user)
            call.respond(pair.toResponse())
        }

        post("/refresh") {
            val req = call.receive<RefreshRequest>()
            val record = refreshRepo.findActiveByRawToken(req.refreshToken)
            if (record == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("BAD_REFRESH", "refresh token invalid or expired"))
                return@post
            }
            val user = users.findById(record.userId)
            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("BAD_REFRESH", "user not found"))
                return@post
            }
            val pair = tokens.rotate(user, record)
            call.respond(pair.toResponse())
        }
    }
}

private fun TokenPair.toResponse() = AuthResponse(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = accessExpiresAt.toString()
)

private fun io.ktor.server.application.ApplicationCall.remoteAddress(): String =
    request.local.remoteAddress

private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

private fun isEmailValid(email: String): Boolean = emailRegex.matches(email)
