package com.wavestream.core.auth

import com.wavestream.core.storage.DataStore
import com.wavestream.core.storage.DataStoreHelper
import kotlinx.serialization.Serializable

/**
 * Account manager — mirrors CloudStream's account system.
 *
 * CloudStream supports multiple local accounts (each with its own
 * bookmarks, watch history, settings). Switching accounts switches
 * all the per-account state.
 *
 * This is simpler than NuvioMobile's profile system (which syncs to
 * Supabase). CloudStream accounts are purely local.
 */
@Serializable
data class WaveAccount(
    val id: Int,
    val name: String,
    val createdAt: Long,
    val avatarColorHex: String = "#6366F1",
)

object AccountManager {
    private const val ACCOUNTS_KEY = "wave_accounts"
    private const val CURRENT_KEY = "wave_current_account"

    /**
     * Get all accounts.
     */
    fun getAccounts(): List<WaveAccount> {
        return DataStore.getKey(ACCOUNTS_KEY, List::class.java) as? List<WaveAccount>
            ?: listOf(WaveAccount(1, "Default", System.currentTimeMillis()))
    }

    /**
     * Get the current account ID.
     */
    fun getCurrentAccountId(): Int {
        return DataStore.getKey(CURRENT_KEY, Int::class.java, 1) ?: 1
    }

    /**
     * Get the current account.
     */
    fun getCurrentAccount(): WaveAccount {
        val id = getCurrentAccountId()
        return getAccounts().firstOrNull { it.id == id }
            ?: WaveAccount(1, "Default", System.currentTimeMillis())
    }

    /**
     * Switch to a different account.
     */
    fun switchAccount(accountId: Int) {
        DataStore.setKey(CURRENT_KEY, accountId)
        DataStoreHelper.currentAccount = accountId
    }

    /**
     * Create a new account.
     */
    fun createAccount(name: String, avatarColorHex: String = "#6366F1"): WaveAccount {
        val accounts = getAccounts().toMutableList()
        val newId = (accounts.maxOfOrNull { it.id } ?: 0) + 1
        val account = WaveAccount(newId, name, System.currentTimeMillis(), avatarColorHex)
        accounts.add(account)
        DataStore.setKey(ACCOUNTS_KEY, accounts)
        return account
    }

    /**
     * Delete an account.
     * Cannot delete the last remaining account.
     */
    fun deleteAccount(accountId: Int): Boolean {
        val accounts = getAccounts().toMutableList()
        if (accounts.size <= 1) return false
        accounts.removeAll { it.id == accountId }
        DataStore.setKey(ACCOUNTS_KEY, accounts)

        // If we deleted the current account, switch to the first one
        if (getCurrentAccountId() == accountId) {
            switchAccount(accounts.first().id)
        }
        return true
    }

    /**
     * Update an account's name.
     */
    fun renameAccount(accountId: Int, newName: String) {
        val accounts = getAccounts().map {
            if (it.id == accountId) it.copy(name = newName) else it
        }
        DataStore.setKey(ACCOUNTS_KEY, accounts)
    }
}

/**
 * Biometric authentication — mirrors CloudStream's BiometricAuthenticator.
 *
 * On Android: uses androidx.biometric.BiometricPrompt
 * On Desktop: not available
 */
expect class BiometricAuthenticator() {
    fun isAvailable(): Boolean
    fun authenticate(title: String, callback: (Boolean) -> Unit)
}
