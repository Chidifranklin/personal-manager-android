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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.auth.UserProfile
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.UserRecordCounts
import com.example.presentation.landing.QuickGoogleSignInDialog
import com.example.presentation.components.ExportReportBottomSheet
import com.example.presentation.landing.SecurityRulesModalSheet
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLanding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentUser by viewModel.currentUser.collectAsState()
    val recordCounts by viewModel.recordCounts.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSecurityRulesSheet by remember { mutableStateOf(false) }
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
                title = { Text("User Profile & Security") },
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
                            Icon(Icons.Default.CloudSync, contentDescription = "Sync Cloud")
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
                    onEditName = { showEditNameDialog = true },
                    onCopyUid = {
                        currentUser?.uid?.let { uid ->
                            clipboardManager.setText(AnnotatedString(uid))
                            Toast.makeText(context, "Copied UID to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Firebase Scoped Data Isolation Card
            item {
                UserRecordIsolationCard(
                    recordCounts = recordCounts,
                    user = currentUser,
                    onInspectRules = { showSecurityRulesSheet = true }
                )
            }

            // Firebase Cloud Synchronization Card
            item {
                CloudSyncStatusCard(
                    isSyncing = isSyncing,
                    lastSyncTime = lastSyncTime,
                    syncMessage = syncMessage,
                    onSyncNow = { viewModel.triggerCloudSync() }
                )
            }

            // App & Account Settings
            item {
                AccountPreferencesSection(
                    currencyCode = currencyCode,
                    onSelectCurrency = { showCurrencyDialog = true },
                    onExportStatements = { showExportReportSheet = true },
                    onSwitchAccount = { showQuickSwitchDialog = true },
                    onViewLandingPage = onNavigateToLanding
                )
            }

            // Danger Zone
            item {
                DangerZoneSection(
                    onSignOut = {
                        viewModel.signOut {
                            onNavigateToLanding()
                        }
                    },
                    onDeleteAccount = { showDeleteConfirmDialog = true }
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
                Text("This will permanently erase all your accounts, transactions, debts, tasks, and notes from both the local device and Firebase Firestore (/users/${currentUser?.uid ?: ""}/). This operation cannot be undone.")
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

    // Security Rules Sheet
    if (showSecurityRulesSheet) {
        SecurityRulesModalSheet(onDismiss = { showSecurityRulesSheet = false })
    }

    // Quick Switch Dialog
    if (showQuickSwitchDialog) {
        QuickGoogleSignInDialog(
            defaultEmail = "chidifranklin40@gmail.com",
            defaultName = "Franklin Chidi",
            onDismiss = { showQuickSwitchDialog = false },
            onSelectAccount = { email, name ->
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
    onEditName: () -> Unit,
    onCopyUid: () -> Unit
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // UID row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Firebase UID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = user?.uid ?: "local_default_user",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCopyUid,
                    modifier = Modifier.testTag("profile_copy_uid_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy UID", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun UserRecordIsolationCard(
    recordCounts: UserRecordCounts,
    user: UserProfile?,
    onInspectRules: () -> Unit
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldIncome)
                Column {
                    Text(
                        text = "Your Scoped Records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Strictly isolated to /users/${user?.uid ?: "current"}/",
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

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Users can only view and modify their own records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onInspectRules) {
                        Text("Rules", fontWeight = FontWeight.Bold)
                    }
                }
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
                        Text("Firebase Cloud Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    onSwitchAccount: () -> Unit,
    onViewLandingPage: () -> Unit
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
                        Text("Verify record isolation across profiles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // View Product Landing Page
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onViewLandingPage)
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Column {
                        Text("Product Landing Page", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Overview of architecture & capabilities", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DangerZoneSection(
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
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
