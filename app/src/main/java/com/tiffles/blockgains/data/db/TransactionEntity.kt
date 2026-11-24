package com.tiffles.blockgains.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/** One row of the unified CSV. BigDecimal and LocalDate are stored as text (see [Converters]). */
@Entity(tableName = "transactions", indices = [Index("date"), Index("asset")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val date: LocalDate,
    val asset: String,
    @ColumnInfo(name = "asset_class") val assetClass: AssetClass,
    val wallet: String,
    val quantity: BigDecimal,
    @ColumnInfo(name = "price_usd") val priceUsd: BigDecimal?,
    @ColumnInfo(name = "total_usd") val totalUsd: BigDecimal?,
    @ColumnInfo(name = "fee_usd") val feeUsd: BigDecimal?,
    @ColumnInfo(name = "fee_asset") val feeAsset: String? = null,
    @ColumnInfo(name = "fee_quantity") val feeQuantity: BigDecimal? = null,
    @ColumnInfo(name = "to_asset") val toAsset: String?,
    @ColumnInfo(name = "to_quantity") val toQuantity: BigDecimal?,
    @ColumnInfo(name = "to_wallet") val toWallet: String?,
    @ColumnInfo(name = "acquired_date") val acquiredDate: LocalDate?,
    @ColumnInfo(name = "basis_unknown") val basisUnknown: Boolean,
    val note: String?,
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    type = type,
    date = date,
    asset = asset,
    assetClass = assetClass,
    wallet = wallet,
    quantity = quantity,
    priceUsd = priceUsd,
    totalUsd = totalUsd,
    feeUsd = feeUsd,
    feeAsset = feeAsset,
    feeQuantity = feeQuantity,
    toAsset = toAsset,
    toQuantity = toQuantity,
    toWallet = toWallet,
    acquiredDate = acquiredDate,
    basisUnknown = basisUnknown,
    note = note,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type,
    date = date,
    asset = asset,
    assetClass = assetClass,
    wallet = wallet,
    quantity = quantity,
    priceUsd = priceUsd,
    totalUsd = totalUsd,
    feeUsd = feeUsd,
    feeAsset = feeAsset,
    feeQuantity = feeQuantity,
    toAsset = toAsset,
    toQuantity = toQuantity,
    toWallet = toWallet,
    acquiredDate = acquiredDate,
    basisUnknown = basisUnknown,
    note = note,
)
