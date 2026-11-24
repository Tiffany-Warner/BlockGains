package com.tiffles.blockgains.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.usecase.SummarizeYearUseCase
import com.tiffles.blockgains.ui.components.AssetFilterBar
import com.tiffles.blockgains.ui.components.EmptyState
import com.tiffles.blockgains.ui.components.LoadingState
import com.tiffles.blockgains.ui.components.MoneyText
import com.tiffles.blockgains.ui.components.WarningCard
import com.tiffles.blockgains.ui.components.YearPicker
import com.tiffles.blockgains.ui.format.MoneyFormatter
import com.tiffles.blockgains.ui.preview.PreviewData
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import java.math.BigDecimal

/** Where a Summary card leads. */
sealed interface SummaryTarget {
    data class Events(val term: HoldingTerm?) : SummaryTarget
    data object Income : SummaryTarget
    data object Issues : SummaryTarget
    data object Import : SummaryTarget
    data object Settings : SummaryTarget
}

@Composable
fun SummaryScreen(onNavigate: (SummaryTarget) -> Unit, viewModel: SummaryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryContent(state, onYear = viewModel::selectYear, onFilter = viewModel::selectFilter, onNavigate = onNavigate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryContent(
    state: SummaryUiState,
    onYear: (Int) -> Unit,
    onFilter: (AssetFilter) -> Unit,
    onNavigate: (SummaryTarget) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_summary)) },
                actions = {
                    if (state is SummaryUiState.Content) YearPicker(state.summary.year, state.years, onYear)
                    IconButton(onClick = { onNavigate(SummaryTarget.Settings) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            SummaryUiState.Loading -> LoadingState(modifier)
            SummaryUiState.Empty -> EmptyState(
                icon = Icons.Outlined.ReceiptLong,
                title = stringResource(R.string.summary_empty_title),
                body = stringResource(R.string.summary_empty_body),
                actionLabel = stringResource(R.string.action_import_transactions),
                onAction = { onNavigate(SummaryTarget.Import) },
                modifier = modifier,
            )
            is SummaryUiState.Content -> SummaryBody(state, onFilter, onNavigate, modifier)
        }
    }
}

@Composable
private fun SummaryBody(
    state: SummaryUiState.Content,
    onFilter: (AssetFilter) -> Unit,
    onNavigate: (SummaryTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = state.summary
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssetFilterBar(state.filter, onFilter)
        if (summary.issues.isNotEmpty()) IssuesBanner(summary.issues.size) { onNavigate(SummaryTarget.Issues) }
        HeroCard(net = summary.net, eventCount = summary.eventCount)
        BreakdownCard(stringResource(R.string.summary_short_term), summary.shortTerm, signed = true) {
            onNavigate(SummaryTarget.Events(HoldingTerm.SHORT))
        }
        BreakdownCard(stringResource(R.string.summary_long_term), summary.longTerm, signed = true) {
            onNavigate(SummaryTarget.Events(HoldingTerm.LONG))
        }
        BreakdownCard(
            stringResource(R.string.summary_income),
            summary.ordinaryIncome,
            signed = false,
            hint = stringResource(R.string.summary_income_hint),
        ) { onNavigate(SummaryTarget.Income) }
        if (summary.showsLossLimitNote) InfoNote(stringResource(R.string.loss_limit_note))
        Text(
            stringResource(R.string.disclaimer),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HeroCard(net: BigDecimal, eventCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp).semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(if (net.signum() < 0) R.string.summary_net_loss else R.string.summary_net_gain),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            MoneyText(net, style = MaterialTheme.typography.displaySmall, signed = true)
            Text(
                pluralStringResource(R.plurals.summary_event_count, eventCount, eventCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun BreakdownCard(title: String, value: BigDecimal, signed: Boolean, hint: String? = null, onClick: () -> Unit) {
    val amount = if (signed) MoneyFormatter.signed(value) else MoneyFormatter.format(value)
    val description = stringResource(R.string.summary_card_cd, title, amount)
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MoneyText(value, style = MaterialTheme.typography.headlineSmall, signed = signed)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun IssuesBanner(count: Int, onReview: () -> Unit) = WarningCard(
    title = pluralStringResource(R.plurals.issues_banner_title, count, count),
    body = stringResource(R.string.issues_banner_body),
    onReview = onReview,
)

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@PreviewLightDark
@Composable
private fun SummaryContentPreview() {
    val summary = SummarizeYearUseCase()(PreviewData.result, 2024, AssetFilter.ALL)
    BlockGainsTheme(dynamicColor = false) {
        SummaryContent(SummaryUiState.Content(listOf(2024, 2023), AssetFilter.ALL, summary), {}, {}, {})
    }
}

@PreviewLightDark
@Composable
private fun SummaryLossPreview() {
    val summary = SummarizeYearUseCase()(PreviewData.result, 2024, AssetFilter.CRYPTO)
        .copy(shortTerm = BigDecimal("-5120.40"), longTerm = BigDecimal("310.05"), issues = emptyList())
    BlockGainsTheme(dynamicColor = false) {
        SummaryContent(SummaryUiState.Content(listOf(2024), AssetFilter.CRYPTO, summary), {}, {}, {})
    }
}

@PreviewLightDark
@Composable
private fun SummaryEmptyPreview() {
    BlockGainsTheme(dynamicColor = false) { SummaryContent(SummaryUiState.Empty, {}, {}, {}) }
}
