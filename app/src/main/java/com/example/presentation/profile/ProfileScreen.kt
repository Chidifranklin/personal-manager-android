package com.example.presentation.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.fragment.app.FragmentActivity
import com.example.data.auth.UserProfile
import com.example.data.repository.UserRecordCounts
import com.example.presentation.components.AppBrandLogo
import com.example.presentation.components.ExportReportBottomSheet
import com.example.ui.theme.*
import com.example.util.BiometricAuthManager
import com.example.util.BiometricCapability
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLanding: () -> Unit,
    onLockAppNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val recordCounts by viewModel.recordCounts.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val biometricLockEnabled by viewModel.biometricLockEnabled.collectAsState()
    val biometricLockOnResume by viewModel.biometricLockOnResume.collectAsState()
    val biometricProtectFinance by viewModel.biometricProtectFinance.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearAllDataDialog by remember { mutableStateOf(false) }
    var showQuickSwitchDialog by remember { mutableStateOf(false) }
    var showExportReportSheet by remember { mutableStateOf(false) }
    val isExporting by viewModel.isExporting.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("User Profile") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.triggerCloudSync() },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("profile_sync_icon_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync to Cloud")
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Header Card
            item {
                UserProfileCard(
                    user = currentUser,
                    onEditName = { showEditNameDialog = true }
                )
            }

            // Scoped Data Isolation Card
            item {
                UserRecordIsolationCard(
                    recordCounts = recordCounts,
                    user = currentUser
                )
            }

            // Cloud Synchronization Card
            item {
                CloudSyncStatusCard(
                    isSyncing = isSyncing,
                    lastSyncTime = lastSyncTime,
                    syncMessage = syncMessage,
                    onSyncNow = { viewModel.triggerCloudSync() }
                )
            }

            // Biometric Security Card
            item {
                val activity = context as? FragmentActivity
                BiometricSecuritySection(
                    biometricLockEnabled = biometricLockEnabled,
                    biometricLockOnResume = biometricLockOnResume,
                    biometricProtectFinance = biometricProtectFinance,
                    onToggleBiometricLock = { targetState ->
                        if (activity != null) {
                            BiometricAuthManager.authenticate(
                                activity = activity,
                                title = if (targetState) "Enable Biometric App Lock" else "Disable Biometric App Lock",
                                subtitle = "Confirm identity",
                                description = "Authenticate using fingerprint, face, or device credential to update biometric security.",
                                onSuccess = {
                                    viewModel.setBiometricLockEnabled(targetState)
                                },
                                onError = { _, err ->
                                    Toast.makeText(context, "Authentication required: $err", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            viewModel.setBiometricLockEnabled(targetState)
                        }
                    },
                    onToggleLockOnResume = { viewModel.setBiometricLockOnResume(it) },
                    onToggleProtectFinance = { viewModel.setBiometricProtectFinance(it) },
                    onTestBiometrics = {
                        if (activity != null) {
                            BiometricAuthManager.authenticate(
                                activity = activity,
                                title = "Test Biometric Authentication",
                                subtitle = "Fingerprint / Face Unlock verification",
                                description = "Verify that biometric recognition functions correctly on this device.",
                                onSuccess = {
                                    Toast.makeText(context, "Biometric authentication successful!", Toast.LENGTH_LONG).show()
                                },
                                onError = { _, err ->
                                    Toast.makeText(context, "Biometric test result: $err", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Activity context not ready for biometric prompt", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLockAppNow = onLockAppNow
                )
            }

            // App & Account Settings
            item {
                AccountPreferencesSection(
                    currencyCode = currencyCode,
                    onSelectCurrency = { showCurrencyDialog = true },
                    onExportStatements = { showExportReportSheet = true },
                    onSwitchAccount = { showQuickSwitchDialog = true }
                )
            }

            // About Personal Manager Section
            item {
                AboutPersonalManagerSection()
            }

            // Danger Zone
            item {
                DangerZoneSection(
                    onSignOut = {
                        viewModel.signOut {
                            onNavigateToLanding()
                        }
                    },
                    onDeleteAccount = { showDeleteConfirmDialog = true },
                    onClearAllData = { showClearAllDataDialog = true }
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Edit Name Dialog
    if (showEditNameDialog) {
        var newName by remember { mutableStateOf(currentUser?.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Display Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.updateDisplayName(newName.trim())
                            showEditNameDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Currency Picker Dialog
    if (showCurrencyDialog) {
        CurrencyPickerDialog(
            currentCode = currencyCode,
            onDismiss = { showCurrencyDialog = false },
            onSelect = {
                viewModel.setCurrency(it)
                showCurrencyDialog = false
            }
        )
    }

    // Delete Account Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Account & Cloud Records?") },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            text = {
                Text("This will permanently erase all your accounts, transactions, debts, tasks, and notes from both the local device and cloud backup. This operation cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteAccount {
                            onNavigateToLanding()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete All Data Confirmation Dialog
    if (showClearAllDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDataDialog = false },
            title = { Text("Delete All Data?") },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            text = {
                Text("This will permanently remove all accounts, transactions, budgets, debts, tasks, and notes from the app so you can start with a clean slate.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDataDialog = false
                        viewModel.clearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Account Switch Dialog
    if (showQuickSwitchDialog) {
        SwitchAccountDialog(
            currentEmail = currentUser?.email,
            onDismiss = { showQuickSwitchDialog = false },
            onSwitch = { email, name ->
                showQuickSwitchDialog = false
                viewModel.switchUserQuick(email, name)
            }
        )
    }

    if (showExportReportSheet) {
        ExportReportBottomSheet(
            currencyCode = currencyCode,
            isExporting = isExporting,
            initialScope = com.example.data.export.FinancialActivityScope.FULL_STATEMENT,
            onDismiss = { showExportReportSheet = false },
            onGenerateAndShare = { scope, periodType, format, startTimestamp, endTimestamp, dateRangeLabel ->
                viewModel.generateAndShareReport(
                    context = context,
                    scope = scope,
                    periodType = periodType,
                    format = format,
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    dateRangeLabel = dateRangeLabel
                ) { success, errorMsg ->
                    if (success) {
                        showExportReportSheet = false
                    } else if (errorMsg != null) {
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCopyToClipboard = { scope, periodType, startTimestamp, endTimestamp, dateRangeLabel ->
                viewModel.copyReportForGoogleSheets(
                    context = context,
                    scope = scope,
                    periodType = periodType,
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    dateRangeLabel = dateRangeLabel
                ) { _, msg ->
                    if (msg != null) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onOpenGoogleSheets = {
                viewModel.openGoogleSheets(context)
            }
        )
    }
}

@Composable
private fun UserProfileCard(
    user: UserProfile?,
    onEditName: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User Avatar
                if (user?.photoUrl != null && user.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user?.displayName?.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user?.displayName ?: "Personal User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = onEditName, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(16.dp))
                        }
                    }

                    Text(
                        text = user?.email ?: "local_user@personalmanager.app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Surface(
                        shape = CircleShape,
                        color = if (user?.isGoogleUser == true) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (user?.isGoogleUser == true) Icons.Default.CheckCircle else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (user?.isGoogleUser == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (user?.isGoogleUser == true) "Google Verified Account" else "Standard Profile",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (user?.isGoogleUser == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRecordIsolationCard(
    recordCounts: UserRecordCounts,
    user: UserProfile?
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = EmeraldIncome)
                Column {
                    Text(
                        text = "Your Records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Overview of active data in your personal profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 6-grid record count stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordMetricBadge(Modifier.weight(1f), "Accounts", recordCounts.accountCount.toString(), Icons.Default.AccountBalance, EmeraldIncome)
                RecordMetricBadge(Modifier.weight(1f), "Transactions", recordCounts.transactionCount.toString(), Icons.Default.ReceiptLong, MaterialTheme.colorScheme.primary)
                RecordMetricBadge(Modifier.weight(1f), "Debts", recordCounts.debtCount.toString(), Icons.Default.Handshake, VioletReceivable)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordMetricBadge(Modifier.weight(1f), "Tasks", recordCounts.taskCount.toString(), Icons.Default.CheckCircle, MaterialTheme.colorScheme.secondary)
                RecordMetricBadge(Modifier.weight(1f), "Notes", recordCounts.noteCount.toString(), Icons.Default.Description, AmberPayable)
                RecordMetricBadge(Modifier.weight(1f), "Budgets", recordCounts.budgetCount.toString(), Icons.Default.PieChart, EmeraldIncome)
            }
        }
    }
}

@Composable
private fun RecordMetricBadge(
    modifier: Modifier = Modifier,
    label: String,
    count: String,
    icon: ImageVector,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
            Text(count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CloudSyncStatusCard(
    isSyncing: Boolean,
    lastSyncTime: Long?,
    syncMessage: String?,
    onSyncNow: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("Sync to Cloud", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val lastSyncStr = lastSyncTime?.let {
                            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(it))
                        } ?: "Never synced"
                        Text("Last synced: $lastSyncStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    modifier = Modifier.testTag("profile_sync_now_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Sync Now")
                    }
                }
            }

            if (syncMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(syncMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPreferencesSection(
    currencyCode: String,
    onSelectCurrency: () -> Unit,
    onExportStatements: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Preferences & Data Export", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Primary Currency
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSelectCurrency)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CurrencyExchange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Primary Currency", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Default denomination for balances", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = currencyCode,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Export Financial Statements & Activities
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExportStatements)
                    .padding(vertical = 10.dp, horizontal = 6.dp)
                    .testTag("profile_export_statements_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF107C41))
                    Column {
                        Text("Export Statements & Activities", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Export to PDF, Excel (.xlsx), CSV, or Google Sheets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Switch User Account
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onSwitchAccount)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column {
                        Text("Switch Google Account", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Seamlessly switch between profiles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AboutPersonalManagerSection() {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_about_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppBrandLogo(size = 46.dp)
                    Column {
                        Text(
                            text = "About Personal Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version 1.0.0 • Architecture & Features",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Summary description always visible
            Text(
                text = "A unified operating system for multi-currency double-entry accounting, counterparty debt ledgers, offline exact alarms, and encrypted biometric notes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Trust Pillars
                    Text(
                        text = "Security & Reliability Pillars",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileTrustPillarItem(
                            icon = Icons.Default.Lock,
                            title = "Zero Leakage",
                            subtitle = "User Scoped",
                            modifier = Modifier.weight(1f)
                        )
                        ProfileTrustPillarItem(
                            icon = Icons.Default.CloudSync,
                            title = "Cloud Sync",
                            subtitle = "Multi-Device",
                            modifier = Modifier.weight(1f)
                        )
                        ProfileTrustPillarItem(
                            icon = Icons.Default.Fingerprint,
                            title = "Biometrics",
                            subtitle = "Hardware Vault",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Engineered for Precision & Privacy (Executive Capabilities)
                    Text(
                        text = "Executive Capabilities",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ProfileFeatureItem(
                        icon = Icons.Default.AccountBalance,
                        title = "Double-Entry Financial Ledger",
                        description = "Track accounts across cash, bank, and investments. Real-time conversion across 24 global currencies with instant audit reconciliation.",
                        accentColor = EmeraldIncome
                    )

                    ProfileFeatureItem(
                        icon = Icons.Default.Handshake,
                        title = "Counterparty Debt & Loan Ledger",
                        description = "Immutable balance sheets for money lent and borrowed. Track partial repayments, deadlines, and settlement logs.",
                        accentColor = VioletReceivable
                    )

                    ProfileFeatureItem(
                        icon = Icons.Default.Fingerprint,
                        title = "Biometric Markdown Notes Vault",
                        description = "Protect sensitive documents, credentials, and contracts behind Android biometric hardware (Fingerprint & Face ID).",
                        accentColor = AmberPayable
                    )

                    ProfileFeatureItem(
                        icon = Icons.Default.Alarm,
                        title = "Exact Alarms & Priority Tasks",
                        description = "Schedule alarms that ring precisely even after device reboot. Organize agenda items with priority tags.",
                        accentColor = MaterialTheme.colorScheme.primary
                    )

                    ProfileFeatureItem(
                        icon = Icons.Default.FileDownload,
                        title = "Audit Statements: PDF, Excel & CSV",
                        description = "Generate paginated audit-ready PDF reports, native Microsoft Excel (.xlsx) workbooks with formulas, or standard CSV files.",
                        accentColor = Color(0xFF107C41)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Zero-Knowledge Commitment
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Zero-Knowledge Record Privacy",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Personal Manager partitions every ledger entry, task, and note strictly to your authenticated identity. Your data is isolated, locally encrypted on-device, and protected by biometric hardware validation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = EmeraldIncome,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "End-to-End User Scoped Record Isolation",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTrustPillarItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileFeatureItem(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun DangerZoneSection(
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onClearAllData: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Session & Account Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedButton(
                onClick = onClearAllData,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_clear_all_data_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete All Data (Clean Slate)")
            }

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_sign_out_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out of Personal Manager")
            }

            Button(
                onClick = onDeleteAccount,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_delete_account_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete Account & Wipe Cloud Data")
            }
        }
    }
}

@Composable
fun CurrencyPickerDialog(
    currentCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val supportedCurrencies = listOf(
        "USD" to "US Dollar ($)",
        "EUR" to "Euro (€)",
        "GBP" to "British Pound (£)",
        "NGN" to "Nigerian Naira (₦)",
        "CAD" to "Canadian Dollar (CA$)",
        "AUD" to "Australian Dollar (A$)",
        "JPY" to "Japanese Yen (¥)",
        "CHF" to "Swiss Franc (CHF)",
        "INR" to "Indian Rupee (₹)",
        "ZAR" to "South African Rand (R)",
        "BRL" to "Brazilian Real (R$)",
        "AED" to "UAE Dirham (AED)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Primary Currency") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(supportedCurrencies.size) { index ->
                    val (code, name) = supportedCurrencies[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(code, fontWeight = FontWeight.Bold)
                            Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (code == currentCode) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun BiometricSecuritySection(
    biometricLockEnabled: Boolean,
    biometricLockOnResume: Boolean,
    biometricProtectFinance: Boolean,
    onToggleBiometricLock: (Boolean) -> Unit,
    onToggleLockOnResume: (Boolean) -> Unit,
    onToggleProtectFinance: (Boolean) -> Unit,
    onTestBiometrics: () -> Unit,
    onLockAppNow: (() -> Unit)?
) {
    val context = LocalContext.current
    val capability = remember { BiometricAuthManager.checkBiometricCapability(context) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_biometric_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(EmeraldIncome.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = EmeraldIncome,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "Biometric Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fingerprint and Face Unlock protection",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Capability Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (capability.isAvailable) EmeraldIncome.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (capability.isAvailable) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (capability.isAvailable) EmeraldIncome else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when (capability) {
                            is BiometricCapability.Available -> "Biometrics Ready: Fingerprint & Face Unlock active"
                            is BiometricCapability.NotEnrolled -> "Hardware ready, but no biometric enrolled in Android Settings"
                            is BiometricCapability.NoHardware -> "Biometric hardware not detected on this device"
                            is BiometricCapability.HardwareUnavailable -> "Biometric sensor temporarily unavailable"
                            is BiometricCapability.SecurityUpdateRequired -> "Biometric update required by system"
                            is BiometricCapability.Unsupported -> "Biometrics not supported on device"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Switch 1: Biometric App Lock
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "App Lock on Launch",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Require fingerprint or face recognition to open Personal Manager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = biometricLockEnabled,
                    onCheckedChange = onToggleBiometricLock,
                    modifier = Modifier.testTag("toggle_biometric_app_lock")
                )
            }

            // Switch 2: Lock on resume
            if (biometricLockEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Lock when App Backgrounded",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Automatically lock app when returning from home screen or other apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricLockOnResume,
                        onCheckedChange = onToggleLockOnResume,
                        modifier = Modifier.testTag("toggle_biometric_lock_resume")
                    )
                }
            }

            // Switch 3: Protect Financial Ledger
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Protect Financial Ledger & Balances",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Require biometric verification before accessing sensitive bank accounts and ledger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = biometricProtectFinance,
                    onCheckedChange = onToggleProtectFinance,
                    modifier = Modifier.testTag("toggle_biometric_protect_finance")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTestBiometrics,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_biometric_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Test Sensor", style = MaterialTheme.typography.labelMedium)
                }

                if (biometricLockEnabled && onLockAppNow != null) {
                    FilledTonalButton(
                        onClick = onLockAppNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lock_app_now_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Lock Now", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchAccountDialog(
    currentEmail: String?,
    onDismiss: () -> Unit,
    onSwitch: (email: String, name: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.SwitchAccount, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text("Switch Account", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Sign in with a different account. All your records are securely scoped to each account profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Account Email") },
                    placeholder = { Text("user@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("User Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.contains("@")) {
                        onSwitch(email.trim(), name.trim().ifBlank { email.substringBefore("@") })
                    }
                },
                enabled = email.contains("@")
            ) {
                Text("Switch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

