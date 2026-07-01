package com.chris.financeapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
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

    val formatter = NumberFormat.getCurrencyInstance(Locale.UK)

    // Dynamic calculations running on parameter updates
    val projection = remember(retirementAge1, retirementAge2, equityReturn, inflationRate, targetAnnualIncome) {
        val updatedAssumptions = assumptions.copy(
            equityReturn = (equityReturn / 100.0),
            inflationRate = (inflationRate / 100.0)
        )
        val updatedDrawdown = drawdown.copy(
            targetAnnualIncome = targetAnnualIncome
        )
        PensionCalculator.calculateProjections(
            accounts = accounts,
            assumptions = updatedAssumptions,
            preferences = updatedDrawdown,
            person1 = person1,
            person2 = person2,
            retirementAge1 = retirementAge1,
            retirementAge2 = retirementAge2
        )
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
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        // line chart displaying growth & depletion phases
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Consolidated Wealth Growth & Drawdown",
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

        // Feasibility Indicator Card
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
                val depletionResult = projection.results.firstOrNull { it.age >= retirementAge1 && (it.totalPensionValue + it.totalSavings) <= 0.0 }
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

        // Sliders card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Chris's Retirement Age slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Chris's Retirement Age", fontWeight = FontWeight.SemiBold, color = TextPrimary)
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

                // 1b. Lisa's Retirement Age slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lisa's Retirement Age", fontWeight = FontWeight.SemiBold, color = TextPrimary)
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

                // 2. Expected Investment Return
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

                // 3. Inflation Rate
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

                // 4. Target annual income
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
            }
        }
    }
}
