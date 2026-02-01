package com.tiffles.blockgains.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tiffles.blockgains.data.model.TaxableEvent
import com.tiffles.blockgains.data.model.YearSummary
import com.tiffles.blockgains.ui.viewmodel.DashboardUiState
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onBackClick: () -> Unit,
    onYearSelected: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capital Gains Report") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is DashboardUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is DashboardUiState.Error -> {
                    ErrorContent(
                        message = uiState.message,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is DashboardUiState.Success -> {
                    val summary = uiState.yearSummaries[uiState.selectedYear]
                    if (summary != null) {
                        DashboardContent(
                            summary = summary,
                            selectedYear = uiState.selectedYear,
                            availableYears = uiState.availableYears,
                            onYearSelected = onYearSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorContent(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DashboardContent(
    summary: YearSummary,
    selectedYear: Int,
    availableYears: List<Int>,
    onYearSelected: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Year selector
        item {
            YearSelector(
                selectedYear = selectedYear,
                availableYears = availableYears,
                onYearSelected = onYearSelected
            )
        }

        // Summary cards
        item {
            SummaryCards(summary = summary)
        }

        // Transactions header
        item {
            Text(
                text = "Taxable Events (${summary.taxableEvents.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Transaction list
        items(summary.taxableEvents) { event ->
            TaxableEventCard(event = event)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearSelector(
    selectedYear: Int,
    availableYears: List<Int>,
    onYearSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.padding(16.dp)
        ) {
            OutlinedTextField(
                value = "Tax Year: $selectedYear",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, "Select year")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString()) },
                        onClick = {
                            onYearSelected(year)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryCards(summary: YearSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Total Gain/Loss Card
        GainLossCard(
            title = "Total Capital ${if (summary.totalGain >= BigDecimal.ZERO) "Gain" else "Loss"}",
            amount = summary.totalGain,
            isTotal = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Short-term card
            GainLossCard(
                title = "Short-Term",
                amount = summary.shortTermGain,
                modifier = Modifier.weight(1f)
            )

            // Long-term card
            GainLossCard(
                title = "Long-Term",
                amount = summary.longTermGain,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun GainLossCard(
    title: String,
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    isTotal: Boolean = false
) {
    val isGain = amount >= BigDecimal.ZERO
    val backgroundColor = when {
        isTotal && isGain -> MaterialTheme.colorScheme.primaryContainer
        isTotal && !isGain -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        isTotal && isGain -> MaterialTheme.colorScheme.onPrimaryContainer
        isTotal && !isGain -> MaterialTheme.colorScheme.onErrorContainer
        else -> if (isGain) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = if (isTotal) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isTotal) FontWeight.Bold else FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = amount.toCurrency(),
                style = if (isTotal) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
fun TaxableEventCard(event: TaxableEvent) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val isGain = event.gain >= BigDecimal.ZERO

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with gain/loss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.sellDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = event.gain.toCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isGain) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Term indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (event.isLongTerm) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (event.isLongTerm) "Long-Term" else "Short-Term",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.isLongTerm) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider()

            Spacer(modifier = Modifier.height(12.dp))

            // Transaction details
            TransactionDetailRow("Quantity:", event.quantity.toPlainString())
            TransactionDetailRow("Purchase Date:", event.buyDate.format(dateFormatter))
            TransactionDetailRow("Cost Basis:", event.costBasis.toCurrency() + " per unit")
            TransactionDetailRow("Sale Price:", event.salePrice.toCurrency() + " per unit")

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            TransactionDetailRow("Total Cost:", event.totalCost.toCurrency(), bold = true)
            TransactionDetailRow("Total Proceeds:", event.totalProceeds.toCurrency(), bold = true)
        }
    }
}

@Composable
fun TransactionDetailRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

fun BigDecimal.toCurrency(): String {
    val format = NumberFormat.getCurrencyInstance(Locale.US)
    return format.format(this)
}