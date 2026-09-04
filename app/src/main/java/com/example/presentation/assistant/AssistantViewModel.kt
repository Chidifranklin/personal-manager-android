package com.example.presentation.assistant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiDraftAction
import com.example.data.ai.GeminiAssistantService
import com.example.data.repository.PersonalManagerRepository
import com.example.util.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val draftActions: List<AiDraftAction> = emptyList(),
    val isCommitted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val pendingActionDrafts: List<AiDraftAction> = emptyList()
)

class AssistantViewModel(
    private val assistantService: GeminiAssistantService,
    private val repository: PersonalManagerRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AssistantUiState(
            messages = listOf(
                ChatMessage(
                    isUser = false,
                    text = "Hello! I am your Personal Manager Executive AI Assistant. You can ask me to log expenses, track loans, schedule agenda alarms, or answer questions about your financial health.\n\nTry compound commands like:\n• \"Lent Sarah \$50 for lunch from my Bank account, remind me Friday at 5 PM to collect\"\n• \"Spent \$25 on Groceries from Bank\"\n• \"How much did I spend this month?\""
                )
            )
        )
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(isUser = true, text = userText.trim())
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true
        )

        viewModelScope.launch {
            try {
                val result = assistantService.processUserPrompt(userText)
                val assistantMessage = ChatMessage(
                    isUser = false,
                    text = result.replyText,
                    draftActions = result.draftActions
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + assistantMessage,
                    isLoading = false
                )
            } catch (e: Exception) {
                val errorMessage = ChatMessage(
                    isUser = false,
                    text = "I encountered an error processing your request: ${e.localizedMessage}. Please try again."
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errorMessage,
                    isLoading = false
                )
            }
        }
    }

    fun removeDraftAction(messageId: String, actionId: String) {
        val updated = _uiState.value.messages.map { msg ->
            if (msg.id == messageId) {
                msg.copy(draftActions = msg.draftActions.filter { it.id != actionId })
            } else msg
        }
        _uiState.value = _uiState.value.copy(messages = updated)
    }

    fun confirmAndExecuteDrafts(messageId: String, drafts: List<AiDraftAction>) {
        viewModelScope.launch {
            val accounts = repository.accountsFlow.firstOrNull() ?: emptyList()
            val defaultAccount = repository.defaultAccountFlow.firstOrNull() ?: accounts.firstOrNull()
            val defaultAccountId = defaultAccount?.accountId ?: ""

            for (draft in drafts) {
                when (draft) {
                    is AiDraftAction.TaskDraft -> {
                        repository.createTask(
                            title = draft.title,
                            priority = draft.priority,
                            dueDate = draft.dueDate
                        )
                    }
                    is AiDraftAction.TransactionDraft -> {
                        val targetAccount = accounts.firstOrNull { it.name.equals(draft.accountName, ignoreCase = true) }
                            ?: defaultAccount
                        if (targetAccount != null) {
                            repository.logTransaction(
                                accountId = targetAccount.accountId,
                                type = draft.type,
                                amount = draft.amount,
                                category = draft.category,
                                note = draft.note
                            )
                        }
                    }
                    is AiDraftAction.DebtDraft -> {
                        val targetAccount = accounts.firstOrNull { it.name.equals(draft.accountName, ignoreCase = true) }
                            ?: defaultAccount
                        if (targetAccount != null) {
                            repository.createDebt(
                                counterpartyName = draft.counterparty,
                                direction = draft.direction,
                                principalAmount = draft.amount,
                                accountId = targetAccount.accountId,
                                dueDate = draft.dueDate,
                                note = draft.note
                            )
                        }
                    }
                    is AiDraftAction.ReminderDraft -> {
                        val reminder = repository.createReminder(
                            title = draft.title,
                            triggerTime = draft.triggerTime,
                            isCritical = draft.isCritical,
                            exportedToCalendar = true
                        )
                        alarmScheduler.scheduleReminderAlarm(reminder)
                    }
                    is AiDraftAction.BudgetDraft -> {
                        repository.saveBudget(
                            category = draft.category,
                            monthlyLimit = draft.monthlyLimit,
                            allowRollover = draft.allowRollover,
                            rolloverFloor = 0.0
                        )
                    }
                }
            }

            // Mark message as committed
            val updatedMessages = _uiState.value.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(isCommitted = true)
                } else msg
            }

            val confirmationMsg = ChatMessage(
                isUser = false,
                text = "✅ Successfully confirmed and saved ${drafts.size} action(s) to your Personal Manager database!"
            )

            _uiState.value = _uiState.value.copy(
                messages = updatedMessages + confirmationMsg
            )
        }
    }
}
