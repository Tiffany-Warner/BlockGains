package com.tiffles.blockgains.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.data.repository.RoomTransactionRepository
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/**
 * Same-day TRANSFER/SWAP order is input order, which in Room is the autoincrement `id`
 * (`ORDER BY date, id`). Restore and import must keep list order; later imports go after.
 */
class TransactionDaoOrderTest {

    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        BlockGainsDatabase::class.java,
    ).build()
    private val repository = RoomTransactionRepository(db.transactionDao())

    @After
    fun close() = db.close()

    private fun tx(type: TransactionType, asset: String, date: String = "2025-03-10") = Transaction(
        type = type, date = LocalDate.parse(date), asset = asset, wallet = "FAKE-WALLET-2", quantity = BigDecimal("1"),
        toWallet = if (type == TransactionType.TRANSFER) "FAKE-EXCHANGE-1" else null,
        toAsset = if (type == TransactionType.SWAP) "FAKEC" else null,
        toQuantity = if (type == TransactionType.SWAP) BigDecimal("4") else null,
        totalUsd = if (type == TransactionType.SWAP) BigDecimal("200") else null,
    )

    private val chain = listOf(tx(TransactionType.TRANSFER, "FAKEC"), tx(TransactionType.SWAP, "FAKEB"), tx(TransactionType.TRANSFER, "FAKEB"))

    private fun List<Transaction>.withoutIds() = map { it.copy(id = 0) }

    @Test
    fun restore_keepsListOrderWithinADate() = runBlocking {
        repository.add(listOf(tx(TransactionType.SWAP, "FAKEB")))
        repository.replaceAll(chain)
        assertThat(repository.getAll().withoutIds()).containsExactlyElementsIn(chain).inOrder()
    }

    @Test
    fun laterImport_goesAfterEarlierRowsOfTheSameDate() = runBlocking {
        val earlier = listOf(tx(TransactionType.TRANSFER, "FAKEB"))
        val later = listOf(tx(TransactionType.SWAP, "FAKEB"), tx(TransactionType.BUY, "FAKEA", date = "2025-03-09"))
        repository.add(earlier)
        repository.add(later)
        assertThat(repository.getAll().withoutIds()).containsExactly(later[1], earlier[0], later[0]).inOrder()
    }
}
