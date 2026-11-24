package com.tiffles.blockgains.data.export

import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.ThemeMode
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Full-fidelity JSON backup: every record plus settings. Numbers are strings so precision
 * survives (never JSON numbers, which many tools read as doubles).
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String,
    val settings: BackupSettings,
    val transactions: List<BackupTransaction>,
) {
    companion object {
        const val FORMAT = "blockgains-backup"
        /** 2 (`1.7.0`): `feeAsset` / `feeQuantity` (R13). Version 1 backups still restore (the fields are optional). */
        const val VERSION = 2
    }
}

@Serializable
data class BackupSettings(
    val defaultWallet: String,
    val walletLabels: List<String>,
    val themeMode: String,
    val dynamicColor: Boolean,
)

@Serializable
data class BackupTransaction(
    val type: String,
    val date: String,
    val asset: String,
    val assetClass: String,
    val wallet: String,
    val quantity: String,
    val priceUsd: String? = null,
    val totalUsd: String? = null,
    val feeUsd: String? = null,
    val feeAsset: String? = null,
    val feeQuantity: String? = null,
    val toAsset: String? = null,
    val toQuantity: String? = null,
    val toWallet: String? = null,
    val acquiredDate: String? = null,
    val basisUnknown: Boolean = false,
    val note: String? = null,
)

/** A decoded backup, ready to preview and restore. */
data class Backup(val exportedAt: Instant, val settings: AppSettings, val transactions: List<Transaction>)

class BackupCodec @Inject constructor() {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true // `format` and `version` must always be written
        ignoreUnknownKeys = true // forward-compatible with newer optional fields
    }

    fun encode(transactions: List<Transaction>, settings: AppSettings, exportedAt: Instant): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                exportedAt = exportedAt.toString(),
                settings = BackupSettings(settings.defaultWallet, settings.walletLabels.sorted(), settings.themeMode.name, settings.dynamicColor),
                transactions = transactions.map { it.toBackup() },
            ),
        )

    /** Fails (never throws) on anything that isn't a readable BlockGains backup of a known version. */
    fun decode(text: String): Result<Backup> = runCatching {
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == BackupFile.FORMAT) { "Not a BlockGains backup" }
        require(file.version <= BackupFile.VERSION) { "Backup is from a newer version" }
        Backup(
            exportedAt = Instant.parse(file.exportedAt),
            settings = AppSettings(
                defaultWallet = file.settings.defaultWallet,
                walletLabels = file.settings.walletLabels.toSet(),
                themeMode = ThemeMode.valueOf(file.settings.themeMode),
                dynamicColor = file.settings.dynamicColor,
            ),
            transactions = file.transactions.map { it.toDomain() },
        )
    }

    private fun Transaction.toBackup() = BackupTransaction(
        type = type.name,
        date = date.toString(),
        asset = asset,
        assetClass = assetClass.name,
        wallet = wallet,
        quantity = quantity.toPlainString(),
        priceUsd = priceUsd?.toPlainString(),
        totalUsd = totalUsd?.toPlainString(),
        feeUsd = feeUsd?.toPlainString(),
        feeAsset = feeAsset,
        feeQuantity = feeQuantity?.toPlainString(),
        toAsset = toAsset,
        toQuantity = toQuantity?.toPlainString(),
        toWallet = toWallet,
        acquiredDate = acquiredDate?.toString(),
        basisUnknown = basisUnknown,
        note = note,
    )

    private fun BackupTransaction.toDomain() = Transaction(
        type = TransactionType.valueOf(type),
        date = LocalDate.parse(date),
        asset = asset,
        assetClass = AssetClass.valueOf(assetClass),
        wallet = wallet,
        quantity = BigDecimal(quantity),
        priceUsd = priceUsd?.let(::BigDecimal),
        totalUsd = totalUsd?.let(::BigDecimal),
        feeUsd = feeUsd?.let(::BigDecimal),
        feeAsset = feeAsset,
        feeQuantity = feeQuantity?.let(::BigDecimal),
        toAsset = toAsset,
        toQuantity = toQuantity?.let(::BigDecimal),
        toWallet = toWallet,
        acquiredDate = acquiredDate?.let(LocalDate::parse),
        basisUnknown = basisUnknown,
        note = note,
    )
}
