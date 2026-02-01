package com.tiffles.blockgains.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.data.calculator.FifoCalculator
import com.tiffles.blockgains.data.model.YearSummary
import com.tiffles.blockgains.data.parser.CsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(
        val yearSummaries: Map<Int, YearSummary>,
        val selectedYear: Int,
        val availableYears: List<Int>
    ) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var yearSummaries: Map<Int, YearSummary> = emptyMap()

    /**
     * Load and process CSV files
     */
    fun loadCsvFiles(context: Context, buysUri: Uri, sellsUri: Uri) {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading

            try {
                val result = withContext(Dispatchers.IO) {
                    // Parse CSV files
                    val buys = context.contentResolver.openInputStream(buysUri)?.use { stream ->
                        CsvParser.parseBuyTransactions(stream)
                    } ?: throw IllegalArgumentException("Could not open buys CSV file")

                    val sells = context.contentResolver.openInputStream(sellsUri)?.use { stream ->
                        CsvParser.parseSellTransactions(stream)
                    } ?: throw IllegalArgumentException("Could not open sells CSV file")

                    // Calculate taxable events using FIFO
                    val taxableEvents = FifoCalculator.calculateTaxableEvents(buys, sells)

                    // Group by year
                    FifoCalculator.calculateYearSummaries(taxableEvents)
                }

                yearSummaries = result

                if (result.isEmpty()) {
                    _uiState.value = DashboardUiState.Error("No transactions found in CSV files")
                } else {
                    val years = result.keys.sortedDescending()
                    _uiState.value = DashboardUiState.Success(
                        yearSummaries = result,
                        selectedYear = years.first(),
                        availableYears = years
                    )
                }

            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(
                    e.message ?: "An error occurred while processing CSV files"
                )
            }
        }
    }

    /**
     * Change the selected year
     */
    fun selectYear(year: Int) {
        val currentState = _uiState.value
        if (currentState is DashboardUiState.Success) {
            _uiState.value = currentState.copy(selectedYear = year)
        }
    }
}
