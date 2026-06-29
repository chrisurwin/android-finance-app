package com.chris.financeapp.data.model

import kotlinx.serialization.Serializable

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
    var endAge: Int = 95
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
    val canRetire: Boolean
)

@Serializable
data class RetirementProjection(
    val results: List<ProjectionResult>,
    val retirementAge: Int,
    val feasible: Boolean
)
