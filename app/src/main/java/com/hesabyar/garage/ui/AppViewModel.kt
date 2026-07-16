package com.hesabyar.garage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hesabyar.garage.AppContainer
import com.hesabyar.garage.data.*
import com.hesabyar.garage.data.local.*
import com.hesabyar.garage.domain.*
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.garageRepository
    private val settingsRepository = container.settingsRepository

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val employees = repository.employees().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val years = repository.financialYears().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selectedYear = settings.map { it.selectedYear }.stateIn(viewModelScope, SharingStarted.Eagerly, PersianDate.today().year)

    val incomes = selectedYear.flatMapLatest(repository::incomeRows)
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
    val settlements = selectedYear.flatMapLatest(repository::settlements)
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
    fun addAbsence(employeeId: Long, start: Long, returned: Long?, note: String, onSuccess: (Long) -> Unit) = launchAction(onSuccess) {
        repository.addAbsence(employeeId, start, returned, note)
    }
    fun addEmployment(employeeId: Long, start: Long, end: Long?, onSuccess: (Long) -> Unit) = launchAction(onSuccess) {
        repository.addEmploymentPeriod(employeeId, start, end)
    }
    fun endEmployment(employeeId: Long, lastWorkingDay: Long, onSuccess: () -> Unit) = launchAction(onSuccess = { onSuccess() }) {
        repository.endEmployment(employeeId, lastWorkingDay)
    }
    fun addCard(command: AddCardCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.addCard(command) }
    fun registerIncome(command: RegisterIncomeCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.registerIncome(command) }
    fun transfer(command: TransferCustodyCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.transferCustody(command) }
    fun settle(command: RecordSettlementCommand, onSuccess: (Long) -> Unit) = launchAction(onSuccess) { repository.recordSettlement(command) }

    fun closeYear(backupChecksum: String, backupAt: Long, onSuccess: (Long) -> Unit) = launchAction(onSuccess) {
        repository.closeFinancialYear(selectedYear.value, backupChecksum, backupAt)
    }

    fun createBackup(password: CharArray, onSuccess: (BackupExport) -> Unit) = launchSilent(onSuccess) {
        container.backupService.export(password)
    }

    fun recordBackupSuccess(checksum: String) {
        viewModelScope.launch { settingsRepository.recordBackup(checksum); _messages.emit("فایل پشتیبان با موفقیت ذخیره شد") }
    }

    fun restoreBackup(bytes: ByteArray, password: CharArray, onSuccess: (RestoreSummary) -> Unit) = launchAction(onSuccess) {
        container.backupService.restore(bytes, password)
    }

    fun revealCard(accountId: Long, onSuccess: (String) -> Unit) = launchSilent(onSuccess) { repository.revealCard(accountId) }
    fun notifyMessage(message: String) { viewModelScope.launch { _messages.emit(message) } }

    fun accounts(employeeId: Long): Flow<List<MoneyAccountEntity>> = repository.accounts(employeeId)
    fun employment(employeeId: Long): Flow<List<EmploymentPeriodEntity>> = repository.employmentPeriods(employeeId)
    fun absences(employeeId: Long): Flow<List<AbsencePeriodEntity>> = repository.absences(employeeId)
    fun history(employeeId: Long): Flow<List<PersonHistoryRow>> = selectedYear.flatMapLatest { repository.personHistory(employeeId, it) }
    fun eligibility(eventId: Long): Flow<List<EligibilitySnapshotEntity>> = repository.eligibility(eventId)

    suspend fun employee(id: Long): EmployeeEntity? = repository.employee(id)
    suspend fun incomeEvent(id: Long): IncomeEventEntity? = repository.incomeEvent(id)
    suspend fun account(id: Long): MoneyAccountEntity? = repository.account(id)
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

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
    }
}
