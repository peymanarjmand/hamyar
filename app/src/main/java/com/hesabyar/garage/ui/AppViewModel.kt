package com.hesabyar.garage.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hesabyar.garage.AppContainer
import com.hesabyar.garage.data.*
import com.hesabyar.garage.data.local.*
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.util.PersianDate
import com.hesabyar.garage.util.toPersianDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface AppAccessState {
    data object Loading : AppAccessState
    data object Unlocked : AppAccessState
    data class Locked(
        val settings: AppSecuritySettings,
        val sessionId: Long,
    ) : AppAccessState
}

internal fun resolveAppAccessState(
    security: AppSecuritySettings?,
    sessionUnlocked: Boolean,
    sessionId: Long,
): AppAccessState = when {
    security == null -> AppAccessState.Loading
    !security.lockEnabled || sessionUnlocked -> AppAccessState.Unlocked
    else -> AppAccessState.Locked(security, sessionId)
}

internal fun shouldLockAfterBackground(timeoutSeconds: Int, elapsedMillis: Long): Boolean {
    require(timeoutSeconds >= 0)
    return elapsedMillis.coerceAtLeast(0L) >= Math.multiplyExact(timeoutSeconds.toLong(), 1_000L)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.garageRepository
    private val settingsRepository = container.settingsRepository
    private val appSecurityRepository = container.appSecurityRepository
    private val transactionDeletionService = container.transactionDeletionService

    val securitySettings: StateFlow<AppSecuritySettings?> = appSecurityRepository.settings
        .map { it as AppSecuritySettings? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val sessionUnlocked = MutableStateFlow(false)
    private val lockSessionId = MutableStateFlow(1L)
    val appAccess: StateFlow<AppAccessState> = combine(
        securitySettings,
        sessionUnlocked,
        lockSessionId,
    ) { security, unlocked, sessionId ->
        resolveAppAccessState(security, unlocked, sessionId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppAccessState.Loading)

    private var backgroundedAtElapsedRealtime: Long? = null
    private var automaticallyPromptedSessionId = Long.MIN_VALUE

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val employees = repository.employees().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val years = repository.financialYears().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selectedYear = settings.map { it.selectedYear }.stateIn(viewModelScope, SharingStarted.Eagerly, PersianDate.today().year)

    val incomes = selectedYear.flatMapLatest(repository::incomeRows)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val incomeHistory = selectedYear.flatMapLatest(repository::incomeHistoryRows)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val balances = selectedYear.flatMapLatest(repository::balances)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val custodyAccounts = selectedYear.flatMapLatest(repository::accountCustody)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalIncome = selectedYear.flatMapLatest(repository::totalIncome)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val remainder = selectedYear.flatMapLatest(repository::remainder)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val transfers = selectedYear.flatMapLatest(repository::transfers)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transferHistory = selectedYear.flatMapLatest(repository::transferHistory)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settlements = selectedYear.flatMapLatest(repository::settlements)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settlementHistory = selectedYear.flatMapLatest(repository::settlementHistory)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeEmployeeCount = repository.activeEmployeeCount(LocalDate.now().toEpochDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val suggestions = balances.map(AccountingEngine::suggestSettlement)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedYearEntity = combine(years, selectedYear) { list, year -> list.firstOrNull { it.year == year } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val _pendingRecoveryCode = MutableStateFlow<String?>(null)
    val pendingRecoveryCode: StateFlow<String?> = _pendingRecoveryCode.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureYear(PersianDate.today().year) }
    }

    fun selectYear(year: Int) = launchSilent { settingsRepository.selectYear(year) }

    fun finishOnboarding(shopName: String, managerName: String, phone: String, startDate: PersianDate, onSuccess: () -> Unit) = launchAction(onSuccess = { onSuccess() }) {
        val year = startDate.year
        repository.ensureYear(year)
        repository.addEmployee(AddEmployeeCommand(managerName, phone, EmployeeRole.PAINTER, startDate.toEpochDay()))
        settingsRepository.finishOnboarding(shopName, PersianDate.today().year)
    }

    fun addEmployee(command: AddEmployeeCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.addEmployee(command) }
    fun updateEmployee(command: UpdateEmployeeCommand, onSuccess: () -> Unit) = launchAction(onSuccess = { onSuccess() }) {
        repository.updateEmployee(command)
    }
    fun addAbsence(employeeId: Long, start: Long, returned: Long?, note: String, onSuccess: (Long) -> Unit) = launchAction(onSuccess) {
        repository.addAbsence(employeeId, start, returned, note)
    }
    fun updateAbsence(absenceId: Long, employeeId: Long, start: Long, returned: Long?, note: String, onSuccess: () -> Unit) = launchAction(onSuccess = { onSuccess() }) {
        repository.updateAbsence(absenceId, employeeId, start, returned, note)
    }
    fun addEmployment(employeeId: Long, start: Long, end: Long?, onSuccess: (Long) -> Unit) = launchAction(onSuccess) {
        repository.addEmploymentPeriod(employeeId, start, end)
    }
    fun updateEmployment(periodId: Long, employeeId: Long, start: Long, end: Long?, onSuccess: () -> Unit) = launchAction(onSuccess = { onSuccess() }) {
        repository.updateEmploymentPeriod(periodId, employeeId, start, end)
    }
    fun endEmployment(employeeId: Long, lastWorkingDay: Long, onSuccess: () -> Unit) = launchAction(onSuccess = { onSuccess() }) {
        repository.endEmployment(employeeId, lastWorkingDay)
    }
    fun addCard(command: AddCardCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.addCard(command) }
    fun registerIncome(command: RegisterIncomeCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.registerIncome(command) }
    fun transfer(command: TransferCustodyCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.transferCustody(command) }
    fun settle(command: RecordSettlementCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.recordSettlement(command) }
    fun correctIncome(command: CorrectIncomeCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.correctIncome(command) }
    fun correctTransfer(command: CorrectTransferCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.correctTransfer(command) }
    fun correctSettlement(command: CorrectSettlementCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.correctSettlement(command) }
    fun deleteIncome(
        eventId: Long,
        reason: String,
        password: CharArray,
        onSuccess: () -> Unit,
        onFinished: () -> Unit,
    ) =
        launchSecurityOperation(
            incorrectMessage = "رمز برنامه درست نیست؛ تراکنش حذف نشد",
            successMessage = "تراکنش از محاسبات حذف شد و سابقه آن در تاریخچه باقی ماند",
            operation = { transactionDeletionService.deleteIncome(eventId, reason, password) },
            onSuccess = { onSuccess() },
            onFinished = onFinished,
        )
    fun deleteTransfer(
        transferId: Long,
        reason: String,
        password: CharArray,
        onSuccess: () -> Unit,
        onFinished: () -> Unit,
    ) =
        launchSecurityOperation(
            incorrectMessage = "رمز برنامه درست نیست؛ تراکنش حذف نشد",
            successMessage = "تراکنش از محاسبات حذف شد و سابقه آن در تاریخچه باقی ماند",
            operation = { transactionDeletionService.deleteTransfer(transferId, reason, password) },
            onSuccess = { onSuccess() },
            onFinished = onFinished,
        )
    fun deleteSettlement(
        paymentId: Long,
        reason: String,
        password: CharArray,
        onSuccess: () -> Unit,
        onFinished: () -> Unit,
    ) =
        launchSecurityOperation(
            incorrectMessage = "رمز برنامه درست نیست؛ تراکنش حذف نشد",
            successMessage = "تراکنش از محاسبات حذف شد و سابقه آن در تاریخچه باقی ماند",
            operation = { transactionDeletionService.deleteSettlement(paymentId, reason, password) },
            onSuccess = { onSuccess() },
            onFinished = onFinished,
        )

    fun closeYear(backupChecksum: String, backupAt: Long, onSuccess: (Long) -> Unit) = launchAction(onSuccess) {
        repository.closeFinancialYear(selectedYear.value, backupChecksum, backupAt)
    }

    fun createBackup(password: CharArray, onSuccess: (BackupExport) -> Unit) = launchSilent(onSuccess) {
        container.backupService.export(password)
    }

    fun recordBackupSuccess(checksum: String) {
        viewModelScope.launch { settingsRepository.recordBackup(checksum); _messages.emit("فایل پشتیبان با موفقیت ذخیره شد") }
    }

    fun setAbsenceGraceDays(days: Int, onSuccess: () -> Unit = {}) = launchAction(onSuccess = { onSuccess() }) {
        settingsRepository.setAbsenceGraceDays(days)
    }

    fun restoreBackup(bytes: ByteArray, password: CharArray, onSuccess: (RestoreSummary) -> Unit) = launchAction(onSuccess) {
        container.backupService.restore(bytes, password)
    }

    fun revealCard(accountId: Long, onSuccess: (String) -> Unit) = launchSilent(onSuccess) { repository.revealCard(accountId) }
    fun revealMovementCard(encryptedValue: String, onSuccess: (String) -> Unit) =
        launchSilent(onSuccess) { repository.revealMovementCard(encryptedValue) }
    fun notifyMessage(message: String) { viewModelScope.launch { _messages.emit(message) } }

    fun onAppBackgrounded() {
        if (securitySettings.value?.lockEnabled == true) {
            backgroundedAtElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    fun onAppForegrounded() {
        val backgroundedAt = backgroundedAtElapsedRealtime ?: return
        backgroundedAtElapsedRealtime = null
        val security = securitySettings.value ?: return
        if (!security.lockEnabled) return
        val elapsed = (SystemClock.elapsedRealtime() - backgroundedAt).coerceAtLeast(0L)
        if (shouldLockAfterBackground(security.lockTimeoutSeconds, elapsed)) lockNow()
    }

    fun lockNow() {
        if (securitySettings.value?.lockEnabled != true || !sessionUnlocked.value) return
        sessionUnlocked.value = false
        lockSessionId.value = if (lockSessionId.value == Long.MAX_VALUE) 1L else lockSessionId.value + 1L
    }

    fun shouldAutomaticallyPromptDeviceAuthentication(sessionId: Long): Boolean {
        if (automaticallyPromptedSessionId == sessionId) return false
        automaticallyPromptedSessionId = sessionId
        return true
    }

    fun enableAppLock(
        password: CharArray,
        recoveryQuestion: String,
        recoveryAnswer: CharArray,
        lockTimeoutSeconds: Int,
        onSuccess: (String) -> Unit,
    ) = launchSecurityOperation(
        incorrectMessage = "اطلاعات امنیتی معتبر نیست",
        successMessage = "قفل برنامه فعال شد",
        operation = {
            appSecurityRepository.enableLock(
                password = password,
                recoveryQuestion = recoveryQuestion,
                recoveryAnswer = recoveryAnswer,
                lockTimeoutSeconds = lockTimeoutSeconds,
            )
        },
        onSuccess = { recoveryCode ->
            sessionUnlocked.value = true
            _pendingRecoveryCode.value = recoveryCode
            onSuccess(recoveryCode)
        },
    )

    fun unlockWithAppPassword(password: CharArray) = launchSecurityOperation(
        incorrectMessage = "رمز برنامه درست نیست",
        operation = { appSecurityRepository.verifyPassword(password) },
        onSuccess = { sessionUnlocked.value = true },
    )

    fun recordDeviceAuthenticationSuccess() {
        backgroundedAtElapsedRealtime = null
        launchSilent(onSuccess = {
            sessionUnlocked.value = true
        }) {
            appSecurityRepository.recordDeviceAuthenticationSuccess()
        }
    }

    fun changeAppPassword(
        currentPassword: CharArray,
        newPassword: CharArray,
        onSuccess: () -> Unit = {},
    ) = launchSecurityOperation(
        incorrectMessage = "رمز فعلی برنامه درست نیست",
        successMessage = "رمز برنامه تغییر کرد",
        operation = { appSecurityRepository.changePassword(currentPassword, newPassword) },
        onSuccess = { onSuccess() },
    )

    fun resetAppPasswordWithAnswer(
        recoveryAnswer: CharArray,
        newPassword: CharArray,
        onSuccess: () -> Unit = {},
    ) = launchSecurityOperation(
        incorrectMessage = "پاسخ بازیابی درست نیست",
        successMessage = "رمز جدید برنامه ثبت شد",
        operation = { appSecurityRepository.resetPasswordWithRecoveryAnswer(recoveryAnswer, newPassword) },
        onSuccess = {
            sessionUnlocked.value = true
            onSuccess()
        },
    )

    fun resetAppPasswordWithCode(
        recoveryCode: String,
        newPassword: CharArray,
        onSuccess: (String) -> Unit,
    ) = launchSecurityOperation(
        incorrectMessage = "کد بازیابی درست نیست",
        successMessage = "رمز جدید برنامه ثبت شد؛ کد بازیابی تازه را نگه دارید",
        operation = { appSecurityRepository.resetPasswordWithRecoveryCode(recoveryCode, newPassword) },
        onSuccess = { nextCode ->
            // The recovery credential has already authenticated this session. Unlock before
            // presenting the one-time replacement code so it can never render over the gate.
            sessionUnlocked.value = true
            _pendingRecoveryCode.value = nextCode
            onSuccess(nextCode)
        },
    )

    fun disableAppLock(password: CharArray, onSuccess: () -> Unit = {}) = launchSecurityOperation(
        incorrectMessage = "رمز برنامه درست نیست",
        successMessage = "قفل برنامه غیرفعال شد",
        operation = { appSecurityRepository.disableLock(password) },
        onSuccess = {
            sessionUnlocked.value = true
            onSuccess()
        },
    )

    fun updateRecoveryQuestion(
        password: CharArray,
        question: String,
        answer: CharArray,
        onSuccess: () -> Unit = {},
    ) = launchSecurityOperation(
        incorrectMessage = "رمز برنامه درست نیست",
        successMessage = "پرسش بازیابی به‌روزرسانی شد",
        operation = { appSecurityRepository.updateRecoveryQuestion(password, question, answer) },
        onSuccess = { onSuccess() },
    )

    fun regenerateRecoveryCode(password: CharArray, onSuccess: (String) -> Unit) =
        launchSecurityOperation(
            incorrectMessage = "رمز برنامه درست نیست",
            successMessage = "کد بازیابی تازه ساخته شد",
            operation = { appSecurityRepository.regenerateRecoveryCode(password) },
            onSuccess = { recoveryCode ->
                _pendingRecoveryCode.value = recoveryCode
                onSuccess(recoveryCode)
            },
        )

    fun acknowledgeRecoveryCode() {
        _pendingRecoveryCode.value = null
    }

    fun setDeviceAuthenticationEnabled(enabled: Boolean, onSuccess: () -> Unit = {}) =
        launchSilent(onSuccess = {
            if (enabled) {
                backgroundedAtElapsedRealtime = null
                sessionUnlocked.value = true
            }
            onSuccess()
            viewModelScope.launch {
                _messages.emit(
                    if (enabled) "باز کردن با قفل گوشی فعال شد" else "باز کردن با قفل گوشی غیرفعال شد",
                )
            }
        }) { appSecurityRepository.setDeviceAuthEnabled(enabled) }

    fun setAppLockTimeout(seconds: Int) = launchSilent(onSuccess = {
        viewModelScope.launch { _messages.emit("زمان قفل خودکار به‌روزرسانی شد") }
    }) { appSecurityRepository.setLockTimeoutSeconds(seconds) }

    fun accounts(employeeId: Long): Flow<List<MoneyAccountEntity>> = repository.accounts(employeeId)
    fun employeeFlow(employeeId: Long): Flow<EmployeeEntity?> = repository.employeeFlow(employeeId)
    fun employment(employeeId: Long): Flow<List<EmploymentPeriodEntity>> = repository.employmentPeriods(employeeId)
    fun absences(employeeId: Long): Flow<List<AbsencePeriodEntity>> = repository.absences(employeeId)
    fun history(employeeId: Long): Flow<List<PersonHistoryRow>> = selectedYear.flatMapLatest { repository.personHistory(employeeId, it) }
    fun eligibility(eventId: Long): Flow<List<EligibilitySnapshotEntity>> = repository.eligibility(eventId)

    suspend fun employee(id: Long): EmployeeEntity? = repository.employee(id)
    suspend fun employmentPeriod(id: Long): EmploymentPeriodEntity? = repository.employmentPeriod(id)
    suspend fun absence(id: Long): AbsencePeriodEntity? = repository.absence(id)
    suspend fun incomeEvent(id: Long): IncomeEventEntity? = repository.incomeEvent(id)
    suspend fun account(id: Long): MoneyAccountEntity? = repository.account(id)
    suspend fun correctionAudit(entityType: String, originalId: Long): AuditLogEntity? =
        repository.correctionAudit(entityType, originalId)
    suspend fun deletionAudit(entityType: String, entityId: Long): AuditLogEntity? =
        repository.deletionAudit(entityType, entityId)
    suspend fun transferDetail(id: Long): TransferDetailData? = repository.transferDetail(id)
    suspend fun settlementDetail(id: Long): SettlementDetailData? = repository.settlementDetail(id)
    suspend fun previewEligibility(epochDay: Long): List<EligibilityPreview> = repository.previewEligibility(epochDay)

    private fun <T> launchAction(onSuccess: (T) -> Unit = {}, block: suspend () -> T) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { block() }
                .onSuccess {
                    onSuccess(it)
                    _messages.emit("با موفقیت ثبت شد")
                }
                .onFailure { _messages.emit(it.message ?: "عملیات انجام نشد") }
            _busy.value = false
        }
    }

    private fun <T> launchSilent(onSuccess: (T) -> Unit = {}, block: suspend () -> T) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { block() }
                .onSuccess(onSuccess)
                .onFailure { _messages.emit(it.message ?: "عملیات انجام نشد") }
            _busy.value = false
        }
    }

    private fun <T> launchSecurityOperation(
        incorrectMessage: String,
        successMessage: String? = null,
        operation: suspend () -> SecurityResult<T>,
        onSuccess: (T) -> Unit,
        onFinished: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _busy.value = true
            try {
                runCatching { withContext(Dispatchers.Default) { operation() } }
                    .onSuccess { result ->
                        when (result) {
                            is SecurityResult.Success -> {
                                onSuccess(result.value)
                                successMessage?.let { _messages.emit(it) }
                            }
                            SecurityResult.Incorrect -> _messages.emit(incorrectMessage)
                            is SecurityResult.Blocked -> {
                                val remainingMillis = (result.retryAtEpochMillis - System.currentTimeMillis())
                                    .coerceAtLeast(1L)
                                val remainingSeconds = (remainingMillis + 999L) / 1_000L
                                _messages.emit(
                                    "تلاش‌های ناموفق زیاد بود؛ ${remainingSeconds.toString().toPersianDigits()} ثانیه دیگر دوباره امتحان کنید",
                                )
                            }
                        }
                    }
                    .onFailure { _messages.emit(it.message ?: "عملیات امنیتی انجام نشد") }
            } finally {
                _busy.value = false
                onFinished()
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}
