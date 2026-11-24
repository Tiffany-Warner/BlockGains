package com.tiffles.blockgains.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schemas are exported to `app/schemas/` and committed. Every version bump needs a
 * migration and a migration test; never `fallbackToDestructiveMigration`.
 */
@Database(entities = [TransactionEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BlockGainsDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "blockgains.db"

        /** v2 (R13): a TRANSFER's network fee in coins. Nullable columns, so existing rows keep their meaning. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `fee_asset` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `fee_quantity` TEXT")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
