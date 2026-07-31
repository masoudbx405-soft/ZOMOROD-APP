package com.example.data.model

/**
 * Canonical order status vocabulary — single source of truth on the Android side.
 *
 * This is the SAME set of values the web panel's API contract must use
 * (see step 1 of the app<->panel coordination review). Never write a raw
 * status string literal elsewhere in the app; always reference OrderStatus.X
 * so the whole codebase (and the backend contract) stays in sync.
 *
 * NOTE: `COLLECTED_IN_INSPECTION` was renamed to `COLLECTED` to match the
 * web panel's vocabulary exactly (previously the two projects used different
 * names for the same step).
 */
object OrderStatus {
    const val ASSIGNED = "ASSIGNED"
    const val COLLECTED = "COLLECTED"
    const val DELIVERED_TO_WORKSHOP = "DELIVERED_TO_WORKSHOP"
    const val WASHING = "WASHING"
    const val READY_FOR_DELIVERY = "READY_FOR_DELIVERY"
    const val DELIVERED_SETTLED = "DELIVERED_SETTLED"
    const val RETURNED_TO_CLEAN_WAREHOUSE = "RETURNED_TO_CLEAN_WAREHOUSE"
    const val OFFICE_SETTLED = "OFFICE_SETTLED"
    const val CANCELLED = "CANCELLED"
}
