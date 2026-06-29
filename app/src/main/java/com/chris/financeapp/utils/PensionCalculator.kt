package com.chris.financeapp.utils

import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.InvestmentAssumptions
import com.chris.financeapp.data.model.DrawdownPreferences
import com.chris.financeapp.data.model.ProjectionResult
import com.chris.financeapp.data.model.RetirementProjection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object PensionCalculator {

    private const val FULL_STATE_PENSION_WEEKLY = 230.25
    private const val FULL_STATE_PENSION_ANNUAL = FULL_STATE_PENSION_WEEKLY * 52.0
    private const val STATE_PENSION_AGE = 67

    // UK Tax Rates (2024/25)
    private const val PERSONAL_ALLOWANCE = 12570.0
    private const val BASIC_RATE_THRESHOLD = 37700.0 // up to 50,270
    private const val HIGHER_RATE_THRESHOLD = 125140.0 // up to 125,140
    
    private const val BASIC_RATE = 0.20
    private const val HIGHER_RATE = 0.40
    private const val ADDITIONAL_RATE = 0.45

    fun calculateStatePension(qualifyingYears: Int): Double {
        val proportion = min(qualifyingYears / 35.0, 1.0)
        return FULL_STATE_PENSION_ANNUAL * proportion
    }

    fun calculateIncomeTax(taxableIncome: Double): Double {
        var tax = 0.0
        var remainingIncome = max(0.0, taxableIncome - PERSONAL_ALLOWANCE)

        // Basic rate band: £12,570 to £50,270 (size: £37,700)
        if (remainingIncome > 0.0) {
            val taxableAtBasic = min(remainingIncome, BASIC_RATE_THRESHOLD)
            tax += taxableAtBasic * BASIC_RATE
            remainingIncome -= taxableAtBasic
        }

        // Higher rate band: £50,270 to £125,140 (size: £74,870)
        if (remainingIncome > 0.0) {
            val higherRateBandLimit = HIGHER_RATE_THRESHOLD - BASIC_RATE_THRESHOLD
            val taxableAtHigher = min(remainingIncome, higherRateBandLimit)
            tax += taxableAtHigher * HIGHER_RATE
            remainingIncome -= taxableAtHigher
        }

        // Additional rate band: > £125,140
        if (remainingIncome > 0.0) {
            tax += remainingIncome * ADDITIONAL_RATE
        }

        return tax
    }

    fun calculateProjections(
        accounts: List<Account>,
        assumptions: InvestmentAssumptions,
        preferences: DrawdownPreferences,
        birthYear: Int,
        retirementAgeInput: Int
    ): RetirementProjection {
        val currentYear = 2026
        val currentAge = currentYear - birthYear
        val endAge = preferences.endAge
        
        val results = mutableListOf<ProjectionResult>()
        
        // Calculate weighted portfolio return based on allocations
        val portfolioGrowthRate = (assumptions.equityReturn * assumptions.equityAllocation) +
                (assumptions.bondReturn * assumptions.bondAllocation) +
                (assumptions.cashReturn * assumptions.cashAllocation)

        // Prepare working copies of balances
        // We will simulate the growth and drawdown of these balances over time
        var dcPensions = accounts.filter { it.type == AccountType.PENSION }
            .map { it.copy() }
            .toMutableList()
            
        var savings = accounts.filter { it.type != AccountType.PENSION }
            .map { it.copy() }
            .toMutableList()

        var retirementFeasible = true

        for (age in currentAge..endAge) {
            val yearsFromNow = age - currentAge
            val year = currentYear + yearsFromNow
            val inflationFactor = (1 + assumptions.inflationRate).pow(yearsFromNow.toDouble())

            val targetIncome = if (preferences.inflationAdjusted) {
                preferences.targetAnnualIncome * inflationFactor
            } else {
                preferences.targetAnnualIncome
            }

            // 1. Growth/Accumulation or Drawdown phase execution
            val isRetired = age >= retirementAgeInput

            if (!isRetired) {
                // Accumulation Phase: Grow assets AND add contributions
                dcPensions = dcPensions.map { pension ->
                    val totalContrib = (pension.monthlyContribution + pension.employerContribution) * 12.0 * inflationFactor
                    val realReturn = portfolioGrowthRate - (pension.annualManagementCharge / 100.0)
                    val newVal = (pension.balance + totalContrib) * (1.0 + realReturn)
                    pension.copy(balance = newVal)
                }.toMutableList()

                savings = savings.map { saving ->
                    val totalContrib = saving.monthlyContribution * 12.0 * inflationFactor
                    val newVal = (saving.balance + totalContrib) * (1.0 + (saving.interestRate / 100.0))
                    saving.copy(balance = newVal)
                }.toMutableList()

                // In accumulation phase, income is assumed to be salary, net income is target income
                val totalPensionVal = dcPensions.sumOf { it.balance }
                val totalSavingsVal = savings.sumOf { it.balance }
                results.add(
                    ProjectionResult(
                        age = age,
                        year = year,
                        totalPensionValue = totalPensionVal,
                        totalSavings = totalSavingsVal,
                        annualIncome = 0.0,
                        netIncome = 0.0,
                        tax = 0.0,
                        canRetire = false
                    )
                )
            } else {
                // Drawdown Phase: Grow assets first, then execute withdrawals
                dcPensions = dcPensions.map { pension ->
                    val realReturn = portfolioGrowthRate - (pension.annualManagementCharge / 100.0)
                    val newVal = pension.balance * (1.0 + realReturn)
                    pension.copy(balance = newVal)
                }.toMutableList()

                savings = savings.map { saving ->
                    val newVal = saving.balance * (1.0 + (saving.interestRate / 100.0))
                    saving.copy(balance = newVal)
                }.toMutableList()

                // Calculate fixed income (State Pension from age 67)
                val statePension = if (age >= STATE_PENSION_AGE) {
                    calculateStatePension(35) * inflationFactor
                } else {
                    0.0
                }

                var taxFreeIncome = 0.0
                var taxableIncome = statePension
                var remainingTarget = max(0.0, targetIncome - statePension)

                // Drawdown Strategy:
                // 1. Drawdown from ISAs (tax-free savings) first
                for (saving in savings.filter { it.type == AccountType.ISA }) {
                    if (remainingTarget <= 0.0) break
                    val withdrawal = min(saving.balance, remainingTarget)
                    taxFreeIncome += withdrawal
                    saving.balance -= withdrawal
                    remainingTarget -= withdrawal
                }

                // 2. Drawdown from General Investments / Current accounts (treated as taxable savings in this model)
                for (saving in savings.filter { it.type != AccountType.ISA }) {
                    if (remainingTarget <= 0.0) break
                    val withdrawal = min(saving.balance, remainingTarget)
                    taxableIncome += withdrawal
                    saving.balance -= withdrawal
                    remainingTarget -= withdrawal
                }

                // 3. Drawdown from Pensions (25% tax-free, 75% taxable)
                for (pension in dcPensions) {
                    if (remainingTarget <= 0.0) break
                    val maxPensionWithdrawal = pension.balance
                    if (maxPensionWithdrawal > 0.0) {
                        // In the UK, you can take 25% tax-free.
                        // To satisfy remainingTarget of net income, we take a combined withdrawal:
                        // Net = 0.25 * Gross + 0.75 * Gross * (1 - TaxRate)
                        // For simplicity in year-by-year calculations, we withdraw from the pension pot.
                        // If we withdraw W from the pension:
                        // 25% is tax-free: W_tf = 0.25 * W
                        // 75% is taxable: W_t = 0.75 * W
                        // Let's withdraw W = min(balance, remainingTarget / 0.85) (rough average tax adjustment, or exact calculation)
                        val withdrawal = min(pension.balance, remainingTarget / 0.90) // estimate
                        val tfPart = withdrawal * 0.25
                        val taxablePart = withdrawal * 0.75
                        
                        taxFreeIncome += tfPart
                        taxableIncome += taxablePart
                        pension.balance -= withdrawal
                        remainingTarget -= (tfPart + taxablePart * 0.80) // rough net reduction
                    }
                }

                val tax = calculateIncomeTax(taxableIncome)
                val netIncome = taxFreeIncome + taxableIncome - tax
                val totalPensionVal = dcPensions.sumOf { it.balance }
                val totalSavingsVal = savings.sumOf { it.balance }

                val metTarget = netIncome >= targetIncome || (totalPensionVal + totalSavingsVal > 0.0 && netIncome >= targetIncome * 0.85)
                if (!metTarget && age < 90) {
                    retirementFeasible = false
                }

                results.add(
                    ProjectionResult(
                        age = age,
                        year = year,
                        totalPensionValue = totalPensionVal,
                        totalSavings = totalSavingsVal,
                        annualIncome = taxableIncome + taxFreeIncome,
                        netIncome = netIncome,
                        tax = tax,
                        canRetire = metTarget
                    )
                )
            }
        }

        return RetirementProjection(
            results = results,
            retirementAge = retirementAgeInput,
            feasible = retirementFeasible
        )
    }
}
