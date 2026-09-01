# Dealer budget: stored schema and recovery

Phase 2 of `BOUNDED_LIABILITY_DESIGN.md`. This document is the reference for
what NCCasino stores about a limited dealer's money, why it is stored that
way, and what happens when a file is damaged, hand-edited, or written by an
older version.

## Two files, two meanings

Dealer economics are deliberately split from dealer configuration.

| Where | What it holds | Who writes it |
| --- | --- | --- |
| `config.yml` → `dealers.<name>.budget` | Risk *policy*: mode, underwriting baseline, guaranteed rounds, refill settings | An administrator |
| `data/dealer-budgets.yml` | *Money*: live balance, active reservations, refill clock | NCCasino |

Editing policy never moves a balance, and a balance change never rewrites
configuration. NCCasino does not rewrite `config.yml` because a value in it is
invalid — it reports the problem and fails closed for that dealer instead.

## Configuration

```yaml
dealers:
  highroller:
    budget:
      mode: UNLIMITED              # UNLIMITED | LIMITED
      underwriting-baseline: 5000  # LIMITED only
      guaranteed-worst-case-rounds: 1
      refill-mode: NONE            # NONE | ADD | RESET
      refill-amount: 100           # ADD
      refill-period: 1h            # ADD, RESET
      refill-cap: 10000            # ADD, optional
      reset-target: 5000           # RESET
```

An absent `budget:` block means `UNLIMITED` with no refill, which is exactly
pre-Phase-2 behavior. Every existing dealer therefore keeps working unchanged
until an administrator opts it in.

### Validation

Invalid values are reported once per dealer per session with the exact
configuration path, and never damage stored balances:

- An unreadable `mode` falls back to `UNLIMITED`.
- A `LIMITED` dealer with no usable `underwriting-baseline` is **unusable**: it
  refuses every commitment with `CONFIGURATION_INVALID` rather than
  underwriting real money on a guessed baseline.
- `guaranteed-worst-case-rounds` below 1, non-numeric, or absent falls back to
  1; an implausibly large value clamps to `Integer.MAX_VALUE`.
- An unusable `refill-amount` or `reset-target` disables that refill; it never
  wipes or reduces a funded dealer.
- A `refill-period` shorter than 60s clamps to 60s.

## `data/dealer-budgets.yml`

```yaml
version: 2
dealers:
  highroller:
    live-balance: "1000.000000"
    refill-boundary: 1735689600
    baseline-initialized: 1735689600
    reservations:
    - id: "highroller|<player-uuid>|spin-7"
      player: "<player-uuid>"
      game: "Slots"
      amount: "250.000000"
      created: 1735689600
      currency-mode: STANDARD
      currency-material: EMERALD
      currency-name: "Casino Token"
    settled:
    - id: "highroller|<player-uuid>|spin-6"
      settled-at: 1735689650
    shortfalls:
    - reservation-id: "highroller|<player-uuid>|spin-9"
      amount: "499.000000"
      recorded-at: 1735689700
```

`settled` is the bounded tombstone list (see Idempotency, below): commitment
ids that have already been settled and must never be recreated as a fresh
reservation. `shortfalls` is new in version 2 (see below). A version-1 file
(no `settled`/`shortfalls` keys) loads unchanged, with both lists starting
empty -- both additions are purely additive and never invalidate an older
file.

### Why amounts are strings

Every monetary value is a plain decimal string, not a YAML number. A YAML
number round-trips through a binary `double`, which is precisely the
representation this system exists to avoid: repeated credit and debit of
`double` balances drifts, and a dealer eventually holds `999.9999999999999`
and refuses a payout it can obviously afford. Values are held as `BigDecimal`
at scale 6 (`Money.SCALE`) and written with `toPlainString()`, so no exponent
notation reaches the file.

### Why reservations are a list

Reservation ids contain `|` and may contain `.`, both of which a Bukkit
configuration path would split. Storing them as a list avoids escaping
entirely.

### `baseline-initialized` is not `refill-boundary`

These are deliberately two separate fields, gating two unrelated things.

`baseline-initialized` answers exactly one question: *has this LIMITED
dealer's one-time underwriting-baseline seed ever been applied?* Nothing
else sets it, and nothing else reads it. `refill-boundary` (below) answers a
different question -- *what is the last ADD/RESET period this dealer has
been credited for?* -- and can become positive from ordinary refill activity
that has nothing to do with the baseline seed.

Earlier code used `refill-boundary > 0` as a stand-in for "already seeded,"
reasoning that `ensureInitialFunding` itself sets the boundary at seed time.
That reasoning breaks on migration: a `dealer-budgets.yml` written before
`ensureInitialFunding` existed (or a file from a dealer that was manually
funded and then refilled) can have a positive `refill-boundary` while never
having received the baseline floor. Under the old conflated check, such a
dealer would be permanently, silently denied its one-time seed. A file
without a `baseline-initialized` key loads it as `0` ("never seeded")
regardless of what `refill-boundary` says, and `ensureInitialFunding` re-runs
against it exactly once. Because it only ever raises the balance with
`max(liveBalance, baseline)`, applying it to an already-healthy dealer during
migration is a safe no-op, never a double-mint; applying it to a dealer that
happens to be under baseline gives it the one-time floor the design always
intended it to have. Once applied (during migration or otherwise), the
marker is set and blocks every future call, exactly as before.

Seeding still starts the refill clock (`refill-boundary`) the first time it
runs -- but only when that clock has genuinely never been touched
(`<= 0`). An already-running ADD/RESET clock picked up during migration is
never reset back to "now", which would otherwise discard legitimately
accrued refill periods.

### `refill-boundary`

Epoch seconds of the last *applied* ADD/RESET refill period. It advances by
whole periods only, never to "now". Two consequences:

- A restart cannot re-apply a period that was already applied.
- Frequent access cannot drift the schedule forward and starve a refill. A
  dealer touched at 59 minutes past every hour still refills hourly.

`0` means the clock has never started; the next access starts it and grants
nothing, so a newly created dealer is not handed back-dated refills.

## The invariant

One line, from which everything else follows:

> `reservedTotal <= liveBalance`, and both are non-negative.

No double payment, no negative dealer, and no reservation leak are not
separate mechanisms — they are consequences of maintaining this on every
operation, including operations that fail partway.

### Settling one commitment never weakens another's backing

`settle` never debits more than what is left of `liveBalance` once every
*other* still-active reservation is left fully covered — never the whole
`liveBalance`. Concretely, if a dealer holds 500 live with reservation A at
300 and reservation B at 200, and A is settled for a (buggy, oversized)
payout of 999, at most 300 can be paid out of the dealer's economy: B's 200
remains fully backed afterward, live balance ends at 200, and the invariant
above holds throughout. The player is still paid the full 999 by the
game/delivery layer regardless — `Settlement.paid()` is never reduced — but
699 of that payout had no backing in the dealer's economy. That gap is
recorded as a **shortfall** (see below) rather than silently absorbed by
starving another player's reservation or by flooring the whole dealer to
zero. This is what stops a single oversized settlement from ever producing
`reservedTotal > liveBalance`.

## Shortfalls

A shortfall record (`Settlement.insolvent() == true`) means a settlement's
full payout could not be covered without consuming money already promised to
another active reservation. It is written to the `shortfalls` list, keyed by
the settled reservation id, in the same persisted mutation as the settlement
itself, so it can never be lost or recorded without the settlement that
caused it.

Unlike the `settled` tombstone list, `shortfalls` is **never bounded and
never evicts** an unresolved record: a shortfall is unresolved economic debt,
and a bounded, oldest-evicted-first cap (appropriate for tombstones, which
exist purely as replay guards and accumulate on every ordinary settlement)
would silently destroy the exact information an administrator needs to
reconcile the dealer's books. Since a reservation id is tombstoned the
instant it settles and can never be recreated, at most one shortfall can ever
exist per id, so unbounded growth here tracks real, rare anomalies rather
than routine traffic.

A shortfall is cleared only by an explicit `DealerBudgetStore#resolveShortfall`
call once an administrator has actually restored the missing backing (e.g.
via a manual deposit) — never automatically, and never merely because time
has passed. Like `staleReservations`, this is a durable, honest signal for an
administrator to act on, never something the system resolves on its own or
hides by reporting the dealer's books as healthy when they are not.
`resolveShortfall` is idempotent (resolving an already-resolved or
never-recorded id is a harmless no-op) and persists atomically: if the write
fails, the shortfall record is restored exactly as it was, so a caller can
never believe a shortfall is resolved when it is not durably so.

## Durability

Writes go to `dealer-budgets.yml.tmp` and are then moved over the target with
`ATOMIC_MOVE` (falling back to a plain replace only where the filesystem
refuses one). A crash mid-write leaves the previous complete file rather than
a truncated one.

Every mutation persists **before** it is reported successful. If the write
fails, the in-memory state is rolled back to its exact prior value, so a
caller can never act on a reservation that is not on disk. A failed
settlement leaves the reservation held and is logged for reconciliation —
never reported as paid.

## Idempotency

A reservation's `id` is derived from the commitment's identity
(`Reservation.forCommitment`), not generated per attempt. Everything that
makes the system safe against duplicate money follows from that:

- `creditAndReserve` with an existing id returns the existing reservation and
  credits the stake **once**.
- `settle` removes the reservation as part of settling, so a replayed
  settlement finds nothing and reports `ALREADY_SETTLED` — a success that
  moves no money.

That is what makes a reconnect, a duplicated Bukkit event, a retried payout
delivery, or a shutdown replay safe. A caller that generates a fresh `UUID`
per *attempt* rather than per *commitment* defeats it entirely.

## Recovery

| Situation | Behavior |
| --- | --- |
| File absent | Every dealer starts at zero balance with no reservations. |
| Unreadable `live-balance` | Treated as 0 and logged; reservations are still honored. |
| Malformed individual reservation | Skipped with a warning; the rest of the file loads. |
| `reservedTotal > liveBalance` | **The reservations win.** The balance is raised to cover them and the discrepancy is logged. |

The last row is the important one. Reservations are promises already made to
real players, so they are never deleted to satisfy an accounting check. A
temporarily over-funded dealer is a far better failure than a cancelled
payout, and silently deleting economically meaningful state is forbidden
outright by the design.

Stale reservations (`staleReservations`) are **reported**, never
auto-released. Releasing a promise is an operator decision.

## RESET and active reservations

The reset target is the dealer's **total live balance**, not its free balance.

A dealer with 900 reserved and a target of 1000 ends a reset with 1000 live
and 100 free — not 1900. A reset also never drops the balance below what is
already reserved: with 4200 reserved and a target of 1000, the balance is held
at 4200 and no promise is invalidated.

The alternative reading (target as *free* balance) was rejected: it would let
a player holding a large open commitment time a reset to mint the house extra
funding.

## Unlimited dealers

`UNLIMITED` is not modelled as a very large balance. A fake enormous number
would still pay the cost of every reservation, settlement and disk write, and
would eventually overflow or drift. Instead every entry point checks the mode
first and returns immediately, so an unlimited dealer costs one string read
per call and touches no economic state at all.

## Slots variance backend

`dealers.<name>.slots-variance` selects one of `STEADY`, `LOW`, `BALANCED`
(default), `HIGH`, `HIGH_ROLLER` (`SlotsVariance`). House edge and variance
are independent: variance redistributes *how* the configured return-to-player
arrives (frequent smaller payouts vs. rarer larger ones); it never changes the
configured edge itself.

Each level fixes two things:

- **Sampling weights** (`SlotsVariance.weights()`) — the actual per-reel
  symbol frequencies, consumed by `SlotsSpinGenerator` for every committed
  spin. This is what makes hit frequency real: a rarer-hitting level really
  samples blanks more often, it does not just display a paytable that claims
  it does.
- **Length base** (`SlotsVariance.lengthBase()`) — how much more valuable
  each additional matched reel is. Raising it concentrates payout into
  full-width runs.

`SlotsPaytable.forConfig(columns, houseEdge, variance)` renormalizes whatever
raw shape those two parameters produce back down to exactly
`1 - houseEdge`, for any variance and any supported width — this is the same
renormalization step the original fixed-shape paytable always used, so RTP
preservation is structural, not level-specific. `SlotsVariance.BALANCED`
reuses `SlotsSymbol`'s own historical weights and the original length base of
6.0, so it reproduces the machine's pre-variance behavior exactly; every
2-argument `SlotsPaytable`/`SlotsSpinGenerator` overload defaults to it.

An unrecognized `slots-variance` value falls back to `BALANCED` with a logged
warning and never rewrites the stored config value.

**Not done in this pass:** no Slots GUI shows variance, a preview, or a
per-level comparison — `SlotsVarianceStats` exists as ready-to-render data for
that, but adding it to `SlotsInventory` or the admin settings menu is a GUI
change requiring separate approval per the design's GUI-approval rule.

## Blackjack: one reservation per hand, not per portfolio

Blackjack differs from Roulette/Baccarat (one portfolio reservation) and
Mines/Dragon Descent/the chain games (one reservation for the whole session):
a seat can hold several simultaneously-open hands after a split, each with
its own independent card sequence and its own independent settlement. So
Blackjack reserves **one commitment per `BlackjackHand.getHandId()`**, plus a
separate one for insurance (a side bet independent of the hand outcome).

- **Opening wager**: reserved while chips are still being added, keyed by the
  player (no hand exists yet) — moved onto the hand's own id the instant
  `ensureActiveHand` creates it (`BlackjackInventory.claimPendingOpeningCommitment`).
- **Split**: opens a brand-new reservation for the sibling hand
  (`BlackjackLiability.splitHand`) — never grows the hand it split from,
  which keeps its own reservation exactly as it was.
- **Double**: grows the acting hand's existing reservation
  (`BlackjackLiability.doubledHand`) — a doubled hand can never be a
  natural (three cards), so its ceiling is lower than a fresh hand's would be.
- **Insurance**: its own reservation, independent of the hand — a hand and its
  insurance can both pay in the same round.
- **Settlement**: exactly one call per hand, in `settleHandOutcome`, using
  `hand.getWager() * outcome.getMultiplier()` — this single expression is
  correct for all five outcomes (2.5x blackjack, 2x win, 1x push, 0x
  loss/bust) because `BlackjackOutcome`'s multipliers already encode exactly
  that. Insurance settles separately in `payInsuranceWinners`/
  `forfeitInsuranceStakes`.
- **Abandonment** (kick, voluntary leave, disconnect-refund, shoe-exhaustion
  abort): every open hand and insurance reservation for the affected seat(s)
  releases through one shared helper, `releaseAllBudgetCommitments` — paying
  each its own real stake back on a refund, or nothing on a forfeit. A
  disconnect that can still ride to a real result (`RIDE_TO_RESULT`) touches
  nothing; the round resolves normally into `settleHandOutcome` on schedule.
