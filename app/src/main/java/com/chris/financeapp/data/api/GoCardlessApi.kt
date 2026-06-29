package com.chris.financeapp.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import java.io.IOException

class GoCardlessApi(private val client: OkHttpClient) {

    private val baseUrl = "https://bankaccountdata.gocardless.com"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val parser = Json { ignoreUnknownKeys = true }

    // Swaps developer credentials for an access token
    fun getAccessToken(secretId: String, secretKey: String): String? {
        val payload = """{"secret_id":"$secretId","secret_key":"$secretKey"}"""
        val request = Request.Builder()
            .url("$baseUrl/api/v2/tokens/new/")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val element = parser.parseToJsonElement(body)
                element.jsonObject["access"]?.jsonPrimitive?.contentOrNull
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // Creates an Open Banking requisition link for user login
    fun createRequisitionLink(
        accessToken: String,
        institutionId: String,
        redirectUrl: String = "financeapp://gocardless-callback",
        reference: String = "ref-${System.currentTimeMillis()}"
    ): Pair<String, String>? { // Returns Pair(RequisitionID, AuthLink)
        val payload = """
            {
                "redirect": "$redirectUrl",
                "institution_id": "$institutionId",
                "reference": "$reference"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("$baseUrl/api/v2/requisitions/")
            .addHeader("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val element = parser.parseToJsonElement(body)
                val id = element.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return null
                val link = element.jsonObject["link"]?.jsonPrimitive?.contentOrNull ?: return null
                Pair(id, link)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // Retrieve account IDs for a requisition link
    fun getRequisitionAccounts(accessToken: String, requisitionId: String): List<String> {
        val request = Request.Builder()
            .url("$baseUrl/api/v2/requisitions/$requisitionId/")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val element = parser.parseToJsonElement(body)
                val accountsArray = element.jsonObject["accounts"]?.jsonArray ?: return emptyList()
                accountsArray.mapNotNull { it.jsonPrimitive.contentOrNull }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Fetches the live balance of an account
    fun getAccountBalance(accessToken: String, accountId: String): Double? {
        val request = Request.Builder()
            .url("$baseUrl/api/v2/accounts/$accountId/balances/")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val element = parser.parseToJsonElement(body)
                val balances = element.jsonObject["balances"]?.jsonArray ?: return null
                if (balances.isEmpty()) return null
                val firstBalanceObj = balances[0].jsonObject
                val balanceAmount = firstBalanceObj["balanceAmount"]?.jsonObject
                val amountStr = balanceAmount?.get("amount")?.jsonPrimitive?.contentOrNull
                amountStr?.toDoubleOrNull()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
