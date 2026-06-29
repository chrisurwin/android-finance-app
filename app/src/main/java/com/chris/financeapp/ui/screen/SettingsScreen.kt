package com.chris.financeapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chris.financeapp.BuildConfig
import com.chris.financeapp.data.repository.FinanceRepository
import com.chris.financeapp.ui.theme.SlateBg
import com.chris.financeapp.ui.theme.TextPrimary
import com.chris.financeapp.ui.theme.TextSecondary
import com.chris.financeapp.utils.AppUpdater
import okhttp3.OkHttpClient
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: FinanceRepository) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Load configurations from Repository
    val (gcId, gcKey) = remember { repository.getGoCardlessCredentials() }
    val (ghOwner, ghRepo, ghToken) = remember { repository.getGitHubSettings() }

    var gocardlessId by remember { mutableStateOf(gcId) }
    var gocardlessKey by remember { mutableStateOf(gcKey) }
    var githubOwner by remember { mutableStateOf(ghOwner) }
    var githubRepo by remember { mutableStateOf(ghRepo) }
    var githubToken by remember { mutableStateOf(ghToken) }

    // Updater states
    var updateStatus by remember { mutableStateOf("") }
    var updateProgress by remember { mutableStateOf(0) }
    var isChecking by remember { mutableStateOf(false) }
    var latestAssetId by remember { mutableStateOf<String?>(null) }
    var latestAssetName by remember { mutableStateOf<String?>(null) }

    val client = remember { OkHttpClient() }
    val updater = remember(githubOwner, githubRepo, githubToken) {
        AppUpdater(context, client, githubOwner, githubRepo, githubToken)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "App Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        // GoCardless Open Banking section
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("GoCardless Open Banking API", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Register a free GoCardless (Nordigen) developer account to query real account balances.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                
                OutlinedTextField(
                    value = gocardlessId,
                    onValueChange = { gocardlessId = it },
                    label = { Text("Secret ID") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )

                OutlinedTextField(
                    value = gocardlessKey,
                    onValueChange = { gocardlessKey = it },
                    label = { Text("Secret Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )

                Button(
                    onClick = {
                        repository.saveGoCardlessCredentials(gocardlessId, gocardlessKey)
                        Toast.makeText(context, "Credentials Saved", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Credentials")
                }
            }
        }

        // GitHub Release & Updates Section
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("App Updates (GitHub Integration)", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Configuring this allows the app to perform 1-click downloads and upgrades for new releases.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = githubOwner,
                    onValueChange = { githubOwner = it },
                    label = { Text("GitHub Owner / Organization") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )

                OutlinedTextField(
                    value = githubRepo,
                    onValueChange = { githubRepo = it },
                    label = { Text("Repository Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )

                OutlinedTextField(
                    value = githubToken,
                    onValueChange = { githubToken = it },
                    label = { Text("Personal Access Token (PAT)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current Version: ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = TextSecondary)
                    Button(
                        onClick = {
                            repository.saveGitHubSettings(githubOwner, githubRepo, githubToken)
                            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Save Settings")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Updater Control Panel
                Text("Version Control", fontWeight = FontWeight.SemiBold, color = TextPrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            isChecking = true
                            updateStatus = "Checking for new releases..."
                            updater.checkForUpdates(
                                BuildConfig.VERSION_NAME,
                                object : AppUpdater.UpdateCheckCallback {
                                    override fun onUpdateAvailable(newVersion: String, assetId: String, assetName: String, sizeBytes: Long) {
                                        isChecking = false
                                        latestAssetId = assetId
                                        latestAssetName = assetName
                                        updateStatus = "New release found: v$newVersion"
                                    }

                                    override fun onNoUpdateAvailable() {
                                        isChecking = false
                                        updateStatus = "App is up to date."
                                    }

                                    override fun onError(error: String) {
                                        isChecking = false
                                        updateStatus = error
                                    }
                                }
                            )
                        },
                        enabled = !isChecking
                    ) {
                        Text("Check for Update")
                    }

                    if (latestAssetId != null) {
                        Button(
                            onClick = {
                                val assetId = latestAssetId ?: return@Button
                                val name = latestAssetName ?: "update.apk"
                                updateStatus = "Downloading APK update..."
                                updater.downloadUpdateApk(
                                    assetId,
                                    name,
                                    object : AppUpdater.DownloadCallback {
                                        override fun onProgress(percentage: Int) {
                                            updateProgress = percentage
                                            updateStatus = "Downloading: $percentage%"
                                        }

                                        override fun onCompleted(apkFile: File) {
                                            updateStatus = "Download complete. Installing..."
                                            updater.installApk(apkFile)
                                        }

                                        override fun onError(error: String) {
                                            updateStatus = "Download failed: $error"
                                        }
                                    }
                                )
                            }
                        ) {
                            Text("Upgrade Now")
                        }
                    }
                }

                if (updateStatus.isNotEmpty()) {
                    Text(
                        text = updateStatus,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
