package com.hesabyar.garage.data

import android.content.Context
import androidx.room.withTransaction
import com.hesabyar.garage.data.local.*
import com.hesabyar.garage.security.BackupCrypto
import com.hesabyar.garage.security.CardCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupExport(
    val encryptedBytes: ByteArray,
    val checksum: String,
    val createdAt: Long,
    val employeeCount: Int,
    val incomeCount: Int,
)

data class RestoreSummary(
    val employeeCount: Int,
    val incomeCount: Int,
    val createdAt: Long,
    val checksum: String,
)

class BackupService(
    private val context: Context,
    private val database: GarageDatabase,
    private val settingsRepository: SettingsRepository,
    private val cardCrypto: CardCrypto,
) {
    private val dao = database.dao()

    suspend fun export(password: CharArray): BackupExport = withContext(Dispatchers.IO) {
        val snapshot = database.withTransaction { collectSnapshot() }
        val payload = snapshot.toJson().toString()
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val checksum = BackupCrypto.checksum(payloadBytes)
        val outer = JSONObject()
            .put("format", "hesabyar-garage")
            .put("schemaVersion", 1)
            .put("checksum", checksum)
            .put("payload", payload)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(outer, password)
        BackupExport(encrypted.toByteArray(Charsets.UTF_8), checksum, snapshot.createdAt, snapshot.employees.size, snapshot.incomeEvents.size)
    }

    suspend fun inspect(bytes: ByteArray, password: CharArray): RestoreSummary = withContext(Dispatchers.IO) {
        val decoded = decodeEnvelope(bytes, password)
        val snapshot = BackupSnapshot.fromJson(JSONObject(decoded.payload))
        RestoreSummary(snapshot.employees.size, snapshot.incomeEvents.size, snapshot.createdAt, decoded.checksum)
    }

    suspend fun restore(bytes: ByteArray, password: CharArray): RestoreSummary = withContext(Dispatchers.IO) {
        val restorePassword = password.copyOf()
        val preBackupPassword = password.copyOf()
        password.fill('\u0000')
        val decoded = decodeEnvelope(bytes, restorePassword)
        val snapshot = BackupSnapshot.fromJson(JSONObject(decoded.payload)).withCardsReEncrypted(cardCrypto)

        runCatching {
            val pre = export(preBackupPassword)
            context.filesDir.resolve("pre-restore-${System.currentTimeMillis()}.hybackup").writeBytes(pre.encryptedBytes)
        }
        database.withTransaction {
            clearAll()
            restoreAll(snapshot)
        }
        settingsRepository.restore(snapshot.settings.copy(lastBackupChecksum = decoded.checksum))
        RestoreSummary(snapshot.employees.size, snapshot.incomeEvents.size, snapshot.createdAt, decoded.checksum)
    }

    private data class DecodedEnvelope(val payload: String, val checksum: String)

    private fun decodeEnvelope(bytes: ByteArray, password: CharArray): DecodedEnvelope {
        val plain = BackupCrypto.decrypt(bytes.toString(Charsets.UTF_8), password).toString(Charsets.UTF_8)
        val outer = JSONObject(plain)
        require(outer.getString("format") == "hesabyar-garage" && outer.getInt("schemaVersion") == 1) { "نسخه فایل پشتیبان پشتیبانی نمی‌شود" }
        val payload = outer.getString("payload")
        val expected = outer.getString("checksum")
        require(BackupCrypto.checksum(payload.toByteArray(Charsets.UTF_8)) == expected) { "صحت فایل پشتیبان تأیید نشد" }
        return DecodedEnvelope(payload, expected)
    }

    private suspend fun collectSnapshot(): BackupSnapshot {
        val accounts = dao.allAccounts().map { account ->
            if (account.cardNumberEncrypted.isBlank()) account else account.copy(
                cardNumberEncrypted = "PLAIN:${cardCrypto.decrypt(account.cardNumberEncrypted)}",
            )
        }
        return BackupSnapshot(
            createdAt = System.currentTimeMillis(),
            settings = settingsRepository.settings.first(),
            employees = dao.allEmployees(),
            employmentPeriods = dao.allEmploymentPeriods(),
            absences = dao.allAbsences(),
            accounts = accounts,
            financialYears = dao.allFinancialYears(),
            incomeEvents = dao.allIncomeEvents(),
            eligibility = dao.allEligibilitySnapshots(),
            claims = dao.allClaimPostings(),
            custody = dao.allCustodyPostings(),
            transfers = dao.allCustodyTransfers(),
            transferAllocations = dao.allCustodyTransferAllocations(),
            settlements = dao.allSettlementPayments(),
            settlementClaims = dao.allSettlementClaimAllocations(),
            settlementCustody = dao.allSettlementCustodyAllocations(),
            annualCloses = dao.allAnnualCloses(),
            annualCloseSnapshots = dao.allAnnualCloseSnapshots(),
            auditLogs = dao.allAuditLogs(),
        )
    }

    private suspend fun clearAll() {
        dao.clearAnnualCloseSnapshots(); dao.clearAnnualCloses()
        dao.clearSettlementCustodyAllocations(); dao.clearSettlementClaimAllocations(); dao.clearSettlementPayments()
        dao.clearCustodyTransferAllocations(); dao.clearCustodyTransfers()
        dao.clearCustodyPostings(); dao.clearClaimPostings(); dao.clearEligibilitySnapshots(); dao.clearIncomeEvents()
        dao.clearAbsences(); dao.clearEmploymentPeriods(); dao.clearAccounts(); dao.clearFinancialYears(); dao.clearAuditLogs(); dao.clearEmployees()
    }

    private suspend fun restoreAll(s: BackupSnapshot) {
        dao.restoreEmployees(s.employees)
        dao.restoreEmploymentPeriods(s.employmentPeriods)
        dao.restoreAbsences(s.absences)
        dao.restoreAccounts(s.accounts)
        dao.restoreFinancialYears(s.financialYears)
        dao.restoreIncomeEvents(s.incomeEvents)
        dao.restoreEligibility(s.eligibility)
        dao.restoreClaims(s.claims)
        dao.restoreCustody(s.custody)
        dao.restoreTransfers(s.transfers)
        dao.restoreTransferAllocations(s.transferAllocations)
        dao.restoreSettlements(s.settlements)
        dao.restoreSettlementClaims(s.settlementClaims)
        dao.restoreSettlementCustody(s.settlementCustody)
        dao.restoreAnnualCloses(s.annualCloses)
        dao.restoreAnnualCloseSnapshots(s.annualCloseSnapshots)
        dao.restoreAuditLogs(s.auditLogs)
    }
}

private data class BackupSnapshot(
    val createdAt: Long,
    val settings: AppSettings,
    val employees: List<EmployeeEntity>,
    val employmentPeriods: List<EmploymentPeriodEntity>,
    val absences: List<AbsencePeriodEntity>,
    val accounts: List<MoneyAccountEntity>,
    val financialYears: List<FinancialYearEntity>,
    val incomeEvents: List<IncomeEventEntity>,
    val eligibility: List<EligibilitySnapshotEntity>,
    val claims: List<ClaimPostingEntity>,
    val custody: List<CustodyPostingEntity>,
    val transfers: List<CustodyTransferEntity>,
    val transferAllocations: List<CustodyTransferAllocationEntity>,
    val settlements: List<SettlementPaymentEntity>,
    val settlementClaims: List<SettlementClaimAllocationEntity>,
    val settlementCustody: List<SettlementCustodyAllocationEntity>,
    val annualCloses: List<AnnualCloseEntity>,
    val annualCloseSnapshots: List<AnnualCloseEmployeeSnapshotEntity>,
    val auditLogs: List<AuditLogEntity>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("createdAt", createdAt)
        .put("settings", JSONObject().put("shopName", settings.shopName).put("onboardingDone", settings.onboardingDone).put("selectedYear", settings.selectedYear).put("lastBackupAt", settings.lastBackupAt).put("lastBackupChecksum", settings.lastBackupChecksum))
        .put("employees", entityArray(employees))
        .put("employmentPeriods", entityArray(employmentPeriods))
        .put("absences", entityArray(absences))
        .put("accounts", entityArray(accounts))
        .put("financialYears", entityArray(financialYears))
        .put("incomeEvents", entityArray(incomeEvents))
        .put("eligibility", entityArray(eligibility))
        .put("claims", entityArray(claims))
        .put("custody", entityArray(custody))
        .put("transfers", entityArray(transfers))
        .put("transferAllocations", entityArray(transferAllocations))
        .put("settlements", entityArray(settlements))
        .put("settlementClaims", entityArray(settlementClaims))
        .put("settlementCustody", entityArray(settlementCustody))
        .put("annualCloses", entityArray(annualCloses))
        .put("annualCloseSnapshots", entityArray(annualCloseSnapshots))
        .put("auditLogs", entityArray(auditLogs))

    fun withCardsReEncrypted(crypto: CardCrypto): BackupSnapshot = copy(
        accounts = accounts.map { account ->
            if (!account.cardNumberEncrypted.startsWith("PLAIN:")) account else {
                val plain = account.cardNumberEncrypted.removePrefix("PLAIN:")
                account.copy(
                    cardNumberEncrypted = crypto.encrypt(plain),
                    cardFingerprint = crypto.fingerprint(plain),
                    cardLast4 = plain.takeLast(4),
                )
            }
        },
    )

    companion object {
        fun fromJson(root: JSONObject): BackupSnapshot {
            val settingsJson = root.getJSONObject("settings")
            return BackupSnapshot(
                createdAt = root.getLong("createdAt"),
                settings = AppSettings(
                    shopName = settingsJson.getString("shopName"),
                    onboardingDone = settingsJson.getBoolean("onboardingDone"),
                    selectedYear = settingsJson.getInt("selectedYear"),
                    lastBackupAt = settingsJson.getLong("lastBackupAt"),
                    lastBackupChecksum = settingsJson.getString("lastBackupChecksum"),
                ),
                employees = decodeArray(root.getJSONArray("employees"), EmployeeEntity::class.java),
                employmentPeriods = decodeArray(root.getJSONArray("employmentPeriods"), EmploymentPeriodEntity::class.java),
                absences = decodeArray(root.getJSONArray("absences"), AbsencePeriodEntity::class.java),
                accounts = decodeArray(root.getJSONArray("accounts"), MoneyAccountEntity::class.java),
                financialYears = decodeArray(root.getJSONArray("financialYears"), FinancialYearEntity::class.java),
                incomeEvents = decodeArray(root.getJSONArray("incomeEvents"), IncomeEventEntity::class.java),
                eligibility = decodeArray(root.getJSONArray("eligibility"), EligibilitySnapshotEntity::class.java),
                claims = decodeArray(root.getJSONArray("claims"), ClaimPostingEntity::class.java),
                custody = decodeArray(root.getJSONArray("custody"), CustodyPostingEntity::class.java),
                transfers = decodeArray(root.getJSONArray("transfers"), CustodyTransferEntity::class.java),
                transferAllocations = decodeArray(root.getJSONArray("transferAllocations"), CustodyTransferAllocationEntity::class.java),
                settlements = decodeArray(root.getJSONArray("settlements"), SettlementPaymentEntity::class.java),
                settlementClaims = decodeArray(root.getJSONArray("settlementClaims"), SettlementClaimAllocationEntity::class.java),
                settlementCustody = decodeArray(root.getJSONArray("settlementCustody"), SettlementCustodyAllocationEntity::class.java),
                annualCloses = decodeArray(root.getJSONArray("annualCloses"), AnnualCloseEntity::class.java),
                annualCloseSnapshots = decodeArray(root.getJSONArray("annualCloseSnapshots"), AnnualCloseEmployeeSnapshotEntity::class.java),
                auditLogs = decodeArray(root.getJSONArray("auditLogs"), AuditLogEntity::class.java),
            )
        }
    }
}

private fun entityArray(items: List<*>): JSONArray = JSONArray().apply {
    items.forEach { item ->
        requireNotNull(item)
        val objectJson = JSONObject()
        fields(item.javaClass).forEach { field ->
            field.isAccessible = true
            val value = field.get(item)
            objectJson.put(field.name, when (value) { null -> JSONObject.NULL; is Enum<*> -> value.name; else -> value })
        }
        put(objectJson)
    }
}

/*
 * ART does not guarantee the order returned by Class.getDeclaredFields(). A
 * backup codec must therefore use an explicit, versioned constructor order.
 * Keeping the JSON keys as property names also makes the on-disk format stable
 * across JVM/ART implementations.
 */
internal fun backupFieldNames(clazz: Class<*>): List<String> = when (clazz) {
    EmployeeEntity::class.java -> listOf("id", "name", "phone", "role", "notes", "createdAt", "archivedAt")
    EmploymentPeriodEntity::class.java -> listOf("id", "employeeId", "startEpochDay", "endEpochDay", "createdAt", "voidedAt")
    AbsencePeriodEntity::class.java -> listOf("id", "employeeId", "startEpochDay", "returnEpochDay", "note", "createdAt", "voidedAt")
    MoneyAccountEntity::class.java -> listOf("id", "employeeId", "type", "title", "ownerName", "bankName", "cardNumberEncrypted", "cardLast4", "cardFingerprint", "createdAt", "archivedAt")
    FinancialYearEntity::class.java -> listOf("year", "startEpochDay", "endEpochDay", "status", "createdAt", "closedAt")
    IncomeEventEntity::class.java -> listOf("id", "commandId", "financialYear", "dateEpochDay", "amount", "type", "receiverEmployeeId", "receiverAccountId", "method", "description", "carriedRemainder", "sharePerPerson", "resultingRemainder", "status", "createdAt", "reversedAt", "replacesEventId")
    EligibilitySnapshotEntity::class.java -> listOf("id", "eventId", "employeeId", "status", "reasonCode", "reasonFa", "employmentPeriodId", "absencePeriodId", "absenceDay", "shareAmount", "evaluatedAt")
    ClaimPostingEntity::class.java -> listOf("id", "financialYear", "sourceIncomeEventId", "employeeId", "amountSigned", "type", "referenceId", "businessDateEpochDay", "createdAt")
    CustodyPostingEntity::class.java -> listOf("id", "financialYear", "sourceIncomeEventId", "employeeId", "accountId", "amountSigned", "type", "referenceId", "businessDateEpochDay", "createdAt")
    CustodyTransferEntity::class.java -> listOf("id", "commandId", "financialYear", "dateEpochDay", "fromEmployeeId", "fromAccountId", "toEmployeeId", "toAccountId", "amount", "method", "note", "status", "createdAt", "reversedAt")
    CustodyTransferAllocationEntity::class.java -> listOf("transferId", "sourceIncomeEventId", "amount")
    SettlementPaymentEntity::class.java -> listOf("id", "commandId", "financialYear", "dateEpochDay", "fromEmployeeId", "fromAccountId", "toEmployeeId", "destinationAccountId", "amount", "method", "type", "note", "status", "createdAt", "reversedAt")
    SettlementClaimAllocationEntity::class.java -> listOf("paymentId", "sourceIncomeEventId", "amount")
    SettlementCustodyAllocationEntity::class.java -> listOf("paymentId", "sourceIncomeEventId", "amount")
    AnnualCloseEntity::class.java -> listOf("id", "financialYear", "revision", "closedAt", "ledgerHash", "backupChecksum", "summaryJson", "status", "reversedAt", "reverseReason")
    AnnualCloseEmployeeSnapshotEntity::class.java -> listOf("closeId", "employeeId", "totalClaim", "totalCustodyIn", "totalCustodyOut", "totalSettlementPaid", "totalSettlementReceived", "finalNet")
    AuditLogEntity::class.java -> listOf("id", "financialYear", "action", "entityType", "entityId", "description", "createdAt")
    else -> error("جدول ${clazz.simpleName} در پشتیبان پشتیبانی نمی‌شود")
}

private fun fields(clazz: Class<*>): List<java.lang.reflect.Field> = backupFieldNames(clazz).map { name ->
    clazz.getDeclaredField(name)
}

private fun <T : Any> decodeArray(array: JSONArray, clazz: Class<T>): List<T> = buildList {
    repeat(array.length()) { index ->
        val json = array.getJSONObject(index)
        val fields = fields(clazz)
        val constructor = clazz.declaredConstructors.firstOrNull { it.parameterCount == fields.size }
            ?: error("سازنده ${clazz.simpleName} برای بازیابی پیدا نشد")
        constructor.isAccessible = true
        val args = fields.map { field ->
            if (json.isNull(field.name)) null else when {
                field.type == java.lang.Long.TYPE || field.type == java.lang.Long::class.java -> json.getLong(field.name)
                field.type == java.lang.Integer.TYPE || field.type == java.lang.Integer::class.java -> json.getInt(field.name)
                field.type == java.lang.Boolean.TYPE || field.type == java.lang.Boolean::class.java -> json.getBoolean(field.name)
                field.type == String::class.java -> json.getString(field.name)
                field.type.isEnum -> field.type.enumConstants.first { (it as Enum<*>).name == json.getString(field.name) }
                else -> error("نوع ${field.type.name} در پشتیبان پشتیبانی نمی‌شود")
            }
        }.toTypedArray()
        @Suppress("UNCHECKED_CAST")
        add(constructor.newInstance(*args) as T)
    }
}
