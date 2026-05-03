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
            requestLimit = 100,
            tokenLimit = 100_000,
            modelName = "llm-basic",
            productId = "basic_monthly"
        ),
        Plan(
            id = "pro",
            name = "Pro",
            price = 599.0,
            requestLimit = 500,
            tokenLimit = 500_000,
            modelName = "llm-pro",
            productId = "pro_monthly"
        )
    )

    fun byId(id: String): Plan? = all.firstOrNull { it.id == id }
    fun byProductId(productId: String): Plan? = all.firstOrNull { it.productId == productId }
}
