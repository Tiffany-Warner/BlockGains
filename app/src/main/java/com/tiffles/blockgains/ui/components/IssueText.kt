package com.tiffles.blockgains.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tiffles.blockgains.R
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.InvalidReason
import com.tiffles.blockgains.ui.format.DateFormatter
import com.tiffles.blockgains.ui.format.QuantityFormatter
import java.math.BigDecimal

/** Plain-language text for an issue: what's wrong, where, and how to fix it (ui-guidelines → Summary). */
data class IssueText(val title: String, val body: String, val fix: String?)

@Composable
fun issueText(issue: CalcIssue): IssueText = when (issue) {
    is CalcIssue.InsufficientLots -> IssueText(
        title = stringResource(
            R.string.issue_insufficient_title,
            issue.asset,
            stringResource(if (issue.networkFee) R.string.type_transfer_fee else issue.type.label()).lowercase(),
        ),
        body = stringResource(
            if (issue.pooled) R.string.issue_insufficient_body_pooled else R.string.issue_insufficient_body,
            DateFormatter.format(issue.date),
            issue.wallet,
            qty(issue.requested, issue.asset),
            qty(issue.available, issue.asset),
            qty(issue.shortBy, issue.asset),
        ),
        fix = stringResource(R.string.issue_insufficient_fix),
    )
    is CalcIssue.TransitionMismatch -> IssueText(
        title = stringResource(R.string.issue_mismatch_title, issue.asset),
        body = stringResource(R.string.issue_mismatch_body, qty(issue.expected, issue.asset), qty(issue.opening, issue.asset)),
        fix = stringResource(R.string.issue_mismatch_fix),
    )
    is CalcIssue.ZeroPriceBuy -> IssueText(
        title = stringResource(R.string.issue_zero_title, issue.asset),
        body = stringResource(R.string.issue_zero_body, qty(issue.quantity, issue.asset), DateFormatter.format(issue.date), issue.wallet),
        fix = stringResource(R.string.issue_zero_fix),
    )
    is CalcIssue.InvalidRow -> IssueText(
        title = invalidLocation(issue) ?: stringResource(R.string.issue_invalid_title),
        body = invalidReasonText(issue.reason, issue.detail),
        fix = null,
    )
}

@Composable
/** Full precision: a mismatch can be smaller than the 8 decimals shown elsewhere. */
private fun qty(value: BigDecimal, asset: String) = stringResource(R.string.quantity_asset, QuantityFormatter.full(value), asset)

/** "buys.csv, line 7", or "FAKEA record on Mar 1, 2025" for stored rows. */
@Composable
fun invalidLocation(issue: CalcIssue.InvalidRow): String? {
    val line = issue.lineNumber
    val date = issue.date
    return when {
        line != null && issue.fileName != null -> stringResource(R.string.issue_location_file_line, issue.fileName, line)
        line != null -> stringResource(R.string.issue_location_line, line)
        date != null && issue.asset != null -> stringResource(R.string.issue_location_record, issue.asset, DateFormatter.format(date))
        else -> null
    }
}

@Composable
fun invalidReasonText(reason: InvalidReason, detail: String?): String {
    val value = detail.orEmpty()
    return when (reason) {
        InvalidReason.MISSING_COLUMN -> stringResource(R.string.reason_missing_column, value)
        InvalidReason.MISSING_FIELD -> stringResource(R.string.reason_missing_field, value)
        InvalidReason.UNKNOWN_TYPE -> stringResource(R.string.reason_unknown_type, value)
        InvalidReason.UNKNOWN_ASSET_CLASS -> stringResource(R.string.reason_unknown_asset_class, value)
        InvalidReason.BAD_DATE -> stringResource(R.string.reason_bad_date, value)
        InvalidReason.BAD_NUMBER -> stringResource(R.string.reason_bad_number, value)
        InvalidReason.BAD_BOOLEAN -> stringResource(R.string.reason_bad_boolean)
        InvalidReason.BLANK_ASSET -> stringResource(R.string.reason_blank_asset)
        InvalidReason.BLANK_WALLET -> stringResource(R.string.reason_blank_wallet)
        InvalidReason.QUANTITY_NOT_POSITIVE -> stringResource(R.string.reason_quantity_not_positive)
        InvalidReason.ADJUST_NOT_NEGATIVE -> stringResource(R.string.reason_adjust_not_negative)
        InvalidReason.MISSING_VALUE -> stringResource(
            R.string.reason_missing_value,
            if (detail == null) "" else stringResource(R.string.reason_in_column, detail),
        )
        InvalidReason.NEGATIVE_VALUE -> stringResource(R.string.reason_negative_value)
        InvalidReason.PRICE_TOTAL_MISMATCH -> stringResource(R.string.reason_price_total_mismatch)
        InvalidReason.NEGATIVE_FEE -> stringResource(R.string.reason_negative_fee)
        InvalidReason.FEE_NOT_ALLOWED -> stringResource(R.string.reason_fee_not_allowed)
        InvalidReason.NETWORK_FEE_NOT_ALLOWED -> stringResource(R.string.reason_network_fee_not_allowed)
        InvalidReason.NETWORK_FEE_MISSING_QUANTITY -> stringResource(R.string.reason_network_fee_missing_quantity)
        InvalidReason.NETWORK_FEE_QUANTITY_NOT_POSITIVE -> stringResource(R.string.reason_network_fee_quantity_not_positive)
        InvalidReason.NETWORK_FEE_MISSING_VALUE -> stringResource(R.string.reason_network_fee_missing_value)
        InvalidReason.SWAP_MISSING_TO_ASSET -> stringResource(R.string.reason_swap_missing_to_asset)
        InvalidReason.SWAP_MISSING_TO_QUANTITY -> stringResource(R.string.reason_swap_missing_to_quantity)
        InvalidReason.SWAP_SAME_ASSET -> stringResource(R.string.reason_swap_same_asset)
        InvalidReason.TRANSFER_MISSING_TO_WALLET -> stringResource(R.string.reason_transfer_missing_to_wallet)
        InvalidReason.TRANSFER_SAME_WALLET -> stringResource(R.string.reason_transfer_same_wallet)
        InvalidReason.OPENING_WRONG_DATE -> stringResource(R.string.reason_opening_wrong_date)
        InvalidReason.OPENING_MISSING_ACQUIRED_DATE -> stringResource(R.string.reason_opening_missing_acquired)
        InvalidReason.OPENING_ACQUIRED_AFTER_TRANSITION -> stringResource(R.string.reason_opening_acquired_after)
    }
}
