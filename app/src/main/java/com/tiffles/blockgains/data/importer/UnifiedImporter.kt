package com.tiffles.blockgains.data.importer

import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.ACQUIRED_DATE
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.ASSET
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.ASSET_CLASS
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.BASIS_UNKNOWN
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.DATE
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.FEE_ASSET
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.FEE_QUANTITY
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.FEE_USD
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.NOTE
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.PRICE_USD
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.QUANTITY
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.TOTAL_USD
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.TO_ASSET
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.TO_QUANTITY
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.TO_WALLET
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.TYPE
import com.tiffles.blockgains.data.importer.UnifiedCsvFormat.WALLET
import com.tiffles.blockgains.domain.model.AssetClass
import com.tiffles.blockgains.domain.model.ImportParseResult
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import javax.inject.Inject

/** Reads the unified CSV: any mix of assets, wallets and types (import-format-spec → Unified format). */
class UnifiedImporter @Inject constructor() {

    fun parse(text: String, fileName: String?): ImportParseResult {
        val records = CsvReader.read(text).filterNot { it.fields.first().trim().startsWith(UnifiedCsvFormat.COMMENT_PREFIX) }
        val header = records.firstOrNull() ?: return missingColumns(fileName, UnifiedCsvFormat.REQUIRED)
        val columns = header.fields.withIndex().associate { (i, name) -> name.trim().lowercase() to i }
        val missing = UnifiedCsvFormat.REQUIRED.filterNot { it in columns }
        if (missing.isNotEmpty()) return missingColumns(fileName, missing)

        return collectRows(records.drop(1), fileName) { record ->
            val field = { name: String -> columns[name]?.let { record.fields.getOrNull(it) }?.trim().orEmpty() }
            parseRow(field)
        }
    }

    private fun RowReader.parseRow(field: (String) -> String): Transaction {
        val typeText = field(TYPE)
        val type = TransactionType.entries.find { it.name.equals(typeText, ignoreCase = true) }
            ?: reject(InvalidReason.UNKNOWN_TYPE, typeText)
        val classText = field(ASSET_CLASS)
        val assetClass = if (classText.isEmpty()) {
            AssetClass.CRYPTO
        } else {
            AssetClass.entries.find { it.name.equals(classText, ignoreCase = true) }
                ?: reject(InvalidReason.UNKNOWN_ASSET_CLASS, classText)
        }
        return Transaction(
            type = type,
            date = requireDate(field(DATE), DATE),
            asset = field(ASSET).uppercase(),
            assetClass = assetClass,
            wallet = field(WALLET),
            quantity = requireDecimal(field(QUANTITY), QUANTITY),
            priceUsd = optionalDecimal(field(PRICE_USD), PRICE_USD),
            totalUsd = optionalDecimal(field(TOTAL_USD), TOTAL_USD),
            feeUsd = optionalDecimal(field(FEE_USD), FEE_USD),
            feeAsset = field(FEE_ASSET).uppercase().ifEmpty { null },
            feeQuantity = optionalDecimal(field(FEE_QUANTITY), FEE_QUANTITY),
            toAsset = field(TO_ASSET).uppercase().ifEmpty { null },
            toQuantity = optionalDecimal(field(TO_QUANTITY), TO_QUANTITY),
            toWallet = field(TO_WALLET).ifEmpty { null },
            acquiredDate = optionalDate(field(ACQUIRED_DATE), ACQUIRED_DATE),
            basisUnknown = ValueParsers.boolean(field(BASIS_UNKNOWN)) ?: reject(InvalidReason.BAD_BOOLEAN, BASIS_UNKNOWN),
            note = field(NOTE).ifEmpty { null },
        )
    }
}
