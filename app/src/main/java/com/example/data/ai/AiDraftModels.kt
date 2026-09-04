package com.example.data.ai

import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.Priority
import com.example.data.local.entity.TransactionType
import java.util.UUID

sealed class AiDraftAction {
    abstract val id: String

    data class TaskDraft(
        override val id: String = UUID.randomUUID().toString(),
        var title: String,
        var priority: Priority = Priority.MEDIUM,
        var dueDate: Long? = null
    ) : AiDraftAction()

    data class TransactionDraft(
        override val id: String = UUID.randomUUID().toString(),
        var accountName: String,
        var type: TransactionType,
        var amount: Double,
        var category: String,
        var note: String? = null
    ) : AiDraftAction()

    data class DebtDraft(
        override val id: String = UUID.randomUUID().toString(),
        var counterparty: String,
        var direction: DebtDirection,
        var amount: Double,
        var accountName: String,
        var dueDate: Long? = null,
        var note: String? = null
    ) : AiDraftAction()

    data class ReminderDraft(
        override val id: String = UUID.randomUUID().toString(),
        var title: String,
        var triggerTime: Long,
        var isCritical: Boolean = false
    ) : AiDraftAction()

    data class BudgetDraft(
        override val id: String = UUID.randomUUID().toString(),
        var category: String,
        var monthlyLimit: Double,
        var allowRollover: Boolean = false
    ) : AiDraftAction()
}

data class AssistantTurnResult(
    val replyText: String,
    val draftActions: List<AiDraftAction> = emptyList()
)
