"""Checks tools/legacy_to_unified.py (legacy buys/sells CSV -> unified CSV).

    python -m unittest test_legacy_to_unified -v
"""
import contextlib
import csv
import io
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))  # tools/

import legacy_to_unified  # noqa: E402

TESTDATA = os.path.join(HERE, "testdata")
# What the app's JVM test (BuysSellsFixtureTest) imports with no issues and compares with buys-sells.csv.
GOLDEN = os.path.join(HERE, "..", "..", "app", "src", "test", "resources", "fixtures", "converted-from-legacy.csv")


class ConverterTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def write(self, name: str, text: str) -> str:
        path = os.path.join(self.tmp.name, name)
        with open(path, "w", encoding="utf-8", newline="") as handle:
            handle.write(text)
        return path

    def convert(self, *args: str) -> tuple[int, str, str, str]:
        """(exit code, output CSV text, stdout, stderr)."""
        out = os.path.join(self.tmp.name, "out.csv")
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            code = legacy_to_unified.main([*args, "-o", out])
        text = ""
        if os.path.exists(out):
            with open(out, encoding="utf-8", newline="") as handle:
                text = handle.read()
        return code, text, stdout.getvalue(), stderr.getvalue()

    def rows(self, text: str) -> list[dict]:
        return list(csv.DictReader(io.StringIO(text)))

    def test_legacyFixture_matchesTheGoldenFileTheAppImports(self):
        code, text, stdout, stderr = self.convert(
            "--buys", os.path.join(TESTDATA, "legacy-buys.csv"), "--sells", os.path.join(TESTDATA, "legacy-sells.csv"),
            "--asset", "fakeb", "--wallet", "FAKE-WALLET-1")
        self.assertEqual(code, 0)
        with open(GOLDEN, encoding="utf-8") as handle:  # universal newlines: git may check it out with CRLF
            self.assertEqual(text, handle.read())
        self.assertIn("legacy-buys.csv line 7: skipped (bad date", stderr)
        self.assertIn("legacy-buys.csv line 8: skipped (Quantity is blank)", stderr)
        self.assertIn("Wrote 7 rows", stdout)
        self.assertIn("1 $0 buy(s)", stdout)

    def test_fullUnifiedHeader_isoDates_exactNumbers(self):
        buys = self.write("b.csv", " DATE BOUGHT ,quantity,Purchase Price Per Unit,extra\n"
                                   '1/2/2024,0.000000001,"$1,234,567.891234567",x\n'
                                   "2024/3/4,1.2E-05,$0.10,\n")
        code, text, _, _ = self.convert("--buys", buys, "--asset", "FAKEA", "--wallet", "FAKE-WALLET-2",
                                        "--asset-class", "stock")
        self.assertEqual(code, 0)
        self.assertEqual(text.splitlines()[0], ",".join(legacy_to_unified.UNIFIED_HEADER))
        first, second = self.rows(text)
        self.assertEqual((first["type"], first["date"], first["asset_class"], first["wallet"]),
                         ("BUY", "2024-01-02", "STOCK", "FAKE-WALLET-2"))
        self.assertEqual((first["quantity"], first["price_usd"]), ("0.000000001", "1234567.891234567"))
        self.assertEqual((second["date"], second["quantity"], second["price_usd"]), ("2024-03-04", "1.2E-05", "0.10"))
        for row in (first, second):
            self.assertEqual({k for k, v in row.items() if v},
                             {"type", "date", "asset", "asset_class", "wallet", "quantity", "price_usd"})

    def test_sellsAlone_andZeroBuyStaysZero(self):
        sells = self.write("s.csv", "Date sold,Quantity Sold,Sale price per unit\n12/31/2024,2,5\n")
        code, text, _, _ = self.convert("--sells", sells, "--asset", "FAKEB", "--wallet", "FAKE-WALLET-1")
        self.assertEqual(code, 0)
        self.assertEqual([(r["type"], r["price_usd"]) for r in self.rows(text)], [("SELL", "5")])

        buys = self.write("b.csv", "Date bought,Quantity,Purchase price per unit\n1/1/2024,1,$0.00\n")
        _, text, _, _ = self.convert("--buys", buys, "--asset", "FAKEB", "--wallet", "FAKE-WALLET-1")
        row = self.rows(text)[0]
        self.assertEqual((row["price_usd"], row["basis_unknown"], row["fee_usd"]), ("0.00", "", ""))

    def test_badRows_areSkippedWithFileAndLine(self):
        buys = self.write("b.csv", "Date bought,Quantity,Purchase price per unit\n"
                                   "1/1/2024,\"1,5\",10\n"      # line 2: bad grouping, never read as 15
                                   "2/30/2024,1,10\n"           # line 3: no such date
                                   "1/1/2024,-1,10\n"           # line 4: negative quantity
                                   "1/1/2024,0,10\n"            # line 5: zero quantity
                                   "1/1/2024,1,$-10\n"          # line 6: negative price
                                   "1/1/2024,1,10\n")
        code, text, _, stderr = self.convert("--buys", buys, "--asset", "FAKEB", "--wallet", "FAKE-WALLET-1")
        self.assertEqual(code, 0)
        self.assertEqual(len(self.rows(text)), 1)
        for line in (2, 3, 4, 5, 6):
            self.assertIn(f"b.csv line {line}: skipped", stderr)

    def test_wrongHeader_stopsWithoutWriting(self):
        swapped = self.write("s.csv", "Date sold,Quantity Sold,Sale price per unit\n12/8/2023,1,2\n")
        with self.assertRaises(SystemExit) as raised, contextlib.redirect_stderr(io.StringIO()):
            self.convert("--buys", swapped, "--asset", "FAKEB", "--wallet", "FAKE-WALLET-1")
        self.assertIn("missing columns", str(raised.exception.code))
        self.assertFalse(os.path.exists(os.path.join(self.tmp.name, "out.csv")))

    def test_needsBuysOrSells(self):
        with self.assertRaises(SystemExit), contextlib.redirect_stderr(io.StringIO()):
            self.convert("--asset", "FAKEB", "--wallet", "FAKE-WALLET-1")


if __name__ == "__main__":
    unittest.main()
