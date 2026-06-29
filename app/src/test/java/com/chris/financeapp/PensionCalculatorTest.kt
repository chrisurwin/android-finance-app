package com.chris.financeapp

import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.Institution
import com.chris.financeapp.data.model.InvestmentAssumptions
import com.chris.financeapp.data.model.DrawdownPreferences
import com.chris.financeapp.utils.PensionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PensionCalculatorTest {

    @Test
    fun testStatePension() {
        val fullPension = PensionCalculator.calculateStatePension(35)
        assertEquals(230.25 * 52.0, fullPension, 0.01)

        val halfPension = PensionCalculator.calculateStatePension(17.5.toInt())
        assertEquals((230.25 * 52.0) * (17.0 / 35.0), halfPension, 0.01)
    }

    @Test
    fun testIncomeTax() {
        // Under personal allowance
        assertEquals(0.0, PensionCalculator.calculateIncomeTax(10000.0), 0.01)

        // Basic rate band: personal allowance (£12,570) + (£20,000 - £12,570) * 20%
        val expectedBasicTax = (20000.0 - 12570.0) * 0.20
        assertEquals(expectedBasicTax, PensionCalculator.calculateIncomeTax(20000.0), 0.01)
    }

    @Test
    fun testProjections() {
        val accounts = listOf(
            Account("1", "HSBC Current", AccountType.CURRENT, Institution.HSBC, 10000.0),
            Account("2", "Aviva Pension", AccountType.PENSION, Institution.AVIVA, 50000.0, monthlyContribution = 200.0, employerContribution = 200.0, annualManagementCharge = 0.5)
        )
        val assumptions = InvestmentAssumptions(
            equityReturn = 0.07,
            bondReturn = 0.04,
            cashReturn = 0.02,
            inflationRate = 0.025,
            equityAllocation = 0.6,
            bondAllocation = 0.3,
            cashAllocation = 0.1
        )
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 20000.0,
            inflationAdjusted = true,
            startAge = 65,
            endAge = 95
        )

        // Chris born in 1974, is 52 years old in 2026. Projections from 52 to 95.
        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            birthYear = 1974,
            retirementAgeInput = 65
        )

        assertEquals(44, projection.results.size) // 95 - 52 + 1 = 44 years
        
        // Assert that the initial year pension grew
        val firstYear = projection.results.first()
        assertTrue(firstYear.totalPensionValue > 50000.0)
    }
}
