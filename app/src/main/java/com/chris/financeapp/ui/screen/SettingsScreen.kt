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
    val (tlId, tlSecret) = remember { repository.getTrueLayerCredentials() }

    var truelayerId by remember { mutableStateOf(tlId) }
    var truelayerSecret by remember { mutableStateOf(tlSecret) }

    // Updater states
    var updateStatus by remember { mutableStateOf("") }
    var updateProgress by remember { mutableStateOf(0) }
    var isChecking by remember { mutableStateOf(false) }
    var latestDownloadUrl by remember { mutableStateOf<String?>(null) }
    var latestAssetName by remember { mutableStateOf<String?>(null) }

    val client = remember { OkHttpClient() }
    val updater = remember {
        AppUpdater(context, client, "chrisurwin", "android-finance-app")
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

        // TrueLayer Open Banking section
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("TrueLayer Open Banking API", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Register a free TrueLayer developer console account to query real account balances.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                
                OutlinedTextField(
                    value = truelayerId,
                    onValueChange = { truelayerId = it },
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )

                OutlinedTextField(
                    value = truelayerSecret,
                    onValueChange = { truelayerSecret = it },
                    label = { Text("Client Secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )

                Button(
                    onClick = {
                        repository.saveTrueLayerCredentials(truelayerId, truelayerSecret)
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
                Text("App Updates", fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "The app performs 1-click anonymous downloads and upgrades directly from the public GitHub repository releases.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current Version: ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = TextSecondary)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                                    override fun onUpdateAvailable(newVersion: String, downloadUrl: String, assetName: String, sizeBytes: Long) {
                                        isChecking = false
                                        latestDownloadUrl = downloadUrl
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

                    if (latestDownloadUrl != null) {
                        Button(
                            onClick = {
                                val downloadUrl = latestDownloadUrl ?: return@Button
                                val name = latestAssetName ?: "update.apk"
                                updateStatus = "Downloading APK update..."
                                updater.downloadUpdateApk(
                                    downloadUrl,
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
