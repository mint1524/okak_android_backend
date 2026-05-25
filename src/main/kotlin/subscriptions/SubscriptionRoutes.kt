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

fun Route.subscriptionRoutes(subs: SubscriptionRepository, verifier: GooglePlayVerifier) {

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
                val activeSub = subs.findActive(userId)
                if (activeSub != null) {
                    val plan = Plans.byId(activeSub.planId)
                    call.respond(
                        SubscriptionStatusDto(
                            status = "active",
                            plan = plan?.name,
                            expiresAt = activeSub.expiresAt.toString(),
                            requestLimit = plan?.requestLimit,
                            requestsUsed = activeSub.requestsUsed,
                            tokenLimit = plan?.tokenLimit,
                            tokensUsed = activeSub.tokensUsed
                        )
                    )
                    return@get
                }
                val anySub = subs.findByUser(userId)
                if (anySub == null) {
                    call.respond(SubscriptionStatusDto("inactive", null, null, null, null, null, null))
                    return@get
                }
                val statusStr = when (anySub.status) {
                    Subscription.Status.EXPIRED -> "expired"
                    Subscription.Status.CANCELLED -> "cancelled"
                    else -> "inactive"
                }
                val plan = Plans.byId(anySub.planId)
                call.respond(
                    SubscriptionStatusDto(
                        status = statusStr,
                        plan = plan?.name,
                        expiresAt = anySub.expiresAt.toString(),
                        requestLimit = plan?.requestLimit,
                        requestsUsed = anySub.requestsUsed,
                        tokenLimit = plan?.tokenLimit,
                        tokensUsed = anySub.tokensUsed
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
                if (req.purchaseToken.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("BAD_TOKEN", "empty purchase token"))
                    return@post
                }
                val verification = verifier.verifySubscription(req.productId, req.purchaseToken)
                if (verification is GooglePlayVerifier.VerificationResult.Invalid) {
                    call.respond(HttpStatusCode.PaymentRequired, ErrorResponse("INVALID_PURCHASE", verification.reason))
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
