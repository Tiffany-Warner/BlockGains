package com.tiffles.blockgains.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Upgrading keeps every stored row (never destructive). The v1 database is built from the
 * exact SQL in `app/schemas/.../1.json`, then opened by Room at the current version, which
 * runs the migrations and validates the result against the current schema.
 */
class MigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun v1ToV2_keepsRowsAndAddsEmptyFeeColumns() = runBlocking {
        createV1Database()

        val db = Room.databaseBuilder(context, BlockGainsDatabase::class.java, name)
            .addMigrations(*BlockGainsDatabase.MIGRATIONS)
            .build()
        val rows = db.transactionDao().getAll()
        db.close()

        assertThat(rows).hasSize(2)
        val transfer = rows.single { it.type == TransactionType.TRANSFER }
        assertThat(transfer.toWallet).isEqualTo("FAKE-EXCHANGE-1")
        assertThat(transfer.quantity).isEquivalentAccordingToCompareTo(BigDecimal("0.5"))
        assertThat(transfer.feeAsset).isNull()
        assertThat(transfer.feeQuantity).isNull()
        val buy = rows.single { it.type == TransactionType.BUY }
        assertThat(buy.feeUsd).isEquivalentAccordingToCompareTo(BigDecimal("1.25"))
        assertThat(buy.note).isEqualTo("first")
    }

    private fun createV1Database() {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                    "`date` TEXT NOT NULL, `asset` TEXT NOT NULL, `asset_class` TEXT NOT NULL, `wallet` TEXT NOT NULL, " +
                    "`quantity` TEXT NOT NULL, `price_usd` TEXT, `total_usd` TEXT, `fee_usd` TEXT, `to_asset` TEXT, " +
                    "`to_quantity` TEXT, `to_wallet` TEXT, `acquired_date` TEXT, `basis_unknown` INTEGER NOT NULL, `note` TEXT)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_asset` ON `transactions` (`asset`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '350f7c1147bf3bb2360fc766df9e8c4f')")
            db.execSQL(
                "INSERT INTO transactions (type, date, asset, asset_class, wallet, quantity, price_usd, fee_usd, basis_unknown, note) " +
                    "VALUES ('BUY', '2023-01-05', 'FAKEB', 'CRYPTO', 'FAKE-WALLET-1', '1', '20.00', '1.25', 0, 'first')",
            )
            db.execSQL(
                "INSERT INTO transactions (type, date, asset, asset_class, wallet, quantity, to_wallet, basis_unknown) " +
                    "VALUES ('TRANSFER', '2025-03-12', 'FAKEB', 'CRYPTO', 'FAKE-WALLET-1', '0.5', 'FAKE-EXCHANGE-1', 0)",
            )
            db.version = 1
        }
    }
}
