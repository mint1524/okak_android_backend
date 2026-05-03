package com.example.subscriptions

import com.example.chats.userId
import com.example.plugins.ErrorResponse
import com.example.plugins.JWT_AUTH_NAME
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionStatusDto(
    val status: String,
    val plan: String?,
    val expiresAt: String?,
    val requestLimit: Int?,
    val requestsUsed: Int?,
    val tokenLimit: Int?,
    val tokensUsed: Int?
)

@Serializable
data class VerifyRequest(
    val productId: String,
    val purchaseToken: String
)

fun Route.subscriptionRoutes(subs: SubscriptionRepository) {

    route("/subscriptions") {
        get("/plans") {
            call.respond(Plans.all)
        }

        authenticate(JWT_AUTH_NAME) {
            get("/status") {
                val userId = call.userId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "no token"))
                    return@get
                }
                val sub = subs.findActive(userId)
                if (sub == null) {
                    call.respond(SubscriptionStatusDto("inactive", null, null, null, null, null, null))
                    return@get
                }
                val plan = Plans.byId(sub.planId)
                call.respond(
                    SubscriptionStatusDto(
                        status = "active",
                        plan = plan?.name,
                        expiresAt = sub.expiresAt.toString(),
                        requestLimit = plan?.requestLimit,
                        requestsUsed = sub.requestsUsed,
                        tokenLimit = plan?.tokenLimit,
                        tokensUsed = sub.tokensUsed
                    )
                )
            }

            post("/verify") {
                val userId = call.userId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "no token"))
                    return@post
                }
                val req = call.receive<VerifyRequest>()
                val plan = Plans.byProductId(req.productId)
                if (plan == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("UNKNOWN_PRODUCT", "unknown productId"))
                    return@post
                }
                // TODO: настоящая проверка через Google Play Developer API
                if (req.purchaseToken.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_TOKEN", "empty purchase token"))
                    return@post
                }
                val sub = subs.upsertFromPurchase(userId, plan, req.purchaseToken)
                call.respond(
                    SubscriptionStatusDto(
                        status = "active",
                        plan = plan.name,
                        expiresAt = sub.expiresAt.toString(),
                        requestLimit = plan.requestLimit,
                        requestsUsed = 0,
                        tokenLimit = plan.tokenLimit,
                        tokensUsed = 0
                    )
                )
            }
        }
    }
}
