package com.tiffles.blockgains.domain.model

/** User settings (DataStore). Wallets are free-text labels, never addresses. */
data class AppSettings(
    /** Wallet for the R4 universal remainder (anything held on 2025-01-01 without OPENING records). */
    val defaultWallet: String = DEFAULT_WALLET,
    /** Labels the user created; the app also shows any wallet that appears in records. */
    val walletLabels: Set<String> = setOf(DEFAULT_WALLET),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Wallpaper colors (Android 12+). Off by default: the app uses its own cozy palette. */
    val dynamicColor: Boolean = false,
) {
    companion object {
        const val DEFAULT_WALLET = "WALLET-1"
    }
}
