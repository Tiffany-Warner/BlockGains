package com.tiffles.blockgains.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date, id")
    abstract fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date, id")
    abstract suspend fun getAll(): List<TransactionEntity>

    @Insert
    abstract suspend fun insertAll(rows: List<TransactionEntity>): List<Long>

    @Query("DELETE FROM transactions")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE type = 'OPENING' AND asset = :asset")
    abstract suspend fun deleteOpenings(asset: String)

    @Query("UPDATE transactions SET wallet = :to WHERE wallet = :from")
    abstract suspend fun renameWallet(from: String, to: String)

    @Query("UPDATE transactions SET to_wallet = :to WHERE to_wallet = :from")
    abstract suspend fun renameToWallet(from: String, to: String)

    @Transaction
    open suspend fun replaceAll(rows: List<TransactionEntity>) {
        deleteAll()
        insertAll(rows)
    }

    @Transaction
    open suspend fun replaceOpenings(asset: String, rows: List<TransactionEntity>) {
        deleteOpenings(asset)
        insertAll(rows)
    }

    @Transaction
    open suspend fun renameWalletEverywhere(from: String, to: String) {
        renameWallet(from, to)
        renameToWallet(from, to)
    }
}
