package com.tiffles.blockgains.domain.model

/** Shared Summary/Transactions filter. ALL is the real tax number: crypto and stocks net together (R11). */
enum class AssetFilter {
    ALL,
    CRYPTO,
    STOCK;

    fun matches(assetClass: AssetClass): Boolean = when (this) {
        ALL -> true
        CRYPTO -> assetClass == AssetClass.CRYPTO
        STOCK -> assetClass == AssetClass.STOCK
    }
}
