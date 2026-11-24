package com.tiffles.blockgains.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.tiffles.blockgains.domain.model.AppSettings
import com.tiffles.blockgains.domain.model.ThemeMode
import com.tiffles.blockgains.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val defaults = AppSettings()
        val defaultWallet = prefs[DEFAULT_WALLET] ?: defaults.defaultWallet
        AppSettings(
            defaultWallet = defaultWallet,
            walletLabels = (prefs[WALLET_LABELS] ?: defaults.walletLabels) + defaultWallet,
            themeMode = prefs[THEME_MODE]?.let { name -> ThemeMode.entries.find { it.name == name } } ?: defaults.themeMode,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: defaults.dynamicColor,
        )
    }

    override suspend fun setDefaultWallet(wallet: String) {
        dataStore.edit {
            it[DEFAULT_WALLET] = wallet
            it.addLabel(wallet)
        }
    }

    override suspend fun addWallet(label: String) {
        dataStore.edit { it.addLabel(label) }
    }

    override suspend fun renameWallet(from: String, to: String) {
        dataStore.edit { prefs ->
            prefs[WALLET_LABELS] = labels(prefs) - from + to
            if ((prefs[DEFAULT_WALLET] ?: AppSettings.DEFAULT_WALLET) == from) prefs[DEFAULT_WALLET] = to
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun replaceAll(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[DEFAULT_WALLET] = settings.defaultWallet
            prefs[WALLET_LABELS] = settings.walletLabels + settings.defaultWallet
            prefs[THEME_MODE] = settings.themeMode.name
            prefs[DYNAMIC_COLOR] = settings.dynamicColor
        }
    }

    override suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }

    private fun labels(prefs: Preferences): Set<String> = prefs[WALLET_LABELS] ?: AppSettings().walletLabels

    private fun MutablePreferences.addLabel(label: String) {
        this[WALLET_LABELS] = labels(this) + label
    }

    private companion object {
        val DEFAULT_WALLET = stringPreferencesKey("default_wallet")
        val WALLET_LABELS = stringSetPreferencesKey("wallet_labels")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
