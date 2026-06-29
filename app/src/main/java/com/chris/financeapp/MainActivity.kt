package com.chris.financeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.chris.financeapp.data.repository.FinanceRepository
import com.chris.financeapp.ui.screen.DashboardScreen
import com.chris.financeapp.ui.screen.ConnectBankScreen
import com.chris.financeapp.ui.screen.ProjectionScreen
import com.chris.financeapp.ui.screen.SettingsScreen
import com.chris.financeapp.ui.theme.FinanceAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val repository = FinanceRepository(applicationContext)

        setContent {
            FinanceAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
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
}
