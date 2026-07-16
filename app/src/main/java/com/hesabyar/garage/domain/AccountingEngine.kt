package com.hesabyar.garage.domain

object AccountingEngine {
    fun distribute(amount: Long, carriedRemainder: Long, eligibleCount: Int): IncomeDistribution {
        require(amount > 0) { "مبلغ باید بیشتر از صفر باشد" }
        require(carriedRemainder >= 0) { "باقیمانده نمی‌تواند منفی باشد" }
        require(eligibleCount > 0) { "حداقل یک فرد واجد سهم لازم است" }
        val total = Math.addExact(amount, carriedRemainder)
        val share = total / eligibleCount
        val remainder = total % eligibleCount
        return IncomeDistribution(total, share, remainder, eligibleCount)
    }

    fun suggestSettlement(balances: List<PersonBalance>): List<SettlementSuggestion> {
        val claim = balances.associate { it.employeeId to it.claim }.toMutableMap()
        val custody = balances.associate { it.employeeId to it.custody }.toMutableMap()
        val byId = balances.associateBy { it.employeeId }
        val suggestions = mutableListOf<SettlementSuggestion>()

        balances.sortedBy { it.employeeName }.forEach { person ->
            val amount = minOf(claim.getValue(person.employeeId), custody.getValue(person.employeeId))
            if (amount > 0) {
                suggestions += SettlementSuggestion(
                    person.employeeId,
                    person.employeeName,
                    person.employeeId,
                    person.employeeName,
                    amount,
                    SettlementType.SELF_OFFSET,
                )
                claim[person.employeeId] = Math.subtractExact(claim.getValue(person.employeeId), amount)
                custody[person.employeeId] = Math.subtractExact(custody.getValue(person.employeeId), amount)
            }
        }

        val debtors = custody.filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMutableList()
        val creditors = claim.filterValues { it > 0 }.toList().sortedByDescending { it.second }.toMutableList()
        var debtorIndex = 0
        var creditorIndex = 0
        while (debtorIndex < debtors.size && creditorIndex < creditors.size) {
            val (debtorId, debtorAmount) = debtors[debtorIndex]
            val (creditorId, creditorAmount) = creditors[creditorIndex]
            val amount = minOf(debtorAmount, creditorAmount)
            if (amount > 0) {
                suggestions += SettlementSuggestion(
                    debtorId,
                    byId.getValue(debtorId).employeeName,
                    creditorId,
                    byId.getValue(creditorId).employeeName,
                    amount,
                    SettlementType.PAYMENT,
                )
            }
            debtors[debtorIndex] = debtorId to Math.subtractExact(debtorAmount, amount)
            creditors[creditorIndex] = creditorId to Math.subtractExact(creditorAmount, amount)
            if (debtors[debtorIndex].second == 0L) debtorIndex++
            if (creditors[creditorIndex].second == 0L) creditorIndex++
        }
        return suggestions
    }
}
