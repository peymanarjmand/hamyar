package com.hesabyar.garage.data.local

import androidx.room.TypeConverter
import com.hesabyar.garage.domain.*

class Converters {
    @TypeConverter fun employeeRole(value: EmployeeRole): String = value.name
    @TypeConverter fun employeeRole(value: String): EmployeeRole = EmployeeRole.valueOf(value)
    @TypeConverter fun accountType(value: AccountType): String = value.name
    @TypeConverter fun accountType(value: String): AccountType = AccountType.valueOf(value)
    @TypeConverter fun financialYearStatus(value: FinancialYearStatus): String = value.name
    @TypeConverter fun financialYearStatus(value: String): FinancialYearStatus = FinancialYearStatus.valueOf(value)
    @TypeConverter fun incomeType(value: IncomeType): String = value.name
    @TypeConverter fun incomeType(value: String): IncomeType = IncomeType.valueOf(value)
    @TypeConverter fun moneyMethod(value: MoneyMethod): String = value.name
    @TypeConverter fun moneyMethod(value: String): MoneyMethod = MoneyMethod.valueOf(value)
    @TypeConverter fun recordStatus(value: RecordStatus): String = value.name
    @TypeConverter fun recordStatus(value: String): RecordStatus = RecordStatus.valueOf(value)
    @TypeConverter fun eligibilityStatus(value: EligibilityStatus): String = value.name
    @TypeConverter fun eligibilityStatus(value: String): EligibilityStatus = EligibilityStatus.valueOf(value)
    @TypeConverter fun settlementType(value: SettlementType): String = value.name
    @TypeConverter fun settlementType(value: String): SettlementType = SettlementType.valueOf(value)
    @TypeConverter fun annualCloseStatus(value: AnnualCloseStatus): String = value.name
    @TypeConverter fun annualCloseStatus(value: String): AnnualCloseStatus = AnnualCloseStatus.valueOf(value)
    @TypeConverter fun claimPostingType(value: ClaimPostingType): String = value.name
    @TypeConverter fun claimPostingType(value: String): ClaimPostingType = ClaimPostingType.valueOf(value)
    @TypeConverter fun custodyPostingType(value: CustodyPostingType): String = value.name
    @TypeConverter fun custodyPostingType(value: String): CustodyPostingType = CustodyPostingType.valueOf(value)
}
