package com.example.data.model

import com.example.data.local.entity.BudgetEntity
import com.example.data.local.entity.DebtDirection
import com.example.data.local.entity.DebtLedgerEntity
import com.example.data.local.entity.DebtRepaymentEntity

data class DebtWithDetails(
    val debt: DebtLedgerEntity,
    val repayments: List<DebtRepaymentEntity>
) {
    val totalRepaid: Double = repayments.sumOf { it.amountPaid }
    val remainingAmount: Double = (debt.principalAmount - totalRepaid).coerceAtLeast(0.0)
    val isFullySettled: Boolean = remainingAmount <= 0.001
}

data class FinancialSummary(
    val liquidCash: Double = 0.0,
    val totalReceivables: Double = 0.0, // Active money lent out
    val totalPayables: Double = 0.0,    // Active money borrowed
    val netWorth: Double = 0.0          // liquidCash + totalReceivables - totalPayables
)

data class CategoryBudgetProgress(
    val budget: BudgetEntity,
    val spentThisMonth: Double,
    val effectiveLimit: Double,
    val remaining: Double,
    val percentUsed: Float
)
