package com.chris.financeapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.core.content.FileProvider
import android.content.Intent
import android.content.Context
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import com.chris.financeapp.data.model.Person
import com.chris.financeapp.data.model.ProjectionResult
import com.chris.financeapp.utils.XlsxWriter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.chris.financeapp.data.model.ProjectionType
import com.chris.financeapp.data.model.DrawdownStrategy
import com.chris.financeapp.data.model.LumpSumOption
import com.chris.financeapp.data.repository.FinanceRepository
import com.chris.financeapp.ui.components.LineChart
import com.chris.financeapp.ui.theme.*
import com.chris.financeapp.utils.PensionCalculator
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProjectionScreen(repository: FinanceRepository, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val accounts = remember { repository.getAccounts().filter { it.isIncluded } }
    val person1 = remember { repository.getPerson("person-1") }
    val person2 = remember { repository.getPerson("person-2") }
    val assumptions = remember { repository.getInvestmentAssumptions() }
    val drawdown = remember { repository.getDrawdownPreferences() }

    var retirementAge1 by remember { mutableStateOf(person1.retirementAge) }
    var retirementAge2 by remember { mutableStateOf(person2.retirementAge) }
    var equityReturn by remember { mutableStateOf((assumptions.equityReturn * 100).toFloat()) }
    var inflationRate by remember { mutableStateOf((assumptions.inflationRate * 100).toFloat()) }
    var targetAnnualIncome by remember { mutableStateOf(drawdown.targetAnnualIncome) }

    var projectionType by remember { mutableStateOf(if (drawdown.isCouple) ProjectionType.COUPLE else ProjectionType.INDIVIDUAL_CHRIS) }
    var drawdownStrategy by remember { mutableStateOf(drawdown.strategy) }
    var lumpSumOption by remember { mutableStateOf(drawdown.lumpSumOption) }
    var showYearlyBreakdown by remember { mutableStateOf(false) }
    var expandedYear by remember { mutableStateOf(-1) }
    var stepDownAge by remember { mutableStateOf(drawdown.stepDownAge) }
    var stepDownPercentage by remember { mutableStateOf(drawdown.stepDownPercentage) }

    val formatter = NumberFormat.getCurrencyInstance(Locale.UK)

    // Helper for base assumptions
    val baseAssumptions = remember(equityReturn, inflationRate) {
        assumptions.copy(
            equityReturn = (equityReturn / 100.0),
            inflationRate = (inflationRate / 100.0)
        )
    }

    // Dynamic calculations running on parameter updates
    val projection = remember(projectionType, retirementAge1, retirementAge2, baseAssumptions, targetAnnualIncome, drawdownStrategy, lumpSumOption, stepDownAge, stepDownPercentage) {
        val updatedDrawdown = drawdown.copy(
            targetAnnualIncome = targetAnnualIncome,
            strategy = drawdownStrategy,
            lumpSumOption = lumpSumOption,
            stepDownAge = stepDownAge,
            stepDownPercentage = stepDownPercentage
        )
        PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = baseAssumptions,
            preferences = updatedDrawdown,
            person1 = person1,
            person2 = person2,
            retirementAge1 = retirementAge1,
            retirementAge2 = retirementAge2,
            projectionType = projectionType
        )
    }

    // Comparative projections for the summary card
    val comparison = remember(projectionType, retirementAge1, retirementAge2, baseAssumptions, targetAnnualIncome, drawdownStrategy, lumpSumOption, stepDownAge, stepDownPercentage) {
        val standardPrefs = drawdown.copy(
            targetAnnualIncome = targetAnnualIncome,
            strategy = DrawdownStrategy.STANDARD,
            lumpSumOption = lumpSumOption,
            stepDownAge = stepDownAge,
            stepDownPercentage = stepDownPercentage
        )
        val optimalPrefs = drawdown.copy(
            targetAnnualIncome = targetAnnualIncome,
            strategy = DrawdownStrategy.TAX_MINIMIZED,
            lumpSumOption = lumpSumOption,
            stepDownAge = stepDownAge,
            stepDownPercentage = stepDownPercentage
        )
        
        val upFrontPrefs = drawdown.copy(
            targetAnnualIncome = targetAnnualIncome,
            strategy = drawdownStrategy,
            lumpSumOption = LumpSumOption.UP_FRONT,
            stepDownAge = stepDownAge,
            stepDownPercentage = stepDownPercentage
        )
        val asYouGoPrefs = drawdown.copy(
            targetAnnualIncome = targetAnnualIncome,
            strategy = drawdownStrategy,
            lumpSumOption = LumpSumOption.AS_YOU_GO,
            stepDownAge = stepDownAge,
            stepDownPercentage = stepDownPercentage
        )

        val standardProj = PensionCalculator.calculateProjections(
            accounts = accounts, assumptions = baseAssumptions, preferences = standardPrefs,
            person1 = person1, person2 = person2, retirementAge1 = retirementAge1, retirementAge2 = retirementAge2, projectionType = projectionType
        )
        val optimalProj = PensionCalculator.calculateProjections(
            accounts = accounts, assumptions = baseAssumptions, preferences = optimalPrefs,
            person1 = person1, person2 = person2, retirementAge1 = retirementAge1, retirementAge2 = retirementAge2, projectionType = projectionType
        )
        val upFrontProj = PensionCalculator.calculateProjections(
            accounts = accounts, assumptions = baseAssumptions, preferences = upFrontPrefs,
            person1 = person1, person2 = person2, retirementAge1 = retirementAge1, retirementAge2 = retirementAge2, projectionType = projectionType
        )
        val asYouGoProj = PensionCalculator.calculateProjections(
            accounts = accounts, assumptions = baseAssumptions, preferences = asYouGoPrefs,
            person1 = person1, person2 = person2, retirementAge1 = retirementAge1, retirementAge2 = retirementAge2, projectionType = projectionType
        )
        
        object {
            val standardTax = standardProj.totalTaxPaid
            val optimalTax = optimalProj.totalTaxPaid
            val upFrontTax = upFrontProj.totalTaxPaid
            val asYouGoTax = asYouGoProj.totalTaxPaid
            
            val standardFinalWealth = (standardProj.results.lastOrNull()?.let { it.totalPensionValue + it.totalSavings } ?: 0.0)
            val optimalFinalWealth = (optimalProj.results.lastOrNull()?.let { it.totalPensionValue + it.totalSavings } ?: 0.0)
            val upFrontFinalWealth = (upFrontProj.results.lastOrNull()?.let { it.totalPensionValue + it.totalSavings } ?: 0.0)
            val asYouGoFinalWealth = (asYouGoProj.results.lastOrNull()?.let { it.totalPensionValue + it.totalSavings } ?: 0.0)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val finalDrawdown = drawdown.copy(
                targetAnnualIncome = targetAnnualIncome,
                strategy = drawdownStrategy,
                lumpSumOption = lumpSumOption,
                stepDownAge = stepDownAge,
                stepDownPercentage = stepDownPercentage
            )
            repository.saveDrawdownPreferences(finalDrawdown)

            val finalAssumptions = assumptions.copy(
                equityReturn = (equityReturn / 100.0),
                inflationRate = (inflationRate / 100.0)
            )
            repository.saveInvestmentAssumptions(finalAssumptions)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    text = "Retirement Projections",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            val context = LocalContext.current
            IconButton(
                onClick = {
                    exportProjectionsToXlsx(context, person1, person2, drawdown.isCouple, projection.results)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export Excel",
                    tint = PrimaryIndigo
                )
            }
        }

        // 1. Selector controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Projection Controls",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )

                // Mode Selector
                if (drawdown.isCouple) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Projection Mode", fontSize = 11.sp, color = TextSecondary)
                        SegmentedControl(
                            items = listOf(ProjectionType.INDIVIDUAL_CHRIS, ProjectionType.INDIVIDUAL_LISA, ProjectionType.COUPLE),
                            selectedItem = projectionType,
                            onItemSelected = { projectionType = it },
                            labelProvider = {
                                when (it) {
                                    ProjectionType.INDIVIDUAL_CHRIS -> person1.name
                                    ProjectionType.INDIVIDUAL_LISA -> person2.name
                                    ProjectionType.COUPLE -> "Couple (Joint)"
                                }
                            }
                        )
                    }
                }

                // Strategy Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Drawdown Strategy", fontSize = 11.sp, color = TextSecondary)
                    SegmentedControl(
                        items = listOf(DrawdownStrategy.STANDARD, DrawdownStrategy.TAX_MINIMIZED),
                        selectedItem = drawdownStrategy,
                        onItemSelected = { 
                            drawdownStrategy = it 
                            drawdown.strategy = it
                            repository.saveDrawdownPreferences(drawdown)
                        },
                        labelProvider = {
                            when (it) {
                                DrawdownStrategy.STANDARD -> "Standard (ISA First)"
                                DrawdownStrategy.TAX_MINIMIZED -> "Tax-Minimized"
                            }
                        }
                    )
                }

                // Lump Sum Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pension Tax-Free Cash Option", fontSize = 11.sp, color = TextSecondary)
                    SegmentedControl(
                        items = listOf(LumpSumOption.UP_FRONT, LumpSumOption.AS_YOU_GO),
                        selectedItem = lumpSumOption,
                        onItemSelected = { 
                            lumpSumOption = it 
                            drawdown.lumpSumOption = it
                            repository.saveDrawdownPreferences(drawdown)
                        },
                        labelProvider = {
                            when (it) {
                                LumpSumOption.UP_FRONT -> "25% Up Front"
                                LumpSumOption.AS_YOU_GO -> "Withdraw As You Go"
                            }
                        }
                    )
                }
            }
        }

        // 2. Line Chart Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Wealth Growth & Drawdown Strategy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Simulates wealth up to age 95",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val ages = projection.results.map { it.age }
                val pensionVals = projection.results.map { it.totalPensionValue }
                val savingsVals = projection.results.map { it.totalSavings }

                LineChart(
                    pensionValues = pensionVals,
                    savingsValues = savingsVals,
                    ages = ages,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )

                // Chart Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(ColorPension, RoundedCornerShape(5.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pensions", fontSize = 11.sp, color = TextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(ColorISA, RoundedCornerShape(5.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Savings / ISAs", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        }

        // 3. Feasibility Indicator Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (projection.feasible) Color(0xFF132D2F) else Color(0xFF2E191E)
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RETIREMENT FEASIBILITY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (projection.feasible) ColorCurrent else Color(0xFFEF4444),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (projection.feasible) "Feasible Plan" else "Depletion Risk Alert",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                
                // Determine depletion age
                val retirementAgeActive = when (projectionType) {
                    ProjectionType.INDIVIDUAL_CHRIS -> retirementAge1
                    ProjectionType.INDIVIDUAL_LISA -> retirementAge2
                    ProjectionType.COUPLE -> retirementAge1
                }
                val depletionResult = projection.results.firstOrNull { it.age >= retirementAgeActive && (it.totalPensionValue + it.totalSavings) <= 0.0 }
                val summaryText = if (depletionResult != null) {
                    "Depleted at age ${depletionResult.age}"
                } else {
                    "Funds last past age 95"
                }

                Text(
                    text = summaryText,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            }
        }

        // 4. Comparative Analysis Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tax & Drawdown Optimization Analysis",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                
                // Drawdown strategy comparison
                val strategyTaxDiff = comparison.standardTax - comparison.optimalTax
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Drawdown Strategy Comparison", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Standard (ISA First) Lifetime Tax:", fontSize = 12.sp, color = TextSecondary)
                        Text(formatter.format(comparison.standardTax), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tax-Minimized Lifetime Tax:", fontSize = 12.sp, color = TextSecondary)
                        Text(formatter.format(comparison.optimalTax), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    
                    val strategyAdvanText = if (strategyTaxDiff > 0.0) {
                        "Tax-Minimized strategy saves you ${formatter.format(strategyTaxDiff)} in lifetime taxes!"
                    } else if (strategyTaxDiff < 0.0) {
                        "Standard strategy saves you ${formatter.format(-strategyTaxDiff)} in lifetime taxes!"
                    } else {
                        "Both strategies yield the same lifetime tax."
                    }
                    Text(
                        text = strategyAdvanText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (strategyTaxDiff >= 0.0) ColorCurrent else ColorPension
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SlateBg))

                // Lump Sum Option comparison
                val lumpSumTaxDiff = comparison.upFrontTax - comparison.asYouGoTax
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Tax-Free Lump Sum Comparison", fontSize = 12.sp, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("25% Up Front Lifetime Tax:", fontSize = 12.sp, color = TextSecondary)
                        Text(formatter.format(comparison.upFrontTax), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Withdraw As You Go Lifetime Tax:", fontSize = 12.sp, color = TextSecondary)
                        Text(formatter.format(comparison.asYouGoTax), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    
                    val lumpSumAdvanText = if (lumpSumTaxDiff > 0.0) {
                        "Withdrawing 'As You Go' saves ${formatter.format(lumpSumTaxDiff)} in lifetime taxes!"
                    } else if (lumpSumTaxDiff < 0.0) {
                        "Taking lump sum 'Up Front' saves ${formatter.format(-lumpSumTaxDiff)} in lifetime taxes!"
                    } else {
                        "Both options yield the same lifetime tax."
                    }
                    Text(
                        text = lumpSumAdvanText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lumpSumTaxDiff >= 0.0) ColorCurrent else ColorPension
                    )
                }
            }
        }

        // 5. Sliders Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Person 1's Retirement Age slider
                if (projectionType == ProjectionType.COUPLE || projectionType == ProjectionType.INDIVIDUAL_CHRIS) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${person1.name}'s Retirement Age", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("$retirementAge1 Years Old", fontWeight = FontWeight.Bold, color = ColorPension)
                        }
                        Slider(
                            value = retirementAge1.toFloat(),
                            onValueChange = { 
                                retirementAge1 = it.toInt()
                                person1.retirementAge = it.toInt()
                                repository.savePerson(person1)
                            },
                            valueRange = 50f..75f,
                            steps = 25
                        )
                    }
                }

                // Person 2's Retirement Age slider
                if (drawdown.isCouple && (projectionType == ProjectionType.COUPLE || projectionType == ProjectionType.INDIVIDUAL_LISA)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${person2.name}'s Retirement Age", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("$retirementAge2 Years Old", fontWeight = FontWeight.Bold, color = ColorPension)
                        }
                        Slider(
                            value = retirementAge2.toFloat(),
                            onValueChange = { 
                                retirementAge2 = it.toInt()
                                person2.retirementAge = it.toInt()
                                repository.savePerson(person2)
                            },
                            valueRange = 50f..75f,
                            steps = 25
                        )
                    }
                }

                // Expected Investment Return
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Average Market Return", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(String.format(Locale.UK, "%.1f%%", equityReturn), fontWeight = FontWeight.Bold, color = ColorISA)
                    }
                    Slider(
                        value = equityReturn,
                        onValueChange = { equityReturn = it },
                        valueRange = 0f..15f
                    )
                }

                // Average Inflation Rate
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Average Inflation Rate", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(String.format(Locale.UK, "%.1f%%", inflationRate), fontWeight = FontWeight.Bold, color = ColorCurrent)
                    }
                    Slider(
                        value = inflationRate,
                        onValueChange = { inflationRate = it },
                        valueRange = 0f..10f
                    )
                }

                // Target annual income
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Target Annual Net Income", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(formatter.format(targetAnnualIncome), fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Slider(
                        value = targetAnnualIncome.toFloat(),
                        onValueChange = { targetAnnualIncome = it.toDouble() },
                        valueRange = 10000f..100000f,
                        steps = 90
                    )
                }

                // Step Down Age slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Income Step Down Age", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("$stepDownAge Years Old", fontWeight = FontWeight.Bold, color = ColorISA)
                    }
                    Slider(
                        value = stepDownAge.toFloat(),
                        onValueChange = { stepDownAge = it.toInt() },
                        valueRange = 70f..90f,
                        steps = 20
                    )
                }

                // Step Down Percentage slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Step Down Reduction Amount", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("$stepDownPercentage%", fontWeight = FontWeight.Bold, color = ColorFinalSalary)
                    }
                    Slider(
                        value = stepDownPercentage.toFloat(),
                        onValueChange = { stepDownPercentage = it.toInt() },
                        valueRange = 0f..50f,
                        steps = 50
                    )
                }
            }
        }

        // 6. Year-by-Year breakdown scrollable table
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showYearlyBreakdown = !showYearlyBreakdown },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Annual Year-by-Year Breakdown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (showYearlyBreakdown) "Tap to hide details" else "Tap to view annual details",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Text(
                        text = if (showYearlyBreakdown) "▲" else "▼",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }

                if (showYearlyBreakdown) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateBg, RoundedCornerShape(4.dp))
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Year (Age)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(1.2f))
                        Text("Pensions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(1.5f))
                        Text("Savings/ISA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(1.5f))
                        Text("Tax Paid", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(1.2f))
                        Text("Net Inc.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(1.2f))
                    }

                    projection.results.forEach { result ->
                        val isExpanded = expandedYear == result.year
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedYear = if (isExpanded) -1 else result.year }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${result.year} (${result.age})", fontSize = 11.sp, color = TextPrimary, modifier = Modifier.weight(1.2f))
                                Text(formatter.format(result.totalPensionValue), fontSize = 11.sp, color = TextPrimary, modifier = Modifier.weight(1.5f))
                                Text(formatter.format(result.totalSavings), fontSize = 11.sp, color = TextPrimary, modifier = Modifier.weight(1.5f))
                                Text(
                                    text = formatter.format(result.tax), 
                                    fontSize = 11.sp, 
                                    color = if (result.tax > 0.0) ColorFinalSalary else TextSecondary, 
                                    fontWeight = if (result.tax > 0.0) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Row(
                                    modifier = Modifier.weight(1.2f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(formatter.format(result.netIncome), fontSize = 11.sp, color = TextPrimary)
                                    Text(
                                        text = if (isExpanded) "▲" else "▼",
                                        fontSize = 8.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }

                            if (isExpanded && result.withdrawals.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SlateBg, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Annual Drawdown Detail:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                    result.withdrawals.forEach { withdrawal ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1.2f)) {
                                                Text(withdrawal.potName, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                Text("Owner: ${withdrawal.ownerName}", fontSize = 9.sp, color = TextSecondary)
                                            }
                                            Text(
                                                text = "Drawn: ${formatter.format(withdrawal.amountDrawn)}", 
                                                fontSize = 11.sp, 
                                                color = TextPrimary, 
                                                modifier = Modifier.weight(1.5f)
                                            )
                                            Text(
                                                text = "Tax: ${formatter.format(withdrawal.taxPaid)}", 
                                                fontSize = 11.sp, 
                                                color = if (withdrawal.taxPaid > 0.0) ColorFinalSalary else TextSecondary,
                                                fontWeight = if (withdrawal.taxPaid > 0.0) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.weight(1.2f)
                                            )
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CardSurface))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SlateBg))
                    }
                }
            }
        }
    }
}

@Composable
fun <T> SegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SlateBg, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) PrimaryIndigo else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onItemSelected(item) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private fun exportProjectionsToXlsx(
    context: Context,
    person1: Person,
    person2: Person,
    isCouple: Boolean,
    results: List<ProjectionResult>
) {
    try {
        val fileName = "Retirement_Projections.xlsx"
        val cacheFile = File(context.externalCacheDir, fileName)
        val outputStream = FileOutputStream(cacheFile)

        val headers = mutableListOf(
            "Year",
            "Age (${person1.name})",
            "Age (${person2.name})",
            "Total Pension Value (£)",
            "Total Savings (£)",
            "Gross Annual Income (£)",
            "Net Annual Income (£)",
            "Total Tax (£)",
            "Tax (${person1.name}) (£)",
            "Tax (${person2.name}) (£)",
            "Retirement Feasible?",
            "Withdrawal Details"
        )
        if (!isCouple) {
            headers.remove("Age (${person2.name})")
            headers.remove("Tax (${person2.name}) (£)")
        }

        val rows = results.map { res ->
            val row = mutableListOf<Any>()
            row.add(res.year)
            row.add(res.age) // Age 1
            if (isCouple) {
                val ageDiff = person2.birthYear - person1.birthYear
                row.add(res.age + ageDiff)
            }
            row.add(res.totalPensionValue)
            row.add(res.totalSavings)
            row.add(res.annualIncome)
            row.add(res.netIncome)
            row.add(res.tax)
            row.add(res.tax1)
            if (isCouple) {
                row.add(res.tax2)
            }
            row.add(if (res.canRetire) "Yes" else "No")

            val withdrawalsText = res.withdrawals.joinToString("\n") { detail ->
                "${detail.potName} (${detail.ownerName}): Drawn £${String.format("%.2f", detail.amountDrawn)}, Tax £${String.format("%.2f", detail.taxPaid)}"
            }
            row.add(withdrawalsText)
            row
        }

        XlsxWriter.writeXlsx(headers, rows, outputStream)
        outputStream.close()

        val fileUri = FileProvider.getUriForFile(context, "com.chris.financeapp.fileprovider", cacheFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Projections Excel"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
