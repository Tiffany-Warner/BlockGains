"""Checks the reference calculator against context/tax-rules.md worked cases T1-T14.

    python -m unittest test_refcalc -v
"""
import unittest

from refcalc import calculate

HEADER = ("type,date,asset,asset_class,wallet,quantity,price_usd,total_usd,fee_usd,"
          "to_asset,to_quantity,to_wallet,acquired_date,basis_unknown,note\n")


FEE_HEADER = ("type,date,asset,asset_class,wallet,quantity,price_usd,total_usd,fee_usd,fee_asset,fee_quantity,"
              "to_asset,to_quantity,to_wallet,acquired_date,basis_unknown,note\n")


def run(rows: str, default_wallet: str = "FAKE-WALLET-1") -> dict:
    return calculate(HEADER + rows.strip() + "\n", default_wallet)


def run_fees(rows: str, default_wallet: str = "FAKE-WALLET-1") -> dict:
    """Rows with the R13 columns fee_asset and fee_quantity (after fee_usd)."""
    return calculate(FEE_HEADER + rows.strip() + "\n", default_wallet)


def events(report):
    return [(e["asset"], e["quantity"], e["term"], e["proceeds"], e["basis"], e["gain"]) for e in report["events"]]


def kinds(report):
    return [i["kind"] for i in report["issues"]]


class WorkedCases(unittest.TestCase):
    def test_T1_anniversary_is_short_term(self):
        r = run("""
BUY,2023-03-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
SELL,2024-03-01,FAKEA,,FAKE-WALLET-1,1,150,,,,,,,,
""")
        self.assertEqual(events(r), [("FAKEA", "1", "ST", "150.00", "100.00", "50.00")])
        r = run("""
BUY,2023-03-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
SELL,2024-03-02,FAKEA,,FAKE-WALLET-1,1,150,,,,,,,,
""")
        self.assertEqual(events(r)[0][2], "LT")

    def test_T2_leap_day(self):
        for sell_date, term in (("2025-02-28", "ST"), ("2025-03-01", "LT")):
            r = run(f"""
BUY,2024-02-29,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
SELL,{sell_date},FAKEA,,FAKE-WALLET-1,1,150,,,,,,,,
""")
            self.assertEqual(events(r)[0][2], term, sell_date)

    def test_T3_fifo_across_lots_with_fees(self):
        r = run("""
BUY,2022-01-10,FAKEA,,FAKE-WALLET-1,2,100,,2,,,,,,
BUY,2022-06-01,FAKEA,,FAKE-WALLET-1,1,200,,,,,,,,
SELL,2023-03-01,FAKEA,,FAKE-WALLET-1,2.5,300,,3,,,,,,
""")
        self.assertEqual(events(r), [
            ("FAKEA", "2", "LT", "597.60", "202.00", "395.60"),
            ("FAKEA", "0.5", "ST", "149.40", "100.00", "49.40"),
        ])
        self.assertEqual(r["years"]["2023"]["net_gain"], "445.00")
        self.assertEqual(r["remaining_lots"][0]["quantity"], "0.5")
        self.assertEqual(r["remaining_lots"][0]["basis"], "100.00")

    def test_T4_no_matching_future_lots(self):
        r = run("""
BUY,2023-02-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
SELL,2023-01-01,FAKEA,,FAKE-WALLET-1,1,150,,,,,,,,
""")
        # Unmatched units: one short-term event, $0 basis, full proceeds.
        self.assertEqual(events(r), [("FAKEA", "1", "ST", "150.00", "0.00", "150.00")])
        self.assertTrue(r["events"][0]["unmatched"])
        self.assertIsNone(r["events"][0]["acquired"])
        self.assertEqual(r["years"]["2023"]["short_term"]["gain"], "150.00")
        self.assertEqual(kinds(r), ["InsufficientLots"])
        self.assertEqual(r["issues"][0]["short_by"], "1")
        self.assertEqual(r["remaining_lots"][0]["quantity"], "1")

    def test_T5_per_wallet_fifo_from_2025(self):
        r = run("""
OPENING,2025-01-01,FAKEA,,FAKE-EXCHANGE-1,1,,10000,,,,,2020-01-01,,
OPENING,2025-01-01,FAKEA,,FAKE-WALLET-1,1,,15000,,,,,2018-01-01,,
SELL,2025-06-01,FAKEA,,FAKE-EXCHANGE-1,1,60000,,,,,,,,
""")
        self.assertEqual(events(r), [("FAKEA", "1", "LT", "60000.00", "10000.00", "50000.00")])
        self.assertEqual(kinds(r), [])  # no pre-2025 records for FAKEA -> no mismatch warning

    def test_T6_transfer_keeps_original_date(self):
        r = run("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,2,3000,,,,,,2024-11-01,,
TRANSFER,2025-03-01,FAKEB,,FAKE-WALLET-1,1,,,,,,FAKE-EXCHANGE-1,,,
SELL,2025-06-01,FAKEB,,FAKE-EXCHANGE-1,1,4000,,,,,,,,
""")
        self.assertEqual(events(r), [("FAKEB", "1", "ST", "4000.00", "3000.00", "1000.00")])
        left = [(l["wallet"], l["quantity"]) for l in r["remaining_lots"]]
        self.assertEqual(left, [("FAKE-WALLET-1", "1")])
        self.assertEqual(kinds(r), [])

    def test_T7_swap_transfer_sell(self):
        r = run("""
BUY,2023-01-05,FAKEB,,FAKE-WALLET-2,10,20,,,,,,,,
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-2,10,20,,,,,,2023-01-05,,
SWAP,2025-03-10,FAKEB,,FAKE-WALLET-2,10,,1500,,FAKEUSD,1500,,,,
TRANSFER,2025-03-12,FAKEUSD,,FAKE-WALLET-2,1500,,,,,,FAKE-EXCHANGE-1,,,
SELL,2025-03-15,FAKEUSD,,FAKE-EXCHANGE-1,1500,1.00,,,,,,,,
""")
        self.assertEqual(events(r), [
            ("FAKEB", "10", "LT", "1500.00", "200.00", "1300.00"),
            ("FAKEUSD", "1500", "ST", "1500.00", "1500.00", "0.00"),
        ])
        self.assertEqual(kinds(r), [])

    def test_T8_income_creates_basis(self):
        r = run("""
INCOME,2025-02-01,FAKEB,,FAKE-EXCHANGE-1,0.5,100,,,,,,,,
SELL,2025-04-01,FAKEB,,FAKE-EXCHANGE-1,0.5,120,,,,,,,,
""")
        self.assertEqual(r["years"]["2025"]["ordinary_income"], "50.00")
        self.assertEqual(events(r), [("FAKEB", "0.5", "ST", "60.00", "50.00", "10.00")])

    def test_T9_adjustment_has_no_tax_effect(self):
        r = run("""
BUY,2023-01-01,FAKEB,,FAKE-WALLET-1,1,1000,,,,,,,,
ADJUST,2023-06-01,FAKEB,,FAKE-WALLET-1,-0.001,,,,,,,,,
SELL,2024-06-01,FAKEB,,FAKE-WALLET-1,0.999,2000,,,,,,,,
""")
        self.assertEqual(events(r), [("FAKEB", "0.999", "LT", "1998.00", "999.00", "999.00")])
        self.assertEqual(r["adjusted_away_basis"], "1.00")

    def test_T10_unknown_basis(self):
        r = run("""
BUY,2022-07-15,FAKEB,,FAKE-WALLET-1,1.5,0,,,,,,,true,
SELL,2024-03-01,FAKEB,,FAKE-WALLET-1,1.5,100,,,,,,,,
""")
        self.assertEqual(events(r), [("FAKEB", "1.5", "LT", "150.00", "0.00", "150.00")])
        self.assertEqual(kinds(r), [])
        r = run("""
BUY,2022-07-15,FAKEB,,FAKE-WALLET-1,1.5,0,,,,,,,,
SELL,2024-03-01,FAKEB,,FAKE-WALLET-1,1.5,100,,,,,,,,
""")
        self.assertEqual(kinds(r), ["ZeroPriceBuy"])

    def test_T11_transition_mismatch(self):
        r = run("""
BUY,2023-05-01,FAKEB,,FAKE-WALLET-1,5,100,,,,,,,,
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,4.99,100,,,,,,2023-05-01,,
""")
        self.assertEqual(r["issues"], [{"kind": "TransitionMismatch", "line": None, "date": "2025-01-01",
                                        "asset": "FAKEB", "wallet": None, "expected": "5", "opening": "4.99"}])
        self.assertEqual([l["quantity"] for l in r["remaining_lots"]], ["4.99"])

    def test_T12_year_totals_and_netting(self):
        r = run("""
BUY,2022-01-01,FAKEB,,FAKE-WALLET-1,1,1000,,,,,,,,
BUY,2024-01-10,FAKEA,,FAKE-WALLET-1,1,1400,,,,,,,,
SELL,2024-02-01,FAKEA,,FAKE-WALLET-1,1,1000,,,,,,,,
SELL,2024-03-01,FAKEB,,FAKE-WALLET-1,1,2000,,,,,,,,
BUY,2021-01-01,FAKEC,,FAKE-WALLET-1,1,1000,,,,,,,,
BUY,2023-01-10,FAKED,,FAKE-WALLET-1,1,6000,,,,,,,,
SELL,2023-02-01,FAKED,,FAKE-WALLET-1,1,1000,,,,,,,,
SELL,2023-03-01,FAKEC,,FAKE-WALLET-1,1,2000,,,,,,,,
""")
        y24, y23 = r["years"]["2024"], r["years"]["2023"]
        self.assertEqual((y24["short_term"]["gain"], y24["long_term"]["gain"], y24["net_gain"]),
                         ("-400.00", "1000.00", "600.00"))
        self.assertFalse(y24["loss_limit_note"])
        self.assertEqual((y23["short_term"]["gain"], y23["long_term"]["gain"], y23["net_gain"]),
                         ("-5000.00", "1000.00", "-4000.00"))
        self.assertTrue(y23["loss_limit_note"])


class TransferFees(unittest.TestCase):
    """R13: the coins spent on a transfer's network fee are a disposal at fee_usd."""

    def test_T13_transfer_fee_in_the_same_coin(self):
        r = run_fees("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,1,,2000,,,,,,,2024-01-10,,
TRANSFER,2025-04-01,FAKEB,,FAKE-WALLET-1,0.99,,,30.00,,0.01,,,FAKE-EXCHANGE-1,,,
SELL,2025-06-01,FAKEB,,FAKE-EXCHANGE-1,0.99,3100,,,,,,,,,,
""")
        self.assertEqual(events(r), [
            ("FAKEB", "0.01", "LT", "30.00", "20.00", "10.00"),
            ("FAKEB", "0.99", "LT", "3069.00", "1980.00", "1089.00"),
        ])
        self.assertEqual([e["kind"] for e in r["events"]], ["transfer_fee", "disposal"])
        self.assertEqual((r["events"][0]["wallet"], r["events"][0]["acquired"]), ("FAKE-WALLET-1", "2024-01-10"))
        self.assertEqual(r["years"]["2025"]["long_term"]["gain"], "1099.00")
        self.assertEqual(r["remaining_lots"], [])
        self.assertEqual(kinds(r), [])

    def test_T13_moved_units_keep_date_and_basis(self):
        r = run_fees("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,1,,2000,,,,,,,2024-01-10,,
TRANSFER,2025-04-01,FAKEB,,FAKE-WALLET-1,0.99,,,30.00,,0.01,,,FAKE-EXCHANGE-1,,,
""")
        self.assertEqual([(l["wallet"], l["quantity"], l["basis"], l["acquired"]) for l in r["remaining_lots"]],
                         [("FAKE-EXCHANGE-1", "0.99", "1980.00", "2024-01-10")])

    def test_T14_fee_in_another_coin_before_2025_across_two_lots(self):
        r = run_fees("""
BUY,2023-01-01,FAKEB,,FAKE-WALLET-1,0.004,1000,,,,,,,,,,
BUY,2023-11-01,FAKEB,,FAKE-WALLET-1,1,2000,,,,,,,,,,
BUY,2023-01-01,FAKEUSD,,FAKE-WALLET-1,100,1.00,,,,,,,,,,
TRANSFER,2024-03-01,FAKEUSD,,FAKE-WALLET-1,100,,,15.00,FAKEB,0.005,,,FAKE-EXCHANGE-1,,,
""")
        self.assertEqual(events(r), [
            ("FAKEB", "0.004", "LT", "12.00", "4.00", "8.00"),
            ("FAKEB", "0.001", "ST", "3.00", "2.00", "1.00"),
        ])
        self.assertEqual({e["scope"] for e in r["events"]}, {"universal"})
        y = r["years"]["2024"]
        self.assertEqual((y["short_term"]["gain"], y["long_term"]["gain"]), ("1.00", "8.00"))
        left = {l["asset"]: (l["quantity"], l["basis"]) for l in r["remaining_lots"]}
        self.assertEqual(left, {"FAKEB": ("0.999", "1998.00"), "FAKEUSD": ("100", "100.00")})
        self.assertEqual(kinds(r), [])

    def test_fee_is_taken_before_the_moved_units(self):
        # Oldest lot pays the fee; the moved units come from what's left.
        r = run_fees("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,0.01,,10,,,,,,,2022-01-01,,
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,1,,2000,,,,,,,2024-06-01,,
TRANSFER,2025-03-01,FAKEB,,FAKE-WALLET-1,0.5,,,25.00,,0.01,,,FAKE-EXCHANGE-1,,,
""")
        self.assertEqual(events(r), [("FAKEB", "0.01", "LT", "25.00", "10.00", "15.00")])
        moved = [(l["quantity"], l["acquired"]) for l in r["remaining_lots"] if l["wallet"] == "FAKE-EXCHANGE-1"]
        self.assertEqual(moved, [("0.5", "2024-06-01")])

    def test_fee_shortfall_gets_a_zero_basis_slice(self):
        r = run_fees("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,0.004,,8,,,,,,,2023-01-01,,
TRANSFER,2025-03-01,FAKEUSD,,FAKE-WALLET-1,50,,,20.00,FAKEB,0.01,,,FAKE-EXCHANGE-1,,,
""")
        self.assertEqual(events(r), [
            ("FAKEB", "0.004", "LT", "8.00", "8.00", "0.00"),
            ("FAKEB", "0.006", "ST", "12.00", "0.00", "12.00"),
        ])
        self.assertEqual([e["unmatched"] for e in r["events"]], [False, True])
        # The fee shortfall and the moved units' shortfall (no FAKEUSD) are both reported.
        self.assertEqual([(i["kind"], i["asset"], i["short_by"]) for i in r["issues"]],
                         [("InsufficientLots", "FAKEB", "0.006"), ("InsufficientLots", "FAKEUSD", "50")])

    def test_invalid_fee_rows(self):
        rows = [
            "TRANSFER,2025-03-01,FAKEB,,FAKE-WALLET-1,1,,,,,0.01,,,FAKE-EXCHANGE-1,,,",    # fee_quantity, no fee_usd
            "TRANSFER,2025-03-01,FAKEB,,FAKE-WALLET-1,1,,,5.00,,,,,FAKE-EXCHANGE-1,,,",    # fee_usd, no fee_quantity
            "TRANSFER,2025-03-01,FAKEB,,FAKE-WALLET-1,1,,,,FAKEA,,,,FAKE-EXCHANGE-1,,,",   # fee_asset, no fee_quantity
            "TRANSFER,2025-03-01,FAKEB,,FAKE-WALLET-1,1,,,5.00,,0,,,FAKE-EXCHANGE-1,,,",   # fee_quantity <= 0
            "SELL,2025-03-01,FAKEB,,FAKE-WALLET-1,1,100,,,,0.01,,,,,,",                    # fee_quantity on non-TRANSFER
            "BUY,2025-03-01,FAKEB,,FAKE-WALLET-1,1,100,,,FAKEA,,,,,,,",                    # fee_asset on non-TRANSFER
            "INCOME,2025-03-01,FAKEB,,FAKE-WALLET-1,1,100,,2.00,,,,,,,,",                  # fee_usd on INCOME
        ]
        for row in rows:
            self.assertEqual(kinds(run_fees(row)), ["InvalidRow"], row)

    def test_zero_fee_usd_alone_is_no_fee(self):
        r = run_fees("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,1,,100,,,,,,,2023-01-01,,
TRANSFER,2025-03-01,FAKEB,,FAKE-WALLET-1,1,,,0,,,,,FAKE-EXCHANGE-1,,,
""")
        self.assertEqual((events(r), kinds(r)), ([], []))


class RuleChanges(unittest.TestCase):
    def test_partial_shortfall_splits_proceeds_and_fee(self):
        r = run("""
BUY,2023-01-01,FAKEB,,FAKE-WALLET-1,1,100,,,,,,,,
SELL,2023-06-01,FAKEB,,FAKE-WALLET-1,1.5,200,,3,,,,,,
""")
        # net proceeds 297: matched 1 unit gets 198.00, unmatched 0.5 gets 99.00 at $0 basis
        self.assertEqual(events(r), [
            ("FAKEB", "1", "ST", "198.00", "100.00", "98.00"),
            ("FAKEB", "0.5", "ST", "99.00", "0.00", "99.00"),
        ])
        self.assertEqual([e["unmatched"] for e in r["events"]], [False, True])
        self.assertEqual(r["issues"][0]["short_by"], "0.5")

    def test_transfer_and_adjust_shortfalls_create_no_events(self):
        r = run("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,1,100,,,,,,2023-01-01,,
TRANSFER,2025-02-01,FAKEB,,FAKE-WALLET-1,2,,,,,,FAKE-EXCHANGE-1,,,
ADJUST,2025-03-01,FAKEB,,FAKE-WALLET-1,-0.5,,,,,,,,,
""")
        self.assertEqual(events(r), [])
        self.assertEqual(kinds(r), ["InsufficientLots", "InsufficientLots"])

    def test_mismatch_when_pre2025_history_fully_sold(self):
        r = run("""
BUY,2023-01-01,FAKEB,,FAKE-WALLET-1,1,100,,,,,,,,
SELL,2024-01-01,FAKEB,,FAKE-WALLET-1,1,150,,,,,,,,
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,2,100,,,,,,2023-06-01,,
""")
        self.assertEqual(kinds(r), ["TransitionMismatch"])
        self.assertEqual((r["issues"][0]["expected"], r["issues"][0]["opening"]), ("0", "2"))

    def test_swap_target_counts_as_pre2025_history(self):
        r = run("""
BUY,2023-01-01,FAKEB,,FAKE-WALLET-1,1,100,,,,,,,,
SWAP,2023-02-01,FAKEB,,FAKE-WALLET-1,1,,120,,FAKEUSD,120,,,,
OPENING,2025-01-01,FAKEUSD,,FAKE-WALLET-1,100,1,,,,,,2023-02-01,,
""")
        self.assertEqual(kinds(r), ["TransitionMismatch"])


class SameDayOrder(unittest.TestCase):
    """R3: acquisitions, then TRANSFER/SWAP in input order, then ADJUST/SELL in input order."""

    def test_buy_after_sell_in_file_still_comes_first(self):
        r = run("""
SELL,2023-05-01,FAKEA,,FAKE-WALLET-1,1,150,,,,,,,,
BUY,2023-05-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
""")
        self.assertEqual(events(r), [("FAKEA", "1", "ST", "150.00", "100.00", "50.00")])
        self.assertEqual(kinds(r), [])

    def test_swap_then_transfer_keeps_input_order(self):
        r = run("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-2,10,,200,,,,,2023-01-05,,
SWAP,2025-03-10,FAKEB,,FAKE-WALLET-2,10,,1500,,FAKEUSD,1500,,,,
TRANSFER,2025-03-10,FAKEUSD,,FAKE-WALLET-2,1500,,,,,,FAKE-EXCHANGE-1,,,
SELL,2025-03-10,FAKEUSD,,FAKE-EXCHANGE-1,1500,1.00,,,,,,,,
""")
        self.assertEqual(kinds(r), [])
        self.assertEqual([e["asset"] for e in r["events"]], ["FAKEB", "FAKEUSD"])

    def test_transfer_before_swap_in_file_runs_first(self):
        # TRANSFER and SWAP share a phase, so the file order decides: the transfer finds nothing yet.
        r = run("""
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-2,10,,200,,,,,2023-01-05,,
TRANSFER,2025-03-10,FAKEUSD,,FAKE-WALLET-2,1500,,,,,,FAKE-EXCHANGE-1,,,
SWAP,2025-03-10,FAKEB,,FAKE-WALLET-2,10,,1500,,FAKEUSD,1500,,,,
""")
        self.assertEqual(kinds(r), ["InsufficientLots"])

    def test_sell_before_adjust_in_file_keeps_input_order(self):
        # ADJUST and SELL share a phase: the sell takes the whole lot, the adjustment comes up short.
        r = run("""
BUY,2023-01-01,FAKEB,,FAKE-WALLET-1,1,1000,,,,,,,,
SELL,2023-06-01,FAKEB,,FAKE-WALLET-1,1,2000,,,,,,,,
ADJUST,2023-06-01,FAKEB,,FAKE-WALLET-1,-0.001,,,,,,,,,
""")
        self.assertEqual(events(r), [("FAKEB", "1", "ST", "2000.00", "1000.00", "1000.00")])
        self.assertEqual(kinds(r), ["InsufficientLots"])
        self.assertEqual(r["adjusted_away_basis"], "0.00")

    def test_unmatched_units_are_marked_unknown_basis(self):
        r = run("SELL,2023-01-01,FAKEA,,FAKE-WALLET-1,1,150,,,,,,,,")
        self.assertTrue(r["events"][0]["basis_unknown"])
        self.assertIsNone(r["events"][0]["lot_line"])

class Parsing(unittest.TestCase):
    def test_comment_lines_are_skipped_and_line_numbers_stay_physical(self):
        text = ("# BlockGains template\n" + HEADER
                + "# BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,99,1,,,,,,,,\n"
                + "BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,\n"
                + "  # indented comment, still skipped\n"
                + "BUY,2023-13-45,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,\n"
                + "SELL,2024-06-01,FAKEA,,FAKE-WALLET-1,1,250,,,,,,,,\n")
        r = calculate(text, "FAKE-WALLET-1")
        self.assertEqual(kinds(r), ["InvalidRow"])
        self.assertEqual(r["issues"][0]["line"], 6)
        self.assertEqual(events(r), [("FAKEA", "1", "LT", "250.00", "100.00", "150.00")])

    def test_bad_rows_become_issues_and_others_still_count(self):
        r = run("""
BUY,2023-13-45,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,"$1,000.50",,,,,,,,
SELL,2024-06-01,FAKEA,,FAKE-WALLET-1,1,2000,,,,,,,,
""")
        self.assertEqual(kinds(r), ["InvalidRow"])
        self.assertEqual(r["issues"][0]["line"], 2)
        self.assertEqual(events(r)[0][4], "1000.50")

    def test_mixed_date_formats(self):
        r = run("""
BUY,9/4/2021,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
SELL,2022/09/05,FAKEA,,FAKE-WALLET-1,1,200,,,,,,,,
""")
        self.assertEqual(events(r)[0][2], "LT")

    def test_price_and_total_must_agree(self):
        r = run("BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,2,100,250,,,,,,,")
        self.assertEqual(kinds(r), ["InvalidRow"])

    def test_opening_must_be_dated_on_the_transition(self):
        r = run("""
BUY,2023-05-01,FAKEB,,FAKE-WALLET-1,5,100,,,,,,,,
OPENING,2024-12-31,FAKEB,,FAKE-WALLET-1,5,100,,,,,,2023-05-01,,
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,1,100,,,,,,2025-02-01,,
""")
        self.assertEqual(kinds(r), ["InvalidRow", "InvalidRow"])
        # Both rows are dropped: no mismatch, and the 5 bought units are still held.
        self.assertEqual([l["quantity"] for l in r["remaining_lots"]], ["5"])

    def test_default_wallet_assignment_without_openings(self):
        r = run("""
BUY,2023-01-01,FAKEA,,FAKE-EXCHANGE-1,1,100,,,,,,,,
SELL,2025-06-01,FAKEA,,FAKE-WALLET-9,1,200,,,,,,,,
""", default_wallet="FAKE-WALLET-9")
        self.assertEqual(events(r)[0][5], "100.00")
        self.assertEqual(kinds(r), [])


class ImportRules(unittest.TestCase):
    """import-format-spec section 2, strict parsing (1.8.1): the app's rules, matched here."""

    def test_malformed_value_in_an_unused_column_rejects_the_row(self):
        for row in ("BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,2023-13-45,,",
                    "BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,100,,,,abc,,,,",
                    "SELL,2023-01-01,FAKEA,,FAKE-WALLET-1,1,100,,,,\"1,5\",,,,"):
            self.assertEqual(kinds(run(row)), ["InvalidRow"], row)

    def test_valid_values_in_unused_columns_are_ignored(self):
        r = run("""
BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,100,,,,5,FAKE-WALLET-2,2020-01-01,,
SELL,2023-06-01,FAKEA,,FAKE-WALLET-1,1,150,,,,,,,,
""")
        self.assertEqual(kinds(r), [])
        self.assertEqual(r["events"][0]["acquired"], "2023-01-01")

    def test_stray_to_asset_before_2025_is_not_history(self):
        r = run("""
BUY,2024-03-01,FAKEA,,FAKE-WALLET-1,1,100,,,FAKEB,,,,,
OPENING,2025-01-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,2024-03-01,,
OPENING,2025-01-01,FAKEB,,FAKE-WALLET-1,5,10,,,,,,2024-03-01,,
""")
        self.assertEqual(kinds(r), [])

    def test_asset_class(self):
        self.assertEqual(kinds(run("BUY,2023-01-01,FAKEA,stock,FAKE-WALLET-1,1,100,,,,,,,,")), [])
        self.assertEqual(kinds(run("BUY,2023-01-01,FAKEA,BOND,FAKE-WALLET-1,1,100,,,,,,,,")), ["InvalidRow"])

    def test_basis_unknown_values(self):
        for value in ("true", "TRUE", "yes", "Y", "1"):
            self.assertEqual(kinds(run(f"BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,0,,,,,,,{value},")), [], value)
        for value in ("", "false", "No", "n", "0"):
            self.assertEqual(kinds(run(f"BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,0,,,,,,,{value},")), ["ZeroPriceBuy"], value)
        self.assertEqual(kinds(run("BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,0,,,,,,,maybe,")), ["InvalidRow"])

    def test_swap_to_its_own_asset(self):
        r = run("""
BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
SWAP,2023-02-01,FAKEA,,FAKE-WALLET-1,1,,150,,fakea,1,,,,
""")
        self.assertEqual(kinds(r), ["InvalidRow"])
        self.assertEqual(r["events"], [])

    def test_number_grouping(self):
        for value in ('"1,5"', '"12,34"', '"1,234E2"', '"1,23,456"', "1.2.3"):
            self.assertEqual(kinds(run(f"BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,{value},,,,,,,,")), ["InvalidRow"], value)
        r = run("""
BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,"1,000",$1.5e-1,,,,,,,,
SELL,2023-06-01,FAKEA,,FAKE-WALLET-1,1000,,"$1,234.50",,,,,,,
""")
        self.assertEqual(events(r), [("FAKEA", "1000", "ST", "1234.50", "150.00", "1084.50")])

    def test_unicode_minus(self):  # the quantities below use U+2212, not an ASCII hyphen
        r = run("""
BUY,2023-01-01,FAKEA,,FAKE-WALLET-1,1,100,,,,,,,,
ADJUST,2023-02-01,FAKEA,,FAKE-WALLET-1,−0.25,,,,,,,,,
ADJUST,2023-02-02,FAKEA,,FAKE-WALLET-1,$−0.25,,,,,,,,,
""")
        self.assertEqual(kinds(r), [])
        self.assertEqual([l["quantity"] for l in r["remaining_lots"]], ["0.5"])


if __name__ == "__main__":
    unittest.main()
