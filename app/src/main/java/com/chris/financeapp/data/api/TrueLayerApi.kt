package com.chris.financeapp.data.api

import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

class TrueLayerApi(private val client: OkHttpClient, private val isSandbox: Boolean) {

    private val authUrl = if (isSandbox) "https://auth.truelayer-sandbox.com" else "https://auth.truelayer.com"
    private val dataUrl = if (isSandbox) "https://api.truelayer-sandbox.com" else "https://api.truelayer.com"
    
    private val parser = Json { ignoreUnknownKeys = true }

    // Swap temporary authorization code for persistent access tokens
    fun exchangeCodeForToken(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String = "financeapp://truelayer-callback"
    ): Pair<String, String>? { // Returns Pair(AccessToken, RefreshToken)
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("redirect_uri", redirectUri)
            .add("code", code)
            .build()

        val request = Request.Builder()
            .url("$authUrl/connect/token")
            .post(formBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("TrueLayerApi", "Code exchange failed (HTTP ${response.code}): $bodyStr")
                    return null
                }
                val json = parser.parseToJsonElement(bodyStr).jsonObject
                val access = json["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
                val refresh = json["refresh_token"]?.jsonPrimitive?.contentOrNull ?: ""
                Pair(access, refresh)
            }
        } catch (e: IOException) {
            Log.e("TrueLayerApi", "Code exchange connection exception", e)
            null
        }
    }

    // Swaps refresh token for a new access token
    fun refreshAccessToken(clientId: String, clientSecret: String, refreshToken: String): Pair<String, String>? {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url("$authUrl/connect/token")
            .post(formBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("TrueLayerApi", "Token refresh failed (HTTP ${response.code}): $bodyStr")
                    return null
                }
                val json = parser.parseToJsonElement(bodyStr).jsonObject
                val access = json["access_token"]?.jsonPrimitive?.contentOrNull ?: return null
                val refresh = json["refresh_token"]?.jsonPrimitive?.contentOrNull ?: refreshToken
                Pair(access, refresh)
            }
        } catch (e: IOException) {
            Log.e("TrueLayerApi", "Token refresh connection exception", e)
            null
        }
    }

    // Fetches account details
    fun getAccounts(accessToken: String): List<Pair<String, String>> { // Returns list of Pair(AccountID, AccountDisplayName)
        val request = Request.Builder()
            .url("$dataUrl/data/v1/accounts")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("TrueLayerApi", "Accounts fetch failed (HTTP ${response.code}): $bodyStr")
                    return emptyList()
                }
                val json = parser.parseToJsonElement(bodyStr).jsonObject
                val results = json["results"]?.jsonArray ?: return emptyList()
                results.mapNotNull { result ->
                    val obj = result.jsonObject
                    val id = obj["account_id"]?.jsonPrimitive?.contentOrNull
                    val name = obj["display_name"]?.jsonPrimitive?.contentOrNull ?: "Bank Account"
                    if (id != null) Pair(id, name) else null
                }
            }
        } catch (e: IOException) {
            Log.e("TrueLayerApi", "Accounts fetch connection exception", e)
            emptyList()
        }
    }

    // Fetches account balance
    fun getAccountBalance(accessToken: String, accountId: String): Double? {
        val request = Request.Builder()
            .url("$dataUrl/data/v1/accounts/$accountId/balance")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("TrueLayerApi", "Balance fetch failed (HTTP ${response.code}): $bodyStr")
                    return null
                }
                val json = parser.parseToJsonElement(bodyStr).jsonObject
                val results = json["results"]?.jsonArray ?: return null
                if (results.isEmpty()) return null
                val balanceObj = results[0].jsonObject
                balanceObj["current"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            }
        } catch (e: IOException) {
            Log.e("TrueLayerApi", "Balance fetch connection exception", e)
            null
        }
    }
}
