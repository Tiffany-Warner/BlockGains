# Tax Rules

Source of truth for every rule the calculator applies. US federal only. Rules were last checked **2026-11-22**. Re-check the "Watch list" items before each tax season.

> This app is an estimator for personal records, not tax advice. Results should be checked against broker forms (1099-DA / 1099-B) and a tax professional.

Each rule has an ID (`R#`). Code comments should reference rule IDs (e.g. `// R2: holding period`), and each worked case (`T#`) becomes a unit test of the same name.

---

## R1. Crypto is property, so every disposal is a capital event
Selling crypto for USD, swapping it for another crypto, or spending it is a disposal. Gain/loss = amount realized − adjusted basis.
- Source: IRS virtual currency FAQ: exchanging virtual currency for other property, including another virtual currency, recognizes a capital gain or loss. <https://www.irs.gov/individuals/international-taxpayers/frequently-asked-questions-on-virtual-currency-transactions>

## R2. Holding period: long-term = held MORE than one year
The holding period starts the **day after** acquisition. Exactly one year is short-term.
- **Implementation:** `isLongTerm = sellDate.isAfter(acquiredDate.plusYears(1))`. Do **not** use `daysBetween > 365`, because that's wrong across leap years and on anniversaries (the bug in v1).
- `LocalDate.plusYears(1)` maps Feb 29 → Feb 28 of the next year, so a lot bought 2024‑02‑29 becomes long-term when sold on 2025‑03‑01 or later.
- Source: 26 U.S.C. §1222 ("held for more than 1 year"); IRS FAQ A47; standard practice of counting from the day after purchase.

## R3. Lot matching: FIFO, scoped by period
- **Disposals before 2025‑01‑01:** FIFO per **asset**, across all wallets (one "universal" pool).
- **Disposals on/after 2025‑01‑01:** FIFO per **(asset, wallet)**. Starting in 2025 the IRS requires wallet-by-wallet / account-by-account basis tracking. FIFO means the earliest **original** acquired date among the units in that wallet (Treas. Reg. §1.1012‑1(j)(3)(i)): transferred-in lots keep their original acquired date and sort by it in the destination pool, not by arrival date.
- **Pre‑2025 ADJUST rows** remove units from the universal pool (R9).
- A disposal may only match lots acquired **on or before** the disposal date. If there isn't enough quantity, do not match a later lot. Report an `InsufficientLots` issue (see R10), and the unmatched units of a SELL or SWAP, or of a TRANSFER's network fee (R13), become their own lot slice: **$0 basis**, marked unknown basis, **short-term**, with their pro-rata share of proceeds and fee (decided under Open questions: partial shortfall). Their acquired date is shown and exported as "Unknown".
- **TRANSFER and ADJUST shortfalls** report `InsufficientLots` and create no event (a transfer isn't taxable; an adjustment has no gain or loss). Only SELL and SWAP get the $0-basis slice.
- Same-day ordering, when there's no time column: acquisitions (OPENING, BUY, INCOME) first, then TRANSFER and SWAP, then disposals (ADJUST, SELL). Within each group, rows keep input order (the order they were imported), so ADJUST and SELL also stay in file order. A SWAP is processed as one step at its place in that order. Rows imported later come after rows already stored for the same date.
  - **Known non-conservative edge:** acquisitions-first can let a same-day sale use a buy that really happened later that day, which can hide a shortfall (gain near $0 instead of option B's $0-basis gain). The Phase 2 time column fixes this.
- FIFO is the only method in v2. Other methods (HIFO, specific ID) are a later phase and must be designed so the matching strategy is pluggable.
- **FIFO is only the default.** For 2025–2026, specific identification or a standing order recorded in the owner's own books and records (Notice 2025‑07 §4.02, extended through 2026‑12‑31 by Notice 2026‑20), or given to the broker, overrides it. Self-custody wallets always use books and records. The app doesn't model this: if a broker standing order (e.g. HIFO) or specific identification exists, the app's results won't match the tax numbers.
- Source: Rev. Proc. 2024‑28 and Treas. Reg. §1.1012‑1(j) (wallet-by-wallet from 2025). <https://www.irs.gov/pub/irs-drop/rp-24-28.pdf>

## R4. The 2025 transition: opening positions
Per-wallet pools start from a snapshot of holdings on **2025‑01‑01**.
- An `OPENING` record = (asset, wallet, quantity, **original acquired date**, **original cost basis**). It keeps the original date (for R2) and basis.
- At 2025‑01‑01 the calculator **replaces** the universal pool's remaining lots for an asset with that asset's OPENING records.
- If an asset has remaining universal lots but **no** OPENING records, assign all of them, unchanged, to the user's **default wallet** (a setting).
- If an asset has records dated **before 2025‑01‑01** (as the record's asset, or as the asset received in a SWAP) and its OPENING quantity differs from the universal remainder, surface a `TransitionMismatch` issue showing both numbers. Don't fail. OPENING records for an asset with no earlier records don't warn: the user's history simply starts there.
- Pre‑2025 transfers between one's own wallets are **not** needed and are ignored for matching.
- Being the coin that paid a transfer's network fee (R13) doesn't count as a record before 2025‑01‑01 for the `TransitionMismatch` check above. An asset with no other pre‑2025 record has nothing to dispose of the fee from, so the fee is already reported as an `InsufficientLots` shortfall.
- `TransitionMismatch` checks **totals** only, not per-wallet counts. The default-wallet fallback above is only correct if those units really sat in the default wallet on 2025‑01‑01.
- Rev. Proc. 2024‑28 facts (for the record; both deadlines have passed):
  - A **specific unit allocation** had to be complete before the earlier of the first sale, disposition or transfer of that asset on/after 2025‑01‑01, or the 2025 return due date including extensions (§5.02(4)).
  - A **global allocation** method had to be written down before 2025‑01‑01 (§5.02(5)(a)).
  - Units must be allocated to wallets holding **the same number of units** they actually held on 2025‑01‑01 (§5.01, §5.02(2)), so each wallet's OPENING quantity must match what it really held, not just the total.
  - Allocation can't change basis amounts or acquisition dates (§3.04). The app keeps both. The safe harbor doesn't prove the basis itself; that still needs records (§5.04).
- Whether a formal Rev. Proc. 2024‑28 safe-harbor allocation was made, and whether the OPENING records count as one, is a filing question for a tax preparer, not something the app decides.

## R5. Transfers between own wallets are not taxable
A `TRANSFER` moves lots from one wallet's pool to another's. It keeps each lot's original acquired date and basis, and moves lots in FIFO order from the source wallet. No gain or loss.
- Only affects matching for 2025+ (per R3).
- Source: IRS digital assets page: transfers between wallets you own or control aren't a taxable transaction.
- A TRANSFER shortfall reports `InsufficientLots` and creates no event (R3).
- **Network fees** paid in coins on a transfer: see R13.

## R6. Swaps (crypto → crypto)
A `SWAP` of asset A for asset B on date D in wallet W, where `V` is the USD fair market value of the asset received (B):
1. A disposal of A: amount realized = `V` − (fee allocated to the disposal, if any), matched by R3.
2. An acquisition of B: new lot with basis = `V`, acquired date = D, in wallet W.
- `V` is supplied by the user (no price API). If it's missing, the row is invalid.
- **Fees on a swap:**
  - A fee paid in the coin being swapped: include those units in `quantity` and leave `fee_usd` blank (IRS FAQ 97/99: the fee units are disposed of and their value is a cost of the swap, so they net out).
  - A fee withheld from the coin received: enter what arrived and its value, with `fee_usd` blank (IRS FAQ 98/72: no gain or loss on the withheld units, and a swap's costs aren't added to the received coin's basis).
  - `fee_usd` is for a fee paid separately, for example in USD (R7).
  - A fee paid in a third coin isn't modeled yet (see Open questions: swap fee in a third coin).
- Source: IRS FAQ (basis of property received = its FMV at the time of the exchange). Taxpayer Advocate example: BTC bought for $50,000 and exchanged for crypto worth $40,000 → $10,000 short-term loss.

## R7. Fees
- Fees are optional (default 0), in USD.
- Buy fee → added to that lot's basis.
- Sell/swap-out fee → subtracted from amount realized. When one disposal matches several lots, allocate the fee **pro rata by quantity** across the matched lots.
- Skipping fees overstates the gain (conservative), which is acceptable for historical data.
- Source: IRS digital asset FAQ: amount realized is reduced by digital asset transaction costs allocable to the disposition.

## R8. Staking / reward income
An `INCOME` record (staking reward, airdrop, interest) = ordinary income equal to its USD FMV on the date received. The same value becomes the basis of a new lot (acquired that date, in that wallet).
- Reported separately from capital gains (dashboard "Ordinary income" line + income export).
- 1099-DA doesn't report staking income (some platforms send a 1099-MISC), so income won't reconcile against 1099-DA.
- Gifts received aren't income (see Open questions: gifts received).
- Source: Rev. Rul. 2023‑14 (income at FMV when the taxpayer gains dominion and control); Rev. Rul. 2019‑24 (airdrops after a hard fork).

## R9. Balance adjustments and unknown basis
- `ADJUST` with negative quantity removes units FIFO from a wallet pool with **no gain or loss** (it loses a little basis, which is conservative). Used for untracked fee dust. Positive ADJUST is not allowed. Use BUY with an unknown-basis flag instead.
- An ADJUST shortfall reports `InsufficientLots` and creates no event (R3). Before 2025 an ADJUST removes units from the universal pool.
- A lot can be marked `basisUnknown = true` with basis $0. That's intentional and conservative, not an error. The UI shows a small marker but no warning.
- A BUY with price 0 that is **not** marked basisUnknown → import warning asking the user to confirm (it might be untracked income).

## R10. Data problems are issues, not crashes
The calculator returns results **plus** a list of issues (`InsufficientLots`, `TransitionMismatch`, `ZeroPriceBuy`, `InvalidRow`). One bad row must never crash the app or hide valid results. Every issue names the row/date/asset/wallet and the quantity involved.

## R11. Netting and loss limit (display only)
- Show short-term and long-term totals separately, plus the net. Crypto and stocks net together. The default "All" view is the real number.
- If the year's net is a loss, show a note: up to **$3,000** ($1,500 married filing separately) offsets other income per year, and the rest carries forward. The app does **not** track carryovers yet.
- Source: IRS Topic 409; 26 U.S.C. §1211(b). <https://www.irs.gov/taxtopics/tc409>

## R12. Stocks (later phase)
Same R2/R3 (stocks use per-account basis already). **Wash sale rule (§1091) applies to stocks.** Not implemented until the stocks phase, and it needs its own rules and tests first.

## R13. Network fees on transfers
The coins spent (or withheld) to pay a transfer's network fee are a disposal; the rest of the transfer isn't taxable (R5).
- A `TRANSFER` can carry a fee paid in coins: `fee_quantity` units of `fee_asset` (blank = the transferred asset), worth `fee_usd`: their USD fair market value at the time, supplied by the user (no price API).
- `quantity` is what **arrived** in `to_wallet`. With the fee in the same asset, the source wallet loses `quantity` + `fee_quantity`.
- The fee units are a disposal (R1) in the **source wallet** on the transfer's date: amount realized = `fee_usd`, matched FIFO from the source pool (universal pool before 2025, per-wallet from 2025, R3), split pro rata by quantity across the matched lots, term per lot (R2). No transaction cost reduces it.
- The fee units are taken **before** the moved units (the oldest lots pay the fee), all within the transfer's single step in R3's same-day order.
- The fee is **not** added to the moved units' basis and isn't deducted anywhere: fees for transfers between your own wallets aren't digital asset transaction costs (unlike R7's buy/sell/swap fees).
- **Before 2025** the move is still ignored for matching (R4), but the fee disposal **is** applied to the universal pool and reported.
- **Fee shortfall:** unmatched fee units become a $0-basis slice like a SELL's (R3: unknown basis, short-term, acquired "Unknown", their share of `fee_usd`) plus `InsufficientLots`. A shortfall of the moved units stays issue-only (R3).
- Koinly equivalent, for comparison: "Realize gains on transfer fees" on, "Treat transfer fees as deductible costs" off.
- Source: IRS digital asset FAQ 53 (own-wallet transfer fees aren't transaction costs), 81 (an own-wallet transfer is taxable only to the extent of digital assets used or withheld to pay for it), 97 (coins used or withheld to pay for services are a disposal), 62/63 (amount realized = FMV of the service received) and 102 (the coins' own value, when the service's value can't be determined). Page updated 2026‑06‑29. <https://www.irs.gov/individuals/international-taxpayers/frequently-asked-questions-on-digital-asset-transactions>

## Rounding
Full `BigDecimal` precision in storage and math. Round to cents (HALF_UP) only for display and export. Totals are computed from unrounded values and then rounded.

## Watch list (re-check each season)
- **Notice 2026‑20** extends the books-and-records identification relief (Notice 2025‑07) through 2026‑12‑31. Its §4.05 says the owner's identification governs even if the 1099-DA differs.
- **2026 1099-DA:** brokers report basis only for **covered** units (acquired after 2025 in that account). Transferred-in or older units are proceeds only. The broker's basis may not match the app's if the broker uses a different method.
- **Crypto wash sales:** H.R. 10357 (Digital Asset Tax Certainty Act) cleared House Ways & Means 38‑5 on 2026‑09‑16. It would apply §1091 to traded digital assets (qualified USD stablecoins excluded) for dispositions after 2026‑09‑14. No House floor vote as of 2026‑11‑20. **Not law.** If enacted, 2026 losses need R12-style wash-sale logic. Re-check in December and before filing. The bill would also exempt crypto fees of $10 or less from gain/loss starting 2028.
- **Shortfall option B** ($0 basis, short-term) and the "Unknown" acquired date in Form 8949 column (b): confirm with a preparer. Option B is a placeholder for checking, not a filing position; a real return should fix the missing data.
- Capital loss limit and holding period (stable for decades, but confirm).

---

## Worked test cases
All numbers are made up. Dates are ISO. W = wallet. "Basis" includes fees.

**T1. Anniversary is short-term (R2)**
BUY 1 FAKEA @ $100 on 2023‑03‑01. SELL 1 @ $150 on 2024‑03‑01 → **ST +$50.00**. Same sale on 2024‑03‑02 → **LT +$50.00**.

**T2. Leap day (R2)**
BUY 1 @ $100 on 2024‑02‑29. SELL on 2025‑02‑28 → **ST**. SELL on 2025‑03‑01 → **LT**.

**T3. FIFO across lots with fees (R3, R7)**
BUY 2 @ $100 + $2 fee on 2022‑01‑10 (basis $202). BUY 1 @ $200 on 2022‑06‑01. SELL 2.5 @ $300 with $3 fee on 2023‑03‑01 (net proceeds $747).
→ Lot 1: 2 units, proceeds $597.60, basis $202.00 → **LT +$395.60**
→ Lot 2: 0.5 units, proceeds $149.40, basis $100.00 → **ST +$49.40**
→ Total **+$445.00**. Lot 2 has 0.5 left at $100 basis.

**T4. No matching future lots (R3, R10)**
BUY 1 @ $100 on 2023‑02‑01. SELL 1 @ $150 on 2023‑01‑01 → one event: 1 unit, basis $0 (unknown basis), short-term → **ST +$150.00**, plus an `InsufficientLots` issue (date 2023‑01‑01, short by 1). The BUY lot stays fully available afterward.

**T5. Per-wallet FIFO from 2025 (R3, R4)**
OPENING (2025‑01‑01): W=FAKE-EXCHANGE-1 1 FAKEA acquired 2020‑01‑01 basis $10,000. W=FAKE‑WALLET‑1 1 FAKEA acquired 2018‑01‑01 basis $15,000.
SELL 1 FAKEA in FAKE-EXCHANGE-1 @ $60,000 on 2025‑06‑01 → **LT +$50,000.00** (uses the FAKE-EXCHANGE-1 lot). Universal FIFO would wrongly give +$45,000. No issues (no records before 2025, so R4 has nothing to compare).

**T6. Transfer keeps the original date (R5)**
OPENING W=FAKE‑WALLET‑1: 2 FAKEB acquired 2024‑11‑01 basis $3,000 each. TRANSFER 1 FAKEB FAKE‑WALLET‑1 → FAKE-EXCHANGE-1 on 2025‑03‑01. SELL 1 FAKEB in FAKE-EXCHANGE-1 @ $4,000 on 2025‑06‑01 → **ST +$1,000.00** (held since 2024‑11‑01). FAKE‑WALLET‑1 still holds 1 FAKEB. No issues.

**T7. Swap, then transfer, then sell (R5, R6)**
BUY 10 FAKEB @ $20 on 2023‑01‑05 in W=FAKE-WALLET-2 (assume an OPENING carries it into 2025 in FAKE-WALLET-2).
SWAP 10 FAKEB → 1,500 FAKEUSD in FAKE-WALLET-2 on 2025‑03‑10, V = $1,500 → FAKEB disposal **LT +$1,300.00** (wallet FAKE-WALLET-2). New lot: 1,500 FAKEUSD, basis $1,500, acquired 2025‑03‑10.
TRANSFER 1,500 FAKEUSD FAKE-WALLET-2 → FAKE-EXCHANGE-1 on 2025‑03‑12. SELL 1,500 FAKEUSD @ $1.00 on 2025‑03‑15 → **ST $0.00**.

**T8. Income creates basis (R8)**
INCOME 0.5 FAKEB, FMV $100/unit, on 2025‑02‑01 in FAKE-EXCHANGE-1 → **ordinary income $50.00**, new lot basis $50. SELL 0.5 @ $120 on 2025‑04‑01 → **ST +$10.00**.

**T9. Adjustment has no tax effect (R9)**
BUY 1 FAKEB @ $1,000 on 2023‑01‑01. ADJUST −0.001 on 2023‑06‑01. SELL 0.999 @ $2,000 on 2024‑06‑01 → one event, 0.999 units, basis $999.00, proceeds $1,998.00, **LT +$999.00**. No event for the adjustment.

**T10. Unknown basis (R9)**
BUY 1.5 FAKEB @ $0 with basisUnknown = true on 2022‑07‑15. SELL 1.5 @ $100 on 2024‑03‑01 → **LT +$150.00**, no `ZeroPriceBuy` issue. Same row with basisUnknown = false → same math plus one `ZeroPriceBuy` import warning.

**T11. Transition mismatch (R4, R10)**
Universal remainder on 2025‑01‑01 = 5.000 FAKEB. OPENING records total 4.990 FAKEB → `TransitionMismatch` (expected 5.000, opening 4.990) and per-wallet pools use the OPENING records.

**T12. Year totals and netting (R11)**
In 2024: ST −$400, LT +$1,000 → net **+$600**. In 2023: ST −$5,000, LT +$1,000 → net −$4,000 plus the $3,000-limit note.

**T13. Transfer fee in the same coin (R13, R2, R5)**
OPENING W=FAKE-WALLET-1: 1 FAKEB acquired 2024‑01‑10, basis $2,000. TRANSFER 0.99 FAKEB (what arrived) FAKE-WALLET-1 → FAKE-EXCHANGE-1 on 2025‑04‑01, fee_quantity 0.01, fee_usd $30.00.
→ Fee event: 0.01 FAKEB, proceeds $30.00, basis $20.00, acquired 2024‑01‑10 → **LT +$10.00** (wallet FAKE-WALLET-1).
→ FAKE-EXCHANGE-1 holds 0.99 FAKEB, basis $1,980.00, acquired 2024‑01‑10. FAKE-WALLET-1 is empty.
SELL 0.99 FAKEB in FAKE-EXCHANGE-1 @ $3,100 on 2025‑06‑01 → proceeds $3,069.00, basis $1,980.00, **LT +$1,089.00**. 2025 long-term total **+$1,099.00**. No issues.

**T14. Fee paid in another coin, before 2025, across two lots (R13, R3, R4)**
W=FAKE-WALLET-1: BUY 0.004 FAKEB @ $1,000 on 2023‑01‑01 (basis $4.00). BUY 1 FAKEB @ $2,000 on 2023‑11‑01. BUY 100 FAKEUSD @ $1.00 on 2023‑01‑01.
TRANSFER 100 FAKEUSD FAKE-WALLET-1 → FAKE-EXCHANGE-1 on 2024‑03‑01, fee_asset FAKEB, fee_quantity 0.005, fee_usd $15.00.
→ Fee from the universal FAKEB pool: 0.004 units (2023‑01‑01), proceeds $12.00, basis $4.00 → **LT +$8.00**; 0.001 units (2023‑11‑01), proceeds $3.00, basis $2.00 → **ST +$1.00**.
→ FAKEB left: 0.999, basis $1,998.00. FAKEUSD unchanged (100 units; the pre-2025 move is ignored for matching, R4). 2024: ST +$1.00, LT +$8.00.

## Open questions
_(Add entries here instead of guessing. Format: date, question, where it came up, at least two options with what each does to the affected worked case, which one is **conservative** (ai-guidelines → Tax rules), and which one is implemented until the owner decides.)_

- **2026-11-21: Disposal larger than the pool (partial shortfall).** R3/R10 say don't match later lots and report `InsufficientLots`, but not what happens to the units that *were* available. Came up in the M2 calculator. **Decided (owner, 2026-11-21): option B, plus export warnings** (was A until `1.6.5`). Reason: the full proceeds are reportable, a basis that can't be substantiated is $0, and an unknown holding period is treated as short-term. Unresolved `InsufficientLots` issues dated on or before the end of the selected year, for assets in the current filter, get a warning card on the Export tab and a red banner on PDF page 1. CSV contents stay unchanged.
  - **A. Unmatched units are left out (implemented until `1.6.5`).** Match the available units with their pro-rata share of proceeds and fee, and report the shortfall. The unmatched units' proceeds appear nowhere, so the gain is understated until the data is fixed. **T4** then expected zero events and `InsufficientLots` short by 1.
  - **B. Unmatched units count as $0 basis, short-term (conservative; chosen, implemented in `1.6.6`).** Same as A, plus one lot match for the unmatched units: basis $0, `basisUnknown = true`, short-term, with its pro-rata share of proceeds and fee. The issue is still reported. **T4** now has a sell price and expects one event of 1 unit, $0 basis, ST +$150.00. The later BUY lot still stays fully available.
  - The issue stays visible on Summary until resolved. The export warnings are implemented in `1.6.1`; since `1.6.6` they say gains may be overstated.
- **2026-11-21: Same-day order of TRANSFER and SWAP.** R3 orders acquisitions before disposals, but transfers and swaps are both. Came up in the M2 calculator. **Implemented:** per date, acquisitions (OPENING, BUY, INCOME) first, then TRANSFER and SWAP in input order (so a same-day "swap, then transfer out" chain works as written), then ADJUST and SELL. Input order is the Room `id` (insertion order, `ORDER BY date, id`): kept by JSON backup → restore and unified CSV export → import, but a later import always goes after earlier rows of the same date. **Resolved (owner, 2026-11-21):** R3 now states this order.
- **2026-11-22: Gifts received.** Came up in the tax rules review. A gift isn't income; the recipient takes the giver's basis and holding period (carryover basis, §1015). Entering a gift as INCOME overstates income and sets the wrong basis and date. No worked case is affected.
  - **A. Out of scope, with this docs note (implemented).** Gifts aren't modeled. Entered as INCOME, the year received is conservative (income overstated), but the lot's basis becomes the FMV instead of the giver's basis, which can understate a later gain if the giver's basis was lower.
  - **B. A GIFT type later (conservative: matches §1015)**, carrying the giver's basis and acquired date with no income.
- **2026-11-22: Swap fee paid in a third coin.** A SWAP of A for B whose fee is paid in a coin C (for example gas in another token). Came up in the README limitations review. `fee_asset` / `fee_quantity` are allowed only on TRANSFER, so the C units can't be entered on the swap. No worked case is affected.
  - **A. Dispose of the fee coins like R13 (conservative; matches IRS FAQ 97/99).** The C units are a disposal at their FMV from the swap's wallet (their own gain or loss), and that value is a cost of the swap that reduces A's amount realized (R7). Needs `fee_asset` / `fee_quantity` on SWAP and the reference calculator to match.
  - **B. Ignore the fee coins (implemented).** The C units stay in their pool, so they're used up later by FIFO with their basis, and the fee's own gain or loss is never recognized. The user can put the fee's USD value in `fee_usd` (reduces A's proceeds) and remove the C units with an ADJUST (no gain or loss, R9).
