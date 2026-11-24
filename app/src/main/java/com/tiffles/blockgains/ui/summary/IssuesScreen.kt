package com.tiffles.blockgains.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.repository.LedgerSource
import com.tiffles.blockgains.domain.usecase.SummarizeYearUseCase
import com.tiffles.blockgains.domain.usecase.activityYears
import com.tiffles.blockgains.domain.usecase.selectedYear
import com.tiffles.blockgains.ui.SharedSelection
import com.tiffles.blockgains.ui.components.BackButton
import com.tiffles.blockgains.ui.components.EmptyState
import com.tiffles.blockgains.ui.components.LoadingState
import com.tiffles.blockgains.ui.components.issueText
import com.tiffles.blockgains.ui.preview.PreviewData
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface IssuesUiState {
    data object Loading : IssuesUiState
    data class Content(val year: Int, val issues: List<CalcIssue>) : IssuesUiState
}

@HiltViewModel
class IssuesViewModel @Inject constructor(
    ledgerSource: LedgerSource,
    selection: SharedSelection,
    summarize: SummarizeYearUseCase,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<IssuesUiState> = combine(ledgerSource.ledger, selection.year) { ledger, choice ->
        if (ledger == null) {
            IssuesUiState.Loading
        } else {
            val year = selectedYear(choice, activityYears(ledger.transactions), LocalDate.now(clock).year)
            IssuesUiState.Content(year, summarize.issuesForYear(ledger.result.issues, year))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssuesUiState.Loading)
}

@Composable
fun IssuesScreen(onBack: () -> Unit, onOpenOpeningPositions: () -> Unit, viewModel: IssuesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IssuesContent(state, onBack, onOpenOpeningPositions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesContent(state: IssuesUiState, onBack: () -> Unit, onOpenOpeningPositions: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state is IssuesUiState.Content) stringResource(R.string.issues_title, state.year) else "") },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        when (state) {
            IssuesUiState.Loading -> LoadingState(Modifier.padding(padding))
            is IssuesUiState.Content -> if (state.issues.isEmpty()) {
                EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.issues_empty, state.year), body = null, modifier = Modifier.padding(padding))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.issues) { issue -> IssueCard(issue, onOpenOpeningPositions) }
                }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: CalcIssue, onOpenOpeningPositions: () -> Unit) {
    val text = issueText(issue)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(text.title, style = MaterialTheme.typography.titleMedium)
                Text(text.body, style = MaterialTheme.typography.bodyMedium)
            }
            text.fix?.let { fix ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(stringResource(R.string.issue_fix_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(fix, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (issue is CalcIssue.TransitionMismatch) {
                TextButton(onClick = onOpenOpeningPositions, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.opening_card_button))
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun IssuesPreview() {
    val issues = PreviewData.result.issues + CalcIssue.TransitionMismatch("FAKEB", LocalDate.of(2025, 1, 1), java.math.BigDecimal("5"), java.math.BigDecimal("4.99"))
    BlockGainsTheme(dynamicColor = false) { IssuesContent(IssuesUiState.Content(2024, issues), {}, {}) }
}
