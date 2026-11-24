package com.tiffles.blockgains.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose routes. Only primitives are passed; destinations fetch their own data. */
sealed interface Route {
    // Bottom-navigation destinations
    @Serializable data object Summary : Route

    /** [term] / [type] are enum names used to pre-filter when opened from a Summary card. */
    @Serializable data class Transactions(
        val showAll: Boolean = false,
        val term: String? = null,
        val type: String? = null,
    ) : Route

    @Serializable data object ImportExport : Route

    // Secondary screens (no bottom bar)
    @Serializable data object Settings : Route
    @Serializable data object Issues : Route
    @Serializable data object ImportPreview : Route
    @Serializable data object OpeningPositions : Route
    @Serializable data class EventDetail(val transactionId: Long) : Route
    @Serializable data class TransactionDetail(val transactionId: Long) : Route
}
