package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey
    val id: String, // e.g. "ORD-1403-8821"
    val customerName: String,
    val customerPhone: String,
    val address: String,
    val notes: String = "",
    val latitude: Double,
    val longitude: Double,
    val orderType: String, // "PICKUP" or "DELIVERY"
    // Uses com.example.data.model.OrderStatus values (shared vocabulary with the web panel API):
    // ASSIGNED, COLLECTED, DELIVERED_TO_WORKSHOP, WASHING, READY_FOR_DELIVERY,
    // DELIVERED_SETTLED, RETURNED_TO_CLEAN_WAREHOUSE, OFFICE_SETTLED, CANCELLED
    val status: String,
    val totalAmount: Long = 0L,
    val discountAmount: Long = 0L,
    val paidAmount: Long = 0L,
    val paymentMethod: String = "PENDING", // "CASH", "POS", "CREDIT", "PENDING"
    val rackCode: String = "", // e.g. "A-01"
    val routeOrder: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)
