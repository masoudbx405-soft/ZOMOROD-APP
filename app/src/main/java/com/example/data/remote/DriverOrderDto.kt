package com.example.data.remote

import com.example.data.local.entities.CarpetItemEntity
import com.example.data.local.entities.OrderEntity
import com.example.data.local.model.OrderWithItems
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Shared cross-app order/carpet-item contract (step 2 of the app<->panel
 * coordination plan). This is the EXACT JSON shape the web panel's
 * `DriverOrderDTO` / `DriverCarpetItemDTO` (in src/types.ts) produce and
 * expect — field names, casing and types must stay identical on both sides.
 *
 * Never send a raw OrderEntity/CarpetItemEntity over the network — always
 * convert through the mapper functions below. Field-name differences these
 * DTOs resolve (vs. the Room entities):
 *   - requestedServices / defects are arrays here (Room stores them as
 *     comma-joined strings: requestedServicesJson / defectsJson)
 */
@JsonClass(generateAdapter = true)
data class DriverCarpetItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "carpetType") val carpetType: String,
    @Json(name = "lengthMeter") val lengthMeter: Double,
    @Json(name = "widthMeter") val widthMeter: Double,
    @Json(name = "areaSqMeter") val areaSqMeter: Double,
    @Json(name = "unitPricePerMeter") val unitPricePerMeter: Long,
    @Json(name = "totalPrice") val totalPrice: Long,
    @Json(name = "requestedServices") val requestedServices: List<String> = emptyList(),
    @Json(name = "defects") val defects: List<String> = emptyList(),
    @Json(name = "notes") val notes: String = "",
    @Json(name = "barcodeTag") val barcodeTag: String = "",
    @Json(name = "rackLocation") val rackLocation: String = ""
)

@JsonClass(generateAdapter = true)
data class DriverOrderDto(
    @Json(name = "id") val id: String,
    @Json(name = "customerName") val customerName: String,
    @Json(name = "customerPhone") val customerPhone: String,
    @Json(name = "address") val address: String,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "orderType") val orderType: String,
    @Json(name = "status") val status: String,
    @Json(name = "totalAmount") val totalAmount: Long = 0L,
    @Json(name = "prepaidAmount") val prepaidAmount: Long = 0L,
    @Json(name = "remainingAmount") val remainingAmount: Long = 0L,
    @Json(name = "paymentMethod") val paymentMethod: String = "PENDING",
    @Json(name = "rackCode") val rackCode: String = "",
    @Json(name = "notes") val notes: String = "",
    @Json(name = "carpets") val carpets: List<DriverCarpetItemDto> = emptyList()
)

/** Convert a DTO received from the panel's API into local Room entities. */
fun DriverOrderDto.toEntities(existingRouteOrder: Int = 1, isSynced: Boolean = true): Pair<OrderEntity, List<CarpetItemEntity>> {
    val order = OrderEntity(
        id = id,
        customerName = customerName,
        customerPhone = customerPhone,
        address = address,
        notes = notes,
        latitude = latitude,
        longitude = longitude,
        orderType = orderType,
        status = status,
        totalAmount = totalAmount,
        paidAmount = prepaidAmount,
        paymentMethod = paymentMethod,
        rackCode = rackCode,
        routeOrder = existingRouteOrder,
        isSynced = isSynced
    )
    val items = carpets.map { c ->
        CarpetItemEntity(
            orderId = id,
            carpetType = c.carpetType,
            lengthMeter = c.lengthMeter,
            widthMeter = c.widthMeter,
            areaSqMeter = c.areaSqMeter,
            unitPricePerMeter = c.unitPricePerMeter,
            requestedServicesJson = c.requestedServices.joinToString("، "),
            defectsJson = if (c.defects.isEmpty()) "بدون عیب" else c.defects.joinToString("، "),
            totalPrice = c.totalPrice,
            notes = c.notes,
            barcodeTag = c.barcodeTag
        )
    }
    return order to items
}

/** Convert a local order (with its carpet items) into the DTO sent to the panel's API. */
fun OrderWithItems.toDriverOrderDto(): DriverOrderDto {
    return DriverOrderDto(
        id = order.id,
        customerName = order.customerName,
        customerPhone = order.customerPhone,
        address = order.address,
        latitude = order.latitude,
        longitude = order.longitude,
        orderType = order.orderType,
        status = order.status,
        totalAmount = order.totalAmount,
        prepaidAmount = order.paidAmount,
        remainingAmount = order.totalAmount - order.discountAmount - order.paidAmount,
        paymentMethod = order.paymentMethod,
        rackCode = order.rackCode,
        notes = order.notes,
        carpets = items.map { c ->
            DriverCarpetItemDto(
                id = c.id.toString(),
                carpetType = c.carpetType,
                lengthMeter = c.lengthMeter,
                widthMeter = c.widthMeter,
                areaSqMeter = c.areaSqMeter,
                unitPricePerMeter = c.unitPricePerMeter,
                totalPrice = c.totalPrice,
                requestedServices = c.requestedServicesJson.split("، ").filter { it.isNotBlank() },
                defects = if (c.defectsJson == "بدون عیب") emptyList() else c.defectsJson.split("، ").filter { it.isNotBlank() },
                notes = c.notes,
                barcodeTag = c.barcodeTag,
                rackLocation = order.rackCode
            )
        }
    )
}
