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
        retirementAge2: Int,
        projectionType: ProjectionType = ProjectionType.COUPLE
    ): RetirementProjection {
        val currentYear = 2026
        val currentAge1 = currentYear - person1.birthYear
        val currentAge2 = currentYear - person2.birthYear
        val endAge = preferences.endAge

        val (currentAgeActive, activePersonName) = when (projectionType) {
            ProjectionType.INDIVIDUAL_CHRIS -> Pair(currentAge1, "Chris")
            ProjectionType.INDIVIDUAL_LISA -> Pair(currentAge2, "Lisa")
            ProjectionType.COUPLE -> Pair(currentAge1, "Chris & Lisa")
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

        for (yearOffset in 0..yearsToSimulate) {
            val age1 = currentAge1 + yearOffset
            val age2 = currentAge2 + yearOffset
            val ageActive = currentAgeActive + yearOffset
            val year = currentYear + yearOffset
            val inflationFactor = (1 + assumptions.inflationRate).pow(yearOffset.toDouble())

            val targetIncome = if (preferences.inflationAdjusted) {
                preferences.targetAnnualIncome * inflationFactor
            } else {
                preferences.targetAnnualIncome
            }

            val isRetired1 = if (projectionType == ProjectionType.INDIVIDUAL_LISA) false else age1 >= retirementAge1
            val isRetired2 = if (projectionType == ProjectionType.INDIVIDUAL_CHRIS) false else age2 >= retirementAge2

            val yearWithdrawals = mutableListOf<WithdrawalDetail>()

            // Upfront Lump Sum Option logic:
            // Extract 25% of the DC pensions as a tax-free lump sum in the first year of retirement, adding it to savings.
            if (preferences.lumpSumOption == LumpSumOption.UP_FRONT) {
                if (isRetired1 && !hasTakenLumpSum1) {
                    var totalLumpSum1 = 0.0
                    dcPensions = dcPensions.map { pension ->
                        if (pension.personId != "person-2") {
                            val lump = pension.balance * 0.25
                            totalLumpSum1 += lump
                            if (lump > 0.0) {
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name} (Lump Sum)", "Chris", lump, 0.0))
                            }
                            pension.copy(balance = pension.balance * 0.75)
                        } else {
                            pension
                        }
                    }.toMutableList()
                    
                    if (totalLumpSum1 > 0.0) {
                        val isaIndex = savings.indexOfFirst { it.personId != "person-2" && it.type == AccountType.ISA }
                        if (isaIndex >= 0) {
                            savings[isaIndex].balance += totalLumpSum1
                        } else {
                            val saveIndex = savings.indexOfFirst { it.personId != "person-2" }
                            if (saveIndex >= 0) {
                                savings[saveIndex].balance += totalLumpSum1
                            } else {
                                savings.add(Account(id = "lump-sum-isa-1", name = "Tax-Free Lump Sum", type = AccountType.ISA, institution = Institution.HOSTED, balance = totalLumpSum1, personId = "person-1"))
                            }
                        }
                    }
                    hasTakenLumpSum1 = true
                }
                if (isRetired2 && !hasTakenLumpSum2) {
                    var totalLumpSum2 = 0.0
                    dcPensions = dcPensions.map { pension ->
                        if (pension.personId == "person-2") {
                            val lump = pension.balance * 0.25
                            totalLumpSum2 += lump
                            if (lump > 0.0) {
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name} (Lump Sum)", "Lisa", lump, 0.0))
                            }
                            pension.copy(balance = pension.balance * 0.75)
                        } else {
                            pension
                        }
                    }.toMutableList()
                    
                    if (totalLumpSum2 > 0.0) {
                        val isaIndex = savings.indexOfFirst { it.personId == "person-2" && it.type == AccountType.ISA }
                        if (isaIndex >= 0) {
                            savings[isaIndex].balance += totalLumpSum2
                        } else {
                            val saveIndex = savings.indexOfFirst { it.personId == "person-2" }
                            if (saveIndex >= 0) {
                                savings[saveIndex].balance += totalLumpSum2
                            } else {
                                savings.add(Account(id = "lump-sum-isa-2", name = "Tax-Free Lump Sum", type = AccountType.ISA, institution = Institution.HOSTED, balance = totalLumpSum2, personId = "person-2"))
                            }
                        }
                    }
                    hasTakenLumpSum2 = true
                }
            }

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
                val statePension1 = if (isRetired1 && age1 >= STATE_PENSION_AGE) calculateStatePension(35) * inflationFactor else 0.0
                val statePension2 = if (isRetired2 && age2 >= STATE_PENSION_AGE) calculateStatePension(35) * inflationFactor else 0.0
                
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
                
                // Calculate base tax for guaranteed incomes
                val baseTax1 = calculateIncomeTax(taxableIncome1)
                val baseTax2 = calculateIncomeTax(taxableIncome2)

                // Log base withdrawals (State Pension & DB final salaries) with proportional tax allocation
                if (statePension1 > 0.0) {
                    val taxFraction = if (taxableIncome1 > 0.0) statePension1 / taxableIncome1 else 0.0
                    yearWithdrawals.add(WithdrawalDetail("State Pension", "Chris", statePension1, baseTax1 * taxFraction))
                }
                if (statePension2 > 0.0) {
                    val taxFraction = if (taxableIncome2 > 0.0) statePension2 / taxableIncome2 else 0.0
                    yearWithdrawals.add(WithdrawalDetail("State Pension", "Lisa", statePension2, baseTax2 * taxFraction))
                }
                dbPayouts1.forEach { (name, payout) ->
                    val taxFraction = if (taxableIncome1 > 0.0) payout / taxableIncome1 else 0.0
                    yearWithdrawals.add(WithdrawalDetail(name, "Chris", payout, baseTax1 * taxFraction))
                }
                dbPayouts2.forEach { (name, payout) ->
                    val taxFraction = if (taxableIncome2 > 0.0) payout / taxableIncome2 else 0.0
                    yearWithdrawals.add(WithdrawalDetail(name, "Lisa", payout, baseTax2 * taxFraction))
                }

                // Calculate net guaranteed income after tax
                val netGuaranteed = (taxableIncome1 - baseTax1) + (taxableIncome2 - baseTax2)
                var remainingTarget = max(0.0, targetIncome - netGuaranteed)

                // Define taxable fraction of pension draws
                val taxableFraction = if (preferences.lumpSumOption == LumpSumOption.UP_FRONT) 1.0 else 0.75
                val netFractionInBasicRate = (1.0 - taxableFraction) + taxableFraction * 0.80

                if (preferences.strategy == DrawdownStrategy.STANDARD) {
                    // --- STANDARD DRAWDOWN STRATEGY (ISA First) ---
                    
                    // Step 1: Harvest pension up to personal allowance
                    if (isRetired1 && age1 >= 55 && remainingTarget > 0.0) {
                        val remainingAllowance1 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome1)
                        if (remainingAllowance1 > 0.0) {
                            val maxHarvest = remainingAllowance1 / taxableFraction
                            val person1Pensions = dcPensions.filter { it.personId != "person-2" }
                            for (pension in person1Pensions) {
                                if (remainingTarget <= 0.0) break
                                val withdrawal = min(pension.balance, min(maxHarvest, remainingTarget))
                                if (withdrawal > 0.0) {
                                    val tfPart = withdrawal * (1.0 - taxableFraction)
                                    val taxablePart = withdrawal * taxableFraction
                                    
                                    val taxBefore = calculateIncomeTax(taxableIncome1)
                                    taxableIncome1 += taxablePart
                                    val taxAfter = calculateIncomeTax(taxableIncome1)
                                    val taxPaidForThisDraw = taxAfter - taxBefore

                                    taxFreeIncome += tfPart
                                    pension.balance -= withdrawal
                                    remainingTarget -= (tfPart + taxablePart)
                                    yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", "Chris", withdrawal, taxPaidForThisDraw))
                                }
                            }
                        }
                    }

                    if (isRetired2 && age2 >= 55 && remainingTarget > 0.0) {
                        val remainingAllowance2 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome2)
                        if (remainingAllowance2 > 0.0) {
                            val maxHarvest = remainingAllowance2 / taxableFraction
                            val person2Pensions = dcPensions.filter { it.personId == "person-2" }
                            for (pension in person2Pensions) {
                                if (remainingTarget <= 0.0) break
                                val withdrawal = min(pension.balance, min(maxHarvest, remainingTarget))
                                if (withdrawal > 0.0) {
                                    val tfPart = withdrawal * (1.0 - taxableFraction)
                                    val taxablePart = withdrawal * taxableFraction
                                    
                                    val taxBefore = calculateIncomeTax(taxableIncome2)
                                    taxableIncome2 += taxablePart
                                    val taxAfter = calculateIncomeTax(taxableIncome2)
                                    val taxPaidForThisDraw = taxAfter - taxBefore

                                    taxFreeIncome += tfPart
                                    pension.balance -= withdrawal
                                    remainingTarget -= (tfPart + taxablePart)
                                    yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", "Lisa", withdrawal, taxPaidForThisDraw))
                                }
                            }
                        }
                    }

                    // Step 2: Draw from ISAs next (tax-free)
                    for (saving in savings.filter { it.type == AccountType.ISA }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            taxFreeIncome += withdrawal
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            val owner = if (saving.personId == "person-2") "Lisa" else "Chris"
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", owner, withdrawal, 0.0))
                        }
                    }

                    // Step 3: Draw from GIA next (taxable savings)
                    for (saving in savings.filter { it.type != AccountType.ISA }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            val isLisa = saving.personId == "person-2"
                            val taxBefore = if (isLisa) calculateIncomeTax(taxableIncome2) else calculateIncomeTax(taxableIncome1)
                            if (isLisa) {
                                taxableIncome2 += withdrawal
                            } else {
                                taxableIncome1 += withdrawal
                            }
                            val taxAfter = if (isLisa) calculateIncomeTax(taxableIncome2) else calculateIncomeTax(taxableIncome1)
                            val taxPaidForThisDraw = taxAfter - taxBefore
                            
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", if (isLisa) "Lisa" else "Chris", withdrawal, taxPaidForThisDraw))
                        }
                    }

                    // Step 4: Draw remaining from pension
                    for (pension in dcPensions) {
                        if (remainingTarget <= 0.0) break
                        if (pension.balance > 0.0) {
                            val withdrawal = min(pension.balance, remainingTarget / netFractionInBasicRate)
                            if (withdrawal > 0.0) {
                                val tfPart = withdrawal * (1.0 - taxableFraction)
                                val taxablePart = withdrawal * taxableFraction
                                val isLisa = pension.personId == "person-2"
                                
                                val taxBefore = if (isLisa) calculateIncomeTax(taxableIncome2) else calculateIncomeTax(taxableIncome1)
                                if (isLisa) {
                                    taxableIncome2 += taxablePart
                                } else {
                                    taxableIncome1 += taxablePart
                                }
                                val taxAfter = if (isLisa) calculateIncomeTax(taxableIncome2) else calculateIncomeTax(taxableIncome1)
                                val taxPaidForThisDraw = taxAfter - taxBefore

                                taxFreeIncome += tfPart
                                pension.balance -= withdrawal
                                remainingTarget -= (tfPart + taxablePart * 0.80)
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", if (isLisa) "Lisa" else "Chris", withdrawal, taxPaidForThisDraw))
                            }
                        }
                    }

                } else {
                    // --- TAX-MINIMIZED DRAWDOWN STRATEGY (Pension to Basic Rate first, preserving ISA) ---
                    
                    // Step 1: Harvest pension up to personal allowance
                    if (isRetired1 && age1 >= 55 && remainingTarget > 0.0) {
                        val remainingAllowance1 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome1)
                        if (remainingAllowance1 > 0.0) {
                            val maxHarvest = remainingAllowance1 / taxableFraction
                            val person1Pensions = dcPensions.filter { it.personId != "person-2" }
                            for (pension in person1Pensions) {
                                if (remainingTarget <= 0.0) break
                                val withdrawal = min(pension.balance, min(maxHarvest, remainingTarget))
                                if (withdrawal > 0.0) {
                                    val tfPart = withdrawal * (1.0 - taxableFraction)
                                    val taxablePart = withdrawal * taxableFraction
                                    
                                    val taxBefore = calculateIncomeTax(taxableIncome1)
                                    taxableIncome1 += taxablePart
                                    val taxAfter = calculateIncomeTax(taxableIncome1)
                                    val taxPaidForThisDraw = taxAfter - taxBefore

                                    taxFreeIncome += tfPart
                                    pension.balance -= withdrawal
                                    remainingTarget -= (tfPart + taxablePart)
                                    yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", "Chris", withdrawal, taxPaidForThisDraw))
                                }
                            }
                        }
                    }

                    if (isRetired2 && age2 >= 55 && remainingTarget > 0.0) {
                        val remainingAllowance2 = max(0.0, PERSONAL_ALLOWANCE - taxableIncome2)
                        if (remainingAllowance2 > 0.0) {
                            val maxHarvest = remainingAllowance2 / taxableFraction
                            val person2Pensions = dcPensions.filter { it.personId == "person-2" }
                            for (pension in person2Pensions) {
                                if (remainingTarget <= 0.0) break
                                val withdrawal = min(pension.balance, min(maxHarvest, remainingTarget))
                                if (withdrawal > 0.0) {
                                    val tfPart = withdrawal * (1.0 - taxableFraction)
                                    val taxablePart = withdrawal * taxableFraction
                                    
                                    val taxBefore = calculateIncomeTax(taxableIncome2)
                                    taxableIncome2 += taxablePart
                                    val taxAfter = calculateIncomeTax(taxableIncome2)
                                    val taxPaidForThisDraw = taxAfter - taxBefore

                                    taxFreeIncome += tfPart
                                    pension.balance -= withdrawal
                                    remainingTarget -= (tfPart + taxablePart)
                                    yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", "Lisa", withdrawal, taxPaidForThisDraw))
                                }
                            }
                        }
                    }

                    // Step 2: Draw from pensions up to the Basic Rate Threshold (taxable income up to £50,270)
                    val basicRateLimit = PERSONAL_ALLOWANCE + BASIC_RATE_THRESHOLD
                    if (isRetired1 && age1 >= 55 && remainingTarget > 0.0) {
                        val remainingBasicRateAllowance = max(0.0, basicRateLimit - taxableIncome1)
                        if (remainingBasicRateAllowance > 0.0) {
                            val maxBasicRateDraw = remainingBasicRateAllowance / taxableFraction
                            val person1Pensions = dcPensions.filter { it.personId != "person-2" }
                            for (pension in person1Pensions) {
                                if (remainingTarget <= 0.0) break
                                val withdrawal = min(pension.balance, min(maxBasicRateDraw, remainingTarget / netFractionInBasicRate))
                                if (withdrawal > 0.0) {
                                    val tfPart = withdrawal * (1.0 - taxableFraction)
                                    val taxablePart = withdrawal * taxableFraction
                                    
                                    val taxBefore = calculateIncomeTax(taxableIncome1)
                                    taxableIncome1 += taxablePart
                                    val taxAfter = calculateIncomeTax(taxableIncome1)
                                    val taxPaidForThisDraw = taxAfter - taxBefore

                                    taxFreeIncome += tfPart
                                    pension.balance -= withdrawal
                                    remainingTarget -= (tfPart + taxablePart * 0.80)
                                    yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", "Chris", withdrawal, taxPaidForThisDraw))
                                }
                            }
                        }
                    }

                    if (isRetired2 && age2 >= 55 && remainingTarget > 0.0) {
                        val remainingBasicRateAllowance = max(0.0, basicRateLimit - taxableIncome2)
                        if (remainingBasicRateAllowance > 0.0) {
                            val maxBasicRateDraw = remainingBasicRateAllowance / taxableFraction
                            val person2Pensions = dcPensions.filter { it.personId == "person-2" }
                            for (pension in person2Pensions) {
                                if (remainingTarget <= 0.0) break
                                val withdrawal = min(pension.balance, min(maxBasicRateDraw, remainingTarget / netFractionInBasicRate))
                                if (withdrawal > 0.0) {
                                    val tfPart = withdrawal * (1.0 - taxableFraction)
                                    val taxablePart = withdrawal * taxableFraction
                                    
                                    val taxBefore = calculateIncomeTax(taxableIncome2)
                                    taxableIncome2 += taxablePart
                                    val taxAfter = calculateIncomeTax(taxableIncome2)
                                    val taxPaidForThisDraw = taxAfter - taxBefore

                                    taxFreeIncome += tfPart
                                    pension.balance -= withdrawal
                                    remainingTarget -= (tfPart + taxablePart * 0.80)
                                    yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", "Lisa", withdrawal, taxPaidForThisDraw))
                                }
                            }
                        }
                    }

                    // Step 3: Draw from ISAs next (tax-free) to preserve higher rate bracket
                    for (saving in savings.filter { it.type == AccountType.ISA }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            taxFreeIncome += withdrawal
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            val owner = if (saving.personId == "person-2") "Lisa" else "Chris"
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", owner, withdrawal, 0.0))
                        }
                    }

                    // Step 4: Draw from GIAs next (taxable savings)
                    for (saving in savings.filter { it.type != AccountType.ISA }) {
                        if (remainingTarget <= 0.0) break
                        val withdrawal = min(saving.balance, remainingTarget)
                        if (withdrawal > 0.0) {
                            val isLisa = saving.personId == "person-2"
                            val taxBefore = if (isLisa) calculateIncomeTax(taxableIncome2) else calculateIncomeTax(taxableIncome1)
                            if (isLisa) {
                                taxableIncome2 += withdrawal
                            } else {
                                taxableIncome1 += withdrawal
                            }
                            val taxAfter = if (isLisa) calculateIncomeTax(taxableIncome2) else calculateIncomeTax(taxableIncome1)
                            val taxPaidForThisDraw = taxAfter - taxBefore
                            
                            saving.balance -= withdrawal
                            remainingTarget -= withdrawal
                            yearWithdrawals.add(WithdrawalDetail("${saving.institution.displayName} - ${saving.name}", if (isLisa) "Lisa" else "Chris", withdrawal, taxPaidForThisDraw))
                        }
                    }

                    // Step 5: Draw from pensions above Basic Rate threshold (Higher Rate / 40%)
                    val netFractionInHigherRate = (1.0 - taxableFraction) + taxableFraction * 0.60
                    for (pension in dcPensions) {
                        if (remainingTarget <= 0.0) break
                        if (pension.balance > 0.0) {
                            val withdrawal = min(pension.balance, remainingTarget / netFractionInHigherRate)
                            if (withdrawal > 0.0) {
                                val tfPart = withdrawal * (1.0 - taxableFraction)
                                val taxablePart = withdrawal * taxableFraction
                                val isLisa = pension.personId == "person-2"
                                
                                val taxBefore = if (isLisa) calculateIncomeTax(taxableIncome2) else calculateIncomeTax(taxableIncome1)
                                if (isLisa) {
                                    taxableIncome2 += taxablePart
                                } else {
                                    taxableIncome1 += taxablePart
                                }
                                val taxAfter = if (isLisa) calculateIncomeTax(taxableIncome2) else calculateIncomeTax(taxableIncome1)
                                val taxPaidForThisDraw = taxAfter - taxBefore

                                taxFreeIncome += tfPart
                                pension.balance -= withdrawal
                                remainingTarget -= (tfPart + taxablePart * 0.60)
                                yearWithdrawals.add(WithdrawalDetail("${pension.institution.displayName} - ${pension.name}", if (isLisa) "Lisa" else "Chris", withdrawal, taxPaidForThisDraw))
                            }
                        }
                    }
                }

                val tax1 = calculateIncomeTax(taxableIncome1)
                val tax2 = calculateIncomeTax(taxableIncome2)
                val netIncome = taxFreeIncome + (taxableIncome1 - tax1) + (taxableIncome2 - tax2)
                val totalPensionVal = dcPensions.sumOf { it.balance }
                val totalSavingsVal = savings.sumOf { it.balance }

                val metTarget = netIncome >= targetIncome || (totalPensionVal + totalSavingsVal > 0.0 && netIncome >= targetIncome * 0.85)
                if (!metTarget && ageActive < 90) {
                    retirementFeasible = false
                }

                val currentTax = tax1 + tax2
                totalTaxPaid += currentTax

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
                        tax = 0.0,
                        tax1 = 0.0,
                        tax2 = 0.0,
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
