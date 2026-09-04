package com.example.presentation.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.ai.AiDraftAction
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.Priority
import com.example.data.local.entity.TransactionType
import com.example.ui.theme.AmberPayable
import com.example.ui.theme.CrimsonExpense
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.VioletReceivable
import com.example.util.CurrencyFormatter
import com.example.util.VoiceInputManager
import com.example.util.VoiceRecognitionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Native Voice Input Manager
    val voiceInputManager = remember { VoiceInputManager(context) }
    val voiceState by voiceInputManager.recognitionState.collectAsState()
    val isListening = voiceState is VoiceRecognitionState.Listening

    // Handle voice transcription results
    LaunchedEffect(voiceState) {
        when (val vState = voiceState) {
            is VoiceRecognitionState.PartialResult -> {
                inputText = vState.text
            }
            is VoiceRecognitionState.FinalResult -> {
                inputText = vState.text
                voiceInputManager.resetState()
            }
            is VoiceRecognitionState.Error -> {
                Toast.makeText(context, vState.message, Toast.LENGTH_SHORT).show()
                voiceInputManager.resetState()
            }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceInputManager.stopListening()
        }
    }

    // Permission launcher for RECORD_AUDIO
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceInputManager.startListening()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    val quickSuggestions = listOf(
        "Lent Sarah \$50 for lunch from my Bank account, remind me Friday at 5 PM to collect",
        "Spent \$32 on Groceries from Bank",
        "How much did I spend on groceries?",
        "What is my Bank account balance?",
        "Add task to submit expense report"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Gemini AI Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Draft & Confirm Preview Architecture",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Quick prompt suggestions
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickSuggestions) { suggestion ->
                        SuggestionChip(
                            onClick = {
                                viewModel.sendMessage(suggestion)
                            },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                        )
                    }
                }

                // Listening state banner
                AnimatedVisibility(visible = isListening) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.error,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Listening... Speak your command or query",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(if (isListening) "Listening to voice..." else "Ask or issue a compound command...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("assistant_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        enabled = !state.isLoading
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Voice Input Microphone Toggle Button
                    IconButton(
                        onClick = {
                            if (isListening) {
                                voiceInputManager.stopListening()
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    voiceInputManager.startListening()
                                } else {
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("assistant_voice_button")
                    ) {
                        Icon(
                            if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Voice Input",
                            tint = if (isListening) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val text = inputText
                                inputText = ""
                                if (isListening) {
                                    voiceInputManager.stopListening()
                                }
                                viewModel.sendMessage(text)
                            }
                        },
                        enabled = inputText.isNotBlank() && !state.isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank() && !state.isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("assistant_send_button")
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.messages) { message ->
                ChatMessageItem(
                    message = message,
                    currencyCode = state.currencyCode,
                    onRemoveDraftAction = { actionId ->
                        viewModel.removeDraftAction(message.id, actionId)
                    },
                    onConfirmAllDrafts = { drafts ->
                        viewModel.confirmAndExecuteDrafts(message.id, drafts)
                    }
                )
            }

            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Analyzing context & synthesizing draft actions...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    currencyCode: String = "USD",
    onRemoveDraftAction: (String) -> Unit,
    onConfirmAllDrafts: (List<AiDraftAction>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        // Message Bubble
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp)
            )
        }

        // Composite Draft Action Preview Cards
        if (message.draftActions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isCommitted) EmeraldIncome.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Preview,
                                contentDescription = null,
                                tint = if (message.isCommitted) EmeraldIncome else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Draft Action Preview (${message.draftActions.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (message.isCommitted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldIncome.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldIncome, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Saved to DB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldIncome,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Render individual draft items
                    message.draftActions.forEach { draft ->
                        DraftActionItemRow(
                            draft = draft,
                            currencyCode = currencyCode,
                            isCommitted = message.isCommitted,
                            onRemove = { onRemoveDraftAction(draft.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!message.isCommitted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onConfirmAllDrafts(message.draftActions) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldIncome)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Confirm & Save All Items", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DraftActionItemRow(
    draft: AiDraftAction,
    currencyCode: String = "USD",
    isCommitted: Boolean,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color, typeLabel) = when (draft) {
                is AiDraftAction.DebtDraft -> Triple(
                    if (draft.direction == DebtDirection.LENT) Icons.Default.Send else Icons.Default.CallReceived,
                    if (draft.direction == DebtDirection.LENT) VioletReceivable else AmberPayable,
                    if (draft.direction == DebtDirection.LENT) "Lend Loan" else "Borrow Debt"
                )
                is AiDraftAction.ReminderDraft -> Triple(Icons.Default.Alarm, MaterialTheme.colorScheme.primary, "Offline Reminder")
                is AiDraftAction.TransactionDraft -> Triple(
                    if (draft.type == TransactionType.INCOME) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    if (draft.type == TransactionType.INCOME) EmeraldIncome else CrimsonExpense,
                    if (draft.type == TransactionType.INCOME) "Income" else "Expense"
                )
                is AiDraftAction.TaskDraft -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.secondary, "Task")
                is AiDraftAction.BudgetDraft -> Triple(Icons.Default.PieChart, MaterialTheme.colorScheme.tertiary, "Budget")
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                when (draft) {
                    is AiDraftAction.DebtDraft -> {
                        Text(
                            text = "${if (draft.direction == DebtDirection.LENT) "Lend to" else "Borrow from"} ${draft.counterparty}: ${CurrencyFormatter.format(draft.amount, currencyCode)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Account: ${draft.accountName} • Note: ${draft.note ?: "N/A"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is AiDraftAction.ReminderDraft -> {
                        Text(
                            text = draft.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(draft.triggerTime)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is AiDraftAction.TransactionDraft -> {
                        Text(
                            text = "${draft.category}: ${CurrencyFormatter.format(draft.amount, currencyCode)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Account: ${draft.accountName} • Type: ${draft.type.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is AiDraftAction.TaskDraft -> {
                        Text(
                            text = draft.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Priority: ${draft.priority.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is AiDraftAction.BudgetDraft -> {
                        Text(
                            text = "${draft.category}: ${CurrencyFormatter.format(draft.monthlyLimit, currencyCode)}/mo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Rollover: ${if (draft.allowRollover) "Enabled (floor ${CurrencyFormatter.format(0.0, currencyCode)})" else "Disabled"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isCommitted) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
