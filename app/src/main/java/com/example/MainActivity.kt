package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.assistant.AssistantScreen
import com.example.presentation.assistant.AssistantViewModel
import com.example.presentation.dashboard.DashboardScreen
import com.example.presentation.dashboard.DashboardViewModel
import com.example.presentation.finance.FinanceScreen
import com.example.presentation.finance.FinanceViewModel
import com.example.presentation.navigation.Screen
import com.example.presentation.notes.NotesScreen
import com.example.presentation.notes.NotesViewModel
import com.example.presentation.productivity.ProductivityScreen
import com.example.presentation.productivity.ProductivityViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied handled gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val app = application as PersonalManagerApplication
        val repository = app.repository
        val alarmScheduler = app.alarmScheduler
        val aiService = app.aiService
        val preferenceManager = app.preferenceManager

        setContent {
            MyApplicationTheme {
                val dashboardViewModel: DashboardViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            DashboardViewModel(repository, preferenceManager) as T
                    }
                )

                val financeViewModel: FinanceViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            FinanceViewModel(repository, preferenceManager) as T
                    }
                )

                val productivityViewModel: ProductivityViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ProductivityViewModel(repository, alarmScheduler) as T
                    }
                )

                val notesViewModel: NotesViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            NotesViewModel(repository) as T
                    }
                )

                val assistantViewModel: AssistantViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            AssistantViewModel(aiService, repository, alarmScheduler, preferenceManager) as T
                    }
                )

                var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
                var showQuickActionSheet by remember { mutableStateOf(false) }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.testTag("main_navigation_bar")
                        ) {
                            Screen.values().forEach { screen ->
                                NavigationBarItem(
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen },
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_item_${screen.route}")
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Crossfade(
                        targetState = currentScreen,
                        modifier = Modifier.padding(innerPadding)
                    ) { target ->
                        when (target) {
                            Screen.Dashboard -> DashboardScreen(
                                viewModel = dashboardViewModel,
                                onNavigateToFinance = { currentScreen = Screen.Finance },
                                onNavigateToProductivity = { currentScreen = Screen.Productivity },
                                onNavigateToAi = { currentScreen = Screen.Assistant },
                                onOpenQuickAdd = { showQuickActionSheet = true }
                            )
                            Screen.Finance -> FinanceScreen(
                                viewModel = financeViewModel
                            )
                            Screen.Productivity -> ProductivityScreen(
                                viewModel = productivityViewModel
                            )
                            Screen.Notes -> NotesScreen(
                                viewModel = notesViewModel
                            )
                            Screen.Assistant -> AssistantScreen(
                                viewModel = assistantViewModel
                            )
                        }
                    }

                    if (showQuickActionSheet) {
                        QuickActionModalSheet(
                            onDismiss = { showQuickActionSheet = false },
                            onSelectAction = { action ->
                                showQuickActionSheet = false
                                when (action) {
                                    QuickActionType.FINANCE -> currentScreen = Screen.Finance
                                    QuickActionType.PRODUCTIVITY -> currentScreen = Screen.Productivity
                                    QuickActionType.NOTES -> currentScreen = Screen.Notes
                                    QuickActionType.ASSISTANT -> currentScreen = Screen.Assistant
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class QuickActionType {
    FINANCE, PRODUCTIVITY, NOTES, ASSISTANT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionModalSheet(
    onDismiss: () -> Unit,
    onSelectAction: (QuickActionType) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Quick Creation Hub",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select a module to log or schedule:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ListItem(
                headlineContent = { Text("Log Transaction or Loan", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Record income, expenses, or money lent/borrowed") },
                leadingContent = {
                    FilledTonalIconButton(onClick = { onSelectAction(QuickActionType.FINANCE) }) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null)
                    }
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            )

            ListItem(
                headlineContent = { Text("Add Task or Agenda Reminder", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Schedule offline alarms with calendar export option") },
                leadingContent = {
                    FilledTonalIconButton(onClick = { onSelectAction(QuickActionType.PRODUCTIVITY) }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                    }
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            )

            ListItem(
                headlineContent = { Text("Write Secure Note", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Markdown notes with biometric fingerprint/PIN gate") },
                leadingContent = {
                    FilledTonalIconButton(onClick = { onSelectAction(QuickActionType.NOTES) }) {
                        Icon(Icons.Default.Description, contentDescription = null)
                    }
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            )

            ListItem(
                headlineContent = { Text("Gemini AI Assistant", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Use natural language to draft compound actions") },
                leadingContent = {
                    FilledTonalIconButton(onClick = { onSelectAction(QuickActionType.ASSISTANT) }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    }
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
