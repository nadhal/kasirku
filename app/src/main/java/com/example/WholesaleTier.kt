package com.example

import kotlinx.serialization.Serializable

@Serializable
data class WholesaleTier(
    val minQuantity: Int,
    val price: Double
)
