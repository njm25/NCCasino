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
version: 1
dealers:
  highroller:
    live-balance: "1000.000000"
    refill-boundary: 1735689600
    reservations:
    - id: "highroller|<player-uuid>|spin-7"
      player: "<player-uuid>"
      game: "Slots"
      amount: "250.000000"
      created: 1735689600
      currency-mode: STANDARD
      currency-material: EMERALD
      currency-name: "Casino Token"
```

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

### `refill-boundary`

Epoch seconds of the last *applied* refill period. It advances by whole
periods only, never to "now". Two consequences:

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
