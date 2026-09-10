package com.example.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.data.auth.FirebaseAuthService
import com.example.data.auth.UserProfile
import com.example.data.export.FinancialActivityScope
import com.example.data.export.FinancialReportExportService
import com.example.data.export.ReportFormat
import com.example.data.export.ReportPeriodType
import com.example.data.preferences.PreferenceManager
import com.example.data.repository.PersonalManagerRepository
import com.example.data.repository.UserRecordCounts
import com.example.data.sync.FirestoreSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserProfile? = null,
    val recordCounts: UserRecordCounts = UserRecordCounts(),
    val currencyCode: String = PreferenceManager.getDefaultCurrencyCode(),
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null,
    val syncStatusMessage: String? = null,
    val securityRules: String = "",
    val actionMessage: String? = null,
    val isEditingName: Boolean = false
)

class ProfileViewModel(
    private val authService: FirebaseAuthService,
    private val repository: PersonalManagerRepository,
    private val syncService: FirestoreSyncService,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _recordCounts = MutableStateFlow(UserRecordCounts())
    val recordCounts: StateFlow<UserRecordCounts> = _recordCounts.asStateFlow()

    val currentUser: StateFlow<UserProfile?> = authService.currentUser
    val isSyncing: StateFlow<Boolean> = syncService.isSyncing
    val lastSyncTime: StateFlow<Long?> = syncService.lastSyncTimestamp
    val syncMessage: StateFlow<String?> = syncService.syncMessage
    val currencyCode: StateFlow<String> = preferenceManager.currencyCodeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferenceManager.getDefaultCurrencyCode())

    val securityRules: String = syncService.getSecurityRulesCode()

    init {
        loadRecordCounts()
        viewModelScope.launch {
            authService.currentUser.collect { user ->
                loadRecordCounts(user?.uid)
            }
        }
    }

    fun loadRecordCounts(userId: String? = currentUser.value?.uid) {
        viewModelScope.launch {
            val targetUid = userId ?: repository.currentUserId
            val counts = repository.getUserRecordCounts(targetUid)
            _recordCounts.value = counts
        }
    }

    fun triggerCloudSync() {
        val user = currentUser.value
        val uid = user?.uid ?: repository.currentUserId
        viewModelScope.launch {
            val result = syncService.syncLocalToCloud(uid)
            result.onSuccess { summary ->
                _actionMessage.value = "Cloud Sync complete: ${summary.message}"
                loadRecordCounts(uid)
            }.onFailure { err ->
                _actionMessage.value = "Sync notice: ${err.localizedMessage ?: "Sync completed with offline cache"}"
            }
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            val result = authService.updateDisplayName(newName)
            result.onSuccess {
                _actionMessage.value = "Display name updated"
            }.onFailure { err ->
                _actionMessage.value = "Failed to update name: ${err.localizedMessage}"
            }
        }
    }

    fun setCurrency(newCode: String) {
        viewModelScope.launch {
            preferenceManager.setCurrencyCode(newCode)
            _actionMessage.value = "Primary currency set to $newCode"
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        authService.signOut()
        _actionMessage.value = "Signed out"
        onSignedOut()
    }

    fun deleteAccount(onDeleted: () -> Unit) {
        val uid = currentUser.value?.uid ?: repository.currentUserId
        viewModelScope.launch {
            syncService.deleteCloudUserData(uid)
            repository.wipeUserData(uid)
            authService.deleteAccount()
            _actionMessage.value = "Account and all personal records deleted"
            onDeleted()
        }
    }

    fun switchUserQuick(email: String, name: String) {
        viewModelScope.launch {
            authService.signInWithGoogleAccount(email, name)
            _actionMessage.value = "Switched to $name ($email)"
        }
    }

    private val exportService = FinancialReportExportService(repository)

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    fun generateAndShareReport(
        context: Context,
        scope: FinancialActivityScope,
        periodType: ReportPeriodType,
        format: ReportFormat,
        startTimestamp: Long,
        endTimestamp: Long,
        dateRangeLabel: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (_isExporting.value) return
        _isExporting.value = true
        viewModelScope.launch {
            try {
                val reportData = exportService.prepareReportData(
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    periodType = periodType,
                    dateRangeLabel = dateRangeLabel,
                    currencyCode = currencyCode.value,
                    scope = scope
                )
                val file = exportService.generateAndSaveReport(context, reportData, format)
                exportService.shareReportFile(context, file, format, dateRangeLabel)
                _isExporting.value = false
                onComplete(true, null)
            } catch (e: Exception) {
                _isExporting.value = false
                onComplete(false, e.message ?: "Failed to generate report")
            }
        }
    }

    fun copyReportForGoogleSheets(
        context: Context,
        scope: FinancialActivityScope,
        periodType: ReportPeriodType,
        startTimestamp: Long,
        endTimestamp: Long,
        dateRangeLabel: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val reportData = exportService.prepareReportData(
                    startTimestamp = startTimestamp,
                    endTimestamp = endTimestamp,
                    periodType = periodType,
                    dateRangeLabel = dateRangeLabel,
                    currencyCode = currencyCode.value,
                    scope = scope
                )
                val copied = exportService.copyTsvForGoogleSheets(context, reportData)
                if (copied) {
                    onComplete(true, "Copied formatted financial data to clipboard! Open Google Sheets and paste.")
                } else {
                    onComplete(false, "Failed to copy to clipboard.")
                }
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to copy report data")
            }
        }
    }

    fun openGoogleSheets(context: Context) {
        exportService.openGoogleSheetsAppOrWeb(context)
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
