package com.wavestream.core.auth

import com.wavestream.core.storage.DataStore
import com.wavestream.core.storage.DataStoreHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class WaveAccount(
    val id: Int,
    val name: String,
    val createdAt: Long,
    val avatarColorHex: String = "#6366F1",
)

object AccountManager {
    private const val ACCOUNTS_KEY = "wave_accounts_v2"
    private const val CURRENT_KEY = "wave_current_account"
    private val accountSerializer = WaveAccount.serializer()
    private val accountListSerializer = ListSerializer(accountSerializer)

    fun getAccounts(): List<WaveAccount> {
        return DataStore.getSerializedList(ACCOUNTS_KEY, accountSerializer)
            ?: listOf(WaveAccount(1, "Default", System.currentTimeMillis()))
    }

    fun getCurrentAccountId(): Int {
        return DataStore.getKey(CURRENT_KEY, Int::class.java, 1) ?: 1
    }

    fun getCurrentAccount(): WaveAccount {
        val id = getCurrentAccountId()
        return getAccounts().firstOrNull { it.id == id }
            ?: WaveAccount(1, "Default", System.currentTimeMillis())
    }

    fun switchAccount(accountId: Int) {
        DataStore.setKey(CURRENT_KEY, accountId)
        DataStoreHelper.currentAccount = accountId
    }

    fun createAccount(name: String, avatarColorHex: String = "#6366F1"): WaveAccount {
        val accounts = getAccounts().toMutableList()
        val newId = (accounts.maxOfOrNull { it.id } ?: 0) + 1
        val account = WaveAccount(newId, name, System.currentTimeMillis(), avatarColorHex)
        accounts.add(account)
        DataStore.setSerializedList(ACCOUNTS_KEY, accounts, accountSerializer)
        return account
    }

    fun deleteAccount(accountId: Int): Boolean {
        val accounts = getAccounts().toMutableList()
        if (accounts.size <= 1) return false
        accounts.removeAll { it.id == accountId }
        DataStore.setSerializedList(ACCOUNTS_KEY, accounts, accountSerializer)
        if (getCurrentAccountId() == accountId) {
            switchAccount(accounts.first().id)
        }
        return true
    }

    fun renameAccount(accountId: Int, newName: String) {
        val accounts = getAccounts().map {
            if (it.id == accountId) it.copy(name = newName) else it
        }
        DataStore.setSerializedList(ACCOUNTS_KEY, accounts, accountSerializer)
    }
}

expect class BiometricAuthenticator() {
    fun isAvailable(): Boolean
    fun authenticate(title: String, callback: (Boolean) -> Unit)
}
