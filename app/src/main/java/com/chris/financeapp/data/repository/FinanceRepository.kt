package com.chris.financeapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.Institution
import com.chris.financeapp.data.model.InvestmentAssumptions
import com.chris.financeapp.data.model.DrawdownPreferences
import com.chris.financeapp.data.model.Person
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class FinanceRepository(private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    // Encrypted preferences for secure tokens and connection configs
    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_finance_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Standard preferences for account data and parameters
    private val dataPrefs: SharedPreferences = context.getSharedPreferences("finance_data_prefs", Context.MODE_PRIVATE)

    private val parser = Json { ignoreUnknownKeys = true }

    // --- Core Data: Accounts ---

    fun getAccounts(): List<Account> {
        val json = dataPrefs.getString("accounts", null) ?: return emptyList()
        return try {
            parser.decodeFromString<List<Account>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(accounts: List<Account>) {
        val json = parser.encodeToString(accounts)
        dataPrefs.edit().putString("accounts", json).apply()
    }

    fun addOrUpdateAccount(account: Account) {
        val currentList = getAccounts().toMutableList()
        val index = currentList.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            currentList[index] = account
        } else {
            currentList.add(account)
        }
        saveAccounts(currentList)
    }

    fun deleteAccount(accountId: String) {
        val currentList = getAccounts().filter { it.id != accountId }
        saveAccounts(currentList)
    }

    fun getPendingRequisition(): Pair<String, String>? {
        val id = securePrefs.getString("pending_req_id", "") ?: ""
        val inst = securePrefs.getString("pending_req_inst", "") ?: ""
        return if (id.isNotEmpty() && inst.isNotEmpty()) Pair(id, inst) else null
    }

    fun savePendingRequisition(requisitionId: String, institutionName: String) {
        securePrefs.edit()
            .putString("pending_req_id", requisitionId)
            .putString("pending_req_inst", institutionName)
            .apply()
    }

    fun clearPendingRequisition() {
        securePrefs.edit()
            .remove("pending_req_id")
            .remove("pending_req_inst")
            .apply()
    }

    // --- Integration Configurations ---

    fun getGoCardlessCredentials(): Pair<String, String> {
        val id = securePrefs.getString("gocardless_id", "") ?: ""
        val key = securePrefs.getString("gocardless_key", "") ?: ""
        return Pair(id, key)
    }

    fun saveGoCardlessCredentials(secretId: String, secretKey: String) {
        securePrefs.edit()
            .putString("gocardless_id", secretId)
            .putString("gocardless_key", secretKey)
            .apply()
    }

    fun getGitHubSettings(): Triple<String, String, String> {
        val owner = securePrefs.getString("github_owner", "") ?: ""
        val repo = securePrefs.getString("github_repo", "") ?: ""
        val token = securePrefs.getString("github_token", "") ?: ""
        return Triple(owner, repo, token)
    }

    fun saveGitHubSettings(owner: String, repo: String, token: String) {
        securePrefs.edit()
            .putString("github_owner", owner)
            .putString("github_repo", repo)
            .putString("github_token", token)
            .apply()
    }

    // --- Pension Projections Parameters ---

    fun getPerson(): Person {
        val json = dataPrefs.getString("person", null)
        if (json != null) {
            try {
                return parser.decodeFromString<Person>(json)
            } catch (e: Exception) {}
        }
        val default = Person("person-1", "Chris", 1974, 65)
        savePerson(default)
        return default
    }

    fun savePerson(person: Person) {
        dataPrefs.edit().putString("person", parser.encodeToString(person)).apply()
    }

    fun getInvestmentAssumptions(): InvestmentAssumptions {
        val json = dataPrefs.getString("assumptions", null)
        if (json != null) {
            try {
                return parser.decodeFromString<InvestmentAssumptions>(json)
            } catch (e: Exception) {}
        }
        val default = InvestmentAssumptions()
        saveInvestmentAssumptions(default)
        return default
    }

    fun saveInvestmentAssumptions(assumptions: InvestmentAssumptions) {
        dataPrefs.edit().putString("assumptions", parser.encodeToString(assumptions)).apply()
    }

    fun getDrawdownPreferences(): DrawdownPreferences {
        val json = dataPrefs.getString("drawdown", null)
        if (json != null) {
            try {
                return parser.decodeFromString<DrawdownPreferences>(json)
            } catch (e: Exception) {}
        }
        val default = DrawdownPreferences()
        saveDrawdownPreferences(default)
        return default
    }

    fun saveDrawdownPreferences(preferences: DrawdownPreferences) {
        dataPrefs.edit().putString("drawdown", parser.encodeToString(preferences)).apply()
    }
}
