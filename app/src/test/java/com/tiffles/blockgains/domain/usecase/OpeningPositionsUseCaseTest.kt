package com.tiffles.blockgains.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tiffles.blockgains.domain.calc.TaxCalculator
import com.tiffles.blockgains.domain.calc.TxBuilder
import com.tiffles.blockgains.domain.calc.d
import com.tiffles.blockgains.domain.model.CalcIssue
import com.tiffles.blockgains.domain.model.CalcSettings
import com.tiffles.blockgains.domain.model.TransactionType
import java.math.BigDecimal
import org.junit.Test

class OpeningPositionsUseCaseTest {

    private val useCase = OpeningPositionsUseCase()

    private val history = TxBuilder().apply {
        buy("2021-03-01", qty = "2", price = "1000", fee = "1", asset = "FAKEB", wallet = "FAKE-WALLET-1")
        buy("2022-07-01", qty = "3", price = "1500", asset = "FAKEB", wallet = "FAKE-EXCHANGE-1")
        sell("2023-01-15", qty = "0.5", price = "1300", asset = "FAKEB", wallet = "FAKE-WALLET-1")
    }
    private val lots = history.calculate().universalLotsAtTransition

    @Test
    fun noSavedRecords_everythingStartsInTheDefaultWallet() {
        val drafts = useCase.drafts(lots, saved = emptyList(), defaultWallet = "FAKE-WALLET-1")

        assertThat(drafts.map { it.allocations.single().wallet }).containsExactly("FAKE-WALLET-1", "FAKE-WALLET-1")
        assertThat(drafts.all { it.problem == null }).isTrue()
    }

    @Test
    fun splitRecords_keepDatesAndConserveBasisExactly() {
        val drafts = useCase.drafts(lots, emptyList(), "FAKE-WALLET-1").mapIndexed { i, draft ->
            if (i == 1) {
                draft.copy(allocations = listOf(LotAllocation("FAKE-EXCHANGE-1", BigDecimal("1")), LotAllocation("FAKE-WALLET-2", BigDecimal("2"))))
            } else {
                draft
            }
        }
        val records = useCase.toOpeningRecords(drafts)

        assertThat(records.map { it.type }.toSet()).containsExactly(TransactionType.OPENING)
        assertThat(records.map { it.acquiredDate }).containsExactly(d("2021-03-01"), d("2022-07-01"), d("2022-07-01")).inOrder()
        assertThat(records.sumOf { it.totalUsd!! }).isEquivalentAccordingToCompareTo(lots.sumOf { it.basis })
        assertThat(records.sumOf { it.quantity }).isEquivalentAccordingToCompareTo(lots.sumOf { it.quantity })
    }

    @Test
    fun savedRecords_roundTripThroughTheCalculatorWithoutMismatch() {
        val drafts = useCase.drafts(lots, emptyList(), "FAKE-WALLET-1").map { draft ->
            val half = draft.lot.quantity.divide(BigDecimal(2))
            draft.copy(allocations = listOf(LotAllocation("FAKE-WALLET-1", half), LotAllocation("FAKE-EXCHANGE-1", draft.lot.quantity - half)))
        }
        val openings = useCase.toOpeningRecords(drafts)
        val result = TaxCalculator().calculate(history.transactions + openings, CalcSettings("FAKE-WALLET-1"))
        assertThat(result.issues.filterIsInstance<CalcIssue.TransitionMismatch>()).isEmpty()

        // Reloading the helper maps saved records back onto the same lots.
        val reloaded = useCase.drafts(lots, openings, "FAKE-WALLET-1")
        assertThat(reloaded.map { it.allocations }).isEqualTo(drafts.map { it.allocations })
    }

    @Test
    fun savedRecordsThatDontCoverALot_leaveItUnassigned() {
        val partial = useCase.toOpeningRecords(useCase.drafts(lots.take(1), emptyList(), "FAKE-WALLET-1"))
        val drafts = useCase.drafts(lots, partial, "FAKE-WALLET-1")

        assertThat(drafts[0].problem).isNull()
        assertThat(drafts[1].problem).isEqualTo(DraftProblem.UNASSIGNED)
    }

    @Test
    fun draftProblems() {
        val lot = lots.first()
        fun problem(vararg allocations: LotAllocation) = OpeningLotDraft(lot, allocations.toList()).problem

        assertThat(problem(LotAllocation(" ", lot.quantity))).isEqualTo(DraftProblem.BLANK_WALLET)
        assertThat(problem(LotAllocation("W", lot.quantity), LotAllocation("X", BigDecimal.ZERO))).isEqualTo(DraftProblem.NON_POSITIVE)
        assertThat(problem(LotAllocation("W", lot.quantity + BigDecimal.ONE))).isEqualTo(DraftProblem.OVER_ALLOCATED)
    }
}
