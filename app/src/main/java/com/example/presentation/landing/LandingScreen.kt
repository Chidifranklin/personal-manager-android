package com.example.presentation.landing

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    viewModel: LandingViewModel,
    onContinueToApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val currentUser by viewModel.currentUser.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    var showEmailAuthDialog by remember { mutableStateOf(false) }
    var showSecurityRulesDialog by remember { mutableStateOf(false) }
    var showQuickGooglePicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "Personal Manager Logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Personal Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Firebase Cloud Backend",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    if (currentUser != null) {
                        FilledTonalButton(
                            onClick = onContinueToApp,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("landing_enter_app_button")
                        ) {
                            Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open App")
                        }
                    } else {
                        TextButton(
                            onClick = { showEmailAuthDialog = true },
                            modifier = Modifier.testTag("landing_email_signin_top_button")
                        ) {
                            Text("Email Login")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // Hero Section
            item {
                HeroBannerSection(
                    currentUser = currentUser,
                    isLoading = isAuthLoading,
                    onGoogleSignIn = {
                        activity?.let {
                            viewModel.signInWithGoogle(it) {
                                onContinueToApp()
                            }
                        } ?: run {
                            viewModel.signInWithGoogleDirect(onSuccess = onContinueToApp)
                        }
                    },
                    onQuickGooglePicker = { showQuickGooglePicker = true },
                    onContinueToApp = onContinueToApp,
                    onViewSecurityRules = { showSecurityRulesDialog = true }
                )
            }

            // Trust & Architecture Badges
            item {
                TrustBadgesRow(onBadgeClick = { showSecurityRulesDialog = true })
            }

            // Key Feature Highlights
            item {
                FeatureShowcaseSection()
            }

            // Security & Isolation Blueprint Card
            item {
                SecurityBlueprintCard(
                    onInspectRules = { showSecurityRulesDialog = true }
                )
            }

            // Testimonial & Data Scoping Guarantee
            item {
                DataPrivacyGuaranteeSection(
                    onSignInClicked = { showQuickGooglePicker = true }
                )
            }
        }
    }

    // Quick Google Account Picker Dialog
    if (showQuickGooglePicker) {
        QuickGoogleSignInDialog(
            defaultEmail = viewModel.defaultSuggestedEmail,
            defaultName = viewModel.defaultSuggestedName,
            onDismiss = { showQuickGooglePicker = false },
            onSelectAccount = { email, name ->
                showQuickGooglePicker = false
                viewModel.signInWithGoogleDirect(email, name) {
                    onContinueToApp()
                }
            }
        )
    }

    // Email & Password Auth Dialog
    if (showEmailAuthDialog) {
        EmailAuthDialog(
            onDismiss = { showEmailAuthDialog = false },
            onSignIn = { email, pass ->
                showEmailAuthDialog = false
                viewModel.signInWithEmail(email, pass) {
                    onContinueToApp()
                }
            },
            onSignUp = { email, pass, name ->
                showEmailAuthDialog = false
                viewModel.signUpWithEmail(email, pass, name) {
                    onContinueToApp()
                }
            }
        )
    }

    // Security Rules Inspector Sheet
    if (showSecurityRulesDialog) {
        SecurityRulesModalSheet(
            onDismiss = { showSecurityRulesDialog = false }
        )
    }
}

@Composable
private fun HeroBannerSection(
    currentUser: com.example.data.auth.UserProfile?,
    isLoading: Boolean,
    onGoogleSignIn: () -> Unit,
    onQuickGooglePicker: () -> Unit,
    onContinueToApp: () -> Unit,
    onViewSecurityRules: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Badge Chip
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldIncome)
                    )
                    Text(
                        text = "Firebase Firestore • Google Identity",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Headline
            Text(
                text = "Master Your Financial Ledger, Agenda & Notes with Zero Leakage.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Subtitle
            Text(
                text = "Double-entry multi-currency accounting, counterparty debt ledgers, exact offline alarms, and biometric notes — backed by Firebase with strict per-user record isolation.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(4.dp))

            // Action Buttons
            if (currentUser != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUser.displayName.firstOrNull()?.uppercase() ?: "U",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column {
                                Text(
                                    text = "Signed in as ${currentUser.displayName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentUser.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onContinueToApp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("landing_continue_as_user_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Launch My Personal Manager")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Primary Google Sign-In CTA
                    Button(
                        onClick = onGoogleSignIn,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(4.dp, RoundedCornerShape(14.dp))
                            .testTag("landing_google_signin_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            // Stylized Google 'G' icon badge
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF4285F4)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Secondary Quick / Emulator One-Tap Sign In
                    OutlinedButton(
                        onClick = onQuickGooglePicker,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("landing_quick_google_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Select Google Profile (Demo Mode)")
                    }

                    // Guest preview
                    TextButton(
                        onClick = onContinueToApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("landing_guest_preview_button")
                    ) {
                        Text("Explore Dashboard as Guest")
                    }
                }
            }
        }
    }
}

@Composable
private fun TrustBadgesRow(onBadgeClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TrustBadgeItem(icon = Icons.Default.Lock, title = "Per-User Scoped", subtitle = "No Cross-Access", onClick = onBadgeClick)
        TrustBadgeItem(icon = Icons.Default.CloudSync, title = "Firestore Backend", subtitle = "Real-Time Sync", onClick = onBadgeClick)
        TrustBadgeItem(icon = Icons.Default.Fingerprint, title = "Biometric Gate", subtitle = "Hardware Secure", onClick = onBadgeClick)
    }
}

@Composable
private fun TrustBadgeItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeatureShowcaseSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Enterprise-Grade Personal Infrastructure",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Every module is designed for accuracy, speed, and privacy:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FeatureCard(
            icon = Icons.Default.AccountBalance,
            title = "Double-Entry Finance & Currencies",
            description = "Track liquid pools across Cash, Bank, and Savings accounts. Instant currency conversions for 24 global currencies.",
            accentColor = EmeraldIncome
        )

        FeatureCard(
            icon = Icons.Default.Handshake,
            title = "Counterparty Debt Ledger",
            description = "Append-only immutable record of money lent and borrowed. Track repayment settlements with balance updates.",
            accentColor = VioletReceivable
        )

        FeatureCard(
            icon = Icons.Default.Alarm,
            title = "Precision Alarms & Productivity",
            description = "Offline exact alarms that ring even through device reboots. Priority-tiered task checklists with calendar export.",
            accentColor = MaterialTheme.colorScheme.primary
        )

        FeatureCard(
            icon = Icons.Default.Description,
            title = "Biometric Markdown Vault",
            description = "Private notes with optional fingerprint and PIN security. Locked notes are strictly isolated and hidden from AI scans.",
            accentColor = AmberPayable
        )

        FeatureCard(
            icon = Icons.Default.FileDownload,
            title = "Export Statements: PDF, Excel & Google Sheets",
            description = "Generate clean paginated PDF audit statements, native Microsoft Excel (.xlsx) workbooks with SUM formulas, universal CSVs, or one-tap Google Sheets sync.",
            accentColor = Color(0xFF107C41)
        )
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SecurityBlueprintCard(onInspectRules: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Per-User Record Isolation Guarantee",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Under the Firebase backend architecture, every document is partitioned under /users/{userId}/. Firestore security rules strictly evaluate that request.auth.uid == userId. Cross-user leaks or unauthorized modifications are physically prevented by the Firebase security engine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            OutlinedButton(
                onClick = onInspectRules,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("landing_view_rules_button")
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Inspect Firebase Security Rules")
            }
        }
    }
}

@Composable
private fun DataPrivacyGuaranteeSection(onSignInClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Ready to take complete control of your records?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Sign in securely with Google to enable automatic cloud backup, record synchronization, and end-to-end device privacy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        FilledTonalButton(
            onClick = onSignInClicked,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("landing_bottom_get_started_button")
        ) {
            Icon(Icons.Default.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Get Started with Google")
        }
    }
}

@Composable
fun QuickGoogleSignInDialog(
    defaultEmail: String,
    defaultName: String,
    onDismiss: () -> Unit,
    onSelectAccount: (String, String) -> Unit
) {
    var customEmail by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Text("Google Sign-In")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Select an authenticated Google account for testing user isolation and Firebase syncing:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Primary user card (Detected from prompt/metadata)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectAccount(defaultEmail, defaultName) }
                        .testTag("quick_google_primary_account")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = defaultName.first().toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(defaultName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(defaultEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldIncome)
                    }
                }

                // Alternate demo user (to test user record separation)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectAccount("alex.developer@gmail.com", "Alex River") }
                        .testTag("quick_google_secondary_account")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(VioletReceivable),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Alex River (Test Isolation)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("alex.developer@gmail.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (!showCustomInput) {
                    TextButton(
                        onClick = { showCustomInput = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Sign in with another Google Email...")
                    }
                } else {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customEmail,
                        onValueChange = { customEmail = it },
                        label = { Text("Google Email") },
                        placeholder = { Text("user@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (customEmail.isNotBlank()) {
                                onSelectAccount(customEmail.trim(), customName.ifBlank { "Google User" })
                            }
                        },
                        enabled = customEmail.contains("@"),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Authenticate Custom Account")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmailAuthDialog(
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isRegisterMode) "Create Firebase Account" else "Sign In with Email")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isRegisterMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = { isRegisterMode = !isRegisterMode },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (isRegisterMode) "Have an account? Sign in" else "Need an account? Register")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isNotBlank() && password.length >= 6) {
                        if (isRegisterMode) {
                            onSignUp(email.trim(), password, name.ifBlank { "User" })
                        } else {
                            onSignIn(email.trim(), password)
                        }
                    }
                },
                enabled = email.contains("@") && password.length >= 6
            ) {
                Text(if (isRegisterMode) "Register" else "Sign In")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityRulesModalSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Firestore Security Blueprint",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Personal Manager enforces zero cross-user access using authenticated Firestore rules:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = """
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Isolated per-user document root:
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;

      match /{allSubcollections=**} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
}
                    """.trimIndent(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "✓ Every account, transaction, debt, task, and note has a userId field.\n✓ When you log in with Google, only records matching your UID are fetched or modified.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close Inspector")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
