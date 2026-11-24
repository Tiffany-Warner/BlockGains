package com.tiffles.blockgains.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.d
import com.tiffles.blockgains.domain.model.ImportParseResult
import com.tiffles.blockgains.domain.model.ImportedRow
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.domain.model.ZeroPriceResolution
import java.math.BigDecimal
import org.junit.Test

class BuildImportPreviewUseCaseTest {

    private val useCase = BuildImportPreviewUseCase()

    private fun buy(date: String, qty: String, price: String, id: Long = 0) = Transaction(
        id = id, type = TransactionType.BUY, date = d(date), asset = "FAKEB", wallet = "FAKE-WALLET-1",
        quantity = BigDecimal(qty), priceUsd = BigDecimal(price),
    )

    private fun transfer(toWallet: String, id: Long = 0) = Transaction(
        id = id, type = TransactionType.TRANSFER, date = d("2025-03-01"), asset = "FAKEB", wallet = "FAKE-WALLET-1",
        quantity = BigDecimal("2"), toWallet = toWallet,
    )

    private fun swap(toAsset: String, id: Long = 0) = Transaction(
        id = id, type = TransactionType.SWAP, date = d("2025-03-01"), asset = "FAKEB", wallet = "FAKE-WALLET-1",
        quantity = BigDecimal("2"), totalUsd = BigDecimal("500"), toAsset = toAsset, toQuantity = BigDecimal("500"),
    )

    private fun parsed(vararg txs: Transaction) =
        ImportParseResult(txs.mapIndexed { i, tx -> ImportedRow(tx, i + 2, "f.csv") }, emptyList())

    @Test
    fun duplicatesOfStoredRows_areSkipped_scaleInsensitive() {
        val existing = listOf(buy("2023-01-01", "1.50", "100.0", id = 9))
        val preview = useCase(parsed(buy("2023-01-01", "1.5", "100"), buy("2023-02-01", "2", "100")), existing)

        assertThat(preview.duplicates.map { it.lineNumber }).containsExactly(2)
        assertThat(preview.toAdd.map { it.lineNumber }).containsExactly(3)
        assertThat(preview.firstDate).isEqualTo(d("2023-02-01"))
        assertThat(preview.lastDate).isEqualTo(d("2023-02-01"))
    }

    @Test
    fun sameDayTransferToAnotherWallet_isNotADuplicate() {
        val stored = transfer(toWallet = "FAKE-EXCHANGE-1", id = 9)
        val preview = useCase(parsed(transfer(toWallet = "FAKE-WALLET-2"), transfer(toWallet = "FAKE-EXCHANGE-1")), listOf(stored))

        assertThat(preview.toAdd.map { it.lineNumber }).containsExactly(2)
        assertThat(preview.duplicates.map { it.lineNumber }).containsExactly(3) // the true duplicate
    }

    @Test
    fun networkFee_isPartOfTheKey_andBlankFeeAssetMeansTheTransferredAsset() {
        val stored = transfer(toWallet = "FAKE-EXCHANGE-1", id = 9).copy(feeQuantity = BigDecimal("0.010"), feeUsd = BigDecimal("3"))
        val sameExplicit = transfer(toWallet = "FAKE-EXCHANGE-1").copy(feeQuantity = BigDecimal("0.01"), feeUsd = BigDecimal("3.00"), feeAsset = "FAKEB")
        val otherFee = transfer(toWallet = "FAKE-EXCHANGE-1").copy(feeQuantity = BigDecimal("0.02"), feeUsd = BigDecimal("3"))
        val otherCoin = transfer(toWallet = "FAKE-EXCHANGE-1").copy(feeQuantity = BigDecimal("0.01"), feeUsd = BigDecimal("3"), feeAsset = "FAKEA")
        val noFee = transfer(toWallet = "FAKE-EXCHANGE-1")
        val preview = useCase(parsed(sameExplicit, otherFee, otherCoin, noFee), listOf(stored))

        assertThat(preview.duplicates.map { it.lineNumber }).containsExactly(2)
        assertThat(preview.toAdd.map { it.lineNumber }).containsExactly(3, 4, 5).inOrder()
    }

    @Test
    fun sameDaySwapIntoAnotherAsset_isNotADuplicate() {
        val stored = swap(toAsset = "FAKEUSD", id = 9)
        val preview = useCase(parsed(swap(toAsset = "FAKEC"), swap(toAsset = "FAKEUSD")), listOf(stored))

        assertThat(preview.toAdd.map { it.lineNumber }).containsExactly(2)
        assertThat(preview.duplicates.map { it.lineNumber }).containsExactly(3)
    }

    @Test
    fun zeroPriceBuys_needResolution_andResolutionsApply() {
        val preview = useCase(parsed(buy("2023-01-01", "1", "0"), buy("2023-02-01", "2", "0"), buy("2023-03-01", "1", "5")), emptyList())
        assertThat(preview.zeroPriceIndices).containsExactly(0, 1)

        val written = useCase.recordsToWrite(
            preview,
            mapOf(0 to ZeroPriceResolution.UnknownBasis, 1 to ZeroPriceResolution.Income(BigDecimal("12.5"))),
        )
        assertThat(written[0].basisUnknown).isTrue()
        assertThat(written[1].type).isEqualTo(TransactionType.INCOME)
        assertThat(written[1].usdValue).isEquivalentAccordingToCompareTo(BigDecimal("25"))
        assertThat(written[2]).isEqualTo(preview.toAdd[2].transaction)
    }

    @Test
    fun scientificValues_countOnlyRowsToAdd() {
        val stored = buy("2024-01-01", "1", "100", id = 1)
        val parsed = ImportParseResult(
            listOf(
                ImportedRow(buy("2024-01-01", "1", "100"), 2, "f.csv", scientificValues = 2), // duplicate, skipped
                ImportedRow(buy("2024-01-02", "1", "100"), 3, "f.csv", scientificValues = 1),
                ImportedRow(buy("2024-01-03", "1", "100"), 4, "f.csv", scientificValues = 3),
            ),
            emptyList(),
        )
        assertThat(useCase(parsed, listOf(stored)).scientificValues).isEqualTo(4)
    }

    @Test
    fun emptyImport() {
        val preview = useCase(ImportParseResult(emptyList(), emptyList()), emptyList())
        assertThat(preview.isEmpty).isTrue()
        assertThat(preview.firstDate).isNull()
    }
}
