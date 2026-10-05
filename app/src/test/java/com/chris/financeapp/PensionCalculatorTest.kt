package com.chris.financeapp

import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.Institution
import com.chris.financeapp.data.model.InvestmentAssumptions
import com.chris.financeapp.data.model.DrawdownPreferences
import com.chris.financeapp.data.model.LumpSumOption
import com.chris.financeapp.data.model.Person
import com.chris.financeapp.data.model.ProjectionType
import com.chris.financeapp.utils.PensionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun testStatePensionAgeGraduation() {
        assertEquals(66, PensionCalculator.getStatePensionAge(1955))
        assertEquals(67, PensionCalculator.getStatePensionAge(1974))
        assertEquals(68, PensionCalculator.getStatePensionAge(1980))
    }

    @Test
    fun testIncomeTax() {
        // Under personal allowance
        assertEquals(0.0, PensionCalculator.calculateIncomeTax(10000.0), 0.01)

        // Basic rate band: personal allowance (£12,570) + (£20,000 - £12,570) * 20%
        val expectedBasicTax = (20000.0 - 12570.0) * 0.20
        assertEquals(expectedBasicTax, PensionCalculator.calculateIncomeTax(20000.0), 0.01)

        // £100,000 (standard basic + higher rate without taper)
        // Basic: 37,700 * 0.20 = 7,540. Higher: (100,000 - 50,270) * 0.40 = 19,892. Total = 27,432
        assertEquals(27432.0, PensionCalculator.calculateIncomeTax(100000.0), 0.01)

        // £110,000: Personal allowance tapered by £5,000 to £7,570.
        // Basic: 37,700 * 0.20 = 7,540. Higher: (102,430 - 37,700) * 0.40 = 25,892. Total = 33,432
        assertEquals(33432.0, PensionCalculator.calculateIncomeTax(110000.0), 0.01)

        // £125,140: Personal allowance fully tapered to £0.
        // Basic: 37,700 * 0.20 = 7,540. Higher: (125,140 - 37,700) * 0.40 = 34,976. Total = 42,516
        assertEquals(42516.0, PensionCalculator.calculateIncomeTax(125140.0), 0.01)

        // £150,000: Additional rate (45%) on income above £125,140
        // 42,516 + (150,000 - 125,140) * 0.45 = 42,516 + 11,187 = 53,703
        assertEquals(53703.0, PensionCalculator.calculateIncomeTax(150000.0), 0.01)
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
        val person1 = Person("person-1", "Chris", 1974, 65)
        val person2 = Person("person-2", "Lisa", 1976, 65)
        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 65,
            retirementAge2 = 65,
            projectionType = ProjectionType.INDIVIDUAL_CHRIS
        )

        assertEquals(44, projection.results.size) // 95 - 52 + 1 = 44 years
        
        // Assert that the initial year pension grew
        val firstYear = projection.results.first()
        assertTrue(firstYear.totalPensionValue > 50000.0)
    }

    @Test
    fun testMaxTaxFreeLumpSumCapUpFront() {
        // Person with £2,000,000 pension retiring immediately
        // 25% of 2M would be £500,000, but UK Lump Sum Allowance MUST cap at £268,275
        val accounts = listOf(
            Account("p1", "Large DC Pension", AccountType.PENSION, Institution.AVIVA, 2000000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.05, bondReturn = 0.03, cashReturn = 0.01, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 30000.0,
            inflationAdjusted = false,
            lumpSumOption = LumpSumOption.UP_FRONT,
            startAge = 60,
            endAge = 65
        )
        val person1 = Person("person-1", "Chris", 1966, 60) // Age 60 in 2026
        val person2 = Person("person-2", "Lisa", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_CHRIS
        )

        // Find lump sum withdrawal in first year
        val firstYearWithdrawals = projection.results.first().withdrawals
        val lumpSumWithdrawal = firstYearWithdrawals.firstOrNull { it.potName.contains("Lump Sum") }
        
        assertTrue("Lump sum withdrawal should exist", lumpSumWithdrawal != null)
        assertEquals("Lump sum must be capped at exactly £268,275", 268275.0, lumpSumWithdrawal!!.amountDrawn, 0.01)
        assertEquals("Lump sum must be tax-free", 0.0, lumpSumWithdrawal.taxPaid, 0.01)
    }

    @Test
    fun testMaxTaxFreeLumpSumCapAsYouGo() {
        // High drawdown from a large pension pot under AS_YOU_GO (UFPLS).
        // Total tax-free cash drawn across all years must never exceed £268,275.
        val accounts = listOf(
            Account("p1", "Large DC Pension", AccountType.PENSION, Institution.AVIVA, 3000000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.04, bondReturn = 0.02, cashReturn = 0.01, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 100000.0,
            inflationAdjusted = false,
            lumpSumOption = LumpSumOption.AS_YOU_GO,
            startAge = 60,
            endAge = 80
        )
        val person1 = Person("person-1", "Chris", 1966, 60)
        val person2 = Person("person-2", "Lisa", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_CHRIS
        )

        // Calculate total gross drawn, total tax paid, and total net income across the entire simulation
        var totalPensionDrawn = 0.0
        var totalPensionTax = 0.0
        projection.results.forEach { res ->
            res.withdrawals.forEach { w ->
                if (w.potName.contains("Pension")) {
                    totalPensionDrawn += w.amountDrawn
                    totalPensionTax += w.taxPaid
                }
            }
        }

        // Over 21 years of drawing ~£100k net, total gross drawn is > £2.5M.
        assertTrue("Total drawn should exceed 2 million", totalPensionDrawn > 2000000.0)
        
        // If 25% uncapped was taken, tax-free cash would be > £500,000.
        // But with LSA cap, at most £268,275 could be tax-free.
        val effectiveTaxFreeCash = totalPensionDrawn - (projection.results.sumOf { it.annualIncome } - projection.results.sumOf { it.netIncome } + totalPensionTax)
        // Every withdrawal from DC pension in later years must pay full income tax because LSA is exhausted.
        val laterYear = projection.results.last()
        val dcDraws = laterYear.withdrawals.filter { it.potName.contains("Large DC Pension") }
        assertTrue("Later withdrawals must exist", dcDraws.isNotEmpty())
        val totalTaxPaidInLaterYear = dcDraws.sumOf { it.taxPaid }
        assertTrue("Later withdrawals must pay significant income tax once LSA is exhausted, was $totalTaxPaidInLaterYear", totalTaxPaidInLaterYear > 0.0)
    }

    @Test
    fun testCoupleSeparateTaxFreeAllowance() {
        // Chris and Lisa both have large pensions (£1.5M each)
        // Upfront lump sum for each should be £268,275, totaling £536,550
        val accounts = listOf(
            Account("p1", "Chris Pension", AccountType.PENSION, Institution.AVIVA, 1500000.0, personId = "person-1"),
            Account("p2", "Lisa Pension", AccountType.PENSION, Institution.AVIVA, 1500000.0, personId = "person-2")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.04, bondReturn = 0.02, cashReturn = 0.01, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 40000.0,
            inflationAdjusted = false,
            lumpSumOption = LumpSumOption.UP_FRONT,
            startAge = 60,
            endAge = 65
        )
        val person1 = Person("person-1", "Chris", 1966, 60)
        val person2 = Person("person-2", "Lisa", 1966, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.COUPLE
        )

        val firstYearWithdrawals = projection.results.first().withdrawals
        val chrisLump = firstYearWithdrawals.firstOrNull { it.ownerName == "Chris" && it.potName.contains("Lump Sum") }
        val lisaLump = firstYearWithdrawals.firstOrNull { it.ownerName == "Lisa" && it.potName.contains("Lump Sum") }

        assertTrue(chrisLump != null)
        assertTrue(lisaLump != null)
        assertEquals(268275.0, chrisLump!!.amountDrawn, 0.01)
        assertEquals(268275.0, lisaLump!!.amountDrawn, 0.01)
    }

    @Test
    fun testRetirementFeasibilityFailsOnShortfall() {
        // Person with only £50,000 trying to draw £30,000/year for 20 years.
        // Should deplete quickly and plan must be marked NOT feasible.
        val accounts = listOf(
            Account("p1", "Modest Pension", AccountType.PENSION, Institution.AVIVA, 50000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.03, bondReturn = 0.02, cashReturn = 0.01, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 30000.0,
            inflationAdjusted = false,
            startAge = 60,
            endAge = 75
        )
        val person1 = Person("person-1", "Chris", 1966, 60)
        val person2 = Person("person-2", "Lisa", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_CHRIS
        )

        assertFalse("Plan must be reported as NOT feasible when funds deplete", projection.feasible)
        val lateYear = projection.results.last()
        assertFalse("Later year cannot retire when target income cannot be met", lateYear.canRetire)
    }

    @Test
    fun testCurrentAccountWithdrawalTaxFree() {
        // Current account funds are already taxed cash and must be completely tax-free on withdrawal
        val accounts = listOf(
            Account("c1", "HSBC Current Account", AccountType.CURRENT, Institution.HSBC, 50000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.0, bondReturn = 0.0, cashReturn = 0.0, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 25000.0,
            inflationAdjusted = false,
            startAge = 60,
            endAge = 62
        )
        val person1 = Person("person-1", "Chris", 1966, 60)
        val person2 = Person("person-2", "Lisa", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_CHRIS
        )

        val firstYear = projection.results.first()
        assertEquals("Net income should equal gross withdrawal from current account", 25000.0, firstYear.netIncome, 0.01)
        assertEquals("Zero tax should be paid on current account withdrawal", 0.0, firstYear.tax, 0.01)
        val w = firstYear.withdrawals.first()
        assertEquals(0.0, w.taxPaid, 0.01)
    }
}


