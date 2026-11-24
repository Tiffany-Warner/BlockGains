package com.tiffles.blockgains.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.AssetFilter
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.ui.format.MoneyFormatter
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import com.tiffles.blockgains.ui.theme.gainLoss
import com.tiffles.blockgains.ui.theme.tabular
import java.math.BigDecimal

/** Centered empty state that says what to do next (ui-guidelines → Principles). */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    Box(modifier.fillMaxSize().semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** `All · Crypto · Stocks` (ui-guidelines → Navigation). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetFilterBar(selected: AssetFilter, onSelect: (AssetFilter) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(R.string.filter_all, R.string.filter_crypto, R.string.filter_stocks)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        AssetFilter.entries.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                shape = SegmentedButtonDefaults.itemShape(index, AssetFilter.entries.size),
            ) { Text(stringResource(labels[index]), maxLines = 1) }
        }
    }
}

/** Money with a sign and tabular figures. Gains/losses are also colored, never color alone. */
@Composable
fun MoneyText(
    value: BigDecimal,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    signed: Boolean = false,
) {
    val color = when {
        !signed || value.signum() == 0 -> MaterialTheme.colorScheme.onSurface
        value.signum() < 0 -> MaterialTheme.gainLoss.loss
        else -> MaterialTheme.gainLoss.gain
    }
    Text(
        text = if (signed) MoneyFormatter.signed(value) else MoneyFormatter.format(value),
        modifier = modifier,
        style = style.tabular(),
        color = color,
    )
}

/** Short-term / Long-term / Mixed label; text, not just color. */
@Composable
fun TermChip(term: HoldingTerm?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (term == HoldingTerm.LONG) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(termLabel(term)),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

fun termLabel(term: HoldingTerm?): Int = when (term) {
    HoldingTerm.SHORT -> R.string.term_short
    HoldingTerm.LONG -> R.string.term_long
    null -> R.string.term_mixed
}

/** Small neutral marker, e.g. "Unknown basis" (R9: shown, but not a warning). */
@Composable
fun MarkerChip(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Label on the left, value on the right. When both don't fit on one line (long values, 200% font)
 * the value flows onto its own line instead of squeezing the label.
 */
@Composable
fun LabeledValue(label: String, modifier: Modifier = Modifier, supporting: String? = null, value: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value()
    }
}

@Composable
fun LabeledText(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null) {
    LabeledValue(label, modifier, supporting) {
        Text(value, style = MaterialTheme.typography.bodyMedium.tabular(), textAlign = TextAlign.End)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

fun TransactionType.icon(): ImageVector = when (this) {
    TransactionType.BUY -> Icons.Outlined.Download
    TransactionType.SELL -> Icons.Outlined.Upload
    TransactionType.SWAP -> Icons.Outlined.SwapHoriz
    TransactionType.INCOME -> Icons.Outlined.Savings
    TransactionType.TRANSFER -> Icons.Outlined.SyncAlt
    TransactionType.OPENING -> Icons.Outlined.Flag
    TransactionType.ADJUST -> Icons.Outlined.Tune
}

fun TransactionType.label(): Int = when (this) {
    TransactionType.BUY -> R.string.type_buy
    TransactionType.SELL -> R.string.type_sell
    TransactionType.SWAP -> R.string.type_swap
    TransactionType.INCOME -> R.string.type_income
    TransactionType.TRANSFER -> R.string.type_transfer
    TransactionType.OPENING -> R.string.type_opening
    TransactionType.ADJUST -> R.string.type_adjust
}

/** Red warning card with a Review action (Summary issues, Export shortfalls). */
@Composable
fun WarningCard(title: String, body: String, onReview: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = onReview, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_review))
                }
            }
        }
    }
}

/** Gain, zero and loss on the surfaces they appear on (background, card, hero card). */
@PreviewLightDark
@Composable
private fun MoneyTextPreview() {
    BlockGainsTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val amounts = listOf(BigDecimal("1300.00"), BigDecimal.ZERO, BigDecimal("-250.50"))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { amounts.forEach { MoneyText(it, signed = true) } }
                Card { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { amounts.forEach { MoneyText(it, signed = true) } } }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        amounts.forEach { MoneyText(it, style = MaterialTheme.typography.titleLarge, signed = true) }
                    }
                }
            }
        }
    }
}
