package com.chris.financeapp.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProjectionType {
    INDIVIDUAL_CHRIS,
    INDIVIDUAL_LISA,
    COUPLE
}

@Serializable
enum class DrawdownStrategy {
    STANDARD,
    TAX_MINIMIZED
}

@Serializable
enum class LumpSumOption {
    UP_FRONT,
    AS_YOU_GO
}

@Serializable
data class Person(
    val id: String,
    val name: String,
    val birthYear: Int,
    var retirementAge: Int
)

@Serializable
data class InvestmentAssumptions(
    var equityReturn: Double = 0.07,     // 7% annual
    var bondReturn: Double = 0.04,       // 4% annual
    var cashReturn: Double = 0.02,       // 2% annual
    var inflationRate: Double = 0.025,   // 2.5% annual
    var equityAllocation: Double = 0.60, // 60%
    var bondAllocation: Double = 0.30,   // 30%
    var cashAllocation: Double = 0.10    // 10%
)

@Serializable
data class DrawdownPreferences(
    var targetAnnualIncome: Double = 25000.0,
    var inflationAdjusted: Boolean = true,
    var startAge: Int = 65,
    var endAge: Int = 95,
    var strategy: DrawdownStrategy = DrawdownStrategy.STANDARD,
    var lumpSumOption: LumpSumOption = LumpSumOption.AS_YOU_GO,
    var stepDownAge: Int = 80,
    var stepDownPercentage: Int = 25
)

@Serializable
data class WithdrawalDetail(
    val potName: String,
    val ownerName: String,
    val amountDrawn: Double,
    val taxPaid: Double
)

@Serializable
data class ProjectionResult(
    val age: Int,
    val year: Int,
    val totalPensionValue: Double,
    val totalSavings: Double,
    val annualIncome: Double,
    val netIncome: Double,
    val tax: Double,
    val tax1: Double = 0.0, // Chris's tax component
    val tax2: Double = 0.0, // Lisa's tax component
    val canRetire: Boolean,
    val withdrawals: List<WithdrawalDetail> = emptyList()
)

@Serializable
data class RetirementProjection(
    val results: List<ProjectionResult>,
    val retirementAge: Int,
    val feasible: Boolean,
    val totalTaxPaid: Double = 0.0
)

