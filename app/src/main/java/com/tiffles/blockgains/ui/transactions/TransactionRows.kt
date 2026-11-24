package com.tiffles.blockgains.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.HoldingTerm
import com.tiffles.blockgains.domain.model.TaxableEvent
import com.tiffles.blockgains.domain.model.Transaction
import com.tiffles.blockgains.domain.model.TransactionType
import com.tiffles.blockgains.ui.components.MoneyText
import com.tiffles.blockgains.ui.components.TermChip
import com.tiffles.blockgains.ui.components.icon
import com.tiffles.blockgains.ui.components.label
import com.tiffles.blockgains.ui.components.termLabel
import com.tiffles.blockgains.ui.format.DateFormatter
import com.tiffles.blockgains.ui.format.MoneyFormatter
import com.tiffles.blockgains.ui.format.QuantityFormatter
import com.tiffles.blockgains.ui.preview.PreviewData
import com.tiffles.blockgains.ui.theme.BlockGainsTheme
import com.tiffles.blockgains.ui.theme.tabular

/**
 * Taxable event row: date sold, asset, wallet, quantity, proceeds, basis, gain/loss, ST/LT (ui-guidelines → Transactions).
 * With a [termFilter], a mixed disposal keeps its title and Mixed chip but shows only that term's proceeds, basis and gain.
 */
@Composable
fun TaxableEventRow(event: TaxableEvent, onClick: () -> Unit, modifier: Modifier = Modifier, termFilter: HoldingTerm? = null) {
    val shown = event.termPart(termFilter)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                eventTitle(event, QuantityFormatter.format(event.quantity)),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                style = MaterialTheme.typography.titleMedium.tabular(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MoneyText(shown.gain, style = MaterialTheme.typography.titleMedium, signed = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${DateFormatter.format(event.date)} · ${event.wallet}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            TermChip(event.term)
        }
        val part = if (shown !== event && termFilter != null) stringResource(R.string.label_term_part, stringResource(termLabel(termFilter))) + " " else ""
        Text(
            part + "${stringResource(R.string.label_proceeds)} ${MoneyFormatter.format(shown.proceeds)} · " +
                "${stringResource(R.string.label_basis)} ${MoneyFormatter.format(shown.basis)}",
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Raw record row: type icon + label, date, asset, wallet, quantity, USD value. */
@Composable
fun TransactionRow(tx: Transaction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        leadingContent = { Icon(tx.type.icon(), contentDescription = null) },
        headlineContent = { Text(headline(tx), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text("${DateFormatter.format(tx.date)} · ${walletText(tx)}")
                networkFeeText(tx)?.let { Text(it, style = MaterialTheme.typography.bodySmall.tabular()) }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(QuantityFormatter.format(tx.quantity), style = MaterialTheme.typography.bodyMedium.tabular())
                tx.usdValue?.let {
                    Text(MoneyFormatter.format(it), style = MaterialTheme.typography.bodySmall.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

@Composable
private fun headline(tx: Transaction): String {
    val type = stringResource(tx.type.label())
    val asset = if (tx.type == TransactionType.SWAP && tx.toAsset != null) {
        stringResource(R.string.arrow_pair, tx.asset, tx.toAsset)
    } else {
        tx.asset
    }
    return "$type · $asset"
}

/** "1.5 FAKEB", or "0.01 FAKEB (transfer fee)" for the coins that paid a transfer's network fee (R13). */
@Composable
fun eventTitle(event: TaxableEvent, quantity: String): String =
    stringResource(if (event.isTransferFee) R.string.quantity_asset_transfer_fee else R.string.quantity_asset, quantity, event.asset)

/** R13: "Sent 1 · fee 0.01 · received 0.99" (fee in the same coin) or "Received 100 · fee 0.005 FAKEB". Null without a fee. */
@Composable
fun networkFeeText(tx: Transaction): String? {
    val feeAsset = tx.networkFeeAsset ?: return null
    val fee = tx.feeQuantity ?: return null
    val format = QuantityFormatter::format
    return if (feeAsset == tx.asset) {
        stringResource(R.string.transfer_fee_same_asset, format(tx.quantity + fee), format(fee), format(tx.quantity))
    } else {
        stringResource(R.string.transfer_fee_other_asset, format(tx.quantity), format(fee), feeAsset)
    }
}

@Composable
private fun walletText(tx: Transaction): String =
    if (tx.type == TransactionType.TRANSFER && tx.toWallet != null) stringResource(R.string.arrow_pair, tx.wallet, tx.toWallet) else tx.wallet

@PreviewLightDark
@Composable
private fun RowsPreview() {
    BlockGainsTheme(dynamicColor = false) {
        androidx.compose.material3.Surface {
            Column {
                PreviewData.result.taxableEvents.take(2).forEach { TaxableEventRow(it, onClick = {}) }
                PreviewData.transactions.takeLast(3).forEach { TransactionRow(it, onClick = {}) }
            }
        }
    }
}
