package com.chris.financeapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.financeapp.BuildConfig
import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.repository.FinanceRepository
import com.chris.financeapp.ui.components.DonutChart
import com.chris.financeapp.ui.components.DonutSlice
import com.chris.financeapp.ui.theme.*
import com.chris.financeapp.utils.AppUpdater
import okhttp3.OkHttpClient
import java.text.NumberFormat
import java.util.Locale

@Composable
fun DashboardScreen(
    repository: FinanceRepository,
    onNavigateToConnect: () -> Unit,
    onNavigateToProjections: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val accounts = remember { mutableStateListOf<Account>() }
    val scrollState = rememberScrollState()

    // Load accounts on view launch
    LaunchedEffect(Unit) {
        accounts.clear()
        accounts.addAll(repository.getAccounts())
    }

    // Version Check state
    var isUpdateAvailable by remember { mutableStateOf(false) }
    var latestTagName by remember { mutableStateOf("") }
    
    // Check update on dashboard start
    LaunchedEffect(Unit) {
        val (owner, repo, token) = repository.getGitHubSettings()
        if (owner.isNotEmpty() && repo.isNotEmpty()) {
            val updater = AppUpdater(context, OkHttpClient(), owner, repo, token)
            updater.checkForUpdates(BuildConfig.VERSION_NAME, object : AppUpdater.UpdateCheckCallback {
                override fun onUpdateAvailable(newVersion: String, assetId: String, assetName: String, sizeBytes: Long) {
                    latestTagName = newVersion
                    isUpdateAvailable = true
                }
                override fun onNoUpdateAvailable() {}
                override fun onError(error: String) {}
            })
        }
    }

    val totalPortfolioVal = accounts.sumOf { it.balance }
    
    val currentVal = accounts.filter { it.type == AccountType.CURRENT }.sumOf { it.balance }
    val isaVal = accounts.filter { it.type == AccountType.ISA }.sumOf { it.balance }
    val investmentVal = accounts.filter { it.type == AccountType.GENERAL_INVESTMENT }.sumOf { it.balance }
    val pensionVal = accounts.filter { it.type == AccountType.PENSION }.sumOf { it.balance }

    val formatter = NumberFormat.getCurrencyInstance(Locale.UK)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Consolidated Finance",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Consolidated portfolio tracking & planning",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
            }
        }

        // Update Notification Banner
        if (isUpdateAvailable) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Update Available", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
                        Text("Version v$latestTagName is ready. Perform update in Settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    Button(
                        onClick = onNavigateToSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimaryContainer, contentColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("Update", fontSize = 12.sp)
                    }
                }
            }
        }

        // Consolidated Portfolio Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CONSOLIDATED NET WORTH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatter.format(totalPortfolioVal),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Donut allocation chart
                DonutChart(
                    slices = listOf(
                        DonutSlice(currentVal, ColorCurrent, "Current"),
                        DonutSlice(isaVal, ColorISA, "ISA"),
                        DonutSlice(investmentVal, ColorInvestment, "Investment"),
                        DonutSlice(pensionVal, ColorPension, "Pension")
                    ),
                    centerText = "Consolidated Assets",
                    centerValue = formatter.format(totalPortfolioVal),
                    modifier = Modifier.size(200.dp)
                )
            }
        }

        // Categorized Breakdown Lists
        Text("Account Categories", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

        listOf(
            Triple(AccountType.CURRENT, ColorCurrent, currentVal),
            Triple(AccountType.ISA, ColorISA, isaVal),
            Triple(AccountType.GENERAL_INVESTMENT, ColorInvestment, investmentVal),
            Triple(AccountType.PENSION, ColorPension, pensionVal)
        ).forEach { (type, color, valSum) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(color, RoundedCornerShape(6.dp))
                        )
                        Text(
                            text = type.displayName,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = formatter.format(valSum),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // Navigation Actions Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToConnect,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Link Account")
            }

            Button(
                onClick = onNavigateToProjections,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet)
            ) {
                Text("Future Projections")
            }
        }
    }
}
