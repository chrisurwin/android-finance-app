package com.chris.financeapp.utils

import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.InvestmentAssumptions
import com.chris.financeapp.data.model.DrawdownPreferences
import com.chris.financeapp.data.model.ProjectionResult
import com.chris.financeapp.data.model.RetirementProjection
import com.chris.financeapp.data.model.Person
import com.chris.financeapp.data.model.ProjectionType
import com.chris.financeapp.data.model.DrawdownStrategy
import com.chris.financeapp.data.model.LumpSumOption
import com.chris.financeapp.data.model.Institution
import com.chris.financeapp.data.model.WithdrawalDetail
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object PensionCalculator {

    // UK Lump Sum Allowance (LSA) effective 6 April 2024:
    // The maximum tax-free lump sum that can be taken across pensions is capped at £268,275 per individual.
    const val MAX_TAX_FREE_LUMP_SUM = 268275.0

    // UK Annual ISA Subscription Allowance (per individual)
    const val ANNUAL_ISA_ALLOWANCE = 20000.0

    private const val FULL_STATE_PENSION_WEEKLY = 230.25
    private const val FULL_STATE_PENSION_ANNUAL = FULL_STATE_PENSION_WEEKLY * 52.0

    // UK Income Tax Rates (2024/25 - 2025/26)
    private const val PERSONAL_ALLOWANCE = 12570.0
    private const val BASIC_RATE_THRESHOLD = 37700.0 // up to £50,270 with standard PA
    private const val HIGHER_RATE_THRESHOLD = 125140.0 // up to £125,140
    
    private const val BASIC_RATE = 0.20
    private const val HIGHER_RATE = 0.40
    private const val ADDITIONAL_RATE = 0.45

    /**
     * Determines UK legislated State Pension age based on birth year.
     * Prevents overestimating retirement income by granting State Pension too early.
     */
    fun getStatePensionAge(birthYear: Int): Int {
        return when {
            birthYear < 1961 -> 66
            birthYear <= 1977 -> 67
            else -> 68
        }
    }

    fun calculateStatePension(qualifyingYears: Int): Double {
        val proportion = min(qualifyingYears / 35.0, 1.0)
        return FULL_STATE_PENSION_ANNUAL * proportion
    }

    /**
     * Calculates UK Income Tax including the Personal Allowance taper for incomes above £100,000.
     * £1 of Personal Allowance is lost for every £2 of income above £100,000 (0 allowance at £125,140).
     */
    fun calculateIncomeTax(taxableIncome: Double): Double {
        if (taxableIncome <= 0.0) return 0.0

        // Personal Allowance taper (£1 reduction for every £2 over £100,000)
        val personalAllowance = max(0.0, PERSONAL_ALLOWANCE - max(0.0, taxableIncome - 100000.0) / 2.0)
        var remainingIncome = max(0.0, taxableIncome - personalAllowance)
        var tax = 0.0

        // Basic rate band: £37,700 of income above personal allowance at 20%
        if (remainingIncome > 0.0) {
            val taxableAtBasic = min(remainingIncome, BASIC_RATE_THRESHOLD)
            tax += taxableAtBasic * BASIC_RATE
            remainingIncome -= taxableAtBasic
        }

        // Higher rate band: total income from (personalAllowance + BASIC_RATE_THRESHOLD) up to HIGHER_RATE_THRESHOLD (£125,140) at 40%
        if (remainingIncome > 0.0 && taxableIncome <= HIGHER_RATE_THRESHOLD) {
            tax += remainingIncome * HIGHER_RATE
            remainingIncome = 0.0
        } else if (remainingIncome > 0.0) {
            val higherRateBandSize = max(0.0, HIGHER_RATE_THRESHOLD - (personalAllowance + BASIC_RATE_THRESHOLD))
            val taxableAtHigher = min(remainingIncome, higherRateBandSize)
            tax += taxableAtHigher * HIGHER_RATE
            remainingIncome -= taxableAtHigher

            // Additional rate band: > £125,140 at 45%
            if (remainingIncome > 0.0) {
                tax += remainingIncome * ADDITIONAL_RATE
            }
        }

        return tax
    }

    data class PensionWithdrawalResult(
        val grossWithdrawal: Double,
        val taxFreeAmount: Double,
        val taxableAmount: Double,
        val taxPaid: Double,
        val netReceived: Double
    )

    /**
     * Exact binary search solver to determine the gross pension withdrawal needed to deliver netNeeded cash,
     * taking into account current taxable income, available Lump Sum Allowance (LSA), upfront vs as-you-go,
     * and optional taxable income caps (e.g. harvesting up to Personal Allowance or Basic Rate).
     */
    fun calculatePensionWithdrawal(
        potBalance: Double,
        netNeeded: Double,
        currentTaxableIncome: Double,
        remainingLSA: Double,
        isUpFront: Boolean,
        maxTaxableAmount: Double = Double.MAX_VALUE
    ): PensionWithdrawalResult {
        if (potBalance <= 0.0 || netNeeded <= 0.0 || maxTaxableAmount <= 0.0) {
            return PensionWithdrawalResult(0.0, 0.0, 0.0, 0.0, 0.0)
        }

        fun evaluate(gross: Double): PensionWithdrawalResult {
            val tf = if (isUpFront) 0.0 else min(gross * 0.25, remainingLSA)
            val taxable = gross - tf
            val taxBefore = calculateIncomeTax(currentTaxableIncome)
            val taxAfter = calculateIncomeTax(currentTaxableIncome + taxable)
            val taxPaid = max(0.0, taxAfter - taxBefore)
            val net = gross - taxPaid
            return PensionWithdrawalResult(gross, tf, taxable, taxPaid, net)
        }

        val maxGrossAllowed = if (maxTaxableAmount < Double.MAX_VALUE) {
            if (isUpFront) {
                maxTaxableAmount
            } else {
                if (maxTaxableAmount / 0.75 * 0.25 <= remainingLSA) {
                    maxTaxableAmount / 0.75
                } else {
                    maxTaxableAmount + remainingLSA
                }
            }
        } else {
            potBalance
        }

        val effectiveMaxGross = min(potBalance, maxGrossAllowed)
        if (effectiveMaxGross <= 0.0) {
            return PensionWithdrawalResult(0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val maxRes = evaluate(effectiveMaxGross)
        if (maxRes.netReceived <= netNeeded) {
            return maxRes
        }

        var low = 0.0
        var high = effectiveMaxGross
        for (i in 0 until 30) {
            val mid = (low + high) / 2.0
            val res = evaluate(mid)
            if (res.netReceived < netNeeded) {
                low = mid
            } else {
                high = mid
            }
        }
        return evaluate(high)
    }

    /**
     * Exact solver for General Investment Account (GIA) withdrawals assuming conservative income taxation on withdrawals.
     */
    fun calculateGiaWithdrawal(
        potBalance: Double,
        netNeeded: Double,
        currentTaxableIncome: Double
    ): Pair<Double, Double> {
        if (potBalance <= 0.0 || netNeeded <= 0.0) return Pair(0.0, 0.0)

        fun netOf(gross: Double): Double {
            val taxBefore = calculateIncomeTax(currentTaxableIncome)
            val taxAfter = calculateIncomeTax(currentTaxableIncome + gross)
            return gross - max(0.0, taxAfter - taxBefore)
        }

        if (netOf(potBalance) <= netNeeded) {
            val taxBefore = calculateIncomeTax(currentTaxableIncome)
            val taxAfter = calculateIncomeTax(currentTaxableIncome + potBalance)
            return Pair(potBalance, max(0.0, taxAfter - taxBefore))
        }

        var low = 0.0
        var high = potBalance
        for (i in 0 until 30) {
            val mid = (low + high) / 2.0
            if (netOf(mid) < netNeeded) {
                low = mid
            } else {
                high = mid
            }
        }
        val taxBefore = calculateIncomeTax(currentTaxableIncome)
        val taxAfter = calculateIncomeTax(currentTaxableIncome + high)
        return Pair(high, max(0.0, taxAfter - taxBefore))
    }

    /**
     * Calculates UK Tax on Savings Interest from General Investment Accounts (GIA).
     * Accounts for the Personal Allowance, Personal Savings Allowance (£1,000 basic / £500 higher / £0 additional),
     * and taxes non-exempt savings returns at the individual's marginal income tax rate.
     */
    fun calculateTaxOnSavings(savingsInterest: Double, otherTaxableIncome: Double): Double {
        if (savingsInterest <= 0.0) return 0.0

        val personalAllowance = max(0.0, PERSONAL_ALLOWANCE - max(0.0, (otherTaxableIncome + savingsInterest) - 100000.0) / 2.0)
        val unusedPA = max(0.0, personalAllowance - otherTaxableIncome)

        val totalIncome = otherTaxableIncome + savingsInterest
        val psa = when {
            totalIncome <= (PERSONAL_ALLOWANCE + BASIC_RATE_THRESHOLD) -> 1000.0
            totalIncome <= HIGHER_RATE_THRESHOLD -> 500.0
            else -> 0.0
        }

        val taxableSavings = max(0.0, savingsInterest - unusedPA - psa)
        if (taxableSavings <= 0.0) return 0.0

        val taxBefore = calculateIncomeTax(otherTaxableIncome + unusedPA)
        val taxAfter = calculateIncomeTax(otherTaxableIncome + unusedPA + taxableSavings)
        return max(0.0, taxAfter - taxBefore)
    }

    /**
     * Executes annual "Bed and ISA" transfer from taxable General Investment Accounts (GIA) to tax-free ISAs.
     * Moves up to £20,000 per person per year (split between the couple up to £40,000 total if a couple),
     * sheltering funds from tax on savings.
     */
    private fun performBedAndIsaTransfer(
        savings: MutableList<Account>,
        projectionType: ProjectionType,
        person1: Person,
        person2: Person,
        isRetired1: Boolean,
        isRetired2: Boolean,
        assumptions: InvestmentAssumptions,
        inflationFactor: Double
    ) {
        val totalGia = savings.filter { it.type == AccountType.GENERAL_INVESTMENT && it.balance > 0.0 }.sumOf { it.balance }
        if (totalGia <= 0.0) return

        val usedIsa1 = savings.filter { it.personId != "person-2" && it.type == AccountType.ISA }
            .sumOf { if (!isRetired1) it.monthlyContribution * 12.0 * inflationFactor else 0.0 }
        val maxIsa1 = if (projectionType == ProjectionType.INDIVIDUAL_LISA) 0.0 else max(0.0, ANNUAL_ISA_ALLOWANCE - usedIsa1)

        val usedIsa2 = savings.filter { it.personId == "person-2" && it.type == AccountType.ISA }
            .sumOf { if (!isRetired2) it.monthlyContribution * 12.0 * inflationFactor else 0.0 }
        val maxIsa2 = if (projectionType == ProjectionType.INDIVIDUAL_CHRIS) 0.0 else max(0.0, ANNUAL_ISA_ALLOWANCE - usedIsa2)

        val maxTransferTotal = maxIsa1 + maxIsa2
        val actualTransferTotal = min(totalGia, maxTransferTotal)
        if (actualTransferTotal <= 0.0) return

        val (t1, t2) = if (projectionType == ProjectionType.COUPLE) {
            if (actualTransferTotal >= (maxIsa1 + maxIsa2)) {
                Pair(maxIsa1, maxIsa2)
            } else {
                val half = actualTransferTotal / 2.0
                val p1 = min(maxIsa1, half)
                val p2 = min(maxIsa2, actualTransferTotal - p1)
                Pair(p1, p2)
            }
        } else if (projectionType == ProjectionType.INDIVIDUAL_CHRIS) {
            Pair(actualTransferTotal, 0.0)
        } else {
            Pair(0.0, actualTransferTotal)
        }

        var remDeduct = actualTransferTotal
        for (gia in savings.filter { it.type == AccountType.GENERAL_INVESTMENT }) {
            if (remDeduct <= 0.0) break
            val d = min(gia.balance, remDeduct)
            gia.balance -= d
            remDeduct -= d
        }

        if (t1 > 0.0) {
            val isa1 = savings.firstOrNull { it.personId != "person-2" && it.type == AccountType.ISA }
            if (isa1 != null) {
                isa1.balance += t1
            } else {
                savings.add(Account(
                    id = "isa-bed-1",
                    name = "${person1.name}'s ISA",
                    type = AccountType.ISA,
                    institution = Institution.HOSTED,
                    balance = t1,
                    personId = "person-1",
                    interestRate = assumptions.cashReturn * 100
                ))
            }
        }

        if (t2 > 0.0) {
            val isa2 = savings.firstOrNull { it.personId == "person-2" && it.type == AccountType.ISA }
            if (isa2 != null) {
                isa2.balance += t2
            } else {
                savings.add(Account(
                    id = "isa-bed-2",
                    name = "${person2.name}'s ISA",
                    type = AccountType.ISA,
                    institution = Institution.HOSTED,
                    balance = t2,
                    personId = "person-2",
                    interestRate = assumptions.cashReturn * 100
                ))
            }
        }
    }

    fun calculateProjections(
        accounts: List<Account>,
        assumptions: InvestmentAssumptions,
        preferences: DrawdownPreferences,
        person1: Person,
        person2: Person,
        retirementAge1: Int,
        retirementAge2: Int,
        projectionType: ProjectionType = ProjectionType.COUPLE
    ): RetirementProjection {
        val currentYear = 2026
        val currentAge1 = currentYear - person1.birthYear
        val currentAge2 = currentYear - person2.birthYear
        val endAge = preferences.endAge
        
        val minPensionAge1 = if (person1.birthYear < 1973) 55 else 57
        val minPensionAge2 = if (person2.birthYear < 1973) 55 else 57

        val spAge1 = getStatePensionAge(person1.birthYear)
        val spAge2 = getStatePensionAge(person2.birthYear)

        val (currentAgeActive, activePersonName) = when (projectionType) {
            ProjectionType.INDIVIDUAL_CHRIS -> Pair(currentAge1, person1.name)
            ProjectionType.INDIVIDUAL_LISA -> Pair(currentAge2, person2.name)
            ProjectionType.COUPLE -> Pair(currentAge1, "${person1.name} & ${person2.name}")
        }

        val totalAssets = accounts.sumOf { it.balance }
        if (totalAssets <= 0.0) {
            val results = (0..(endAge - currentAgeActive)).map { yearOffset ->
                ProjectionResult(
                    age = currentAgeActive + yearOffset,
                    year = currentYear + yearOffset,
                    totalPensionValue = 0.0,
                    totalSavings = 0.0,
                    annualIncome = 0.0,
                    netIncome = 0.0,
                    tax = 0.0,
                    tax1 = 0.0,
                    tax2 = 0.0,
                    canRetire = false,
                    withdrawals = emptyList()
                )
            }
            return RetirementProjection(
                results = results,
                retirementAge = retirementAge1,
                feasible = false,
                totalTaxPaid = 0.0
            )
        }

        val results = mutableListOf<ProjectionResult>()
        
        // Calculate weighted portfolio return based on allocations
        val portfolioGrowthRate = (assumptions.equityReturn * assumptions.equityAllocation) +
                (assumptions.bondReturn * assumptions.bondAllocation) +
                (assumptions.cashReturn * assumptions.cashAllocation)

        // Filter accounts based on who we are simulating
        val activeAccounts = when (projectionType) {
            ProjectionType.INDIVIDUAL_CHRIS -> accounts.filter { it.personId != "person-2" }
            ProjectionType.INDIVIDUAL_LISA -> accounts.filter { it.personId == "person-2" }
            ProjectionType.COUPLE -> accounts
        }

        // Prepare working copies of balances
        var dcPensions = activeAccounts.filter { it.type == AccountType.PENSION }
            .map { it.copy() }
            .toMutableList()

        val finalSalaries = activeAccounts.filter { it.type == AccountType.FINAL_SALARY }
            .map { it.copy() }
            .toList()
            
        var savings = activeAccounts.filter { it.type != AccountType.PENSION && it.type != AccountType.FINAL_SALARY }
            .map { it.copy() }
            .toMutableList()

        var retirementFeasible = true
        val yearsToSimulate = endAge - currentAgeActive

        var hasTakenLumpSum1 = false
        var hasTakenLumpSum2 = false
        var totalTaxPaid = 0.0

        // Track remaining UK Lump Sum Allowance (max £268,275 per individual across lifetime)
        var remainingTaxFreeLumpSum1 = MAX_TAX_FREE_LUMP_SUM
        var remainingTaxFreeLumpSum2 = MAX_TAX_FREE_LUMP_SUM

        for (yearOffset in 0..yearsToSimulate) {
            val age1 = currentAge1 + yearOffset
            val age2 = currentAge2 + yearOffset
            val ageActive = currentAgeActive + yearOffset
            val year = currentYear + yearOffset
            val inflationFactor = (1 + assumptions.inflationRate).pow(yearOffset.toDouble())

            var targetIncome = if (preferences.inflationAdjusted) {
                preferences.targetAnnualIncome * inflationFactor
            } else {
                preferences.targetAnnualIncome
            }

            if (ageActive >= preferences.stepDownAge) {
                targetIncome *= (1.0 - preferences.stepDownPercentage / 100.0)
            }

            val isRetired1 = if (projectionType == ProjectionType.INDIVIDUAL_LISA) false else age1 >= retirementAge1
            val isRetired2 = if (projectionType == ProjectionType.INDIVIDUAL_CHRIS) false else age2 >= retirementAge2
            val anyRetired = isRetired1 || isRetired2

            val isRetiredActive = when (projectionType) {
                ProjectionType.INDIVIDUAL_CHRIS -> isRetired1
                ProjectionType.INDIVIDUAL_LISA -> isRetired2
                ProjectionType.COUPLE -> anyRetired
            }

            val yearWithdrawals = mutableListOf<WithdrawalDetail>()

            // 1. Contributions for active workers
            dcPensions.forEach { pension ->
                val isOwnerRetired = if (pension.personId == "person-2") isRetired2 else isRetired1
                if (!isOwnerRetired) {
                    val totalContrib = (pension.monthlyContribution + pension.employerContribution) * 12.0 * inflationFactor
                    pension.balance += totalContrib
                }
            }

            savings.forEach { saving ->
                val isOwnerRetired = if (saving.personId == "person-2") isRetired2 else isRetired1
                if (!isOwnerRetired) {
                    val totalContrib = saving.monthlyContribution * 12.0 * inflationFactor
                    saving.balance += totalContrib
                }
            }

            // 2. Upfront Lump Sum Option logic:
            // Extract 25% of DC pensions capped at available Lump Sum Allowance (£268,275 per person)
            // Can ONLY be taken at or after reaching minimum pension age (55/57)
            // Lump sum is deposited into a General Investment Account (GIA), where it is subject to tax on savings,
            // but can be split between the couple (up to £20k each per year) into an ISA to remove it from tax.
            if (preferences.lumpSumOption == LumpSumOption.UP_FRONT) {
                if (isRetired1 && age1 >= minPensionAge1 && !hasTakenLumpSum1) {
                    var totalLumpSum1 = 0.0
                    dcPensions = dcPensions.map { pension ->
                        if (pension.personId != "person-2") {
                            val maxLump = pension.balance * 0.25
                            val actualLump = min(maxLump, remainingTaxFreeLumpSum1)
                            if (actualLump > 0.0) {
                                totalLumpSum1 += actualLump
                                remainingTaxFreeLumpSum1 -= actualLump
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name} (Lump Sum)", person1.name, actualLump, 0.0))
                                pension.copy(balance = pension.balance - actualLump)
                            } else {
                                pension
                            }
                        } else {
                            pension
                        }
                    }.toMutableList()
                    
                    if (totalLumpSum1 > 0.0) {
                        val giaIndex = savings.indexOfFirst { it.personId != "person-2" && it.type == AccountType.GENERAL_INVESTMENT }
                        if (giaIndex >= 0) {
                            savings[giaIndex].balance += totalLumpSum1
                        } else {
                            val saveIndex = savings.indexOfFirst { it.personId != "person-2" && it.type != AccountType.ISA }
                            if (saveIndex >= 0) {
                                savings[saveIndex].balance += totalLumpSum1
                            } else {
                                savings.add(Account(id = "lump-sum-gia-1", name = "Tax-Free Lump Sum (GIA)", type = AccountType.GENERAL_INVESTMENT, institution = Institution.HOSTED, balance = totalLumpSum1, personId = "person-1", interestRate = assumptions.cashReturn * 100))
                            }
                        }
                    }
                    hasTakenLumpSum1 = true
                }
                if (isRetired2 && age2 >= minPensionAge2 && !hasTakenLumpSum2) {
                    var totalLumpSum2 = 0.0
                    dcPensions = dcPensions.map { pension ->
                        if (pension.personId == "person-2") {
                            val maxLump = pension.balance * 0.25
                            val actualLump = min(maxLump, remainingTaxFreeLumpSum2)
                            if (actualLump > 0.0) {
                                totalLumpSum2 += actualLump
                                remainingTaxFreeLumpSum2 -= actualLump
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name} (Lump Sum)", person2.name, actualLump, 0.0))
                                pension.copy(balance = pension.balance - actualLump)
                            } else {
                                pension
                            }
                        } else {
                            pension
                        }
                    }.toMutableList()
                    
                    if (totalLumpSum2 > 0.0) {
                        val giaIndex = savings.indexOfFirst { it.personId == "person-2" && it.type == AccountType.GENERAL_INVESTMENT }
                        if (giaIndex >= 0) {
                            savings[giaIndex].balance += totalLumpSum2
                        } else {
                            val saveIndex = savings.indexOfFirst { it.personId == "person-2" && it.type != AccountType.ISA }
                            if (saveIndex >= 0) {
                                savings[saveIndex].balance += totalLumpSum2
                            } else {
                                savings.add(Account(id = "lump-sum-gia-2", name = "Tax-Free Lump Sum (GIA)", type = AccountType.GENERAL_INVESTMENT, institution = Institution.HOSTED, balance = totalLumpSum2, personId = "person-2", interestRate = assumptions.cashReturn * 100))
                            }
                        }
                    }
                    hasTakenLumpSum2 = true
                }
            }

            // 3. Annual Bed & ISA Transfer:
            // Move up to £20k per person per year (split between the couple if couple) from GIA to ISA to remove from tax
            performBedAndIsaTransfer(
                savings = savings,
                projectionType = projectionType,
                person1 = person1,
                person2 = person2,
                isRetired1 = isRetired1,
                isRetired2 = isRetired2,
                assumptions = assumptions,
                inflationFactor = inflationFactor
            )

            if (anyRetired) {
                // Calculate State Pension (from statutory state pension age)
                val statePension1 = if (isRetired1 && age1 >= spAge1) calculateStatePension(35) * inflationFactor else 0.0
                val statePension2 = if (isRetired2 && age2 >= spAge2) calculateStatePension(35) * inflationFactor else 0.0
                
                // Calculate Defined Benefit (Final Salary) payouts
                var dbIncome1 = 0.0
                var dbIncome2 = 0.0
                val dbPayouts1 = mutableMapOf<String, Double>()
                val dbPayouts2 = mutableMapOf<String, Double>()

                for (db in finalSalaries) {
                    val isOwnerEligible = if (db.personId == "person-2") age2 >= db.payoutAge else age1 >= db.payoutAge
                    if (isOwnerEligible) {
                        val payout = if (db.isInflationLinked) db.balance * inflationFactor else db.balance
                        val potKey = "${db.institution.displayName} - ${db.name}"
                        if (db.personId == "person-2") {
                            dbIncome2 += payout
                            dbPayouts2[potKey] = (dbPayouts2[potKey] ?: 0.0) + payout
                        } else {
                            dbIncome1 += payout
                            dbPayouts1[potKey] = (dbPayouts1[potKey] ?: 0.0) + payout
                        }
                    }
                }

                var taxFreeIncome = 0.0
                var taxableIncome1 = statePension1 + dbIncome1
                var taxableIncome2 = statePension2 + dbIncome2
                
                // Base tax on guaranteed incomes
                val baseTax1 = calculateIncomeTax(taxableIncome1)
                val baseTax2 = calculateIncomeTax(taxableIncome2)

                if (statePension1 > 0.0) {
                    val taxFraction = if (taxableIncome1 > 0.0) statePension1 / taxableIncome1 else 0.0
                    yearWithdrawals.add(WithdrawalDetail("State Pension", person1.name, statePension1, baseTax1 * taxFraction))
                }
                if (statePension2 > 0.0) {
                    val taxFraction = if (taxableIncome2 > 0.0) statePension2 / taxableIncome2 else 0.0
                    yearWithdrawals.add(WithdrawalDetail("State Pension", person2.name, statePension2, baseTax2 * taxFraction))
                }
                dbPayouts1.forEach { (name, payout) ->
                    val taxFraction = if (taxableIncome1 > 0.0) payout / taxableIncome1 else 0.0
                    yearWithdrawals.add(WithdrawalDetail(name, person1.name, payout, baseTax1 * taxFraction))
                }
                dbPayouts2.forEach { (name, payout) ->
                    val taxFraction = if (taxableIncome2 > 0.0) payout / taxableIncome2 else 0.0
                    yearWithdrawals.add(WithdrawalDetail(name, person2.name, payout, baseTax2 * taxFraction))
                }

                val netGuaranteed = (taxableIncome1 - baseTax1) + (taxableIncome2 - baseTax2)
                var remainingTarget = max(0.0, targetIncome - netGuaranteed)

                val isUpFront = preferences.lumpSumOption == LumpSumOption.UP_FRONT

                if (preferences.strategy == DrawdownStrategy.STANDARD) {
                    // --- STANDARD DRAWDOWN STRATEGY (Harvest PA -> ISAs/Cash -> GIA -> Pensions) ---
                    
                    // Step 1: Harvest pension up to personal allowance
                    if (isRetired1 && age1 >= minPensionAge1 && remainingTarget > 0.0) {
                        val person1Pensions = dcPensions.filter { it.personId != "person-2" }
                        for (pension in person1Pensions) {
                            if (remainingTarget <= 0.0) break
                            val allowance1 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome1)
                            if (allowance1 <= 0.0) break

                            val res = calculatePensionWithdrawal(
                                potBalance = pension.balance,
                                netNeeded = remainingTarget,
                                currentTaxableIncome = taxableIncome1,
                                remainingLSA = remainingTaxFreeLumpSum1,
                                isUpFront = isUpFront,
                                maxTaxableAmount = allowance1
                            )
                            if (res.grossWithdrawal > 0.0) {
                                taxFreeIncome += res.taxFreeAmount
                                remainingTaxFreeLumpSum1 -= res.taxFreeAmount
                                taxableIncome1 += res.taxableAmount
                                pension.balance -= res.grossWithdrawal
                                remainingTarget = max(0.0, remainingTarget - res.netReceived)
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", person1.name, res.grossWithdrawal, res.taxPaid))
                            }
                        }
                    }

                    if (isRetired2 && age2 >= minPensionAge2 && remainingTarget > 0.0) {
                        val person2Pensions = dcPensions.filter { it.personId == "person-2" }
                        for (pension in person2Pensions) {
                            if (remainingTarget <= 0.0) break
                            val allowance2 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome2)
                            if (allowance2 <= 0.0) break

                            val res = calculatePensionWithdrawal(
                                potBalance = pension.balance,
                                netNeeded = remainingTarget,
                                currentTaxableIncome = taxableIncome2,
                                remainingLSA = remainingTaxFreeLumpSum2,
                                isUpFront = isUpFront,
                                maxTaxableAmount = allowance2
                            )
                            if (res.grossWithdrawal > 0.0) {
                                taxFreeIncome += res.taxFreeAmount
                                remainingTaxFreeLumpSum2 -= res.taxFreeAmount
                                taxableIncome2 += res.taxableAmount
                                pension.balance -= res.grossWithdrawal
                                remainingTarget = max(0.0, remainingTarget - res.netReceived)
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", person2.name, res.grossWithdrawal, res.taxPaid))
                            }
                        }
                    }

                    // Step 2: Draw from other savings (Current accounts, then GIAs) BEFORE ISAs due to ISA tax-free nature
                    for (saving in savings.filter { it.type == AccountType.CURRENT }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            taxFreeIncome += withdrawal
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            val owner = if (saving.personId == "person-2") person2.name else person1.name
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", owner, withdrawal, 0.0))
                        }
                    }
                    for (saving in savings.filter { it.type == AccountType.GENERAL_INVESTMENT }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            taxFreeIncome += withdrawal
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            val owner = if (saving.personId == "person-2") person2.name else person1.name
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", owner, withdrawal, 0.0))
                        }
                    }

                    // Step 3: Draw from ISAs (tax-free savings preserved until other savings are exhausted)
                    for (saving in savings.filter { it.type == AccountType.ISA }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            taxFreeIncome += withdrawal
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            val owner = if (saving.personId == "person-2") person2.name else person1.name
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", owner, withdrawal, 0.0))
                        }
                    }

                    // Step 4: Draw remaining from pension
                    for (pension in dcPensions) {
                        if (remainingTarget <= 0.0) break
                        if (pension.balance <= 0.0) continue
                        val isLisa = pension.personId == "person-2"
                        val isOwnerRetired = if (isLisa) isRetired2 else isRetired1
                        val ownerAge = if (isLisa) age2 else age1
                        val minAge = if (isLisa) minPensionAge2 else minPensionAge1
                        if (!isOwnerRetired || ownerAge < minAge) continue

                        val currentTaxable = if (isLisa) taxableIncome2 else taxableIncome1
                        val currentLSA = if (isLisa) remainingTaxFreeLumpSum2 else remainingTaxFreeLumpSum1

                        val res = calculatePensionWithdrawal(
                            potBalance = pension.balance,
                            netNeeded = remainingTarget,
                            currentTaxableIncome = currentTaxable,
                            remainingLSA = currentLSA,
                            isUpFront = isUpFront
                        )
                        if (res.grossWithdrawal > 0.0) {
                            taxFreeIncome += res.taxFreeAmount
                            if (isLisa) {
                                remainingTaxFreeLumpSum2 -= res.taxFreeAmount
                                taxableIncome2 += res.taxableAmount
                            } else {
                                remainingTaxFreeLumpSum1 -= res.taxFreeAmount
                                taxableIncome1 += res.taxableAmount
                            }
                            pension.balance -= res.grossWithdrawal
                            remainingTarget = max(0.0, remainingTarget - res.netReceived)
                            yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", if (isLisa) person2.name else person1.name, res.grossWithdrawal, res.taxPaid))
                        }
                    }

                } else {
                    // --- TAX-MINIMIZED DRAWDOWN STRATEGY (Pension to Basic Rate first, preserving ISA) ---
                    
                    // Step 1: Harvest pension up to personal allowance
                    if (isRetired1 && age1 >= minPensionAge1 && remainingTarget > 0.0) {
                        val person1Pensions = dcPensions.filter { it.personId != "person-2" }
                        for (pension in person1Pensions) {
                            if (remainingTarget <= 0.0) break
                            val allowance1 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome1)
                            if (allowance1 <= 0.0) break

                            val res = calculatePensionWithdrawal(
                                potBalance = pension.balance,
                                netNeeded = remainingTarget,
                                currentTaxableIncome = taxableIncome1,
                                remainingLSA = remainingTaxFreeLumpSum1,
                                isUpFront = isUpFront,
                                maxTaxableAmount = allowance1
                            )
                            if (res.grossWithdrawal > 0.0) {
                                taxFreeIncome += res.taxFreeAmount
                                remainingTaxFreeLumpSum1 -= res.taxFreeAmount
                                taxableIncome1 += res.taxableAmount
                                pension.balance -= res.grossWithdrawal
                                remainingTarget = max(0.0, remainingTarget - res.netReceived)
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", person1.name, res.grossWithdrawal, res.taxPaid))
                            }
                        }
                    }

                    if (isRetired2 && age2 >= minPensionAge2 && remainingTarget > 0.0) {
                        val person2Pensions = dcPensions.filter { it.personId == "person-2" }
                        for (pension in person2Pensions) {
                            if (remainingTarget <= 0.0) break
                            val allowance2 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome2)
                            if (allowance2 <= 0.0) break

                            val res = calculatePensionWithdrawal(
                                potBalance = pension.balance,
                                netNeeded = remainingTarget,
                                currentTaxableIncome = taxableIncome2,
                                remainingLSA = remainingTaxFreeLumpSum2,
                                isUpFront = isUpFront,
                                maxTaxableAmount = allowance2
                            )
                            if (res.grossWithdrawal > 0.0) {
                                taxFreeIncome += res.taxFreeAmount
                                remainingTaxFreeLumpSum2 -= res.taxFreeAmount
                                taxableIncome2 += res.taxableAmount
                                pension.balance -= res.grossWithdrawal
                                remainingTarget = max(0.0, remainingTarget - res.netReceived)
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", person2.name, res.grossWithdrawal, res.taxPaid))
                            }
                        }
                    }

                    // Step 2: Draw from pensions up to the Basic Rate Threshold (£50,270 taxable income)
                    val basicRateLimit = PERSONAL_ALLOWANCE + BASIC_RATE_THRESHOLD
                    if (isRetired1 && age1 >= minPensionAge1 && remainingTarget > 0.0) {
                        val person1Pensions = dcPensions.filter { it.personId != "person-2" }
                        for (pension in person1Pensions) {
                            if (remainingTarget <= 0.0) break
                            val basicCap1 = max(0.0, basicRateLimit - taxableIncome1)
                            if (basicCap1 <= 0.0) break

                            val res = calculatePensionWithdrawal(
                                potBalance = pension.balance,
                                netNeeded = remainingTarget,
                                currentTaxableIncome = taxableIncome1,
                                remainingLSA = remainingTaxFreeLumpSum1,
                                isUpFront = isUpFront,
                                maxTaxableAmount = basicCap1
                            )
                            if (res.grossWithdrawal > 0.0) {
                                taxFreeIncome += res.taxFreeAmount
                                remainingTaxFreeLumpSum1 -= res.taxFreeAmount
                                taxableIncome1 += res.taxableAmount
                                pension.balance -= res.grossWithdrawal
                                remainingTarget = max(0.0, remainingTarget - res.netReceived)
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", person1.name, res.grossWithdrawal, res.taxPaid))
                            }
                        }
                    }

                    if (isRetired2 && age2 >= minPensionAge2 && remainingTarget > 0.0) {
                        val person2Pensions = dcPensions.filter { it.personId == "person-2" }
                        for (pension in person2Pensions) {
                            if (remainingTarget <= 0.0) break
                            val basicCap2 = max(0.0, basicRateLimit - taxableIncome2)
                            if (basicCap2 <= 0.0) break

                            val res = calculatePensionWithdrawal(
                                potBalance = pension.balance,
                                netNeeded = remainingTarget,
                                currentTaxableIncome = taxableIncome2,
                                remainingLSA = remainingTaxFreeLumpSum2,
                                isUpFront = isUpFront,
                                maxTaxableAmount = basicCap2
                            )
                            if (res.grossWithdrawal > 0.0) {
                                taxFreeIncome += res.taxFreeAmount
                                remainingTaxFreeLumpSum2 -= res.taxFreeAmount
                                taxableIncome2 += res.taxableAmount
                                pension.balance -= res.grossWithdrawal
                                remainingTarget = max(0.0, remainingTarget - res.netReceived)
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", person2.name, res.grossWithdrawal, res.taxPaid))
                            }
                        }
                    }

                    // Step 3: Draw from other savings (Current accounts, then GIAs) BEFORE ISAs due to ISA tax-free nature
                    for (saving in savings.filter { it.type == AccountType.CURRENT }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            taxFreeIncome += withdrawal
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            val owner = if (saving.personId == "person-2") person2.name else person1.name
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", owner, withdrawal, 0.0))
                        }
                    }
                    for (saving in savings.filter { it.type == AccountType.GENERAL_INVESTMENT }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            taxFreeIncome += withdrawal
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            val owner = if (saving.personId == "person-2") person2.name else person1.name
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", owner, withdrawal, 0.0))
                        }
                    }

                    // Step 4: Draw from ISAs (tax-free savings preserved until other savings are exhausted)
                    for (saving in savings.filter { it.type == AccountType.ISA }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            taxFreeIncome += withdrawal
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            val owner = if (saving.personId == "person-2") person2.name else person1.name
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", owner, withdrawal, 0.0))
                        }
                    }

                    // Step 5: Draw from pensions above Basic Rate threshold (Higher Rate / 40%)
                    for (pension in dcPensions) {
                        if (remainingTarget <= 0.0) break
                        if (pension.balance <= 0.0) continue
                        val isLisa = pension.personId == "person-2"
                        val isOwnerRetired = if (isLisa) isRetired2 else isRetired1
                        val ownerAge = if (isLisa) age2 else age1
                        val minAge = if (isLisa) minPensionAge2 else minPensionAge1
                        if (!isOwnerRetired || ownerAge < minAge) continue

                        val currentTaxable = if (isLisa) taxableIncome2 else taxableIncome1
                        val currentLSA = if (isLisa) remainingTaxFreeLumpSum2 else remainingTaxFreeLumpSum1

                        val res = calculatePensionWithdrawal(
                            potBalance = pension.balance,
                            netNeeded = remainingTarget,
                            currentTaxableIncome = currentTaxable,
                            remainingLSA = currentLSA,
                            isUpFront = isUpFront
                        )
                        if (res.grossWithdrawal > 0.0) {
                            taxFreeIncome += res.taxFreeAmount
                            if (isLisa) {
                                remainingTaxFreeLumpSum2 -= res.taxFreeAmount
                                taxableIncome2 += res.taxableAmount
                            } else {
                                remainingTaxFreeLumpSum1 -= res.taxFreeAmount
                                taxableIncome1 += res.taxableAmount
                            }
                            pension.balance -= res.grossWithdrawal
                            remainingTarget = max(0.0, remainingTarget - res.netReceived)
                            yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", if (isLisa) person2.name else person1.name, res.grossWithdrawal, res.taxPaid))
                        }
                    }
                }

                val incomeTax1 = calculateIncomeTax(taxableIncome1)
                val incomeTax2 = calculateIncomeTax(taxableIncome2)

                // 3. Investment returns and Tax on Savings
                // DC pensions grow tax-free inside pension wrapper
                dcPensions.forEach { pension ->
                    val netGrowthRate = max(0.0, portfolioGrowthRate - (pension.annualManagementCharge / 100.0))
                    pension.balance *= (1.0 + netGrowthRate)
                }

                // Calculate savings interest / returns in General Investment Accounts (GIA)
                val giaReturn1 = savings.filter { it.personId != "person-2" && it.type == AccountType.GENERAL_INVESTMENT }
                    .sumOf { it.balance * (if (it.interestRate > 0.0) it.interestRate / 100.0 else portfolioGrowthRate) }
                val giaReturn2 = savings.filter { it.personId == "person-2" && it.type == AccountType.GENERAL_INVESTMENT }
                    .sumOf { it.balance * (if (it.interestRate > 0.0) it.interestRate / 100.0 else portfolioGrowthRate) }

                val savingsTax1 = calculateTaxOnSavings(giaReturn1, taxableIncome1)
                val savingsTax2 = calculateTaxOnSavings(giaReturn2, taxableIncome2)

                if (savingsTax1 > 0.0) {
                    yearWithdrawals.add(WithdrawalDetail("GIA Savings Tax", person1.name, giaReturn1, savingsTax1))
                }
                if (savingsTax2 > 0.0) {
                    yearWithdrawals.add(WithdrawalDetail("GIA Savings Tax", person2.name, giaReturn2, savingsTax2))
                }

                // Apply growth to savings accounts (ISAs and Current accounts grow tax-free; GIA is net of savings tax)
                savings.forEach { saving ->
                    val rate = if (saving.type == AccountType.ISA || saving.type == AccountType.GENERAL_INVESTMENT) {
                        if (saving.interestRate > 0.0) saving.interestRate / 100.0 else portfolioGrowthRate
                    } else {
                        saving.interestRate / 100.0
                    }
                    if (saving.type == AccountType.GENERAL_INVESTMENT) {
                        val isLisa = saving.personId == "person-2"
                        val totalReturn = if (isLisa) giaReturn2 else giaReturn1
                        val totalTax = if (isLisa) savingsTax2 else savingsTax1
                        val returnAmount = saving.balance * rate
                        val taxForThis = if (totalReturn > 0.0) totalTax * (returnAmount / totalReturn) else 0.0
                        saving.balance += max(0.0, returnAmount - taxForThis)
                    } else {
                        saving.balance *= (1.0 + rate)
                    }
                }

                val totalPensionVal = dcPensions.sumOf { it.balance }
                val totalSavingsVal = savings.sumOf { it.balance }

                val tax1 = incomeTax1 + savingsTax1
                val tax2 = incomeTax2 + savingsTax2
                val currentTax = tax1 + tax2
                totalTaxPaid += currentTax

                val netIncome = taxFreeIncome + (taxableIncome1 - incomeTax1) + (taxableIncome2 - incomeTax2)

                // Realistic feasibility: user must genuinely meet target income (within £1 tolerance for rounding).
                val metTarget = netIncome >= (targetIncome - 1.0)
                if (isRetiredActive && !metTarget) {
                    retirementFeasible = false
                }

                results.add(
                    ProjectionResult(
                        age = ageActive,
                        year = year,
                        totalPensionValue = totalPensionVal,
                        totalSavings = totalSavingsVal,
                        annualIncome = taxableIncome1 + taxableIncome2 + taxFreeIncome,
                        netIncome = netIncome,
                        tax = currentTax,
                        tax1 = tax1,
                        tax2 = tax2,
                        canRetire = metTarget,
                        withdrawals = yearWithdrawals
                    )
                )
            } else {
                // Accumulation phase: apply growth to pots and annual Bed & ISA
                performBedAndIsaTransfer(
                    savings = savings,
                    projectionType = projectionType,
                    person1 = person1,
                    person2 = person2,
                    isRetired1 = false,
                    isRetired2 = false,
                    assumptions = assumptions,
                    inflationFactor = inflationFactor
                )

                dcPensions.forEach { pension ->
                    val netGrowthRate = max(0.0, portfolioGrowthRate - (pension.annualManagementCharge / 100.0))
                    pension.balance *= (1.0 + netGrowthRate)
                }

                val giaReturn1 = savings.filter { it.personId != "person-2" && it.type == AccountType.GENERAL_INVESTMENT }
                    .sumOf { it.balance * (if (it.interestRate > 0.0) it.interestRate / 100.0 else portfolioGrowthRate) }
                val giaReturn2 = savings.filter { it.personId == "person-2" && it.type == AccountType.GENERAL_INVESTMENT }
                    .sumOf { it.balance * (if (it.interestRate > 0.0) it.interestRate / 100.0 else portfolioGrowthRate) }

                val savingsTax1 = calculateTaxOnSavings(giaReturn1, 0.0)
                val savingsTax2 = calculateTaxOnSavings(giaReturn2, 0.0)
                val currentTax = savingsTax1 + savingsTax2
                totalTaxPaid += currentTax

                savings.forEach { saving ->
                    val rate = if (saving.type == AccountType.ISA || saving.type == AccountType.GENERAL_INVESTMENT) {
                        if (saving.interestRate > 0.0) saving.interestRate / 100.0 else portfolioGrowthRate
                    } else {
                        saving.interestRate / 100.0
                    }
                    if (saving.type == AccountType.GENERAL_INVESTMENT) {
                        val isLisa = saving.personId == "person-2"
                        val totalReturn = if (isLisa) giaReturn2 else giaReturn1
                        val totalTax = if (isLisa) savingsTax2 else savingsTax1
                        val returnAmount = saving.balance * rate
                        val taxForThis = if (totalReturn > 0.0) totalTax * (returnAmount / totalReturn) else 0.0
                        saving.balance += max(0.0, returnAmount - taxForThis)
                    } else {
                        saving.balance *= (1.0 + rate)
                    }
                }

                val totalPensionVal = dcPensions.sumOf { it.balance }
                val totalSavingsVal = savings.sumOf { it.balance }
                results.add(
                    ProjectionResult(
                        age = ageActive,
                        year = year,
                        totalPensionValue = totalPensionVal,
                        totalSavings = totalSavingsVal,
                        annualIncome = 0.0,
                        netIncome = 0.0,
                        tax = currentTax,
                        tax1 = savingsTax1,
                        tax2 = savingsTax2,
                        canRetire = false,
                        withdrawals = emptyList()
                    )
                )
            }
        }

        return RetirementProjection(
            results = results,
            retirementAge = retirementAge1,
            feasible = retirementFeasible,
            totalTaxPaid = totalTaxPaid
        )
    }
}
