package com.tiffles.blockgains.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.tiffles.blockgains.data.db.BlockGainsDatabase
import com.tiffles.blockgains.data.db.TransactionDao
import com.tiffles.blockgains.data.files.DocumentFiles
import com.tiffles.blockgains.data.files.SafDocumentFiles
import com.tiffles.blockgains.data.repository.LedgerRepository
import com.tiffles.blockgains.data.repository.RoomTransactionRepository
import com.tiffles.blockgains.data.settings.DataStoreSettingsRepository
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.repository.SettingsRepository
import com.tiffles.blockgains.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher

/** Process-lifetime scope for shared, app-wide flows (the derived calculation). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {
    @Provides @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @Singleton @ApplicationScope
    fun applicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    /** Injected so "current year" and export dates are testable. */
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): BlockGainsDatabase =
        Room.databaseBuilder(context, BlockGainsDatabase::class.java, BlockGainsDatabase.NAME)
            .addMigrations(*BlockGainsDatabase.MIGRATIONS)
            .build()

    @Provides
    fun transactionDao(db: BlockGainsDatabase): TransactionDao = db.transactionDao()

    @Provides @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun transactions(impl: RoomTransactionRepository): TransactionRepository

    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds abstract fun documents(impl: SafDocumentFiles): DocumentFiles

    @Binds abstract fun ledger(impl: LedgerRepository): LedgerSource
}
