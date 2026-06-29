package com.chris.financeapp.ui.screen

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.Institution
import com.chris.financeapp.data.repository.FinanceRepository
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.chris.financeapp.data.api.TrueLayerApi
import com.chris.financeapp.ui.theme.SlateBg
import com.chris.financeapp.ui.theme.TextPrimary
import com.chris.financeapp.ui.theme.TextSecondary
import com.chris.financeapp.utils.PensionScraper
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectBankScreen(repository: FinanceRepository, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var selectedInstitution by remember { mutableStateOf<Institution?>(null) }
    var showWebView by remember { mutableStateOf(false) }
    var webViewUrl by remember { mutableStateOf("") }
    
    // Scraped / Auth balance details
    var fetchedBalance by remember { mutableStateOf<Double?>(null) }
    var showSetupDialog by remember { mutableStateOf(false) }
    
    // WebView reference to inject JS
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var clickScrapeActive by remember { mutableStateOf(false) }

    // Dialog Fields
    var lastError by remember { mutableStateOf(repository.getLastIntegrationError()) }
    var accountName by remember { mutableStateOf("") }
    var accountNumberInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.CURRENT) }
    var balanceInput by remember { mutableStateOf("") }
    var monthlyContribution by remember { mutableStateOf("0") }
    var employerContribution by remember { mutableStateOf("0") }
    var interestRate by remember { mutableStateOf("0.0") }
    var amc by remember { mutableStateOf("0.0") }

    val institutions = remember { Institution.values() }

    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            title = { Text("Configure ${selectedInstitution?.displayName}", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Account Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accountNumberInput,
                        onValueChange = { accountNumberInput = it },
                        label = { Text("Account Number (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Type selection
                    Text("Account Type", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccountType.values().forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.displayName) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = balanceInput,
                        onValueChange = { balanceInput = it },
                        label = { Text("Current Balance (£)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = monthlyContribution,
                        onValueChange = { monthlyContribution = it },
                        label = { Text("Your Monthly Contribution (£)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedType == AccountType.PENSION) {
                        OutlinedTextField(
                            value = employerContribution,
                            onValueChange = { employerContribution = it },
                            label = { Text("Employer Monthly Contribution (£)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = interestRate,
                        onValueChange = { interestRate = it },
                        label = { Text("Expected Annual Return / Interest Rate (%)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedType == AccountType.PENSION || selectedType == AccountType.GENERAL_INVESTMENT) {
                        OutlinedTextField(
                            value = amc,
                            onValueChange = { amc = it },
                            label = { Text("Annual Management Charge (AMC %)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val inst = selectedInstitution ?: return@Button
                            if (accountNumberInput.trim().isEmpty()) {
                                Toast.makeText(context, "Account Number is required.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val balanceVal = balanceInput.toDoubleOrNull() ?: 0.0
                            val monthlyVal = monthlyContribution.toDoubleOrNull() ?: 0.0
                            val employerVal = employerContribution.toDoubleOrNull() ?: 0.0
                            val interestVal = interestRate.toDoubleOrNull() ?: 0.0
                            val amcVal = amc.toDoubleOrNull() ?: 0.0

                            val accountId = "${inst.name.lowercase()}-${accountNumberInput.trim().lowercase()}"

                            val newAccount = Account(
                                id = accountId,
                                name = accountName,
                                type = selectedType,
                                institution = inst,
                                balance = balanceVal,
                                monthlyContribution = monthlyVal,
                                employerContribution = employerVal,
                                interestRate = interestVal,
                                annualManagementCharge = amcVal,
                                isConnected = true,
                                accountNumber = accountNumberInput.trim()
                            )
                            repository.addOrUpdateAccount(newAccount)
                            showSetupDialog = false
                            selectedInstitution = null
                            showWebView = false
                            onNavigateBack()
                        } catch (e: Exception) {
                            Log.e("ConnectBankScreen", "Failed to save account", e)
                            Toast.makeText(context, "Error saving account: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Save Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showWebView) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScraperWebView(
                url = webViewUrl,
                onBalanceScraped = { amount ->
                    fetchedBalance = amount
                    balanceInput = amount.toString()
                    showSetupDialog = true
                },
                onWebViewCreated = { webViewInstance = it }
            )

            // Header overlays with Back and Custom Scraping Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showWebView = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                
                Text(
                    text = selectedInstitution?.displayName ?: "Scraper",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Button(
                    onClick = {
                        clickScrapeActive = true
                        webViewInstance?.evaluateJavascript(PensionScraper.ENABLE_CLICK_SCRAPE_JS, null)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (clickScrapeActive) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                ) {
                    Text("Click-to-Scrape")
                }
            }
        }
        return
    }

    // Default: grid list of institutions
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (lastError.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Integration Error Details:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(lastError))
                                Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Copy Error", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                repository.clearLastIntegrationError()
                                lastError = ""
                            }
                        ) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onNavigateBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = "Link New Account",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Text(
            text = "Select your institution to connect. Banks leverage secure Open Banking; Pensions connect via inside-app secure WebView scrapers.",
            fontSize = 14.sp,
            color = TextSecondary
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillWeight()
        ) {
            items(institutions) { inst ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clickable {
                            selectedInstitution = inst
                            accountName = inst.displayName
                            
                            // Determine connection strategy
                            if (inst == Institution.AVIVA || inst == Institution.TOWERS_WATSON) {
                                // Webview scrapers
                                showWebView = true
                                clickScrapeActive = false
                                webViewUrl = if (inst == Institution.AVIVA) {
                                     "https://www.direct.aviva.co.uk/MyAccount/login"
                                } else {
                                    "https://www.lifesight.com"
                                }
                                selectedType = AccountType.PENSION
                                interestRate = "6.0" // Default assumptions
                                amc = if (inst == Institution.AVIVA) "0.75" else "0.4"
                                accountName = inst.displayName
                                accountNumberInput = ""
                            } else {
                                // Open Banking Banks: check for TrueLayer keys
                                val (id, secret) = repository.getTrueLayerCredentials()
                                if (id.isNotEmpty() && secret.isNotEmpty()) {
                                    val isSandbox = id.lowercase().contains("sandbox") || secret.lowercase().contains("sandbox")
                                    val authBaseUrl = if (isSandbox) "https://auth.truelayer-sandbox.com" else "https://auth.truelayer.com"
                                    val providersParam = if (isSandbox) {
                                        "sandbox"
                                    } else {
                                        when(inst) {
                                            Institution.HSBC -> "uk-ob-hsbc"
                                            Institution.FIRST_DIRECT -> "uk-ob-first-direct"
                                            Institution.CHASE -> "ob-chase"
                                            Institution.JP_ORGAN -> "ob-jpmorgan-wealth"
                                            else -> "uk-ob-all"
                                        }
                                    }
                                    val redirectUrl = "financeapp://truelayer-callback"
                                    val encodedRedirect = java.net.URLEncoder.encode(redirectUrl, "UTF-8")
                                    val authLink = "$authBaseUrl/?response_type=code&client_id=$id&redirect_uri=$encodedRedirect&scope=info%20accounts%20balance%20offline_access&providers=$providersParam"

                                    // Save pending state so MainActivity knows what bank is being linked when they return
                                    repository.savePendingRequisition("pending", inst.name)
                                    
                                    // Launch system browser for secure App-to-App authentication
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authLink)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(browserIntent)
                                } else {
                                    // Prefill dummy values to make manual/simulated demo flow look premium
                                    accountName = inst.displayName
                                    accountNumberInput = ""
                                    selectedType = if (inst == Institution.JP_ORGAN) AccountType.ISA else AccountType.CURRENT
                                    balanceInput = when(inst) {
                                        Institution.HSBC -> "15000.0"
                                        Institution.FIRST_DIRECT -> "2350.0"
                                        Institution.CHASE -> "8400.0"
                                        Institution.JP_ORGAN -> "48000.0"
                                        else -> "1000.0"
                                    }
                                    interestRate = if (inst == Institution.JP_ORGAN) "5.5" else "2.2"
                                    showSetupDialog = true
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = inst.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ScraperWebView(
    url: String,
    onBalanceScraped: (Double) -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                
                // Secure Interface object
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onValueSelected(value: Double, rawText: String) {
                        post {
                            onBalanceScraped(value)
                        }
                    }
                    @JavascriptInterface
                    fun onError(err: String) {
                        // Log/Handling error
                    }
                }, "AndroidScraperInterface")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Trigger auto detection script
                        view?.evaluateJavascript(PensionScraper.AUTO_DETECT_JS) { result ->
                            val floatVal = result.toDoubleOrNull()
                            if (floatVal != null && floatVal > 0.0) {
                                onBalanceScraped(floatVal)
                            }
                        }
                    }
                }
                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// Extension to help LazyVerticalGrid layout properly
fun Modifier.fillWeight(): Modifier = this.fillMaxHeight()
