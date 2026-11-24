package com.tiffles.blockgains.data.repository

import com.tiffles.blockgains.data.db.TransactionDao
import com.tiffles.blockgains.data.db.toDomain
import com.tiffles.blockgains.data.db.toEntity
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomTransactionRepository @Inject constructor(private val dao: TransactionDao) : TransactionRepository {

    override fun observeAll(): Flow<List<Transaction>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<Transaction> = dao.getAll().map { it.toDomain() }

    override suspend fun add(transactions: List<Transaction>) {
        dao.insertAll(transactions.map { it.copy(id = 0).toEntity() })
    }

    override suspend fun replaceAll(transactions: List<Transaction>) {
        dao.replaceAll(transactions.map { it.copy(id = 0).toEntity() })
    }

    override suspend fun replaceOpenings(asset: String, openings: List<Transaction>) {
        dao.replaceOpenings(asset, openings.map { it.copy(id = 0).toEntity() })
    }

    override suspend fun renameWallet(from: String, to: String) {
        dao.renameWalletEverywhere(from, to)
    }
}
