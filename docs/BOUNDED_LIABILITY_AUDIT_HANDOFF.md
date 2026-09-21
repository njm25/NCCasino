# Bounded Liability audit — implementation handoff

Status: **audit complete, implementation not started.** This document is the
complete record of an independent Audit 1 (reported-findings validation) and
Audit 2 (pre-branch vs. current regression audit) performed against the real
code on `slots-overhaul`, plus a concrete design for the fixes. No production
code was changed while producing this document — every finding below was
independently verified by reading the actual current implementation, not by
trusting the design docs or the original bug report.

## 0. Exact state this audit was run against

- Branch: `slots-overhaul`
- HEAD: `ae009000e4d1566952bd60ccdd0951de62dfdc87`
- `origin/slots-overhaul`: `ae009000e4d1566952bd60ccdd0951de62dfdc87` (identical — fast-forwarded, not diverged)
- Merge-base with `origin/main`: `2e6298c8eb9a63ff34dc72af07c63d33cb9ca538`
- `origin/bounded-liability-phase2`: `ae009000e4d1566952bd60ccdd0951de62dfdc87` (identical to `slots-overhaul` HEAD — already fully incorporated, confirmed by `git merge-base --is-ancestor`)
- Working tree: clean, no uncommitted changes, before and after this audit
- Java 21 / Gradle 8.8 confirmed available in this environment (`./gradlew`, not `.\gradlew.bat`, when working from a Linux checkout — use whichever your shell needs)

**Important environment note for whoever resumes this:** at the start of this
session, `origin/slots-overhaul` did *not* yet contain the `budget/*` package
or `docs/DEALER_BUDGET_SCHEMA.md` (Phase 2 was not merged in). The user pushed
their local branch mid-session, this session fetched and fast-forwarded, and
*that* is the state audited here. If you resume this in a fresh clone, confirm
`docs/DEALER_BUDGET_SCHEMA.md` and `src/main/java/org/nc/nccasino/budget/`
exist before trusting anything below — if they don't, you have an older
`origin/slots-overhaul` and need to re-sync first.

---

## 1. Audit 1 — reported findings, independently validated

Every row below was checked by reading the actual current source, not the
design docs. File:line references are to HEAD `ae00900`.

| # | Finding | Classification | Key evidence |
|---|---|---|---|
| 1 | Settlement persistence failures ignored by game integrations | **Confirmed**, all 8 games (Slots, Baccarat, Roulette, Blackjack, Mines, Dragon Descent, Coin Flip PvE, RPS PvE) | Every game calls `DealerBudgetService.settle/refund/releaseLoss` and discards the returned `Settlement`, clearing its own local commitment unconditionally in the same statement. `Settlement.paid()`/`.status()` is never inspected anywhere in game code. **Important nuance:** delivery to the player is computed independently in every game (not from `Settlement.paid()`), so a `FAILED` settlement does **not** cause a duplicated payment or a re-rolled result — the actual consequence is a silently **orphaned dealer reservation** (ledger drift, `reservedTotal` permanently inflated) with no reconciliation path. See §3.1 for the design. |
| 2a | Baccarat/Roulette stake credit ordered before player debit | **Confirmed**, both games, worse than reported for Baccarat | **Baccarat `BaccaratClient.java:724-735`**: `ensurePortfolioCovered` (dealer credit/reserve) runs, bet is recorded into `betHistory`/`betStacks`, *then* `removeWagerFromInventory` runs and its `boolean` result is **discarded**. A bank-blocked or otherwise-failed debit still leaves a live, payable, unpaid bet — **this is reachable today** (Audit 2 finding B1, not gated behind LIMITED mode). Same discard on rebet (`BaccaratClient.java:884`) which additionally **never calls `ensurePortfolioCovered` at all** (`BaccaratClient.java:854-887`) — a full rebet round can run with zero dealer-budget participation. Baccarat undo (`:572-655`) never calls `refundPortfolio`, leaking the reservation; a player who undoes every bet one-at-a-time is dropped from `playerBets` and never settled. **Roulette `BettingTable.java:827` vs `:843`**: reserve happens before the bank gate/funds check/debit, and a failed debit (`:843-846`) does not roll back the just-created reservation/credit — no compensating release exists. Roulette undo-last (`:955-974`) never shrinks the reservation (undo-all at `:1072-1077` is already correct). Undo-all/exit/shutdown/forfeit/ordinary resolution are already-correct, exactly-once paths (`BettingTable.java:464,1074,1125-1143`; `RouletteInventory.java:1997-2026`). |
| 2b | Blackjack stake credit ordered before player debit | **Confirmed**, multiple distinct sub-bugs | (1) **Zero-stake exposure growth — confirmed**: every chip added to an *existing* pending opening wager calls `budget.increase(..., Money.ZERO)` (`BlackjackInventory.java:817`, also `:6584`) — exposure grows by the chip's full value, credited stake is zero; only the first chip actually funds the reservation. (2) Reserve/credit-before-debit is the universal, by-design pattern for opening/double/split/insurance (`:2559` vs `:2562`; `:5086`; `:5535` vs `:5548`; `:7378` vs `:7386`) — safe only if rollback is correct, see (4). (3) **Idempotency gap — confirmed for opening(first chip)/split/insurance**: their commitment ids are generated from an ever-incrementing `budgetRoundCounter` per *call* (`:809/815`, `:878/884`, `:957/963`), not derived from stable action identity — a duplicated Bukkit event double-reserves and double-credits. (Double is already safe — it reuses the hand's existing stable id via `increase()`.) (4) **Wrong rollback direction — confirmed**: on a failed split/insurance debit, the rollback calls `releaseLoss` (`:5548-5558`, `:7386-7396`) which is `settle(..., ZERO)` — this treats the never-actually-collected stake as a forfeited player loss and the dealer **permanently keeps free money** equal to the attempted stake. The correct call is `refund`, not `releaseLoss`. The opening-wager debit failure path (`:2556-2579`) has **no rollback call at all** — the reservation dangles until a later undo-all/seat-leave sweeps it up, at which point it is settled as a forfeit of a stake the player never paid. (5) **Undo-last confirmed broken**: `handleUndoLastBet` (`:6712-6770`) refunds the player's chip but never calls `releasePendingOpeningBudgetCommitment` — the pending reservation is left stale/oversized, and if it was the only chip, permanently orphaned. `handleUndoAllBets` (`:6689`) is correct. (6) Debit-source ownership (cursor/inventory/Vault/custom-provider) is **already correct** (`tryRemoveWager`, `:6850-6886`) — the real gaps are (3)/(4)/(5), not missing debit ownership. |
| 3 | Settlement silently clamps a legitimate payout to its reservation | **Partially confirmed — needs a different fix, not "already broken as described"** | `DealerBudgetStore.settle` (`DealerBudgetStore.java:464-492`) does clamp the **ledger debit** to `reservation.amount()` when `payout > reservation.amount()`, and returns that clamped figure as `Settlement.paid()`. However, **no caller reads `Settlement.paid()` to decide what to deliver** (see finding 1) — every game computes and delivers the full, correct payout independently. So the player is *not* shortchanged today. What's real: the dealer's persisted `live-balance` is under-debited relative to what actually left the dealer's economy, silently overstating the dealer's true remaining capacity every time an exposure-calculation bug causes `payout > reservation.amount()`. Logged as `SEVERE` (`DealerBudgetService.java:309-315`) but never reconciled. See §3.2 for the design (debit the full payout from live balance, floored at zero, never clamp `Settlement.paid()`, keep the loud logging, add an explicit insolvency signal). |
| 4a | Reusing a commitment id with different payload | **Confirmed** | `DealerBudgetStore.creditAndReserve` (`:389-414`) returns the *existing* reservation on an id match with **zero comparison** of player/game/currency/exposure against the incoming payload. A payload swap under a reused id is silently accepted. |
| 4b | Replaying an adjustment double-credits | **Confirmed, distinct gap from 4a** | `adjustReservation` (`:425-447`) has **no replay guard at all** — every call unconditionally re-applies `additionalStake` to `liveBalance`. A duplicated increase call (retried Blackjack double, duplicated Roulette bet event) credits the dealer twice for one real deposit. |
| 4c | Recreating a settled commitment | **Confirmed** | `settle()` removes the reservation entirely (`:478`) with **no tombstone kept anywhere**. A subsequent `creditAndReserve` with the same (now-absent) id falls through to the "create new" branch and credits a fresh stake — a genuine double-pay/double-reserve vector, survivable across restarts since the schema (`data/dealer-budgets.yml`) has no record of past settlements. |
| 4d | Nonexistent vs. already-settled commitment indistinguishable | **Confirmed, but deliberate per current docs** | Both cases hit `!current.hasReservation(reservationId)` → `Settlement.alreadySettled()` (`:469-471`). This is explicitly documented and tested as intentional (`DealerBudgetStoreTest.settlingAnUnknownCommitmentMovesNoMoney`). Kept as-is for the public `settle()` contract (a legitimate replay must stay a harmless success) — but combined with 4c, a typo'd id masquerades as a harmless success while corrupting state elsewhere. Fixing 4c (tombstones) closes the dangerous half of this without changing the public contract. |
| 4e | Restart behavior | **Confirmed — unaffected**, gaps are structural | None of 4a/4b/4c depend on process-local state; the persisted schema has no settled-id ledger, so restart changes nothing about any of the above. |
| 5 | LIMITED dealers have no practical initial funding path | **Confirmed, all three refill modes** | Underwriting baseline only sizes the risk-tier ceiling (`AdmissionPolicy.admit`, `DealerBudgetSettings.maxHouseLossPerRound`) — it never touches `live-balance`. A fresh dealer starts at 0 (`DealerBudgetStore` — no persisted file, or `DealerBudgetState` default ctor). `NONE`: never funds, ever (`RefillPolicy.apply` short-circuits before even running when `hasRefill()` is false). `ADD`/`RESET`: `RefillPolicy.apply`'s "first contact" branch (`RefillPolicy.java:94-96`) explicitly grants **nothing** on first access, only starts the clock — a dealer is unusable until a full period after that first access. **Product decision recorded in §2.** |
| 6 | Failed Vault/custom-provider payouts can lose winnings | **Confirmed** for several call sites; **already fixed** for others | Root cause: `CurrencyProvider.deposit()` returns `boolean` specifically for retry, but several sites discard it. **Confirmed (loses money):** `Client.java:517,567,797` (shared `creditPlayer`/`refundCurrency` — used directly by CoinFlip `CoinFlipServer.java:657,804,828`, `CoinFlipClient.java:684` and RPS `RockPaperScissorsServer.java:817,929,999`, `RockPaperScissorsClient.java:762`); `Server.java:335,349,385` (duplicate of the same trio for server-authoritative games); `BaccaratServer.java:777` (the offline path at `:805` is already correct); `DragonClient.java:866`; `MinesTable.java:1327,1436,1723` (Mines' own *item*-currency branch, `:1787` region, is already correctly durable). **Already fixed (pattern to copy):** Roulette `BettingTable.java:1195,1215` (checks return, queues exact/chunked remainder), Blackjack `BlackjackInventory.java:6958,8504` (`PayoutDisposition`, single retention owner), Slots `SlotsMachine.java:1037`. |
| 7 | Item payout rounding disagrees with dealer accounting | **Confirmed, all four games** | Each of Baccarat (`BaccaratServer.java:936`), Dragon Descent (`DragonClient.java:683`), Mines (`MinesTable.java:1388`), Blackjack (`BlackjackInventory.java:8635`) has its **own private, independently-implemented** probabilistic-rounding method (each with its own `new Random()`) rather than using the existing shared `MoneyHelper.probabilisticItemAmount` (`currency/MoneyHelper.java:66`, today only used by `PendingPayoutStore.java:345`). In every one of the four games, the dealer-budget **settlement** call (`settlePortfolio`/`settleBudget`/`settleHandBudget`) runs on the **raw, unrounded** `stake × multiplier` value, computed *before* the game's own rounding step independently re-derives the integer actually delivered. Delivery/pending/overflow-bank/chat-message all consistently reuse the *rounded* value (uses (c)-(f) agree with each other), but the **ledger** (a)/(b) disagrees with all of them by up to ±1 item per event — not a directional bias (rounding is probabilistic/unbiased in expectation) but a per-event ledger/delivery mismatch that defeats exact reconciliation. No cross-mode contamination found (Vault vs. item rounding is correctly gated everywhere). |
| 8 | Slots theoretical RTP ≠ realized integer-item RTP | **Confirmed, with a quantified example** | `SlotsPaytable.forConfig` (`SlotsPaytable.java:84-118`) normalizes multipliers in plain `double` against the *un-floored* shape so the pre-floor EV hits exactly `1-houseEdge`. `SlotsMath.totalPayout` (`SlotsMath.java:112-130`) floors the **summed** per-spin payout once, with **zero compensation** anywhere for the resulting EV loss. Concrete computed example (BALANCED, 3 columns, 1 line, houseEdge=0.03, using the real `SlotsSymbol`/`SlotsVariance` constants): at wager=1, realized RTP ≈ **0.9560** (realized house edge ≈4.4% vs. configured 3% — a ~47% relative inflation), converging toward 0.97 as wager grows (wager=100 → 0.96988). Denomination is the dominant factor; reel width/variance modulate the magnitude **non-monotonically** (7-column BALANCED at wager=1 is *worse*, 0.9299, than 3-column, because more of the theoretical RTP concentrates into rare high-multiplier full-width runs that are also floor-rounded). **No test exists that would catch this** — `SlotsPaytableTest.derivedRtpMatchesConfiguredEdge` only checks the pre-floor enumerated theoretical RTP, never calls `SlotsMath.totalPayout`. |
| 9 | Mines/Dragon Descent choose boards before admission | **Incorrect as stated — already correct** | Mines: `ensureBudgetCoversNextPick(0)` (`MinesTable.java:815`) runs and must succeed before `startGame()`→`placeMines()` (RNG, `:947`,`:1015-1019`) is ever called. Dragon Descent: `ensureBudgetCoversNextFloor(0)` (`DragonClient.java:537`) gates `setupGame()`→`generateGameGrid()` (RNG shuffle, `:181`,`:242-255`) identically. **No board/layout is generated for a denied admission in either game.** Related sub-checks, also fine: abandonment before first pick/selection is already handled correctly in both games (`MinesTable.java:1931-2007`, `DragonClient.java:974-1032`, each with a universal `settleBudget(ZERO)` safety net). One minor, non-bug timing note: in both games the player's opening stake leaves their inventory (into a chip/bet-stack) *before* the lever/start press that credits the dealer — a "chips on the table" window, not a defect, since undo-before-start correctly returns the chips and no random draw or dealer credit has happened yet. |
| 10 | ADD refill catch-up can discard elapsed periods | **Confirmed** | `MAX_CATCHUP_PERIODS = 100_000L` (`DealerBudgetSettings.java:56`). In `RefillPolicy.apply` (`RefillPolicy.java:104-120`), `newBoundary` is advanced by the **full, uncapped** `periods * period` (`:112`) before the mode switch runs; only `applyAdd` internally clamps the *credited amount* to `effectivePeriods = min(periods, MAX_CATCHUP_PERIODS)` (`:141-142`). Any elapsed-periods count beyond the cap is **permanently forfeited** — the boundary has already moved past it, and no future access can ever reclaim it. `RefillPolicyTest` has no test anywhere near this threshold (largest tested gap is 72 periods). `refill-cap` interaction and overflow/huge-duration safety are both already fine (no loop, single multiplication, ordinary `long` arithmetic, no realistic overflow risk). |
| 11 | Overflow/pending-payout persistence atomicity | **Confirmed** for claim/join/pre-wager clearing (deliberate, documented tradeoff); **partially confirmed** for drop-delivery | `OverflowBankService.claimAll` (`OverflowBankService.java:225-249`) delivers (`insert`, `:242`) **then** persists the debit (`store.debit`, `:244` → `OverflowBankStore.java:266`). This is the exact reported bug: a crash between delivery and the persisted debit leaves the pre-claim (higher) balance on disk, permitting a duplicate claim after restart. It is a **documented, deliberate** tradeoff (`OverflowBankStore.java:36-41,262-269` explicitly reasons about it and logs SEVERE on persist failure) — the alternative direction (persist-before-deliver) trades this for a "silently destroys money" failure mode instead, which the codebase's own comments reject as worse. All three consumers (`ClaimCommand.java:41`, `PlayerSessionListener.java:176`, `WagerGate`→`OverflowBankService.java:271`) share this one method and risk. Drop-delivery (`OverflowBankService.java:123-161`) is persist-*then*-deliver with a re-credit safety net for partial delivery (`:153-156`) — correct in the common case, but has its own narrow residual window between the persisted debit (`:151`) and the physical `drop()` call (`:152`) where a crash could still lose value, since the re-credit only runs if the method returns normally. Pending-payout path (`PendingPayoutStore.java`) uses the same documented deliver-then-persist pattern consistently. **No fix here can honestly be called crash-proof** — see §3.9 for the realistic low-cost options and their residual windows. |
| 12 | Invalid explicit budget mode fails open | **Confirmed** | `DealerBudgetMode.parse(raw, fallback)` (`DealerBudgetMode.java:17-26`) and its caller `DealerBudgetSettings.parse` (`DealerBudgetSettings.java:99-106`) resolve **both** "absent `mode:`" and "present but garbage `mode:`" (e.g. `LIMTED`) to the identical `UNLIMITED` runtime outcome — the only difference is one extra `warning`-level log line for the garbage case. This contradicts the design's own stated goal (a misconfigured LIMITED dealer must be unusable, not risk-free-unlimited) and directly contradicts `docs/DEALER_BUDGET_SCHEMA.md:46`'s framing of "unreadable falls back to UNLIMITED" as if it covered this case safely — it doesn't distinguish "never configured" from "typo'd". |
| 13 | `/ncc claim` inaccessible to normal players | **Confirmed** | `plugin.yml:11` declares `permission: nccasino.use` directly on the `ncc` command (Bukkit's own dispatcher enforces this before `onCommand` is ever called); `nccasino.use` is `default: op` (`plugin.yml:13-15`) with **no `children:` block** granting it to `nccasino.commands.*`. `CommandExecution.java:44` checks `nccasino.use` first, unconditionally, before any subcommand (including `claim`, `default: true` at `plugin.yml:28-30`) is even parsed at `:56-69`. Confirmed via direct reading of Bukkit permission semantics: no automatic parent/child relationship exists without an explicit `children:` declaration, and `nccasino.use` has none. Every `/ncc` subcommand is blocked for a normal (non-op, ungranted) player today, `claim` included. |

---

## 2. Product decisions — resolved during this session

Both decisions below were explicitly made by the user during this audit and
are **final for implementation** — do not re-litigate them:

1. **LIMITED dealer initial funding (finding 5):** LIMITED dealers receive a
   **one-time live-balance seed equal to the underwriting baseline**, on
   first-ever access, protected by a persisted initialization marker so a
   restart/reload can never re-mint it. A later change to the configured
   baseline must **never** move already-live money — it only affects the
   risk-tier ceiling for future admission checks, exactly as it does today.
   See §3.4 for the concrete design (reuses the existing
   `refill-boundary <= 0` "never touched" marker as the one-time gate).

2. **Roulette item-payout ceiling (Audit 2 finding, `MAX_ITEM_MODE_PAYOUT`):**
   the branch's removal of the old 10,000 physical ceiling
   (`BettingTable.java:1310`, now effectively `Integer.MAX_VALUE`) is
   **kept, not reverted** — same rationale as removing Slots' item ceiling
   once overflow banking exists. Retain only the existing non-configurable
   numeric/precision safety boundary (whatever guards `Money`/`long` overflow
   already do elsewhere) — do **not** reintroduce an arbitrary 10,000-style
   product ceiling, and do not make this newly configurable (the design
   explicitly forbids inventing a separate configurable max-wager/max-payout
   knob).

No other finding in this audit requires a product or GUI decision — every
other confirmed item has a mechanical, design-preserving fix (below).

---

## 3. Proposed shared transaction lifecycle and invariants

### 3.1 Kernel-level fixes (do these first — every game-level fix builds on them)

All in `src/main/java/org/nc/nccasino/budget/`. These were designed in detail
during this session (concrete code was drafted and reasoned through, but
**not applied** — no file under `budget/` was edited).

**(a) Finding 3 — stop clamping the ledger debit, keep the full obligation.**
In `DealerBudgetStore.settle`:
- Remove the `owed = clamped ? reservation.amount() : payout` clamp.
  `Settlement.paid()` must always equal the full `payout` when status is
  `SETTLED` (matches finding 1's discovery that no caller actually reads this
  value for delivery today, so this is a ledger-honesty fix, not a
  behavior-visible one).
- The **ledger debit** becomes `debit = Money.min(payout, state.liveBalance())`
  — pay from live balance up to what exists, floored at zero, so
  `live-balance` can never go negative.
- Rename `Settlement.clamped` → `Settlement.exposureViolation` (kept as a
  loud, logged signal that the pre-commitment exposure math is wrong
  somewhere) and add a second boolean `insolvent` (`debit < payout`, i.e. even
  the *full* live balance couldn't cover it — a strictly worse case than an
  exposure violation, meaning real money already left the dealer's economy
  with no backing). Both get their own `SEVERE` log line in
  `DealerBudgetService.logSettlementAnomaly` — do not merge them into one
  message, they mean different things to an operator.
- Update `DealerBudgetStoreTest.aPayoutLargerThanItsReservationIsClampedRatherThanOverdrawingTheDealer`
  to assert `paid()` is now the **full** requested payout, add a new test for
  the `insolvent` case (payout exceeds even total live balance), and a test
  that `live-balance` never goes negative in either case.

**(b) Finding 4a — reject a payload mismatch on a reused commitment id.**
In `creditAndReserve`, when `existing != null`, compare
`existing.playerId()`/`gameType()`/`currency()`/`amount()` against the
incoming `reservation`'s fields (a `samePayload` helper). On any mismatch,
log `SEVERE` and return `null` (refuse) rather than silently returning the
stale `existing` reservation under a swapped identity. Do not compare stake —
it is not persisted per-reservation and a replay never re-credits it anyway,
so there is no double-credit risk from a stake-only mismatch.

**(c) Finding 4b — make `adjustReservation` idempotent without a new API.**
Minimal, non-invasive fix: before mutating, if the reservation is **already**
at the requested `newAmount`, treat the call as a no-op replay and return the
existing reservation unchanged (do not re-credit `additionalStake`). This
requires no new parameter and no caller changes, and directly covers the
confirmed risk ("the same operation retried" produces an identical
`(newAmount, additionalStake)` pair). It does not protect against a
hypothetical bug that recomputes a *different* `newAmount` on each retry —
that class of bug is out of scope for a minimal fix and would need a real
per-operation id threaded through every `increase()` call site across five
games, which is not proportionate to the confirmed risk.

**(d) Finding 4c/4d — bounded settled-commitment tombstones.**
Add a bounded, persisted per-dealer set of recently-settled reservation ids
to `DealerBudgetState` (e.g. a `LinkedHashMap<String, Long>` id→settled-at,
capped at a fixed size such as 5,000 entries per dealer with oldest-first
eviction — bounded exactly as the task requires, "without destroying
still-needed idempotency guarantees"). Persist it as a new `settled:` list
alongside `reservations:` in `dealer-budgets.yml` (bump nothing — additive,
schema version can stay 1, or bump to 2 if you want an explicit marker; either
is fine as long as `load()` tolerates a file without this key for backward
compatibility with existing saved state). On `settle()`, after removing the
reservation, add its id to the tombstone map. In `creditAndReserve`, when no
live reservation exists for the id, also check the tombstone map — if
present, refuse (log `SEVERE`, "already settled, cannot be recreated")
instead of falling through to "create new". This closes the actual
double-pay vector in 4c without touching the public `settle()` contract that
4d's tests rely on (nonexistent vs. already-settled stay collapsed there,
which is documented as intentional).

**(e) Finding 12 — fail closed on an explicit invalid mode.**
In `DealerBudgetSettings.parse`, only fall back silently to `UNLIMITED` when
`rawMode` is `null`/blank (never configured). When `rawMode` is present but
unparseable, **reuse the existing fail-closed path**: return
`DealerBudgetMode.LIMITED` with `underwritingBaseline = Money.ZERO` (which
`isUsable()` already reports as `false`, and `AdmissionPolicy.admit` already
refuses with `CONFIGURATION_INVALID` — this is exactly how "LIMITED but no
baseline" already fails today, so no new `AdmissionDecision` or code path is
needed). Change the log line from a "treating as UNLIMITED" warning to a
"this dealer will refuse every wager until fixed" severe message.

**(f) Finding 10 — advance the refill boundary only by periods actually credited.**
In `RefillPolicy.apply`, compute `periodsToApply` (capped at
`MAX_CATCHUP_PERIODS` for `ADD`, uncapped for `RESET`/`NONE` since those
aren't a per-period accrual) **before** computing `newBoundary`, and use the
same `periodsToApply` for both the boundary advancement and the credited
amount. This means a dealer with a multi-year gap self-heals across
*multiple* future accesses (each capped at `MAX_CATCHUP_PERIODS`) instead of
permanently forfeiting everything beyond the first cap. Add a `RefillPolicyTest`
case with `periods > MAX_CATCHUP_PERIODS` asserting the boundary only advances
by the capped amount and a second `apply()` call later continues crediting
the remainder.

### 3.2 Finding 5 — one-time LIMITED funding seed (per the resolved decision)

Add `DealerBudgetStore.ensureInitialFunding(dealer, baseline, nowEpochSeconds)`:
reuses the existing `refillBoundaryEpochSeconds() <= 0` "never touched" marker
as the one-time gate (this marker already exists and is already the exact
signal for "this dealer has never been funded"). If untouched and
`baseline` is positive: set `live-balance = max(live-balance, baseline)` and
set `refill-boundary = now` **in the same persisted mutation**, so the marker
is consumed atomically with the seed (a failed persist rolls both back
together via the existing `mutate()` rollback machinery — no separate marker
to get out of sync). Call this from `DealerBudgetService.admit()` (and
`affordableDenominations()`, which independently reads `store.available()`)
for every `LIMITED` dealer, **before** `refreshFunding()` — this composes
correctly with ADD/RESET: since the seed already advances the boundary to
`now`, a subsequent `applyRefill` for an ADD/RESET dealer no longer sees
"first contact" and simply starts accruing/resetting from the seed moment
forward, with no double-funding. A dealer with `refill-mode: NONE` is now
funded once and then genuinely depends on wagers/losses to grow, matching the
design doc's description of `NONE` ("leaves the dealer entirely dependent on
its starting balance and player wagers") — which is only true once there
*is* a starting balance. Test: seed fires exactly once across a
save/reload/restart cycle; a later `underwriting-baseline` config edit does
not change `live-balance` for an already-seeded dealer; a dealer with
baseline ≤ 0 is never seeded (nothing to seed, `isUsable()` already false).

### 3.3 Finding 1 — orphaned-reservation reconciliation, not per-game result-checking

Given finding 1's actual (non-)consequence (no double-pay/re-roll risk, only
silent orphaned reservations), the proportionate fix is **not** to rewire
all 8 games to branch on every `Settlement` return value (high risk of
introducing new bugs across 8 games for a ledger-hygiene issue). Instead:

- Keep every game's existing "clear local commitment regardless of result"
  behavior (it is already safe against double-payment/re-roll).
- Have `DealerBudgetService.settle()` (already the single choke point every
  game calls through) **persist a durable record of any non-`SETTLED`,
  non-`ALREADY_SETTLED` outcome** (i.e. `FAILED`) — reuse the bounded
  tombstone/journal mechanism from §3.1(d), or a small parallel "settlement
  anomalies" list — so an administrator command (or the existing
  `staleReservations` diagnostic) can surface "this reservation is still
  open because its settlement write failed" instead of that information
  existing only in a transient log line. This is additive and requires zero
  per-game code changes.
- Add a focused `DealerBudgetServiceTest`/`DealerBudgetStoreTest` case
  simulating a persist failure at `settle()` time and asserting the
  reservation is (a) still present and (b) discoverable via the
  reconciliation surface above.

### 3.4 Finding 2a/2b — the shared ordering fix, applied per game

The single coherent rule to apply everywhere (do not invent per-game
variations): **check funds and debit the player before crediting/reserving
with the dealer, or — if reserve-then-debit is kept for a specific game's
existing control flow — roll the reservation back with `refund` (never
`releaseLoss`) the moment the debit is found to have failed, in the same
method, before returning.** Concretely:

- **Baccarat (`BaccaratClient.java`):** in `processBetSlot`/the click
  handler, check `removeWagerFromInventory`'s (or the lower-level
  `tryRemoveWagerFromInventory`/`tryRemoveCurrencyFromInventory`) boolean
  result; on `false`, do not add to `betHistory`/`betStacks` and do not call
  `ensurePortfolioCovered` at all (reorder: gate → debit → *then* reserve, to
  match Roulette's already-better structure, or debit first and only reserve
  after `true`). Fix `reapplyPreviousBets` to call `ensurePortfolioCovered`
  for the full rebet exposure exactly like a fresh wager, and to abort the
  entire rebet (refund nothing — nothing was taken yet) if either the
  admission check or the debit fails. Fix undo-last/undo-all to call
  `refundPortfolio` for the exact amount being undone before/alongside the
  currency refund.
- **Roulette (`BettingTable.java`):** reorder `ensurePortfolioCovered` to
  after the bank gate + funds check + debit succeed (matches the design
  doc's stated order: bank gate → funds check → debit → reserve). Where a
  full reorder is riskier than a targeted rollback, an acceptable equivalent
  is: keep the current order but call `refundPortfolio`/`releasePortfolioForExternalResolution`
  immediately when `removeWagerFromInventory` returns `false` (`:843-846`),
  before returning. Add the missing `refundPortfolio` call to undo-last
  (`:955-974`), mirroring undo-all's already-correct shape.
- **Blackjack (`BlackjackInventory.java`):**
  1. Fix the zero-stake bug: chip-add calls to `budget.increase` for an
     *existing* pending opening commitment must pass that chip's own real
     stake as `additionalStake`, not `Money.ZERO` (`:817`, `:6584`).
  2. Fix commitment-id generation for the opening (first chip), split, and
     insurance reservations: derive a stable id from action identity (e.g.
     seat + round + "open"/"split-<slot>"/"insurance", matching how the
     already-safe double path reuses the hand's existing id) instead of an
     ever-incrementing `budgetRoundCounter` per call.
  3. Fix the rollback direction: split/insurance debit-failure cleanup
     (`:5548-5558`, `:7386-7396`) must call `refund` (return the
     never-collected stake to nobody, i.e. release without treating it as a
     loss) instead of `releaseLoss`. Add a rollback call to the opening-wager
     debit-failure path (`:2556-2579`), which currently has none.
  4. Fix undo-last (`:6712-6770`) to call
     `releasePendingOpeningBudgetCommitment` for the undone chip's amount,
     mirroring undo-all's already-correct call at `:6689`.

### 3.5 Finding 6 — extend the already-proven pending-payout pattern

Roulette/Blackjack/Slots already have the correct shape
(check `deposit()`'s boolean → on failure, create/extend a
`PendingPayout` via `PendingPayoutStore` for the undelivered amount, never
duplicate an existing pending record for the same event). Apply the
identical pattern to the confirmed gaps:
- `Client.creditPlayer` (`Client.java:517,567`) and `Client.refundCurrency`
  (`Client.java:797`) — the shared helpers CoinFlip/RPS call directly.
- `Server.creditPlayer` (`Server.java:335,349,385`) — the server-authoritative
  duplicate of the same helper trio.
- `BaccaratServer.java:777` (the live-online-payout Vault deposit; the
  offline path at `:805` is the reference implementation to copy).
- `DragonClient.java:866`, `MinesTable.java:1327,1436,1723` (Vault branches
  only — the item-currency branches are already correct).

Since `creditPlayer`/`refundCurrency` are shared across every game, fixing
them once in `Client.java`/`Server.java` closes the gap for CoinFlip and RPS
automatically; Baccarat/Dragon/Mines need their own game-specific call sites
touched individually because they call the provider directly rather than
through the shared helper.

### 3.6 Finding 7 — decide the rounded amount once, before settlement

In each of Baccarat/Dragon Descent/Mines/Blackjack, move the existing
probabilistic-rounding call (or switch to the shared
`MoneyHelper.probabilisticItemAmount`, consolidating four duplicate
implementations into one) to **before** the `settlePortfolio`/`settleBudget`/
`settleHandBudget` call, and settle on the **rounded** value, not the raw
`stake × multiplier`. This makes the same exact integer flow through
exposure/settlement/delivery/overflow/pending/display uniformly, closing the
±1-item drift. Blackjack's `payOut()` already has the cleanest shape to copy
from for how the *other* five uses (c)-(f) should consume one shared local
variable.

### 3.7 Finding 8 — Slots RTP: recommended fix direction

Per the sub-agent's fix-direction menu, **probabilistic/randomized rounding**
in `SlotsMath.totalPayout` (round up with probability equal to the fractional
remainder, else round down) is the recommended minimal fix: it makes the
floor operation unbiased in expectation with no new persistent state, no new
per-denomination paytable cache, and no visible paytable/GUI change (the
configured multipliers shown to players are untouched — only the internal
rounding of the *realized* payout changes from deterministic-floor to
probabilistic). It must use the same RNG discipline already used for spin
generation (an auditable, seedable source — check what `SlotsSpinGenerator`
already uses and reuse it, do not add a second independent `Random`).
Required test: an expected-value simulation (large N) across every supported
reel width (3/5/7), every variance level, and denomination 1 specifically,
asserting realized RTP is within a defined tolerance (e.g. ±0.5 percentage
points at large N) of `1 - houseEdge` — this test does not exist today and
must be added regardless of which fix direction is chosen.

### 3.8 Finding 13 — narrowest permission fix

Recommended: change `nccasino.use`'s `default` from `op` to `true` in
`plugin.yml`. Verified safe because every admin-capable subcommand
(`create`/`delete`/`reload`/`list`/`help`) already has its **own** node at
`default: op`, checked separately by `CommandExecution.java` after the
umbrella check — so this change grants normal players nothing beyond being
able to reach subcommand dispatch, and `nccasino.commands.claim`
(`default: true`) becomes reachable. Do not remove the `permission:` line
from `plugin.yml`'s `commands:` block as an alternative — that changes
Bukkit's own tab-completion/`?`-listing behavior more broadly than necessary.
Add a permissions test/manual check confirming `create`/`delete`/`reload`
still refuse a non-op player after this change.

### 3.9 Finding 11 — realistic low-cost persistence improvement

Given the codebase's own documented reasoning already rejects
persist-before-deliver (it trades a duplicate-claim risk for a
silently-destroys-money risk, judged worse), the recommended low-cost
improvement is the **two-phase marker**, not a reorder: write a small
"claim in progress" marker to `overflow-bank.yml` before `insert()`, clear it
only after `debit()`'s persist succeeds. On `load()`, a marker found without
a matching completed debit means an interrupted claim — replay the debit
(the balance is already known from the marker) rather than trusting the raw
on-disk balance as-is. This narrows the crash window from "the entire
insert-then-debit gap" to "the much smaller window between writing the
marker and its own persist reaching disk," is self-healing on next boot, and
requires no behavior change to the delivery-then-persist ordering that the
existing code already reasoned about and chose deliberately. Document the
remaining residual window honestly in the code comment — do not claim
crash-proof behavior. Apply the same idea to the drop-delivery path's
narrower window (between the persisted debit and the physical `drop()`
call) if time permits; it is lower priority since it already has a
re-credit safety net for the far more common "partial delivery succeeded"
case.

---

## 4. Recommended implementation order

1. Kernel fixes §3.1 (a)-(f) — foundational, everything else depends on the
   API staying stable. Each is small and independently testable against
   `DealerBudgetStoreTest`/`DealerBudgetServiceTest`/`RefillPolicyTest`.
2. §3.2 LIMITED funding seed — depends on the kernel's `mutate()`/persistence
   machinery being stable, otherwise independent.
3. §3.3 orphaned-reservation reconciliation — additive, no game changes.
4. §3.4 Baccarat fix (contains the only **currently-reachable**, non-LIMITED-
   gated money bug — B1 free bets — this is the single highest-priority item
   in the entire audit and should arguably be pulled ahead of the kernel
   work if only one fix can be shipped immediately).
5. §3.4 Roulette fix.
6. §3.4 Blackjack fix (largest, most sub-bugs — budget time accordingly).
7. §3.5 Vault/custom deposit-failure fix (Client/Server shared helpers first,
   then the four remaining game-specific call sites).
8. §3.6 item-rounding unification (four games, mechanical once the pattern
   from one game is proven).
9. §3.7 Slots RTP fix + its required simulation test.
10. §3.8 claim permission fix (trivial, one-line `plugin.yml` change + test).
11. §3.9 overflow two-phase marker (lowest priority — documented, deliberate,
    already-logged tradeoff; improve opportunistically).

---

## 5. Tests that must be added (beyond what exists today)

Mapped to the task's original testing-requirements list, all confirmed as
currently missing during this audit:

- `DealerBudgetStoreTest`: full-payout-preserved-on-exposure-violation,
  insolvent-settlement-floors-at-zero-and-logs, payload-mismatch-on-reused-id-refused,
  replayed-adjustment-does-not-double-credit, settled-commitment-cannot-be-recreated,
  live-balance-never-negative-under-either-new-path.
- `RefillPolicyTest`: `periods > MAX_CATCHUP_PERIODS` boundary-advances-only-by-applied-periods,
  and a second later `apply()` continues crediting the forfeited remainder.
- New `DealerBudgetStore`/`DealerBudgetService` test: one-time LIMITED seed
  fires exactly once across save/reload; a baseline config edit after seeding
  does not move live money.
- `DealerBudgetSettingsTest` (or wherever settings parsing is tested):
  explicit invalid `mode:` value fails closed (`isUsable() == false`,
  `CONFIGURATION_INVALID` on admission), distinct from an absent `mode:`
  (still `UNLIMITED`).
- Baccarat: failed inventory withdrawal does not record/send a wager (the B1
  regression test); rebet is atomic and bank-gated (checks admission +
  debit, not just debit); undo-last/undo-all reconciles the portfolio
  reservation exactly.
- Roulette: insufficient funds / bank denial create no dealer credit or
  reservation (order-of-operations test); undo-last/undo-all reconciles the
  portfolio.
- Blackjack: second/third opening chip credits its own real stake exactly
  once (catches the zero-stake bug); repeated failed double/split/insurance
  attempts mint nothing (catches the counter-based-id bug); failed
  split/insurance debit correctly refunds rather than forfeits (catches the
  `releaseLoss`-vs-`refund` bug); undo-last/undo-all reconciles the opening
  commitment.
- Shared: failed settlement persistence retains the same known result and
  commitment in every game (can be one parametrized test exercising the
  reconciliation surface from §3.3 per game, rather than 8 separate ad hoc
  tests).
- Item rounding: one exact amount flows through exposure/ledger/delivery/
  retry, per game (4 tests, one per game, once §3.6 lands).
- Vault/custom payout failure: becomes exactly one durable pending
  obligation, no duplicates — per newly-fixed call site.
- Slots: the RTP simulation test from §3.7 (all reel widths × variance
  levels × denomination 1 specifically).
- `/ncc claim`: a non-op player without `nccasino.use` can run `claim`;
  `create`/`delete`/`reload` remain refused for the same player.
- Regression guard: existing UNLIMITED-mode tests continue to pass unchanged
  after every fix above (every kernel method must remain a no-op for
  UNLIMITED — verify this explicitly wasn't broken by the settle()/adjustReservation
  changes, since those are the two methods being restructured).

---

## 6. Audit 2 — pre-branch (`2e6298c`) vs. current (`ae00900`) regression audit

Full diff: 138 files, +18,489/−338. New: Slots game, `budget/*` (Phase 2),
`payout/*` overflow banking. One structural fact bounds most of this section:
`DealerBudgetService` short-circuits on `UNLIMITED` in every method, and
every pre-existing dealer is `UNLIMITED` — so budget-reservation defects
below only bite in the new opt-in LIMITED mode; the overflow bank, by
contrast, is unconditionally live for every dealer today.

### 6.1 GUI/inventory-layout verdict

**No pre-existing game's interior GUI changed.** Diffing every non-Slots
file under `games/` for `setItem`/`createCustomItem`/`Bukkit.createInventory`/
slot constants returns zero added/removed lines — Baccarat, Roulette,
Blackjack, Mines, Dragon Descent slot maps are byte-identical to `2e6298c`.
Menu changes are confined to game-selection/admin surfaces and are additive
only: `GameOptionsMenu` creation menu gets `SLOTS` at a previously-empty slot
7 (edit menu resized 9→18, `SLOTS` added at 8, `EXIT` moved 8→9, no existing
game's slot moved); `PreferencesMenu` gets `OVERFLOW` at a previously-empty
slot 4; `AdminMenu`/`MobSelectionMenu` get additive Slots branches with no
existing branch altered.

### 6.2 Classified findings, ranked

1. **🔴 B1 — Baccarat free bets (highest severity, reachable today, not
   LIMITED-gated).** Covered in full in Audit 1 finding 2a above. This is a
   **pre-existing-exposed-and-materially-worsened** defect: `Client.java`'s
   `hasEnoughWager` pre-check historically masked the discarded-return-value
   bug in practice; this branch's `WagerGate` introduces a **new** failure
   mode (bank-blocked) that the pre-check cannot see, making the pre-existing
   discard actually reachable for the first time. Every other game
   (Blackjack, Mines, Roulette, CoinFlip, RPS, `Client`'s own base path)
   already checks this same return value — Baccarat is the sole outlier.
2. **🟠 Roulette `MAX_ITEM_MODE_PAYOUT` 10,000 → `Integer.MAX_VALUE`**
   (`BettingTable.java:1310`) — **resolved, keep** per §2 decision 2.
3. **R1 — Roulette reserves before the funds check with no rollback on
   failure**, plus **Roulette undo-last never releasing budget**, plus
   **Baccarat rebet skipping the budget check entirely** — all
   **new-feature bugs introduced by the budget package**, LIMITED-mode only,
   covered in Audit 1 finding 2a.
4. **S1/S2/S3 — misleading/silent wager-refusal UX** across every
   `Client`-based game: `WagerGate`'s own "wager blocked" message is
   immediately followed by a generic "insufficient funds" message from the
   caller (contradictory double-message); all-in and cursor-drag paths fail
   **silently** (no message/sound) when the bank gate blocks them. **Introduced**,
   cosmetic/UX only, no money at risk — worth fixing but not urgent.
5. **S13 — `build.gradle`'s `forkEvery = 8` removed.** Test-JVM isolation
   hygiene only (no runtime effect); with ~40 new test classes holding static
   state (`WagerGate.LAST_NOTICE`, `AdminMenu.adminInventories`) this raises
   cross-test-leakage risk during the verification gate in §7 — worth
   restoring or confirming its removal was deliberate before relying on a
   single-JVM test run's results being fully isolated.
6. **S7 — `PendingPayoutStore.depositItems` returns "fully retried" if
   `getOverflowBankService()` is null.** Currently unreachable (service is
   constructed before any listener registers) — flagged as
   **pre-existing-exposed (latent)**, not urgent, but worth a defensive
   comment or assertion so a future refactor of `onEnable` ordering doesn't
   silently reintroduce a real bug here.
7. **Blackjack CUSTOM-currency-mode payout uses plain material items instead
   of vanishing (old `createCurrencyStack` returned `AIR`).** Classified
   **pre-existing-exposed, currently unreachable** — `CustomChipCurrencyProvider`
   is a Phase-0 stub whose `has()`/`withdraw()` make CUSTOM mode unplayable
   today, so this is inert, not a live bug. No action needed now; note it so
   whoever implements a real custom-currency provider later checks this path.

### 6.3 Everything else examined

Every other diffed area (shared Client/Server payout helpers, dealer
interaction/opening, join/quit/kick/shutdown/plugin-disable,
`PendingPayoutStore`'s new partial-delivery/shrink behavior, Preferences,
commands/permissions additions, Mines, Dragon Descent, Coin Flip PvP+PvE,
Rock Paper Scissors PvP+PvE) is **intended** (the approved overflow
banking/capped-drops/universal-bank-gate/overflow-preference behavior) or a
**genuine fix of a real pre-existing money-loss bug** already present in this
branch (Roulette's old silent 1,000,000-item truncation that actually lost
money; Blackjack's old double-queued-refund bug that could double-pay; old
`Client.refundCurrency` destroying a stake that didn't fit in a full
inventory instead of banking it). None of these need reverting — they are
correctly classified as approved/beneficial changes, not regressions.

---

## 7. Exact verification commands for whoever implements this

```
./gradlew clean test localizationCheck build --console=plain
git diff --check
```

(Use `.\gradlew.bat` instead of `./gradlew` on a Windows checkout — same
targets.) Run the narrowest relevant test class while iterating on each fix
in §3 (e.g. `./gradlew test --tests "*DealerBudgetStoreTest*"`), then the
full gate above before considering the work done. `git diff --check` catches
stray whitespace/conflict-marker issues before committing. No live
Minecraft-server verification has been performed for anything in this
document — Bukkit-mocked unit/integration tests are the only verification
available in this environment; anything requiring an actual server
(animation timing, real inventory-click sequencing, real Vault plugin
interaction) needs a manual in-game pass that this session could not perform.

---

## 8. Remaining open items (not product decisions — just not yet done)

- No further product/GUI decisions are outstanding; §2 resolved the only two
  found during this audit.
- `build.gradle`'s `forkEvery = 8` removal (§6.2 item 5) is worth a quick
  "was this deliberate?" check with whoever authored that commit before the
  verification gate is trusted at face value, since static test state exists
  in this codebase.
- Slots variance GUI/preview (`SlotsVarianceStats`) is explicitly deferred
  per `docs/DEALER_BUDGET_SCHEMA.md` itself — not this audit's concern, just
  noting it's a known, already-documented gap, not a defect.

---

## 9. Next-session starting prompt

> Resume the Bounded Liability implementation on `slots-overhaul`
> (HEAD `ae00900` at handoff time, merge-base with `origin/main` is
> `2e6298c`). Read `docs/BOUNDED_LIABILITY_AUDIT_HANDOFF.md` in full first —
> it contains a complete, independently-verified Audit 1 (§1), the two
> resolved product decisions (§2), a concrete kernel-and-per-game fix design
> (§3), a recommended implementation order (§4), the required new tests (§5),
> and a separate Audit 2 regression report (§6). Start with §3.1's kernel
> fixes in `src/main/java/org/nc/nccasino/budget/` (Settlement clamp,
> idempotency, invalid-mode fail-closed, refill catch-up boundary), verify
> each with its narrow test class, then proceed through §4's order —
> Baccarat (§3.4) is the single highest-priority item since it is the only
> confirmed, currently-reachable (non-LIMITED-gated) money bug in the whole
> audit. Do not alter any existing game's GUI/inventory layout without
> presenting the exact proposal first — confirmed unchanged by Audit 2 §6.1
> and must stay that way except where a specific fix in §3 explicitly
> requires a new chat/log message (never a slot/item change). Finish with the
> verification gate in §7, then commit and push `slots-overhaul` following
> this repository's normal commit-confirmation rules.
