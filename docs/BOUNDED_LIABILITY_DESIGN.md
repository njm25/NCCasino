# NCCasino design note: Bounded Liability

## Status

Design draft for approval before implementation.

This document defines the intended product behavior for safe payout delivery,
dealer token inventories, automatically derived wager limits, Slots risk
levels, and the later player-owned dealer system.

The main idea is simple:

> Before NCCasino accepts a wager, or any later decision that increases the
> stakes, it must know the most the dealer could owe and whether that
> commitment can be covered.

Slots is the most urgent example because a tiny wager can create a very large
payout, but the resulting system applies across every game.

---

## 1. The two problems

There are two separate questions that must not be conflated.

### Can the payout be physically delivered?

An item-backed payout may be larger than the player's inventory. Dropping all
overflow on the ground risks theft, despawning, and excessive entities.

This is a delivery-capacity problem. It is solved by safe partial delivery and
an overflow bank.

### Can the dealer afford the result?

A limited dealer may not possess enough tokens to honor the largest possible
result of a wager.

This is a house-liability problem. It is solved by a dealer token inventory,
reservations, and pre-commitment checks.

A dealer with unlimited funds can still have a physical delivery problem. A
player with an empty inventory can still be wagering against an underfunded
dealer. The two systems work together, but they remain conceptually separate.

---

## 2. The commitment rule

A legitimate win must never be silently clamped after the result is known.

If NCCasino cannot honor a possible result, the wager or decision that creates
that exposure must not be accepted.

The check occurs:

- Before a Slots spin
- Before adding Roulette or Baccarat bets
- Before the opening Blackjack hand
- Before Blackjack insurance, a split, a resplit, or a double
- Before a Mines pick that would raise the cash-out value
- Before another Dragon Descent floor or choice
- Before another Coin Flip or Rock Paper Scissors chain round

Once the game accepts a commitment, it owns the result and must settle it.

---

# Part I: Safe payout delivery

## 3. The current item-delivery problem

A normal player inventory has 36 storage slots. With a currency that stacks to
64, an entirely empty inventory holds 2,304 items. The current 10,000-item
ceilings already exceed that by more than four full inventories.

Overflow currently falls to the ground in several payout paths. A player can
lose legitimate winnings to despawning, theft, or simple confusion even though
the game considers the payout complete.

Slots exposes the problem most dramatically. Its possible payouts are large
enough that item mode currently rejects many wagers before a spin can start.

## 4. The overflow bank

NCCasino will introduce a persistent overflow bank for item-backed winnings.

The bank has one narrow purpose:

> Preserve winnings that belong to the player but could not safely be placed
> in their inventory or dropped nearby.

It is a delivery buffer, not a general wallet.

Players cannot manually deposit into it. Ordinary payouts are not routed there
when they fit in the player's inventory. It is used only for genuine delivery
overflow.

### Delivery order

When NCCasino awards an item-backed payout:

1. Put as much as safely fits into the player's inventory.
2. Apply the configured overflow preference to the remainder.
3. If dropping is preferred, drop only up to the server's fixed safety limit.
4. Record everything still undelivered in the overflow bank.
5. Tell the player what was delivered, dropped, and banked.

Nothing is deleted.

### Example

A player wins 10,000 emeralds and has room for 512.

- 512 enter the inventory.
- If banking is preferred, 9,488 are banked.
- If dropping is preferred, a bounded amount is dropped and the rest is
  banked.

The payout is settled once the remainder has been recorded in the bank.
Claiming it later does not charge the originating dealer again.

## 5. The bank is separate from unresolved outcomes

NCCasino already preserves pending outcomes when a player disconnects, the
server stops, or a live deposit fails.

Those records mean:

> This game result still needs to be delivered.

The overflow bank means:

> This money already belongs to the player and is waiting for physical space.

The two systems remain separate. When a pending item payout is eventually
delivered, any part that does not fit moves into the overflow bank.

The first release does not require a broad transaction-journal overhaul. The
new bank should use reliable persistence, duplicate-safe record creation,
clear logging, and focused tests. More advanced crash-recovery guarantees can
be revisited later if needed.

## 6. Banked winnings block all wagering

Any nonzero overflow-bank balance blocks all new NCCasino wagers, regardless
of which currency is banked or which currency the next dealer uses.

Examples:

- Banked emeralds block a diamond wager.
- Banked diamonds block an emerald wager.
- A bank balance earned in Slots blocks Roulette, Blackjack, and every other
  game until it is cleared.

This deliberately treats the bank as a temporary delivery exception rather
than a protected secondary account.

Before accepting any wager, NCCasino automatically tries to deliver all banked
items. If everything fits, play continues. If anything remains, the wager is
blocked.

For the bank-blocked state, the barrier is shown inside the casino game's GUI
inventory: the dealer/game menu the player is currently viewing. It is not
placed into or shown as a modification of the player's normal survival
inventory.

Example game-GUI barrier text:

> Make room for {amount} banked items before playing again.

The exact wording and placement will be approved with the affected game's UI
change.

## 7. Automatic delivery opportunities

NCCasino attempts to deliver banked winnings:

- When the player joins
- When the player opens a dealer or game using NCCasino
- Immediately before any wager is accepted
- When the player manually requests a claim

A configurable periodic reminder is informational only. It tells the player
that winnings remain banked, but it does not attempt delivery or modify the
player's inventory.

## 8. Overflow preferences

Server policy controls how overflow is preferably handled:

- `PLAYER_CHOICE`: each player chooses Bank or Drop.
- `BANK`: all overflow is banked.
- `DROP`: overflow is preferably dropped up to the fixed safety limit; the
  remainder is banked.

“Always drop” is intentionally not used as a label because the server never
authorizes deletion of excess winnings.

A player's preference is stored independently of the current server mode. If
the server temporarily forces banking and later returns to player choice, the
player's prior preference returns. Players who never made a choice inherit the
server default.

A fresh installation defaults to player choice with Bank selected.

## 9. Drop limits

The drop limit exists primarily for recoverability. It also prevents
unnecessarily large entity bursts.

The limit should account for the currency material's real stack size instead
of assuming every currency stacks to 64. A default equivalent to one empty
player inventory, 36 stacks, is a reasonable starting point.

The limit is fixed and configurable. It does not silently move with TPS or
server load.

## 10. No expiry and no destructive bank cap

Banked winnings do not expire. They are real money that the player won.

There is no cleanup task that deletes them, no transfer of forgotten winnings
back to a dealer, and no maximum bank balance that destroys excess value.

The universal wagering block prevents repeated accumulation without
confiscating legitimate winnings.

---

# Part II: Slots

## 11. Why Slots is the urgent case

The Slots overhaul supports three, five, or seven reels, up to nine active
paylines, a configurable house edge, and a paytable derived to land on the
configured return-to-player target.

At the current balanced payout shape and a 3% house edge, the approximate
maximum gross return relative to the total stake is:

| Reels | Maximum gross return |
| ---: | ---: |
| 3 | 421x |
| 5 | 6,422x |
| 7 | 136,437x |

The previously discussed 6,422x and 136,437x figures are both correct. They
refer to five-reel and seven-reel machines respectively.

Because every active payline carries its own stake, adding lines increases
both the total bet and the maximum payout. The largest return relative to the
total stake is driven primarily by the reel width and the payout shape.

## 12. Removing the current item ceiling

Once overflow banking exists, physical inventory size no longer justifies the
Slots 10,000-item ceiling.

The revised spin flow is:

1. Attempt to clear any overflow-bank balance.
2. Validate the selected denomination, lines, and reel configuration.
3. Check the dealer's current ability to cover the spin.
4. Withdraw the total wager.
5. Commit the result and payout.
6. Play the reel animation.
7. Deliver what fits and bank the remainder.

Item mode no longer rejects a spin merely because its possible payout exceeds
one inventory or 10,000 items.

Numeric and precision safety limits remain. Banking removes a delivery limit;
it does not authorize values the currency system cannot represent.

## 13. Stable paytables

A dealer's paytable does not silently change because its token balance falls.

If a dealer cannot currently cover a wager or configuration, that option is
temporarily unavailable. The displayed odds and payouts remain unchanged.

This preserves the rule:

- Narrowing an available option is a visible casino constraint.
- Quietly reducing a promised payout is misleading.

Any visual change to an existing game, including barriers, disabled controls,
or layout changes, must be proposed and explicitly approved before it is
implemented. The design does not grant blanket permission to redesign every
existing game inventory.

Existing chat and return-message patterns may continue where they communicate
the rule clearly. Item lore is preferred when it naturally fits the current
interface, but it is not mandatory for every constraint.

## 14. Slots risk and variance levels

House edge and variance are separate settings.

- House edge controls the machine's expected long-term return.
- Variance controls whether that return arrives through frequent smaller
  payouts or rare large jackpots.

The intended Slots design supports approximately five named levels:

1. `STEADY`: frequent smaller returns and the lowest top multiplier.
2. `LOW`: modest variance with fewer large spikes.
3. `BALANCED`: the general-purpose default and current design reference.
4. `HIGH`: rarer wins and substantially larger jackpots.
5. `HIGH_ROLLER`: a very low chance of an exceptionally large payout.

The final implementation may use between three and six levels if testing
shows that some adjacent levels do not feel meaningfully different.

Every level preserves the configured return-to-player target. Changing the
level changes the shape of the paytable, not the house edge.

The settings preview should show understandable consequences such as:

- Relative hit frequency
- Top multiplier
- Maximum possible payout at the dealer's configured denominations
- Which denominations or reel configurations the dealer can underwrite

A high-roller machine is intentionally difficult to fund. That tradeoff is
part of the casino's character rather than something the system should
optimize away.

---

# Part III: Dealer token inventories

## 15. One budget system across every game

A limited dealer has a persistent token inventory representing the money it
can use to operate its game.

This is shared dealer machinery, not Slots-only logic.

Across every game:

- Player wagers enter the dealer token inventory.
- Payouts leave the dealer token inventory.
- Tokens reserved for active commitments cannot be promised twice.
- A wager or escalating decision is accepted only when its possible result
  can be covered.
- The dealer can never go negative.

Existing server-owned dealers default to `UNLIMITED`, preserving current
behavior. Administrators may opt individual dealers into `LIMITED` mode.

## 16. No separately configured maximum wager

There is no independent `max-bet` or `max-round-stake` setting in this design.

The maximum safe wager is automatically derived from:

- The dealer's fixed underwriting baseline
- The game's maximum possible house loss for the wager
- The configured guaranteed-worst-case-round count
- The dealer's current unreserved token balance
- Numeric and currency safety limits

Existing configured chip denominations remain the actual wager choices shown
to players. The budget system determines which of those choices the dealer is
capable of underwriting.

This keeps the administrator-facing concept focused on funding and risk rather
than requiring a separate arbitrary wager ceiling.

## 17. The underwriting baseline

Continuously recalculating wager sizes from a falling live balance would cause
a death spiral: every loss would produce a slightly smaller allowed wager,
which would shrink again after the next loss and gradually approach zero.

NCCasino will not continuously resize wagers that way.

Instead, every limited dealer has a fixed underwriting baseline. Initially,
this is the balance or funding tier from which the dealer's risk capacity was
established.

The guaranteed-worst-case-round calculation uses that fixed baseline. It does
not recalculate downward whenever the live balance changes.

### When the live balance falls

The dealer keeps the same configured wager denominations and payout table.

If the current unreserved balance can no longer cover a particular wager,
that wager becomes temporarily unavailable. When the balance recovers, the
same wager becomes available again.

The system does not invent progressively smaller wager amounts.

### When the live balance rises

The first implementation keeps the original underwriting baseline and wager
capacity. Extra earnings provide a larger safety cushion but do not
automatically raise the stakes.

An administrator may deliberately rebase or upgrade the dealer later. An
automatic upward-ratcheting policy can be considered after the fixed model is
proven, but it is not required for the initial design.

This produces predictable machines: wagers remain stable, temporary depletion
causes clear unavailability, and recovery restores the known options.

## 18. Guaranteed worst-case rounds

The owner-facing risk setting is:

> Guaranteed worst-case rounds

A value of `1` means the underwriting baseline may support a wager whose worst
possible net result could consume the full baseline.

A value of `10` means the wager capacity is derived so that the baseline could
absorb approximately ten consecutive worst-case results of that type.

This is a floor, not an expected lifespan. Most results are not worst-case,
player losses add tokens to the dealer, and the house edge ordinarily pulls
the long-term balance upward.

Because the calculation uses the fixed underwriting baseline rather than the
changing live balance, it does not continuously shrink toward zero.

The current balance remains the final hard check. A dealer never accepts a
commitment it cannot pay merely because the original baseline was larger.

## 19. Reservations and settlement

When a wager is accepted, the stake enters the dealer inventory and the dealer
reserves the largest payout currently possible from that commitment.

The dealer does not reserve imaginary future actions.

For Blackjack:

- The opening wager reserves the opening hand's possible payout.
- A future split is not reserved yet.
- The split is checked and reserved only when the player attempts it.
- A future double follows the same rule.

At settlement:

- A player loss leaves the wager with the dealer and releases the unused
  reservation.
- A win consumes the required payout.
- A push or cancellation returns the appropriate stake.
- Any unused reservation is released.
- An item payout moved into the overflow bank leaves the dealer immediately;
  claiming it later has no dealer-budget effect.

## 20. Server-owned dealer refill policies

Limited server-owned dealers may receive scheduled funding. The design allows
two clearly different policies.

### Additive refill

`ADD` contributes a configured number of tokens per period, optionally up to a
cap.

Example: add 100 tokens every hour until the live balance reaches 10,000.

This behaves like a token bucket and allows player losses and house earnings
to accumulate naturally.

### Reset funding

`RESET` sets the dealer's operating balance to a configured target at the
scheduled interval. It is useful when an administrator wants the dealer to
receive a fresh fixed allowance each hour, day, or week rather than
accumulating indefinitely.

Active reservations are always honored. A reset never invalidates money
already promised to active games. The exact treatment of surplus around
reserved funds must be tested and made explicit in the administrator UI.

### No refill

`NONE` leaves the dealer entirely dependent on its starting balance and player
wagers.

Refills are calculated lazily when the dealer is accessed; they do not require
a constantly ticking task.

The underwriting baseline remains separate from the changing live balance. A
refill restores availability but does not silently change the dealer's wager
tier.

---

# Part IV: Liability by game

## 21. Single-commitment games

Slots, Roulette, and Baccarat create their main exposure when a wager is
accepted.

### Slots

The check considers reel width, active paylines, per-line wager, house edge,
variance level, and the maximum simultaneous line result.

### Roulette

The check evaluates the player's complete table against every possible wheel
result because several placed bets may win together.

### Baccarat

The check evaluates the player's complete set of Player, Banker, Tie, and pair
bets against every possible result family.

## 22. Progressive-commitment games

Blackjack, Mines, Dragon Descent, Coin Flip chains, and Rock Paper Scissors
chains can increase their exposure during play.

### Blackjack

Every split posts another wager, and every double posts another wager for the
affected hand. Exposure and stake therefore rise together.

The largest gross hand payout is 2.5x for blackjack, approximately 1.5x net
house liability after accounting for the posted stake. Split-21-is-blackjack
can apply that payout to a qualifying split hand, but the split hand has its
own wager.

Opening wagers, insurance, splits, resplits, doubles, and double-after-split
all pass through the shared dealer check.

### Mines

Before revealing a tile, NCCasino checks whether the dealer can cover the new
cash-out value that would exist if the tile is safe. If it cannot, progression
is denied before the random result is chosen. The already-covered cash-out
remains available.

A zero-pick cash-out returns the untouched stake and is treated as a
cancellation rather than a winning payout.

### Dragon Descent

Before another floor or choice is committed, NCCasino reserves the higher
possible cash-out. If it cannot, progression is denied before the result is
chosen.

### Coin Flip and Rock Paper Scissors chains

Before offering another chain round, NCCasino checks the existing numeric
precision ceiling, the configured chain limit, and the dealer's available
tokens. Each denial reason remains distinct in player messaging.

---

# Part V: Abandoned commitments

## 23. Fair automatic resolution

Blackjack already establishes the desired philosophy: a player who closes or
disconnects receives a deadline, and the game resolves fairly instead of
simply confiscating the wager.

Other progressive games follow the same principle.

When a player abandons an unresolved decision:

1. The reservation remains active for a fixed, non-resetting timeout.
2. The player is told what will happen if they do not return.
3. Returning does not restart the deadline.
4. At expiry, the game takes one normal unit of risk.
5. The game then settles immediately.

Examples include one Mines tile, one Dragon Descent progression choice, or one
additional chain round.

If the automatic action wins, the player receives the resulting value. If it
loses, the wager is lost normally. The wager is never seized merely because
the inventory closed.

The player cannot begin another NCCasino session while the abandoned one is
still unresolved.

## 24. Messaging and existing game interfaces

The general goal is that players understand important constraints before
committing, but this design does not mandate a barrier or inventory-layout
change in every existing game.

For existing games:

- Reuse established chat, reconnect, and return-message patterns when they
  communicate the rule clearly.
- Use item lore or a barrier when it naturally fits the current interface.
- Do not add, move, remove, or replace game-GUI items without presenting the
  proposed visual change for approval.
- Keep payout and automatic-resolution receipts in chat where appropriate.

The overflow-bank wagering block is a new shared state and is expected to have
a clear game-GUI representation, but its exact placement is still subject to
per-game UI approval.

---

# Part VI: Player-owned dealers

## 25. Reusing the same token inventory

Player-owned dealers use the same limited dealer machinery. The difference is
where the tokens come from.

A server-owned dealer may receive configured refills. A player-owned dealer is
funded by its owner unless the server deliberately enables another policy.

Owner rules:

- The owner deposits the starting inventory.
- Player wagers enter the dealer inventory.
- Payouts leave it.
- Reserved tokens cannot be withdrawn.
- The owner may withdraw only unreserved tokens.
- Closing the dealer stops new wagers but does not cancel existing
  obligations.
- An underfunded dealer enters a visible unavailable state.
- The owner can never owe more than the dealer contains.

## 26. Small stakes and machine character

Player-owned casinos are expected to operate at smaller stakes than an
unlimited server house.

A cautious owner may operate steady, low-variance games. A wealthy owner may
fund a high-roller Slots machine with rare enormous jackpots. The system does
not choose one correct strategy; it makes the consequences visible before the
owner offers the game.

No progressive jackpot pooling is planned. Every dealer underwrites its own
games.

## 27. Banked winnings and owner accounting

When a player-owned dealer awards an item payout that moves into the player's
overflow bank, the amount leaves the dealer immediately.

The owner cannot reclaim it because the player has not yet made inventory
space. Claiming it later does not charge the owner a second time.

---

# Part VII: Configuration concept

Exact key names may change during implementation, but the intended structure
is:

```yaml
payouts:
  overflow-mode: PLAYER_CHOICE     # PLAYER_CHOICE | BANK | DROP
  default-player-overflow: BANK
  max-drop-stacks: 36
  reminder-period: 1h              # reminder only; does not attempt delivery
  clear-bank-before-wager: true

dealers:
  <name>:
    budget:
      mode: UNLIMITED              # UNLIMITED | LIMITED
      underwriting-baseline: 5000
      guaranteed-worst-case-rounds: 1
      refill-mode: NONE             # NONE | ADD | RESET
      refill-amount: 100            # ADD mode
      refill-period: 1h
      refill-cap: 10000             # ADD mode
      reset-target: 5000            # RESET mode

    slots:
      house-edge: 0.03
      variance: BALANCED            # STEADY | LOW | BALANCED | HIGH | HIGH_ROLLER
```

The live balance and reservations are persistent economic data, not ordinary
configuration values.

Defaults preserve current behavior:

- Player-choice overflow with Bank selected
- No payout expiry
- Bank cleared before any new wager
- Unlimited dealer budget
- No separately configured maximum wager
- No refill unless configured
- Balanced Slots variance

---

# Part VIII: Delivery phases

## Phase 1: Safe payouts and payable Slots

- Add the separate overflow bank.
- Deliver what fits and bank the remainder.
- Add Bank and Drop preferences.
- Cap drops and bank everything beyond the cap.
- Automatically claim on join, dealer open, before wagers, and manual claim.
- Make periodic reminders informational only.
- Block all NCCasino wagers while any bank balance remains.
- Add the approved game-GUI representation for the bank-blocked state.
- Integrate Slots first.
- Remove Slots' 10,000-item ceiling after safe banking is active.
- Route every other game's item overflow through the same system.
- Remove equivalent physical-delivery ceilings only after each payout path is
  migrated.
- Keep the current Slots paytable shape initially.

### Ships

Slots becomes playable in item mode at all supported widths, subject to real
numeric and later dealer-funding constraints. No game destroys large winnings
on the ground.

## Phase 2: Dealer token inventories across all games

- Add unlimited and limited dealer modes.
- Persist live dealer balances and reservations.
- Establish a fixed underwriting baseline.
- Derive wager capacity from the baseline and guaranteed-round setting.
- Keep wager denominations stable as live balance changes.
- Temporarily disable commitments the current balance cannot cover.
- Credit wagers and debit payouts.
- Gate every exposure-increasing decision.
- Add `NONE`, `ADD`, and `RESET` refill policies.
- Add Slots variance levels and previews.
- Present every proposed change to an existing game GUI for approval before
  implementing it.

### Ships

Every game uses the same dealer-funding rule. Existing servers remain
unlimited until an administrator opts in.

## Phase 3: Player-owned dealers

- Allow players to create and fund dealers.
- Prevent withdrawal of reserved funds.
- Add visible underfunded and closed states.
- Provide owner views for balance, reservations, underwriting tier, house
  edge, variance, and maximum exposure.
- Reuse payout banking, abandonment handling, and liability gates unchanged.

### Ships

Players can operate casinos with real upside and downside, without any path to
owner debt.

---

# Deliberately deferred or excluded

## Exact crash-proof delivery

Perfect exactly-once behavior across inventories, files, and third-party Vault
providers is a deeper infrastructure project. It does not block the initial
overflow bank, which will still use strong ordinary persistence and tests.

## Payout expiry

Not planned. Expiry deletes legitimate winnings.

## Live ruin probability

Not planned. Interfaces show concrete balances, reservations, risk levels, and
maximum exposure instead.

## Automatic wager ratcheting

Not planned initially. Wager capacity does not continuously shrink with live
balance or automatically grow with profits. Administrators may deliberately
rebase a dealer later.

## Dynamic drop limits

Not planned. A silently changing limit violates predictable player behavior.

## Silent paytable reshaping

Not allowed. Funding restrictions change availability, never an already
displayed payout table.

## Progressive jackpot pooling

Explicitly out of scope.

---

# Final position

NCCasino needs two shared guarantees:

1. Money already owed to a player has a safe delivery path.
2. A dealer never accepts exposure it cannot cover.

The overflow bank provides the first without becoming a permanent wallet,
because any banked value blocks all further wagering until it is delivered.

Dealer token inventories provide the second across every game. Slots is the
strongest reason to build the system, but Blackjack actions, Mines picks,
Roulette portfolios, chain rounds, server-owned limited dealers, and
player-owned casinos all use the same rule.

> If NCCasino accepts the commitment, NCCasino must honor the result.
