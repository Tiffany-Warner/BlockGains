package com.tiffles.blockgains.domain.usecase

import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.ImportParseResult
import com.tiffles.blockgains.domain.model.ImportedRow
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.model.ZeroPriceResolution
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** What an import would do, shown before anything is written (import-format-spec → preview → confirm). */
data class ImportPreview(
    val toAdd: List<ImportedRow>,
    /** Rows identical to a stored one in every column except note (import-format-spec); skipped. */
    val duplicates: List<ImportedRow>,
    val invalidRows: List<CalcIssue.InvalidRow>,
    /** Indices into [toAdd] of $0 BUYs not marked unknown-basis; each needs a [ZeroPriceResolution] (R9). */
    val zeroPriceIndices: List<Int>,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    /** Numbers in [toAdd] written in scientific notation; the preview asks the user to check them. */
    val scientificValues: Int = 0,
) {
    val isEmpty: Boolean get() = toAdd.isEmpty()
}

class BuildImportPreviewUseCase @Inject constructor() {

    operator fun invoke(parsed: ImportParseResult, existing: List<Transaction>): ImportPreview {
        val stored = existing.mapTo(HashSet()) { duplicateKey(it) }
        val (duplicates, toAdd) = parsed.rows.partition { duplicateKey(it.transaction) in stored }
        val dates = toAdd.map { it.transaction.date }
        return ImportPreview(
            toAdd = toAdd,
            duplicates = duplicates,
            invalidRows = parsed.issues,
            zeroPriceIndices = toAdd.indices.filter { isUnconfirmedZeroPriceBuy(toAdd[it].transaction) },
            firstDate = dates.minOrNull(),
            lastDate = dates.maxOrNull(),
            scientificValues = toAdd.sumOf { it.scientificValues },
        )
    }

    /** The records to write once the user confirms. Unresolved $0 buys stay as they are (and stay flagged). */
    fun recordsToWrite(preview: ImportPreview, resolutions: Map<Int, ZeroPriceResolution>): List<Transaction> =
        preview.toAdd.mapIndexed { index, row ->
            val tx = row.transaction.copy(id = 0)
            when (val resolution = resolutions[index]) {
                null -> tx
                ZeroPriceResolution.UnknownBasis -> tx.copy(basisUnknown = true)
                is ZeroPriceResolution.Income -> tx.copy(
                    type = TransactionType.INCOME,
                    priceUsd = resolution.fmvPerUnit,
                    totalUsd = null,
                    feeUsd = null,
                    basisUnknown = false,
                )
            }
        }

    private fun isUnconfirmedZeroPriceBuy(tx: Transaction): Boolean =
        tx.type == TransactionType.BUY && !tx.basisUnknown && tx.usdValue?.signum() == 0

    /** Every unified-format column except `note` (import-format-spec → General parsing rules). */
    private data class DuplicateKey(
        val type: TransactionType,
        val date: LocalDate,
        val asset: String,
        val assetClass: AssetClass,
        val wallet: String,
        val quantity: String,
        val price: String?,
        val total: String?,
        val fee: String?,
        val feeAsset: String?,
        val feeQuantity: String?,
        val toAsset: String?,
        val toQuantity: String?,
        val toWallet: String?,
        val acquiredDate: LocalDate?,
        val basisUnknown: Boolean,
    )

    private fun duplicateKey(tx: Transaction) = DuplicateKey(
        type = tx.type,
        date = tx.date,
        asset = tx.asset,
        assetClass = tx.assetClass,
        wallet = tx.wallet,
        quantity = tx.quantity.normalized(),
        price = tx.priceUsd?.normalized(),
        total = tx.totalUsd?.normalized(),
        fee = tx.feeUsd?.normalized(),
        feeAsset = tx.networkFeeAsset, // blank fee_asset = the transferred asset (R13)
        feeQuantity = tx.feeQuantity?.normalized(),
        toAsset = tx.toAsset,
        toQuantity = tx.toQuantity?.normalized(),
        toWallet = tx.toWallet,
        acquiredDate = tx.acquiredDate,
        basisUnknown = tx.basisUnknown,
    )

    /** Scale-insensitive text form, so 1.50 and 1.5 compare equal. */
    private fun BigDecimal.normalized(): String = stripTrailingZeros().toPlainString()
}
