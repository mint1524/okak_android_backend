package com.example.subscriptions

import kotlinx.serialization.Serializable

@Serializable
data class Plan(
    val id: String,
    val name: String,
    val price: Double,
    val requestLimit: Int,
    val tokenLimit: Int,
    val modelName: String,
    val productId: String
)

object Plans {
    val all: List<Plan> = listOf(
        Plan(
            id = "basic",
            name = "Basic",
            price = 299.0,
            requestLimit = 200,
            tokenLimit = 200_000,
            modelName = "llama-3.1-8b-instant",
            productId = "basic_monthly"
        ),
        Plan(
            id = "pro",
            name = "Pro",
            price = 699.0,
            requestLimit = 1000,
            tokenLimit = 1_000_000,
            modelName = "llama-3.3-70b-versatile",
            productId = "pro_monthly"
        )
    )

    fun byId(id: String): Plan? = all.firstOrNull { it.id == id }
    fun byProductId(productId: String): Plan? = all.firstOrNull { it.productId == productId }
}
