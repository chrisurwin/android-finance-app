package com.chris.financeapp.utils

import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.InvestmentAssumptions
import com.chris.financeapp.data.model.DrawdownPreferences
import com.chris.financeapp.data.model.ProjectionResult
import com.chris.financeapp.data.model.RetirementProjection
import com.chris.financeapp.data.model.Person
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
        person1: Person,
        person2: Person,
        retirementAge1: Int,
        retirementAge2: Int
    ): RetirementProjection {
        val currentYear = 2026
        val currentAge1 = currentYear - person1.birthYear
        val currentAge2 = currentYear - person2.birthYear
        val endAge = preferences.endAge
        
        val results = mutableListOf<ProjectionResult>()
        
        // Calculate weighted portfolio return based on allocations
        val portfolioGrowthRate = (assumptions.equityReturn * assumptions.equityAllocation) +
                (assumptions.bondReturn * assumptions.bondAllocation) +
                (assumptions.cashReturn * assumptions.cashAllocation)

        // Prepare working copies of balances
        var dcPensions = accounts.filter { it.type == AccountType.PENSION }
            .map { it.copy() }
            .toMutableList()
            
        var savings = accounts.filter { it.type != AccountType.PENSION }
            .map { it.copy() }
            .toMutableList()

        var retirementFeasible = true
        val yearsToSimulate = endAge - currentAge1

        for (yearOffset in 0..yearsToSimulate) {
            val age1 = currentAge1 + yearOffset
            val age2 = currentAge2 + yearOffset
            val year = currentYear + yearOffset
            val inflationFactor = (1 + assumptions.inflationRate).pow(yearOffset.toDouble())

            val targetIncome = if (preferences.inflationAdjusted) {
                preferences.targetAnnualIncome * inflationFactor
            } else {
                preferences.targetAnnualIncome
            }

            val isRetired1 = age1 >= retirementAge1
            val isRetired2 = age2 >= retirementAge2

            // Grow assets and add contributions for active builders
            dcPensions = dcPensions.map { pension ->
                val isOwnerRetired = if (pension.personId == "person-2") isRetired2 else isRetired1
                val realReturn = portfolioGrowthRate - (pension.annualManagementCharge / 100.0)
                if (!isOwnerRetired) {
                    val totalContrib = (pension.monthlyContribution + pension.employerContribution) * 12.0 * inflationFactor
                    pension.copy(balance = (pension.balance + totalContrib) * (1.0 + realReturn))
                } else {
                    pension.copy(balance = pension.balance * (1.0 + realReturn))
                }
            }.toMutableList()

            savings = savings.map { saving ->
                val isOwnerRetired = if (saving.personId == "person-2") isRetired2 else isRetired1
                if (!isOwnerRetired) {
                    val totalContrib = saving.monthlyContribution * 12.0 * inflationFactor
                    saving.copy(balance = (saving.balance + totalContrib) * (1.0 + (saving.interestRate / 100.0)))
                } else {
                    saving.copy(balance = saving.balance * (1.0 + (saving.interestRate / 100.0)))
                }
            }.toMutableList()

            if (isRetired1 || isRetired2) {
                // Calculate State Pension (from age 67) for each retired person
                val statePension1 = if (age1 >= STATE_PENSION_AGE) calculateStatePension(35) * inflationFactor else 0.0
                val statePension2 = if (age2 >= STATE_PENSION_AGE) calculateStatePension(35) * inflationFactor else 0.0
                val totalStatePension = statePension1 + statePension2

                var taxFreeIncome = 0.0
                var taxableIncome1 = statePension1
                var taxableIncome2 = statePension2
                var remainingTarget = max(0.0, targetIncome - totalStatePension)

                // UK Tax-Efficient Drawdown Strategy:
                // Step 1: Harvest pension tax-free up to each retired person's remaining Personal Allowance limit (gross = allowance / 0.75)
                if (isRetired1 && age1 >= 55 && remainingTarget > 0.0) {
                    val remainingAllowance1 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome1)
                    if (remainingAllowance1 > 0.0) {
                        val targetHarvest = remainingAllowance1 / 0.75
                        val person1Pensions = dcPensions.filter { it.personId != "person-2" }
                        for (pension in person1Pensions) {
                            if (remainingTarget <= 0.0) break
                            val withdrawal = min(pension.balance, min(targetHarvest, remainingTarget))
                            if (withdrawal > 0.0) {
                                val tfPart = withdrawal * 0.25
                                val taxablePart = withdrawal * 0.75
                                taxFreeIncome += tfPart
                                taxableIncome1 += taxablePart
                                pension.balance -= withdrawal
                                remainingTarget -= (tfPart + taxablePart)
                            }
                        }
                    }
                }

                if (isRetired2 && age2 >= 55 && remainingTarget > 0.0) {
                    val remainingAllowance2 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome2)
                    if (remainingAllowance2 > 0.0) {
                        val targetHarvest = remainingAllowance2 / 0.75
                        val person2Pensions = dcPensions.filter { it.personId == "person-2" }
                        for (pension in person2Pensions) {
                            if (remainingTarget <= 0.0) break
                            val withdrawal = min(pension.balance, min(targetHarvest, remainingTarget))
                            if (withdrawal > 0.0) {
                                val tfPart = withdrawal * 0.25
                                val taxablePart = withdrawal * 0.75
                                taxFreeIncome += tfPart
                                taxableIncome2 += taxablePart
                                pension.balance -= withdrawal
                                remainingTarget -= (tfPart + taxablePart)
                            }
                        }
                    }
                }

                // Step 2: Draw from ISAs (tax-free savings) next
                for (saving in savings.filter { it.type == AccountType.ISA }) {
                    if (remainingTarget <= 0.0) break
                    val withdrawal = min(saving.balance, remainingTarget)
                    taxFreeIncome += withdrawal
                    saving.balance -= withdrawal
                    remainingTarget -= withdrawal
                }

                // Step 3: Draw from taxable savings (General Investments/Current) next
                for (saving in savings.filter { it.type != AccountType.ISA }) {
                    if (remainingTarget <= 0.0) break
                    val withdrawal = min(saving.balance, remainingTarget)
                    if (saving.personId == "person-2") {
                        taxableIncome2 += withdrawal
                    } else {
                        taxableIncome1 += withdrawal
                    }
                    saving.balance -= withdrawal
                    remainingTarget -= withdrawal
                }

                // Step 4: Draw remaining target from pensions (subject to income tax)
                for (pension in dcPensions) {
                    if (remainingTarget <= 0.0) break
                    if (pension.balance > 0.0) {
                        val withdrawal = min(pension.balance, remainingTarget / 0.85) // basic rate estimate
                        val tfPart = withdrawal * 0.25
                        val taxablePart = withdrawal * 0.75
                        taxFreeIncome += tfPart
                        if (pension.personId == "person-2") {
                            taxableIncome2 += taxablePart
                        } else {
                            taxableIncome1 += taxablePart
                        }
                        pension.balance -= withdrawal
                        remainingTarget -= (tfPart + taxablePart * 0.80)
                    }
                }

                val tax1 = calculateIncomeTax(taxableIncome1)
                val tax2 = calculateIncomeTax(taxableIncome2)
                val netIncome = taxFreeIncome + (taxableIncome1 - tax1) + (taxableIncome2 - tax2)
                val totalPensionVal = dcPensions.sumOf { it.balance }
                val totalSavingsVal = savings.sumOf { it.balance }

                val metTarget = netIncome >= targetIncome || (totalPensionVal + totalSavingsVal > 0.0 && netIncome >= targetIncome * 0.85)
                if (!metTarget && age1 < 90) {
                    retirementFeasible = false
                }

                results.add(
                    ProjectionResult(
                        age = age1,
                        year = year,
                        totalPensionValue = totalPensionVal,
                        totalSavings = totalSavingsVal,
                        annualIncome = taxableIncome1 + taxableIncome2 + taxFreeIncome,
                        netIncome = netIncome,
                        tax = tax1 + tax2,
                        canRetire = metTarget
                    )
                )
            } else {
                val totalPensionVal = dcPensions.sumOf { it.balance }
                val totalSavingsVal = savings.sumOf { it.balance }
                results.add(
                    ProjectionResult(
                        age = age1,
                        year = year,
                        totalPensionValue = totalPensionVal,
                        totalSavings = totalSavingsVal,
                        annualIncome = 0.0,
                        netIncome = 0.0,
                        tax = 0.0,
                        canRetire = false
                    )
                )
            }
        }

        return RetirementProjection(
            results = results,
            retirementAge = retirementAge1,
            feasible = retirementFeasible
        )
    }
}
