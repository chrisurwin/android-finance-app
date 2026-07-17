package com.chris.financeapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.financeapp.BuildConfig
import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.api.TrueLayerApi
import com.chris.financeapp.data.repository.FinanceRepository
import com.chris.financeapp.data.model.Person
import com.chris.financeapp.data.model.DrawdownPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.widget.Toast
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
    currentRoute: String?,
    onNavigateToConnect: () -> Unit,
    onNavigateToProjections: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val accounts = remember { mutableStateListOf<Account>() }
    var person1 by remember { mutableStateOf(repository.getPerson("person-1")) }
    var person2 by remember { mutableStateOf(repository.getPerson("person-2")) }
    var drawdown by remember { mutableStateOf(repository.getDrawdownPreferences()) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var editBalanceValue by remember { mutableStateOf("") }

    // Load and refresh profiles, settings and accounts when back on dashboard
    LaunchedEffect(currentRoute) {
        if (currentRoute == "dashboard") {
            person1 = repository.getPerson("person-1")
            person2 = repository.getPerson("person-2")
            drawdown = repository.getDrawdownPreferences()

            val rawAccounts = repository.getAccounts()
            val processed = if (!drawdown.isCouple) {
                rawAccounts.map {
                    if (it.personId != "person-1") {
                        val updated = it.copy(personId = "person-1")
                        repository.addOrUpdateAccount(updated)
                        updated
                    } else {
                        it
                    }
                }
            } else {
                rawAccounts
            }
            accounts.clear()
            accounts.addAll(processed)
        }
    }

    // Version Check state
    var isUpdateAvailable by remember { mutableStateOf(false) }
    var latestTagName by remember { mutableStateOf("") }
    
    // Check update on dashboard start
    LaunchedEffect(Unit) {
        val updater = AppUpdater(context, OkHttpClient(), "chrisurwin", "android-finance-app")
        updater.checkForUpdates(BuildConfig.VERSION_NAME, object : AppUpdater.UpdateCheckCallback {
            override fun onUpdateAvailable(newVersion: String, downloadUrl: String, assetName: String, sizeBytes: Long) {
                latestTagName = newVersion
                isUpdateAvailable = true
            }
            override fun onNoUpdateAvailable() {}
            override fun onError(error: String) {}
        })
    }

    val totalPortfolioVal = accounts.filter { it.isIncluded && it.type != AccountType.FINAL_SALARY }.sumOf { it.balance }
    
    val currentVal = accounts.filter { it.isIncluded && it.type == AccountType.CURRENT }.sumOf { it.balance }
    val isaVal = accounts.filter { it.isIncluded && it.type == AccountType.ISA }.sumOf { it.balance }
    val investmentVal = accounts.filter { it.isIncluded && it.type == AccountType.GENERAL_INVESTMENT }.sumOf { it.balance }
    val pensionVal = accounts.filter { it.isIncluded && it.type == AccountType.PENSION }.sumOf { it.balance }
    val finalSalaryVal = accounts.filter { it.isIncluded && it.type == AccountType.FINAL_SALARY }.sumOf { it.balance }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                isRefreshing = true
                                try {
                                    refreshBalances(context, repository, accounts)
                                } finally {
                                    isRefreshing = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync feeds", tint = TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
                }
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
            Triple(AccountType.PENSION, ColorPension, pensionVal),
            Triple(AccountType.FINAL_SALARY, ColorFinalSalary, finalSalaryVal)
        ).forEach { (type, color, valSum) ->
            val typeAccounts = accounts.filter { it.type == type }
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Category Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = formatter.format(valSum),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                    }
                    
                    if (typeAccounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = SlateBg.copy(alpha = 0.5f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            typeAccounts.forEach { account ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Checkbox(
                                            checked = account.isIncluded,
                                            onCheckedChange = { isChecked ->
                                                val updated = account.copy(isIncluded = isChecked)
                                                repository.addOrUpdateAccount(updated)
                                                
                                                // Refresh local list state
                                                accounts.clear()
                                                accounts.addAll(repository.getAccounts())
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = color,
                                                uncheckedColor = TextSecondary.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = account.name,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (account.isIncluded) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Institution Tag
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (account.isIncluded) color.copy(alpha = 0.1f) else SlateBg,
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = account.institution.displayName,
                                                        fontSize = 10.sp,
                                                        color = if (account.isIncluded) color else TextSecondary.copy(alpha = 0.6f),
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1
                                                    )
                                                }
                                                // Ownership Tag (Clickable to Toggle!)
                                                if (drawdown.isCouple) {
                                                    val isLisa = account.personId == "person-2"
                                                    val ownerName = if (isLisa) person2.name else person1.name
                                                    val ownerColor = if (isLisa) Color(0xFFD946EF) else Color(0xFF6366F1)
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                ownerColor.copy(alpha = 0.15f),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .clickable {
                                                                val updated = account.copy(personId = if (isLisa) "person-1" else "person-2")
                                                                repository.addOrUpdateAccount(updated)
                                                                accounts.clear()
                                                                accounts.addAll(repository.getAccounts())
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = ownerName,
                                                            fontSize = 10.sp,
                                                            color = ownerColor,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                                if (account.type == AccountType.FINAL_SALARY) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(
                                                                ColorFinalSalary.copy(alpha = 0.15f),
                                                                RoundedCornerShape(4.dp)
                                                            )
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Starts at Age ${account.payoutAge}",
                                                            fontSize = 10.sp,
                                                            color = ColorFinalSalary,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                            if (account.accountNumber.isNotEmpty()) {
                                                Text(
                                                    text = "No: ${account.accountNumber}",
                                                    fontSize = 11.sp,
                                                    color = TextSecondary.copy(alpha = 0.7f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    // Right-side actions and balance
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (account.type == AccountType.FINAL_SALARY) {
                                                "${formatter.format(account.balance)} / yr"
                                            } else {
                                                formatter.format(account.balance)
                                            },
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (account.isIncluded) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                                            fontSize = 14.sp
                                        )
                                        
                                        IconButton(
                                            onClick = {
                                                editingAccount = account
                                                editBalanceValue = account.balance.toString()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit balance",
                                                tint = TextSecondary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        if (account.isConnected && account.institution.category == "Bank") {
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        isRefreshing = true
                                                        try {
                                                            refreshBalances(context, repository, accounts, account)
                                                        } finally {
                                                            isRefreshing = false
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Sync balance",
                                                    tint = color,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                repository.deleteAccount(account.id)
                                                accounts.clear()
                                                accounts.addAll(repository.getAccounts())
                                                Toast.makeText(context, "${account.name} removed.", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete account",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No accounts linked",
                            fontSize = 12.sp,
                            color = TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 24.dp)
                        )
                    }
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

    // Edit Balance Dialog
    if (editingAccount != null) {
        val account = editingAccount!!
        val isDB = account.type == AccountType.FINAL_SALARY
        AlertDialog(
            onDismissRequest = { editingAccount = null },
            title = { Text(if (isDB) "Edit Annual Payout" else "Edit Account Balance", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter new value for ${account.name}:", color = TextSecondary, fontSize = 14.sp)
                    OutlinedTextField(
                        value = editBalanceValue,
                        onValueChange = { editBalanceValue = it },
                        label = { Text(if (isDB) "Annual Payout (£/year)" else "Balance (£)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    onClick = {
                        val newValue = editBalanceValue.toDoubleOrNull()
                        if (newValue != null) {
                            val updated = account.copy(balance = newValue)
                            repository.addOrUpdateAccount(updated)
                            accounts.clear()
                            accounts.addAll(repository.getAccounts())
                            editingAccount = null
                        } else {
                            Toast.makeText(context, "Please enter a valid numeric value.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAccount = null }) {
                    Text("Cancel", color = TextPrimary)
                }
            }
        )
    }
}

// Sync logic for refreshing TrueLayer account balances
private suspend fun refreshBalances(
    context: android.content.Context,
    repository: FinanceRepository,
    accounts: SnapshotStateList<Account>,
    specificAccount: Account? = null
) {
    val client = OkHttpClient()
    val (clientId, clientSecret) = repository.getTrueLayerCredentials()
    if (clientId.isEmpty() || clientSecret.isEmpty()) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "TrueLayer keys not configured in Settings.", Toast.LENGTH_LONG).show()
        }
        return
    }

    val isSandbox = clientId.lowercase().contains("sandbox") || clientSecret.lowercase().contains("sandbox")
    val api = TrueLayerApi(client, isSandbox)

    // Determine which accounts to refresh (either all TrueLayer ones, or one specific)
    val targets = if (specificAccount != null) {
        listOf(specificAccount)
    } else {
        accounts.filter { it.isConnected && it.institution.category == "Bank" }
    }

    if (targets.isEmpty()) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "No linked bank accounts found to refresh.", Toast.LENGTH_SHORT).show()
        }
        return
    }

    var successCount = 0
    var failCount = 0
    val processedInstitutions = mutableSetOf<String>()

    targets.forEach { target ->
        val instName = target.institution.name
        // We refresh tokens once per institution in this session
        var tokenPair = repository.getTrueLayerTokens(instName)
        if (tokenPair == null) {
            failCount++
            Log.e("DashboardScreen", "No stored tokens found for $instName")
            return@forEach
        }

        if (!processedInstitutions.contains(instName)) {
            // Perform token refresh swap
            val refreshed = api.refreshAccessToken(clientId, clientSecret, tokenPair.second)
            if (refreshed != null) {
                tokenPair = refreshed
                repository.saveTrueLayerTokens(instName, refreshed.first, refreshed.second)
                processedInstitutions.add(instName)
            } else {
                failCount++
                Log.e("DashboardScreen", "Token refresh failed for $instName")
                return@forEach
            }
        }

        // Fetch balance
        val newBalance = api.getAccountBalance(tokenPair.first, target.id)
        if (newBalance != null) {
            val updated = target.copy(balance = newBalance)
            repository.addOrUpdateAccount(updated)
            successCount++
        } else {
            failCount++
        }
    }

    // Refresh UI list state
    withContext(Dispatchers.Main) {
        accounts.clear()
        accounts.addAll(repository.getAccounts())
        if (failCount > 0) {
            Toast.makeText(context, "Synced: $successCount succeeded, $failCount failed.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Synced $successCount accounts successfully!", Toast.LENGTH_SHORT).show()
        }
    }
}
