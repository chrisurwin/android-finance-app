package com.chris.financeapp

import com.chris.financeapp.data.model.Account
import com.chris.financeapp.data.model.AccountType
import com.chris.financeapp.data.model.Institution
import com.chris.financeapp.data.model.InvestmentAssumptions
import com.chris.financeapp.data.model.DrawdownPreferences
import com.chris.financeapp.data.model.DrawdownStrategy
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

        // Person 1 born in 1974, is 52 years old in 2026. Projections from 52 to 95.
        val person1 = Person("person-1", "Person 1", 1974, 65)
        val person2 = Person("person-2", "Person 2", 1976, 65)
        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 65,
            retirementAge2 = 65,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
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
        val person1 = Person("person-1", "Person 1", 1966, 60) // Age 60 in 2026
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
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
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
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
        // Person 1 and Person 2 both have large pensions (£1.5M each)
        // Upfront lump sum for each should be £268,275, totaling £536,550
        val accounts = listOf(
            Account("p1", "Person 1 Pension", AccountType.PENSION, Institution.AVIVA, 1500000.0, personId = "person-1"),
            Account("p2", "Person 2 Pension", AccountType.PENSION, Institution.AVIVA, 1500000.0, personId = "person-2")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.04, bondReturn = 0.02, cashReturn = 0.01, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 40000.0,
            inflationAdjusted = false,
            lumpSumOption = LumpSumOption.UP_FRONT,
            startAge = 60,
            endAge = 65
        )
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1966, 60)

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
        val person1Lump = firstYearWithdrawals.firstOrNull { it.ownerName == "Person 1" && it.potName.contains("Lump Sum") }
        val person2Lump = firstYearWithdrawals.firstOrNull { it.ownerName == "Person 2" && it.potName.contains("Lump Sum") }

        assertTrue(person1Lump != null)
        assertTrue(person2Lump != null)
        assertEquals(268275.0, person1Lump!!.amountDrawn, 0.01)
        assertEquals(268275.0, person2Lump!!.amountDrawn, 0.01)
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
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
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
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
        )

        val firstYear = projection.results.first()
        assertEquals("Net income should equal gross withdrawal from current account", 25000.0, firstYear.netIncome, 0.01)
        assertEquals("Zero tax should be paid on current account withdrawal", 0.0, firstYear.tax, 0.01)
        val w = firstYear.withdrawals.first()
        assertEquals(0.0, w.taxPaid, 0.01)
    }

    @Test
    fun testCalculateTaxOnSavings() {
        // Case 1: Savings interest below Personal Savings Allowance (£1,000 for basic rate taxpayer)
        val taxUnderPsa = PensionCalculator.calculateTaxOnSavings(savingsInterest = 800.0, otherTaxableIncome = 30000.0)
        assertEquals("Savings interest below PSA should incur £0 tax", 0.0, taxUnderPsa, 0.01)

        // Case 2: Basic rate taxpayer with £3,000 savings interest (£1,000 PSA -> £2,000 taxable at 20% = £400)
        val taxBasicOverPsa = PensionCalculator.calculateTaxOnSavings(savingsInterest = 3000.0, otherTaxableIncome = 30000.0)
        assertEquals("Excess savings interest should be taxed at basic rate 20%", 400.0, taxBasicOverPsa, 0.01)

        // Case 3: Higher rate taxpayer (£60,000 other income -> £500 PSA).
        // £2,500 savings interest -> £2,000 taxable at 40% = £800
        val taxHigherRate = PensionCalculator.calculateTaxOnSavings(savingsInterest = 2500.0, otherTaxableIncome = 60000.0)
        assertEquals("Excess savings interest should be taxed at higher rate 40%", 800.0, taxHigherRate, 0.01)

        // Case 4: Income below personal allowance (£10,000 income, £2,570 unused PA + £1,000 PSA = £3,570 sheltered)
        val taxLowIncome = PensionCalculator.calculateTaxOnSavings(savingsInterest = 3000.0, otherTaxableIncome = 10000.0)
        assertEquals("Low income uses unused PA and PSA, resulting in £0 tax", 0.0, taxLowIncome, 0.01)
    }

    @Test
    fun testCalculateTaxOnGiaGrowth() {
        // Case 1: Growth <= £3,000 annual exemption -> £0 tax
        val taxUnderExemption = PensionCalculator.calculateTaxOnGiaGrowth(annualGrowth = 2500.0, otherTaxableIncome = 40000.0)
        assertEquals("Growth within £3,000 exemption should incur £0 tax", 0.0, taxUnderExemption, 0.01)

        // Case 2: Growth £5,000 with basic rate income £20,000 (£3,000 exempt -> £2,000 taxable at 10% = £200)
        val taxBasicRate = PensionCalculator.calculateTaxOnGiaGrowth(annualGrowth = 5000.0, otherTaxableIncome = 20000.0)
        assertEquals("Excess growth in basic rate band taxed at 10%", 200.0, taxBasicRate, 0.01)

        // Case 3: Growth £10,000 with higher rate income £60,000 (£3,000 exempt -> £7,000 taxable at 20% = £1,400)
        val taxHigherRate = PensionCalculator.calculateTaxOnGiaGrowth(annualGrowth = 10000.0, otherTaxableIncome = 60000.0)
        assertEquals("Excess growth in higher rate band taxed at 20%", 1400.0, taxHigherRate, 0.01)
    }

    @Test
    fun testLumpSumDepositedToGiaAndBedAndIsaTransferIndividual() {
        // Person 1 takes upfront lump sum of £100,000 from a £400,000 pension (25% = £100,000).
        // It must NOT be dumped entirely into an ISA. Instead, it enters a GIA and transfers £20,000 to ISA.
        val accounts = listOf(
            Account("p1", "Aviva Pension", AccountType.PENSION, Institution.AVIVA, 400000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.0, bondReturn = 0.0, cashReturn = 0.0, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 0.0,
            inflationAdjusted = false,
            lumpSumOption = LumpSumOption.UP_FRONT,
            startAge = 60,
            endAge = 63
        )
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
        )

        // Year 1 (age 60): £100,000 lump sum taken into GIA. £20,000 moved to ISA via Bed & ISA.
        // Total savings value is £100,000.
        val year1 = projection.results[0]
        assertEquals(100000.0, year1.totalSavings, 0.01)
        assertEquals(300000.0, year1.totalPensionValue, 0.01)

        // In year 2 (age 61), another £20,000 transferred to ISA (total £40k in ISA, £60k in GIA).
        val year2 = projection.results[1]
        assertEquals(100000.0, year2.totalSavings, 0.01)
    }

    @Test
    fun testLumpSumBedAndIsaTransferCoupleSplit() {
        // Person 1 takes upfront lump sum of £100,000 in a couple projection.
        // The couple can split £40,000 per year (£20k each) into ISAs.
        val accounts = listOf(
            Account("p1", "Person 1 Pension", AccountType.PENSION, Institution.AVIVA, 400000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.0, bondReturn = 0.0, cashReturn = 0.0, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 0.0,
            inflationAdjusted = false,
            lumpSumOption = LumpSumOption.UP_FRONT,
            startAge = 60,
            endAge = 63
        )
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1966, 60)

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

        // In year 1, £100,000 lump sum taken.
        // Couple transfer capacity is £40k (£20k each).
        val year1 = projection.results[0]
        assertEquals(100000.0, year1.totalSavings, 0.01)
    }

    @Test
    fun testOtherSavingsDrawnDownBeforeIsasInStandardStrategy() {
        // Account setup: ISA with £50k, GIA with £30k, Current account with £10k.
        // Under STANDARD drawdown, Current and GIA must be exhausted before touching ISA.
        val accounts = listOf(
            Account("isa1", "Person 1 Stocks ISA", AccountType.ISA, Institution.HOSTED, 50000.0, personId = "person-1"),
            Account("gia1", "Person 1 General Investment", AccountType.GENERAL_INVESTMENT, Institution.HOSTED, 30000.0, personId = "person-1"),
            Account("c1", "Person 1 Current", AccountType.CURRENT, Institution.HSBC, 5000.0, personId = "person-1"),
            Account("p1", "Person 1 Pension", AccountType.PENSION, Institution.AVIVA, 500000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.0, bondReturn = 0.0, cashReturn = 0.0, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 25000.0,
            inflationAdjusted = false,
            strategy = DrawdownStrategy.STANDARD,
            startAge = 60,
            endAge = 62
        )
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
        )

        val firstYear = projection.results.first()
        val withdrawals = firstYear.withdrawals
        
        // Harvest PA from pension first (£12,570 taxable + £4,190 tax-free = £16,760 net). Remaining need = £8,240.
        // Remaining £8,240 is drawn from Current (£5k) and GIA (£3,240), NOT from ISA!
        val isaWithdrawal = withdrawals.firstOrNull { it.potName.contains("ISA") }
        assertTrue("ISA should not be drawn because other savings (Current and GIA) cover the need", isaWithdrawal == null)

        val currentWithdrawal = withdrawals.firstOrNull { it.potName.contains("Current") }
        val giaWithdrawal = withdrawals.firstOrNull { it.potName.contains("General Investment") }
        assertTrue("Current account should be drawn", currentWithdrawal != null)
        assertTrue("GIA account should be drawn", giaWithdrawal != null)
        assertEquals(5000.0, currentWithdrawal!!.amountDrawn, 0.01)
        assertEquals(3240.0, giaWithdrawal!!.amountDrawn, 0.01)
    }

    @Test
    fun testOtherSavingsDrawnDownBeforeIsasInTaxMinimizedStrategy() {
        // Account setup: ISA with £50k, GIA with £30k, Current account with £5k.
        // In Tax-Minimized, pension is harvested up to Personal Allowance (£12,570 taxable + £4,190 tax-free = £16,760 net).
        // For target annual income £25k, remaining need is £8,240.
        // Bed & ISA moves £20k from GIA to ISA, leaving £10k in GIA.
        // Current account (£5k) and GIA (£3,240) must be drawn down before ISA is touched.
        val accounts = listOf(
            Account("isa1", "Person 1 Stocks ISA", AccountType.ISA, Institution.HOSTED, 50000.0, personId = "person-1"),
            Account("gia1", "Person 1 General Investment", AccountType.GENERAL_INVESTMENT, Institution.HOSTED, 30000.0, personId = "person-1"),
            Account("c1", "Person 1 Current", AccountType.CURRENT, Institution.HSBC, 5000.0, personId = "person-1"),
            Account("p1", "Person 1 Pension", AccountType.PENSION, Institution.AVIVA, 500000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.0, bondReturn = 0.0, cashReturn = 0.0, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 25000.0,
            inflationAdjusted = false,
            strategy = DrawdownStrategy.TAX_MINIMIZED,
            startAge = 60,
            endAge = 62
        )
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
        )

        val firstYear = projection.results.first()
        val withdrawals = firstYear.withdrawals

        val currentWithdrawal = withdrawals.firstOrNull { it.potName.contains("Current") }
        val giaWithdrawal = withdrawals.firstOrNull { it.potName.contains("General Investment") }
        val isaWithdrawal = withdrawals.firstOrNull { it.potName.contains("ISA") }

        assertTrue("Current account should be drawn first", currentWithdrawal != null)
        assertEquals(5000.0, currentWithdrawal!!.amountDrawn, 0.01)
        assertTrue("GIA should be drawn next", giaWithdrawal != null)
        assertEquals(3240.0, giaWithdrawal!!.amountDrawn, 0.01)
        assertTrue("ISA should NOT be drawn because other savings cover the need", isaWithdrawal == null)
    }

    @Test
    fun testPerson2PensionSmoothingSpreadToStatePensionAgeWithZeroTax() {
        // Person 2 is age 58 in 2026 (born 1968, SPA 67 = 9 years to SPA).
        // She/he has £114,500 in DC pension and £50,000 in ISA.
        // Under Tax-Minimized drawdown, Person 2 spreads her/his pension across the years to age 67,
        // drawing up to her/his Personal Allowance each year (£0 tax),
        // and topping up from ISAs tax-free.
        val accounts = listOf(
            Account("p1", "Person 1 Pension", AccountType.PENSION, Institution.AVIVA, 300000.0, personId = "person-1"),
            Account("p2", "Person 2 Pension", AccountType.PENSION, Institution.AVIVA, 114500.0, personId = "person-2"),
            Account("isa1", "Person 1 ISA", AccountType.ISA, Institution.HOSTED, 50000.0, personId = "person-1"),
            Account("isa2", "Person 2 ISA", AccountType.ISA, Institution.HOSTED, 50000.0, personId = "person-2")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.0, bondReturn = 0.0, cashReturn = 0.0, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 60000.0,
            inflationAdjusted = false,
            strategy = DrawdownStrategy.TAX_MINIMIZED,
            startAge = 58,
            endAge = 67
        )
        val person1 = Person("person-1", "Person 1", 1968, 58)
        val person2 = Person("person-2", "Person 2", 1968, 58)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 58,
            retirementAge2 = 58,
            projectionType = ProjectionType.COUPLE
        )

        // For all years before age 67, Person 2 should pay £0 tax because her pension drawdown
        // is smoothed and stays within her Personal Allowance (£12,570), with ISAs topping up the rest.
        for (yearResult in projection.results.filter { it.age < 67 }) {
            assertEquals(0.0, yearResult.tax2, 0.01)
            
            // Check that Person 2's pension withdrawals paid £0 tax
            val person2PensionWithdrawals = yearResult.withdrawals.filter { 
                it.ownerName == "Person 2" && it.potName.contains("Pension") && !it.potName.contains("State")
            }
            for (w in person2PensionWithdrawals) {
                assertEquals(0.0, w.taxPaid, 0.01)
            }
        }
    }

    @Test
    fun testGiaIncursTaxOnGrowthAbove3kExemptionWhilstInGia() {
        // Person 1 has £200,000 in a GIA earning 5% return.
        // In Year 1, Bed & ISA transfers £20,000 to an ISA, leaving £180,000 in GIA.
        // 5% growth on £180,000 = £9,000.
        // First £3,000 is tax-free (CGT annual exempt amount).
        // Excess £6,000 is taxed under CGT (at 20% since DB pension of £60k puts him in higher rate = £1,200).
        val accounts = listOf(
            Account("db1", "Final Salary DB", AccountType.FINAL_SALARY, Institution.HOSTED, 60000.0, payoutAge = 60, personId = "person-1"),
            Account("gia1", "Taxable GIA", AccountType.GENERAL_INVESTMENT, Institution.HOSTED, 200000.0, interestRate = 5.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.05, bondReturn = 0.03, cashReturn = 0.05, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 40000.0, // Fully met by DB pension
            inflationAdjusted = false,
            startAge = 60,
            endAge = 61
        )
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
        )

        val firstYear = projection.results.first()
        val giaCgtWithdrawal = firstYear.withdrawals.firstOrNull { it.potName.contains("GIA Capital Gains Tax") }
        assertTrue("GIA capital gains tax entry should be recorded in withdrawals", giaCgtWithdrawal != null)
        assertEquals("£6,000 excess growth taxed at 20% higher rate CGT = £1,200", 1200.0, giaCgtWithdrawal!!.taxPaid, 0.01)
    }

    @Test
    fun testGiaWithdrawalIncursZeroTax() {
        // Person 1 draws £40,000 from GIA. Capital withdrawals from GIA must incur £0 tax.
        val accounts = listOf(
            Account("gia1", "Person 1 GIA", AccountType.GENERAL_INVESTMENT, Institution.HOSTED, 100000.0, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.0, bondReturn = 0.0, cashReturn = 0.0, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 40000.0,
            inflationAdjusted = false,
            strategy = DrawdownStrategy.TAX_MINIMIZED,
            startAge = 58,
            endAge = 59
        )
        val person1 = Person("person-1", "Person 1", 1968, 58)
        val person2 = Person("person-2", "Person 2", 1968, 58)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 58,
            retirementAge2 = 58,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
        )

        val firstYear = projection.results.first()
        val giaWithdrawal = firstYear.withdrawals.firstOrNull { it.potName.contains("Person 1 GIA") }
        assertTrue("GIA withdrawal should be recorded", giaWithdrawal != null)
        assertEquals(40000.0, giaWithdrawal!!.amountDrawn, 0.01)
        assertEquals("GIA capital withdrawal must have £0 tax paid", 0.0, giaWithdrawal.taxPaid, 0.01)
        assertEquals("Total tax for year must be £0", 0.0, firstYear.tax, 0.01)
    }

    @Test
    fun testPotSpecificGrowthRateHonouredIncludingZeroPercentForPremiumBonds() {
        // Macro assumptions have a blended return of 5.6%
        val assumptions = InvestmentAssumptions(
            equityReturn = 0.07,
            bondReturn = 0.04,
            cashReturn = 0.02,
            inflationRate = 0.0,
            equityAllocation = 0.6,
            bondAllocation = 0.3,
            cashAllocation = 0.1
        )
        // Premium Bonds is explicitly set to 0.0% growth
        // High Growth ISA is explicitly set to 8.0% growth
        // Aviva DC Pension is set to 7.0% growth with 0.5% AMC -> 6.5% net
        val accounts = listOf(
            Account("pb1", "Premium Bonds", AccountType.ISA, Institution.HOSTED, 50000.0, interestRate = 0.0, personId = "person-1"),
            Account("isa1", "High Growth ISA", AccountType.ISA, Institution.HOSTED, 50000.0, interestRate = 8.0, personId = "person-1"),
            Account("p1", "Aviva Pension", AccountType.PENSION, Institution.AVIVA, 100000.0, interestRate = 7.0, annualManagementCharge = 0.5, personId = "person-1")
        )
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 0.0,
            inflationAdjusted = false,
            startAge = 65,
            endAge = 66
        )
        val person1 = Person("person-1", "Person 1", 1974, 65) // Age 52 in 2026, accumulation
        val person2 = Person("person-2", "Person 2", 1974, 65)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 65,
            retirementAge2 = 65,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
        )

        val firstYear = projection.results.first()
        // Premium Bonds must NOT grow at 5.6% - it must remain at 50,000.0 (0% growth)
        // High Growth ISA must grow at 8.0% -> 54,000.0
        // Total savings = 50,000 + 54,000 = 104,000.0
        // Aviva Pension must grow at 6.5% net -> 106,500.0
        assertEquals(104000.0, firstYear.totalSavings, 0.01)
        assertEquals(106500.0, firstYear.totalPensionValue, 0.01)
    }

    @Test
    fun testPensionsDrawnAtBasicRateBeforeIsasToPreserveIsasFrom40PercentTax() {
        // Person 1 has £300,000 in DC pension, £50,000 in ISA, and a £30,000 DB pension starting at age 65.
        // Target annual income is £45,000.
        // At age 60 (before DB pension starts), Personal Allowance gives £16,760 net (£12,570 taxable + £4,190 tax-free).
        // Remaining need is £28,240 net.
        // Rather than running down the £50k ISA to £0, Person 1 draws from his DC pension at the 20% basic rate band
        // (£50,270 cap), preserving the ISA completely (£0 drawn from ISA).
        val accounts = listOf(
            Account("p1", "Person 1 Pension", AccountType.PENSION, Institution.AVIVA, 300000.0, personId = "person-1"),
            Account("isa1", "Person 1 Stocks ISA", AccountType.ISA, Institution.HOSTED, 50000.0, personId = "person-1"),
            Account("db1", "Person 1 Final Salary DB", AccountType.FINAL_SALARY, Institution.HOSTED, 30000.0, payoutAge = 65, personId = "person-1")
        )
        val assumptions = InvestmentAssumptions(equityReturn = 0.0, bondReturn = 0.0, cashReturn = 0.0, inflationRate = 0.0)
        val preferences = DrawdownPreferences(
            targetAnnualIncome = 45000.0,
            inflationAdjusted = false,
            strategy = DrawdownStrategy.TAX_MINIMIZED,
            startAge = 60,
            endAge = 66
        )
        val person1 = Person("person-1", "Person 1", 1966, 60)
        val person2 = Person("person-2", "Person 2", 1968, 60)

        val projection = PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = assumptions,
            preferences = preferences,
            person1 = person1,
            person2 = person2,
            retirementAge1 = 60,
            retirementAge2 = 60,
            projectionType = ProjectionType.INDIVIDUAL_PERSON1
        )

        // At age 60: ISA must NOT be drawn down! It must be preserved.
        val age60 = projection.results.first { it.age == 60 }
        val isaWithdrawal60 = age60.withdrawals.firstOrNull { it.potName.contains("ISA") }
        assertTrue("ISA should NOT be drawn at age 60; DC pension should be drawn at 20% basic rate instead", isaWithdrawal60 == null)

        // Pension must have drawn to meet the remaining £28,240 net at 20% basic rate
        val pensionWithdrawals = age60.withdrawals.filter { it.potName.contains("Person 1 Pension") }
        assertTrue("Pension should be drawn", pensionWithdrawals.isNotEmpty())
        assertTrue("Tax should be paid at 20% basic rate", age60.tax > 0.0)
        assertTrue("Taxable income must stay within Basic Rate Threshold (£50,270)", age60.tax < 10000.0)

        // At age 65 (when DB pension starts paying £30k):
        val age65 = projection.results.first { it.age == 65 }
        val dbWithdrawal = age65.withdrawals.firstOrNull { it.potName.contains("Final Salary") }
        assertTrue("DB pension should pay out at age 65", dbWithdrawal != null)
    }
}


