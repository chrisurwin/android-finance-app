package com.chris.financeapp.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Institution(val displayName: String, val category: String) {
    HSBC("HSBC", "Bank"),
    FIRST_DIRECT("First Direct", "Bank"),
    CHASE("Chase", "Bank"),
    JP_ORGAN("JP Morgan Personal Investing", "Investment"),
    AVIVA("Aviva", "Pension"),
    TOWERS_WATSON("Towers Watson Lifesight", "Pension")
}

@Serializable
enum class AccountType(val displayName: String) {
    CURRENT("Current Account"),
    ISA("ISA"),
    GENERAL_INVESTMENT("General Investment"),
    PENSION("Pension")
}

@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val institution: Institution,
    var balance: Double,
    val monthlyContribution: Double = 0.0,
    val employerContribution: Double = 0.0, // DC pensions only
    val interestRate: Double = 0.0, // interest rate or growth rate (annual %)
    val annualManagementCharge: Double = 0.0, // AMC % (for investments/pensions)
    val isConnected: Boolean = false,
    val personId: String = "person-1" // matching pension logic (Chris / Lisa)
)
