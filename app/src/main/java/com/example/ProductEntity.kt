package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val costPrice: Double = 0.0,
    val iconName: String,
    val category: String,
    val stock: Int = 0,
    val barcode: String? = null,
    val wholesaleTiersJson: String? = null
)
