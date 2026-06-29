package com.chris.financeapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chris.financeapp.data.api.TrueLayerApi
import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.Institution
import com.chris.financeapp.data.repository.FinanceRepository
import com.chris.financeapp.ui.screen.DashboardScreen
import com.chris.financeapp.ui.screen.ConnectBankScreen
import com.chris.financeapp.ui.screen.ProjectionScreen
import com.chris.financeapp.ui.screen.SettingsScreen
import com.chris.financeapp.ui.theme.FinanceAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val repository = FinanceRepository(applicationContext)
        handleDeepLink(intent)

        setContent {
            FinanceAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                repository = repository,
                                onNavigateToConnect = { navController.navigate("connect") },
                                onNavigateToProjections = { navController.navigate("projections") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        
                        composable("connect") {
                            ConnectBankScreen(
                                repository = repository,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("projections") {
                            ProjectionScreen(
                                repository = repository,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                repository = repository
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "financeapp" && data.host == "truelayer-callback") {
            val code = data.getQueryParameter("code") ?: return
            val repository = FinanceRepository(applicationContext)
            val pending = repository.getPendingRequisition()
            if (pending != null) {
                val (_, institutionName) = pending
                val (clientId, clientSecret) = repository.getTrueLayerCredentials()
                val isSandbox = clientId.lowercase().contains("sandbox") || clientSecret.lowercase().contains("sandbox")
                
                lifecycleScope.launch(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val api = TrueLayerApi(client, isSandbox)
                    
                    val result = api.exchangeCodeForToken(clientId, clientSecret, code)
                    if (result.isSuccess) {
                        val (accessToken, _) = result.getOrThrow()
                        val accounts = api.getAccounts(accessToken)
                        if (accounts.isNotEmpty()) {
                            var successCount = 0
                            accounts.forEach { (accountId, accountName) ->
                                val balance = api.getAccountBalance(accessToken, accountId)
                                if (balance != null) {
                                    val institution = try {
                                        Institution.valueOf(institutionName)
                                    } catch (e: Exception) {
                                        Institution.FIRST_DIRECT
                                    }
                                    
                                    val acc = Account(
                                        id = accountId,
                                        name = accountName,
                                        type = if (institution == Institution.JP_ORGAN) AccountType.ISA else AccountType.CURRENT,
                                        institution = institution,
                                        balance = balance,
                                        isConnected = true
                                    )
                                    repository.addOrUpdateAccount(acc)
                                    successCount++
                                }
                            }
                            
                            launch(Dispatchers.Main) {
                                if (successCount > 0) {
                                    Toast.makeText(applicationContext, "Connected $institutionName accounts successfully!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(applicationContext, "Linked, but no active accounts/balances found.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            launch(Dispatchers.Main) {
                                Toast.makeText(applicationContext, "No authorized accounts found for $institutionName.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        val exception = result.exceptionOrNull()
                        val errorMsg = exception?.message ?: "Unknown error"
                        launch(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "Open Banking code exchange failed: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                    repository.clearPendingRequisition()
                }
            }
        }
    }
}
