package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val totalAmount: Double,
    val discountAmount: Double,
    val taxAmount: Double,
    val paymentMethod: String = "Tunai",
    val itemsJson: String // Serialized list of items
)

@Serializable
data class SaleItem(
    val productId: String,
    val productName: String,
    val price: Double,
    val costPrice: Double = 0.0,
    val quantity: Int
)
