package com.tiffles.blockgains.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FilterListOff
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.ui.components.AssetFilterBar
import com.tiffles.blockgains.ui.components.EmptyState
import com.tiffles.blockgains.ui.components.FilterDropdownChip
import com.tiffles.blockgains.ui.components.LoadingState
import com.tiffles.blockgains.ui.components.YearPicker
import com.tiffles.blockgains.ui.components.label
import com.tiffles.blockgains.ui.components.termLabel
import com.tiffles.blockgains.ui.preview.PreviewData
import com.tiffles.blockgains.ui.theme.BlockGainsTheme

/** Callbacks from the Transactions screen. */
data class TransactionsActions(
    val onYear: (Int) -> Unit = {},
    val onAssetFilter: (AssetFilter) -> Unit = {},
    val onTab: (TransactionsTab) -> Unit = {},
    val onTerm: (HoldingTerm?) -> Unit = {},
    val onType: (TransactionType?) -> Unit = {},
    val onAsset: (String?) -> Unit = {},
    val onWallet: (String?) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onEvent: (Long) -> Unit = {},
    val onTransaction: (Long) -> Unit = {},
    val onImport: () -> Unit = {},
)

@Composable
fun TransactionsScreen(
    onEvent: (Long) -> Unit,
    onTransaction: (Long) -> Unit,
    onImport: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TransactionsContent(
        state,
        TransactionsActions(
            onYear = viewModel::selectYear,
            onAssetFilter = viewModel::selectAssetFilter,
            onTab = viewModel::selectTab,
            onTerm = viewModel::selectTerm,
            onType = viewModel::selectType,
            onAsset = viewModel::selectAsset,
            onWallet = viewModel::selectWallet,
            onSearch = viewModel::search,
            onClearFilters = viewModel::clearRecordFilters,
            onEvent = onEvent,
            onTransaction = onTransaction,
            onImport = onImport,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsContent(state: TransactionsUiState, actions: TransactionsActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_transactions)) },
                actions = {
                    if (state is TransactionsUiState.Content && state.filters.tab == TransactionsTab.EVENTS) {
                        YearPicker(state.year, state.years, actions.onYear)
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            TransactionsUiState.Loading -> LoadingState(Modifier.padding(padding))
            is TransactionsUiState.Content -> {
                val modifier = Modifier.padding(padding).fillMaxSize()
                when (state.filters.tab) {
                    TransactionsTab.EVENTS -> EventsTab(state, actions, modifier)
                    TransactionsTab.ALL -> AllTab(state, actions, modifier)
                }
            }
        }
    }
}

/** Asset-class filter and tabs: the list's first item, so they scroll away with it (landscape). */
@Composable
private fun TransactionsHeader(state: TransactionsUiState.Content, actions: TransactionsActions) {
    Column {
        AssetFilterBar(state.assetFilter, actions.onAssetFilter, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        val tabs = TransactionsTab.entries
        PrimaryTabRow(selectedTabIndex = tabs.indexOf(state.filters.tab)) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab == state.filters.tab,
                    onClick = { actions.onTab(tab) },
                    text = { Text(stringResource(if (tab == TransactionsTab.EVENTS) R.string.tab_taxable_events else R.string.tab_all_transactions)) },
                )
            }
        }
    }
}

@Composable
private fun TermChips(state: TransactionsUiState.Content, actions: TransactionsActions) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(null, HoldingTerm.SHORT, HoldingTerm.LONG).forEach { term ->
            FilterChip(
                selected = state.filters.term == term,
                onClick = { actions.onTerm(term) },
                label = { Text(stringResource(if (term == null) R.string.filter_all else termLabel(term))) },
            )
        }
    }
}

@Composable
private fun EventsTab(state: TransactionsUiState.Content, actions: TransactionsActions, modifier: Modifier) {
    LazyColumn(modifier) {
        item(key = HEADER_KEY) { TransactionsHeader(state, actions) }
        item(key = FILTERS_KEY) { TermChips(state, actions) }
        if (state.events.isEmpty()) {
            item(key = EMPTY_KEY) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = stringResource(R.string.events_empty_title, state.year),
                    body = stringResource(R.string.events_empty_body),
                    modifier = Modifier.fillParentMaxHeight(EMPTY_HEIGHT),
                    actionLabel = if (state.hasAnyRecords) null else stringResource(R.string.action_import_transactions),
                    onAction = actions.onImport,
                )
            }
        }
        items(state.events, key = { it.transactionId }) { event ->
            TaxableEventRow(event, onClick = { actions.onEvent(event.transactionId) }, termFilter = state.filters.term)
            HorizontalDivider()
        }
    }
}

@Composable
private fun AllTab(state: TransactionsUiState.Content, actions: TransactionsActions, modifier: Modifier) {
    LazyColumn(modifier) {
        item(key = HEADER_KEY) { TransactionsHeader(state, actions) }
        if (!state.hasAnyRecords) {
            item(key = EMPTY_KEY) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = stringResource(R.string.all_empty_title),
                    body = stringResource(R.string.all_empty_body),
                    modifier = Modifier.fillParentMaxHeight(EMPTY_HEIGHT),
                    actionLabel = stringResource(R.string.action_import_transactions),
                    onAction = actions.onImport,
                )
            }
            return@LazyColumn
        }
        // Keyed, so the search field keeps focus while its results change.
        item(key = FILTERS_KEY) { RecordFilters(state, actions) }
        if (state.records.isEmpty()) {
            item(key = NO_MATCH_KEY) {
                EmptyState(
                    icon = Icons.Outlined.FilterListOff,
                    title = stringResource(R.string.filters_no_match),
                    body = null,
                    modifier = Modifier.fillParentMaxHeight(EMPTY_HEIGHT),
                    actionLabel = stringResource(R.string.filters_clear),
                    onAction = actions.onClearFilters,
                )
            }
        }
        items(state.records, key = { it.id }) { tx ->
            TransactionRow(tx, onClick = { actions.onTransaction(tx.id) })
            HorizontalDivider()
        }
    }
}

private const val HEADER_KEY = "header"
private const val FILTERS_KEY = "filters"
private const val EMPTY_KEY = "empty"
private const val NO_MATCH_KEY = "no-match"

/** Empty states sit under the header and filters; a bounded height lets them center and scroll on their own. */
private const val EMPTY_HEIGHT = 0.6f

@Composable
private fun RecordFilters(state: TransactionsUiState.Content, actions: TransactionsActions) {
    val filters = state.filters
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdownChip(stringResource(R.string.filter_type), filters.type, TransactionType.entries, { stringResource(it.label()) }, actions.onType)
            FilterDropdownChip(stringResource(R.string.filter_asset), filters.asset, state.assets, { it }, actions.onAsset)
            FilterDropdownChip(stringResource(R.string.filter_wallet), filters.wallet, state.wallets, { it }, actions.onWallet)
        }
        OutlinedTextField(
            value = filters.query,
            onValueChange = actions.onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            placeholder = { Text(stringResource(R.string.search_notes)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (filters.query.isNotEmpty()) {
                    IconButton(onClick = { actions.onSearch("") }) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.search_clear_cd))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
}

private fun previewState(tab: TransactionsTab) = TransactionsUiState.Content(
    year = 2024,
    years = listOf(2025, 2024, 2023),
    assetFilter = AssetFilter.ALL,
    filters = TransactionFilters(tab = tab),
    events = PreviewData.result.taxableEvents.filter { it.date.year == 2024 }.sortedByDescending { it.date },
    records = PreviewData.transactions.sortedByDescending { it.date },
    hasAnyRecords = true,
    assets = listOf("FAKEA", "FAKEB", "FAKE-LONG-NAME-TOKEN", "FAKEUSD"),
    wallets = listOf("FAKE-EXCHANGE-1", "FAKE-WALLET-1"),
)

@PreviewLightDark
@Composable
private fun EventsPreview() {
    BlockGainsTheme(dynamicColor = false) { TransactionsContent(previewState(TransactionsTab.EVENTS), TransactionsActions()) }
}

@PreviewLightDark
@Composable
private fun AllPreview() {
    BlockGainsTheme(dynamicColor = false) { TransactionsContent(previewState(TransactionsTab.ALL), TransactionsActions()) }
}
