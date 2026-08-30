# NCCasino translation guide

This is the normative localization contract for human contributors and coding
agents. Read it before modifying player-facing text or any file under
`src/main/resources/lang`. For the mechanics of comparing multiple
translation candidates, blind review, and promotion, see
`tools/localization/REVIEW_PROTOCOL.md` — that document owns the *process*;
this one owns *what a correct translation is*.

## Sources and boundaries

- `src/main/resources/lang/en_US.yml` is the semantic source of truth.
- `src/main/resources/lang/locales.yml` is the locale registry. Adding a
  locale is data-only (id and native display name) —
  `LocaleRegistry`/`LocalizationService` discover new entries automatically,
  no Java change is required to add a language.
- Runtime catalogs live at `src/main/resources/lang/<locale>.yml`. These are
  production.
- Generated comparisons and experiments belong under
  `tools/localization/benchmarks/`. They are not production translations —
  see the operation model below.
- Reviewed, intentionally pinned values may live under
  `tools/localization/overrides/<locale>.yml`.
- Translation is maintainer-led and context-sensitive. Do not add bulk
  machine-translation providers, provider credentials, or runtime network
  calls to the plugin.

Never overwrite runtime catalogs merely because a candidate passes structural
validation. Promotion requires an explicit user decision — see the operation
model.

## A. Operation model

Every localization task is exactly one of five operation types. Classify the
task before editing anything, and do not let it silently change type mid-task
(a targeted refinement must not become a full rewrite merely because other
weak strings were noticed along the way — report them out of scope instead).

### Approved English delta

- Reads current production catalogs.
- Writes production catalogs directly.
- Changes only the exact new or meaningfully-changed English keys.
- Normally triggered by the user's **"ready to translate"** handoff (see
  below).

### Targeted refinement

- Starts from an explicit, machine-readable dotted-key allowlist (see
  §B) — never "improve whatever looks weak."
- Reads current production for context, but does not write it by default.
- Produces a candidate plus a change ledger (§B), not a direct edit.
- Unrelated defects noticed along the way may be *reported* in the ledger,
  never silently fixed — that would expand scope without authorization.

### New locale

- Produces a complete candidate under `tools/localization/benchmarks` first,
  never directly in `src/main/resources/lang`.
- Requires locale/regional-variant review, glossary/context review,
  structural validation, and linguistic review before promotion.
- Once approved, register the locale in `locales.yml` and copy the reviewed
  candidate into production as a promotion (§P), not as a side effect of
  generation.

### Full benchmark/rewrite

- Is an experiment, not an implicit production replacement — treat a
  full-catalog regeneration as a benchmark run, always.
- May be blind or production-aware depending on the benchmark protocol in
  use (see `REVIEW_PROTOCOL.md`).
- Must freeze the exact English source used (hash it) before generating, so
  the comparison has a fixed baseline.
- Produces immutable benchmark artifacts — see §N (candidate immutability).

### Candidate promotion

- May write production only when the user explicitly authorizes it, every
  time — a prior approval does not cover a later, different promotion.
- May promote a whole candidate, or selected keys from a hybrid (§P).
- Requires validation, review, provenance, and a stale-baseline check (§O)
  before any key is copied into production.

**An operation must not silently change type.** For example, a targeted
refinement must not become a full rewrite merely because the agent notices
other weak strings; a benchmark must not become a promotion merely because
the candidate looks strong.

## Current localization surface inventory

This inventory defines the already-established product vocabulary and UI
contexts. A game, menu, or feature family absent from this list is new and
needs a short context/terminology review before translation. Update this
section when the new surface becomes an accepted part of the project; also
extend the glossary or semantic context registry when it introduces a
durable new concept.

Current games/dealer types:

- Blackjack
- Roulette
- Mines
- Baccarat
- Coin Flip
- Rock Paper Scissors
- Dragon Descent
- Test Game (development/testing surface, not a normal player-facing release
  game)

Current shared/player menu surfaces:

- Player, Preferences, Language, Game Options, and Confirm menus
- Shared betting, card, payout, game-welcome, interaction, dealer, occupation,
  error, and command messages

Current administration/configuration menu surfaces:

- Admin, Mob Selection, Mob Settings, Jockey Mob, Jockey Options, and Complex
  Variant menus
- Per-game settings menus for Blackjack, Roulette, Mines, Baccarat, Coin Flip,
  Rock Paper Scissors, and Dragon Descent
- Test Menu (development/testing surface)

For a new surface, inspect its Java implementation and answer before
translation: who sees it, whether it is a title/button/lore/chat/error string,
what game rule it describes, which values are machine-readable, which new terms
need glossary decisions, and whether its tone differs from existing UI. Do not
blindly infer a new game's vocabulary from an unrelated existing game.

## "Ready to translate" handoff

The phrase **"ready to translate"** means the user is confident that the
relevant English wording expresses the intended product behavior and
authorizes an **approved English delta** (§A) for that copy.

When the user gives that handoff:

1. Treat the affected English values as frozen. Do not casually rewrite or
   polish them. Raise a question only for a concrete ambiguity or runtime
   hazard that would make faithful translation unsafe.
2. Determine the exact English keys added or meaningfully changed, normally
   from the current task and `git diff`. If unrelated English edits are
   present, keep them out of scope unless the user includes them.
3. Translate only those keys into every registered production locale, using
   nearby values, the semantic context registry (§C), and Java call sites as
   context.
4. If any key belongs to a game, menu, feature family, token type, or concept
   absent from the inventory, glossary, or semantic context registry, perform
   the new-surface review above and update this guide when the resulting
   decision will matter to future translations.
5. Run deterministic validation (§E) and report the exact translated keys.
   Do not perform a full rewrite, targeted refinement of unrelated keys,
   promotion of a benchmark candidate, commit, or push unless separately
   requested.

Ordinary requests containing "translate," "localize," "add a language," or
similar language still trigger this guide and the operation classification
in §A. "Ready to translate" specifically means **approved English delta**;
it does not authorize a targeted refinement, a full rewrite, or a promotion.

## B. Scope discipline for targeted refinement

A targeted refinement always starts from an exact, machine-readable dotted-key
allowlist (e.g. a literal list of `section.key` paths, not a prose
description like "the blackjack settings menu"). Only keys on that list may
be changed in the candidate.

Every proposed changed key requires a change-ledger entry with at least:

| Field | Meaning |
| --- | --- |
| `key` | Exact dotted key. |
| `english_source` | The current English value the change targets. |
| `production_baseline` | The current production value being replaced. |
| `proposed_value` | The new candidate value. |
| `reason` | Why the change is being made. |
| `context_checked` | Java/runtime context consulted, if any (file/method or "none needed"). |
| `severity_class` | The Tier this addresses, if it's a fix (§L). |
| `provenance` | Candidate/source this value came from, if applicable (e.g. a benchmark run id). |
| `review_status` | Whether an independent reviewer has looked at this key yet. |

Out-of-scope defects may be *recorded* (in the ledger or a short note) but
must not be edited unless the user explicitly expands the scope to include
them. See `REVIEW_PROTOCOL.md` for the full ledger format used in
multi-candidate comparisons.

## C. Semantic context registry

Meaning is not always recoverable from the English string alone. This
section records concepts already verified against Java so future agents
don't have to rediscover them from scratch. If Java contradicts an entry
below (the code changed since this was written), trust and document the
actual implementation, and update this section.

- **`*.player-turn` and similar third-person turn messages** (e.g.
  `coin-flip.player-turn`, `rock-paper-scissors.player-turn`) are rendered as
  an item name/lore visible to *every* viewer at the table (both players'
  clients render the same GUI slot), announcing whose turn it is next. They
  are third-person announcements ("It's {player}'s turn"), not direct
  second-person address to that player — do not translate them as "your
  turn." Contrast with keys like `blackjack.your-turn`/`your-turn-message`,
  which *are* direct second-person address sent to the acting player.
  (Verified: `CoinFlipClient.handlePlayerTwoSit`/`handleSubmitBet`,
  `RockPaperScissorsClient` equivalents.)
- **`*.mode-switch-cashout-notice`** (Coin Flip/RPS) appears only when
  switching game mode will *automatically cash the player out first*; it
  describes a future/conditional consequence of clicking the button the
  player is currently looking at, not something that already happened.
  Preserve future/conditional tense — do not translate it as a past-tense
  confirmation. (Verified: `RockPaperScissorsClient` — the notice is only
  attached when `safeToAutoCashOut` is true, immediately before the
  mode-switch action, describing what clicking switch will do.)
- **Coin Flip and Rock Paper Scissors strings shared between PvP and PvE**
  must remain semantically valid in both contexts — e.g. a "your opponent"
  phrasing must work whether the opponent is another player or the dealer.
  Do not translate in a way that only makes sense for one mode.
- **`PvP`/`PvE` are protected literals** (§D, Class B) even in a locale that
  commonly uses a localized abbreviation for "player vs. player" (e.g.
  French `JcJ`/`JcE`). Do not substitute a localized abbreviation; keep the
  English initialism exactly as written. Spelled-out phrases like "Player vs
  Player" may still be translated.
- **"Max chain" is a streak/round-count cap, not a monetary cap.** Coin
  Flip/RPS `*-settings.edit-max-chain`/`max-chain-current` describe how many
  consecutive PvE rounds a win-streak may extend before it is forced to
  cash out — a count of rounds, never an amount of currency. Contrast with
  `*.max-pot-hit`, which *is* about hitting a payout ceiling.
- **`{game}` receives an arbitrary localized game name** at render time
  (`Server.localizedGameName`, driven by `game-options.*`), one of
  Blackjack/Roulette/Mines/Baccarat/Coin Flip/Rock Paper Scissors/Dragon
  Descent/Test Game (or the raw internal name as a fallback). In a
  gender/article-marking language, do not write surrounding grammar that
  assumes one fixed gender or article for whatever fills `{game}` — different
  game names may take different genders/articles in that locale.
- **Admin "chip size" means monetary denomination/value, not physical
  dimension.** `admin.prompt-chip-size`/`chip-size-updated*` configure
  `dealers.<name>.chip-sizes.sizeN`, an integer currency amount per chip
  slot — translate as "value"/"denomination," not "size" in the
  physical-dimension sense, where the target language would otherwise
  distinguish them. (Verified: `AdminMenu` stores the typed number directly
  as `chip-sizes.sizeN`, later read as `chipValue`.)
- **"Closed player" context (`blackjack.closed-during-turn` /
  `closed-before-turn` / `closed-during-insurance`)** refers to a
  reconnecting player who had closed their inventory (disconnected or
  exited the menu) while a specific timer was or wasn't running against
  them — the three keys are selected by which timer context applies at the
  moment they return, not by a generic "closed" concept. Preserve that
  distinction; do not collapse the three into one generic
  "you were away" message. (Verified:
  `BlackjackInventory.sendRideToResultMessage`.)
- **Blackjack "shoe"** (`blackjack.shoe-exhausted-refunded`) means the
  casino multi-deck card shoe cards are drawn from, not footwear or a
  generic single deck. Use the target language's established casino term
  for the card shoe.
- **Blackjack split-matching rules are two genuinely different game rules,**
  configurable per dealer (`dealers.<name>.splitting.matching`):
  `SAME_RANK` only allows splitting identical ranks (King-King); `SAME_VALUE`
  allows splitting any two ten-value cards (King-Queen, Ten-Jack) as well.
  `blackjack-settings.match-same-rank`/`match-same-value` and their
  descriptions must clearly distinguish these — a near-identical pair of
  labels risks a player picking the wrong rule. (Verified:
  `BlackjackSplitMatching` enum.)
- **`commands.player-only` checks sender type (console vs. player), not
  whether a player is currently inside a game.** It fires from
  `!(sender instanceof Player)` in `HelpCommand`/`CreateCommand`/etc. —
  translate as "this command can only be used by a player," not "can only
  be used in-game" or "while seated."
- **"Rebet" means repeat the immediately previous round's exact wager(s),**
  not a refund, a discount, or an arbitrary new wager. (Verified:
  `BaccaratClient.reapplyPreviousBets` re-applies the stored `previousBets`
  from the prior round.) The same concept and translation should be used
  everywhere rebet appears (Blackjack, Baccarat, Mines, Roulette).
- **Baccarat's "banker"/bank side is distinct from the physical
  dealer/croupier role.** `baccarat.dealer` labels the physical dealer
  skull item; `baccarat.banker` labels a separate betting-outcome slot for
  the banker side of the hand. Do not default the generic "dealer" glossary
  term onto `baccarat.banker` or vice versa. (Verified:
  `BaccaratClient` — separate `text("baccarat.dealer")` and
  `text("baccarat.banker")` calls on different inventory items.)
- **Dragon Descent "vines" is the Minecraft climbing-vine mechanic**
  (`Material.VINE`, `Sound.BLOCK_CAVE_VINES_STEP`), the game's safe-spot
  marker — not a false-friend "grapevine" translation. Use the target
  language's word for the climbing-plant/jungle-vine sense.
- **Payout/cash-out strings must preserve who receives money and whether
  payment is current, future, conditional, or already completed.** The
  `payout.context-*` family in particular distinguishes three different
  historical situations (disconnected mid-round and the game resolved while
  offline; server restarted before resolution and the bet was refunded;
  server restarted after resolution and the outcome was preserved) — do not
  collapse them into one generic "your bet was handled" message, and do not
  change a completed-past-tense payout into a future/conditional one or vice
  versa.

This registry is not exhaustive. When a new ambiguous key is resolved via
Java inspection, add it here with the exact affected key family and a short
"verified: <file/method>" note, so the next translation pass does not have
to re-derive it.

## D. Protected-token classes

Protected material is not all the same kind of "do not touch." Classify it:

### Class A — byte-exact runtime syntax

Never altered, reordered, or paraphrased, in any locale:

- YAML keys and hierarchy
- Placeholders such as `{player}`, `{amount}`, `{seconds}`
- Minecraft legacy formatting codes such as `&a`, `&7`, `&c`, `&o`
- Executable commands/subcommands (`/ncc`, `create`, `list`, `delete`,
  `reload`)
- Parser sentinels such as the `-1` unlimited value
- Permission nodes, config keys, enum/material identifiers, and
  machine-readable paths/URLs where they occur as literal values

Preserve the **combined left-to-right sequence** of Class A tokens, not
merely each category's count — see §E for why frequency-only checks are
insufficient and how to self-check this.

### Class B — exact protected product/technical literals

Kept exactly as written, in every locale, even where a locale would
naturally have its own abbreviation:

- `NCCasino` / `NCCASINO`
- `Vault` / `VAULT`
- `PvP`
- `PvE`

### Class C — context-sensitive technical concepts

These **are** translated, but only correctly after understanding the
implementation/context behind them — a dictionary-literal translation is
often wrong. See §C (semantic context registry) for verified examples:

- Chip denomination/value
- "Max chain" (streak cap)
- Cash out / payout timing
- Blackjack shoe
- Baccarat banker vs. dealer
- RPS "throw"/action vs. generic "turn"
- Rebet (repeat previous wager)
- Split-matching rule pair (same-rank vs. same-value)
- Dragon Descent vine mechanic

### Class D — ordinary translatable prose

Normal linguistic freedom, subject to semantic fidelity (§C), locale voice,
and the glossary (§H). Notable sub-cases that are **not** blanket
do-not-translate items:

- Display-only "Unlimited" is translated naturally.
- `Dealer`, bet/wager terminology, `ON`/`OFF`, and ordinary UI labels are
  translated according to the locale glossary and voice.
- Spelled-out phrases such as "Player vs Player" are translated even though
  the abbreviations `PvP`/`PvE` (Class B) are not.
- Documentation metavariables such as `<name>` and `(page)` may be
  localized; the executable command tokens beside them (Class A) may not.
- Game names use the conventional target-language name when one exists;
  globally established names such as Blackjack may remain unchanged when
  that is normal local usage.

Keep placeholders and formatting tokens unchanged even when surrounding
grammar would tempt movement. Rewrite the prose around the fixed token
order, not the other way around.

## E. Mechanical self-check (make protected-token preservation mechanical, not just a promise)

`.\gradlew.bat localizationCheck` already validates coverage and enforces
**ordered** Class A placeholder/command/`-1`-sentinel matching as a hard
failure (not just token frequency). Minecraft color/format (`&`) codes are
checked too, but only as a frequency comparison surfaced as a non-fatal
warning for values already checked into `src/main/resources/lang` — full
order enforcement for `&` codes is a hard failure in
`localizationCandidateCheck` for newly reviewed candidate content, not on
existing hand-edited production catalog values.
See the tooling notes
in `README.md` for the exact current scope. Do not rely on
`localizationCheck` alone to prove `&`-code interleaving correctness for a
hand-edited catalog value; also do this by hand for every changed value:

1. **List Class A tokens in order** for the English source and for your
   translated value (placeholders and `&`-codes together, left to right).
2. **Compare the two ordered lists**, not just their counts. A translation
   that has the right *number* of `&o` codes but in the wrong position, or
   that has the right placeholders but reordered relative to a
   formatting-code bracket around them, still fails this check even though
   a naive frequency count would pass it.
3. **Watch formatting-token pairs/brackets around a placeholder
   specifically.** If a source contains a bracket like:

   ```
   &o{player}&o's turn
   ```

   the translation must keep *both* `&o` tokens, even if the placeholder
   moves grammatically (e.g. to the end of the sentence). It is easy to
   drop the closing token when restructuring a sentence around a moved
   placeholder — this exact pattern has caused a real, cross-locale defect
   in this catalog before. Preserving the *count* of `&o` independently of
   *where* it sits is not sufficient; the pairing has to make sense too.
4. **Grep your own changed values for Class B literals** that appear in the
   English source (`NCCasino`, `Vault`, `PvP`, `PvE`) and confirm they still
   appear verbatim, unmodified, uninitialismed, in the translation.
5. **Confirm the `-1` sentinel and `/ncc <subcommand>` tokens** are present
   verbatim wherever the English source has them.

## F. Source-language residue detection

A structurally valid translation can still be wrong in the worst possible
way: literally the wrong language. Before finishing any translation work
(delta, refinement, or benchmark), scan changed/generated target values for
source-language (English) residue. Treat a hit as a review gate, not
automatic proof of a defect — legitimate English may remain for protected
brands, commands, technical literals, conventional game names, or other
explicitly allowed terms (§D).

Useful detection signals, in roughly increasing order of confidence:

- The target value is byte-identical (or near-identical) to the English
  source, and the source is more than a couple of words.
- A long identical substring appears in both source and target.
- Multiple common English function words appear in the target
  (e.g. "the," "and," "of," "use," "command" showing up untranslated in a
  non-English value).
- An otherwise-translated sentence has one embedded, obviously-untranslated
  English clause or phrase.
- An unusually high ratio of source-language tokens to total tokens in an
  otherwise target-language value.

This has already caught a real defect: a full-catalog regeneration once left
~215 of 871 keys with untranslated English fragments in one locale, entirely
missed by structural validation because the values were still valid strings
— just wrong-language ones. Do not skip this check because
`localizationCheck` passed.

If the repository's localization tooling can run this deterministically
(see `README.md`), prefer that over a purely manual pass — but a manual
scan is still required wherever the tool doesn't cover the exact case.

## G. Preserve instruction/action-location clauses

Operational clauses are meaning-bearing, not decorative filler, even when
they make a UI string longer. Do not drop them for concision. Examples:

- "in chat" (tells the player *where* to type a value, not just *what*)
- "at your location"
- "after the round"
- "before betting closes"
- other phrases telling the player where, when, or how to act

Dropping one of these is a semantic defect (§L, Tier 1), not a minor style
difference — a player told "type a new value" without "in chat" may not
know where to type it at all.

## H. Working glossary

Use these as defaults unless code context (§C) clearly changes the concept.
Extend this table when a reviewed decision is made so later delta
translations do not re-litigate terminology. A pin here applies **catalog
wide** — gameplay strings, admin/configuration menus, commands, errors,
lore, and help text alike. Do not let one section of a catalog fall back to
a different or untranslated term for the same concept just because it is
far from the original example that established the pin.

| Concept | de_DE | es_ES | fr_FR | pt_BR |
| --- | --- | --- | --- | --- |
| generic physical dealer/croupier | Dealer | crupier | croupier | crupiê |
| Baccarat banker/bank side | *(distinct from dealer — record locale decision when made)* | *(distinct from dealer — record locale decision when made)* | *(distinct from dealer — record locale decision when made)* | *(distinct from dealer — record locale decision when made)* |
| bet / wager amount | Einsatz | apuesta | mise | aposta |
| all in | alles setzen | apostar todo | tout miser | apostar tudo |
| rebet (repeat previous wager) | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| chip denomination/value | *(pin when first reviewed — not a physical-size word)* | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| win streak / chain (PvE) | *(pin when first reviewed — a round count, not a payout)* | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| cash out / payout | *(pin when first reviewed — preserve current/future/conditional tense per key)* | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| Blackjack: shoe | *(pin when first reviewed — the card shoe, not footwear)* | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| Blackjack: hit | Karte ziehen | pedir | tirer | pedir carta |
| Blackjack: stand | halten | plantarse | rester | parar |
| Blackjack: split | teilen | dividir | séparer | dividir |
| Blackjack: insurance | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| Blackjack: same-rank vs. same-value split rule | *(pin distinctly — two different rules, do not use near-identical labels)* | *(pin distinctly)* | *(pin distinctly)* | *(pin distinctly)* |
| RPS: throw/action (not generic "turn") | Wurf | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| Dragon Descent: vine mechanic | *(pin when first reviewed — climbing-vine sense)* | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| ON/OFF display state | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* | *(pin when first reviewed)* |
| seat | Sitz | asiento | siège | assento |

Where a terminology decision is genuinely unresolved, leave the cell marked
as such and require review before the next translation pass treats it as
settled — do not fill a cell with a guess presented as an established pin.

Do not force a generic glossary term into a technically distinct role. For
example, Baccarat's banker is not automatically the physical dealer, and RPS
"throw" is not automatically the generic "turn"/"move" term used elsewhere
in the catalog for Blackjack/Coin Flip turn order — using the wrong one is
a real, previously-observed defect, not a hypothetical risk.

## Locale voice

- `de_DE`: natural, concise German; informal `du` for direct player address.
- `es_ES`: European Spanish; informal `tú`; natural sentence casing.
- `fr_FR`: metropolitan French; preserve the current catalog's formal `vous`
  unless the user explicitly approves a whole-catalog register migration.
- `pt_BR`: Brazilian Portuguese; use `você`; favor terms natural to Brazilian
  gaming and Minecraft players.

Do not mix registers inside one catalog. A deliberate register change is a
full-catalog review, not an incidental edit.

## I. Consistency lookup for targeted work

Reading physically nearby YAML entries is not sufficient — real defects in
this catalog have come from the *same* English concept being translated two
different ways in *different, non-adjacent* sections (e.g. a term correct
in the game-table strings but wrong in the admin menu or the commands
section, dozens of lines away).

For a targeted refinement or delta translation, before finalizing a
translated value, search the **entire target catalog** — not just nearby
lines — for:

- The same English concept elsewhere (`rg -i "dealer" src/main/resources/lang/de_DE.yml`
  to check every existing rendering of a glossary term you're about to
  reuse or introduce).
- Related established translations (`rg -n "crupiê" src/main/resources/lang/pt_BR.yml`
  to confirm you're not about to introduce a second, inconsistent term for
  the same role).
- Glossary-pinned terminology (§H) — confirm the pin, not a plausible
  synonym, is what's actually used.
- Repeated UI concepts (e.g. every settings-prompt key that says "in chat"
  in English — confirm all of them keep it, not just the one you're
  editing).

Physical adjacency in the YAML file does not count as sufficient consistency
review on its own.

## J. Numeric preservation

Not all numbers are the same kind of "must preserve exactly." Split by
category:

### Parser literals

Must remain byte-exact — these are read by code, not by a human:

- `-1` (the unlimited sentinel)
- Any other command/input syntax numeral a player must type verbatim

### Game quantities / odds / exact semantic numbers

Must preserve exact meaning and association with what they quantify, but
locale punctuation (decimal comma vs. decimal point, digit grouping) is
acceptable **only** where the value is display prose, not something parsed
by code. Examples: percentages, multipliers, odds (`11:1`), hand/chain
counts. Verify in Java whether a given numeric display value is ever parsed
back from that same string before assuming punctuation is free to localize.

### Ordinary prose numerals

May move grammatically as long as their meaning and association with the
right noun/quantity remain intact. Do not enforce positional identity with
the English word order when the target language's grammar requires
reordering a numeral relative to its noun.

## K. Source/runtime defect escape hatch

Localization must not paper over a Java or template composition defect by
inventing an increasingly strained translation-only workaround. If you
discover that:

- Java inserts an already-complete localized sentence into another
  sentence's template placeholder (producing a doubled/grammatically broken
  result in every language, regardless of translation quality);
- a placeholder receives the wrong semantic object for the sentence it sits
  in;
- localization composition duplicates grammar (e.g. two full sentences
  concatenated where only one was intended);
- the English source/template structure is intrinsically untranslatable
  because of a code defect, not a wording choice;

then:

1. Flag the affected key and Java call site to the developer instead of
   inventing a locale-specific wording hack to hide the bug.
2. Do not fabricate a workaround that happens to look less broken in one
   language than in others — that hides the underlying defect rather than
   fixing it.
3. Keep the translation faithful to the intended atomic meaning of the
   fragment you *can* control, where possible.
4. Separate this class of finding from ordinary translation-quality
   findings in any review report — it is a source/runtime bug, not a
   candidate defect, and should not be scored against any translation
   candidate.

**Verified example:** `dragon-settings.updated-detailed` and
`blackjack-settings.updated-detailed` are shared VERBOSE-mode templates
(`{setting} to {value}.` / `{setting} to: &e{value}&a.`) whose Java call
sites substitute an *already-complete, already-localized* sentence (e.g.
the rendered `dragon-settings.floors-updated`, "Dragon Descent floors
updated.") into the `{setting}` placeholder. The result reads as a doubled,
grammatically broken sentence in every locale no matter how the template
itself is translated (e.g. "...actualizado. a: PvP."). No candidate
translation can fix this through wording alone; it needs a Java/template
fix (stop composing a full sentence into another sentence's placeholder).
Treat any future instance of this same pattern the same way: as a
source-defect report, not a translation-quality finding.

## L. Review severity and promotion gates

Classify concrete findings using this priority order — higher tiers block
promotion regardless of how many lower-tier advantages a candidate has
elsewhere:

### Tier 0 — structural/runtime

- Invalid YAML
- Missing/extra key
- Damaged placeholder
- Formatting/interleaving defect (§E)
- Protected literal (Class B) alteration
- Executable command/parser (Class A) alteration
- Machine-readable value corruption

A key with a Tier 0 defect cannot be promoted until corrected.

### Tier 1 — semantic

- Wrong actor/recipient
- Wrong action
- Wrong game mechanic
- Wrong timing/tense (§C)
- Negation reversal
- Wrong quantity/odds/payout
- A dropped actionable clause (§G)
- Source-language residue that changes or obscures meaning (§F)
- Materially wrong regional interpretation (locale variant)

A key with a Tier 1 defect cannot be promoted until corrected.

### Tier 2 — terminology/consistency

- Glossary conflict (§H)
- Mixed register
- Recurring term inconsistency
- Gameplay/admin terminology drift (the same concept translated two
  different ways in different sections)

### Tier 3 — naturalness/polish

- Awkward but correct wording
- Punctuation, capitalization, verbosity
- Lost humor/tone where the source was intentionally playful

**A lower-tier advantage never compensates for a higher-tier defect.** Do
not select a whole-catalog "winner" purely by counting stylistic (Tier 3)
wins — a candidate with one Tier 1 rule error is not made superior by many
Tier 3 wins elsewhere. See `REVIEW_PROTOCOL.md` for how this hierarchy
drives multi-candidate adjudication and per-locale outcomes.

## M. Proportional independent review

Review requirements scale with the operation type (§A) — do not require
heavyweight review for trivial maintenance, and do not skip it for
higher-risk operations.

### Routine approved English delta

Require:

- Mechanical validation of the full catalogs (`localizationCheck`).
- Self-review of the changed keys (§E, §F).
- Java inspection for any key touching semantic-context concepts (§C).

A second independent reviewer is not required for every trivial maintenance
change.

### Targeted refinement

Require:

- Mechanical validation.
- Independent review of **every** changed key (not a sample).
- Java inspection for any high-risk semantic concept touched.

### New locale / full benchmark

Require:

- Full structural validation.
- Protected-token validation (§D, §E).
- Source-language residue scan (§F).
- Terminology consistency checks (§H, §I).
- Exhaustive review of every Tier 0/Tier 1 finding (never a sample of
  those, even if the rest of the review is sampled).
- Blind or independent adjudication for meaningful candidate disagreements
  — see `REVIEW_PROTOCOL.md`.
- Native review where a candidate disagreement remains materially semantic
  after independent adjudication, or where every candidate is questionable
  on the same key.

### Promotion

Require:

- Exhaustive review of every key actually being promoted (not the whole
  candidate if only some keys are promoted).
- Successful validation of the final *merged* result — validating the
  candidate in isolation is not sufficient once it's merged with production
  and/or another candidate.

## N. Candidate immutability and provenance

Once a benchmark candidate has been frozen (its manifest records a hash) and
reviewed, do not modify it in place. Follow-up work creates a new run/version
or a targeted-refinement candidate instead — this keeps every review
traceable to an exact, unchanging set of bytes.

Each benchmark run should preserve enough metadata to be reproducible, where
practical:

- Run ID
- Date
- Operation type (§A)
- English source path/hash
- Production baseline hashes
- Candidate file hashes
- Provider/model provenance
- Prompt/protocol version
- Locale
- Exact scope/allowlist (for a targeted refinement)
- Validation result
- Review result

Provider/model provenance should be preserved for auditability (it's part
of the run's metadata), but candidate identity must be hidden from any
blind linguistic adjudication step — see `REVIEW_PROTOCOL.md`'s blind
labeling rules. Use neutral reviewer-facing labels (e.g. "Candidate A" /
"Candidate B"), never a label that reveals or hints at provenance, during
blind review.

## O. Stale-baseline guard

Before promoting any previously-reviewed key:

1. Verify the current production value still matches the production
   baseline that was recorded when that candidate/key was reviewed.
2. Verify the current English source still matches the English source
   snapshot the candidate was generated/reviewed against.
3. If either changed since the review, mark the key **stale** and require
   re-review before promoting it — do not blindly apply an old reviewed
   patch onto a baseline that has since moved.

This matters most for a targeted refinement or hybrid promotion that sits
between review and promotion for any length of time, or where production
receives an unrelated approved-delta edit in the meantime.

## P. Hybrid promotion

Per-key hybrid promotion is explicitly supported and often the right
outcome — do not force a whole-file "winner" when the evidence supports a
mix. A production locale catalog may validly be composed, key by key, from:

- Existing production values
- Candidate A values
- Candidate B values
- Manually reviewed overrides

Promotion records should preserve key-level provenance where practical (which
source each promoted key came from), so a later stale-baseline check or audit
can trace any given production value back to the review that approved it.
See `REVIEW_PROTOCOL.md` for the full hybrid-selection and promotion-record
process.

## Commands

Validate without network access:

```powershell
.\gradlew.bat localizationCheck
```

For multi-candidate comparison, blind review, and promotion mechanics, see
`REVIEW_PROTOCOL.md`. Generated candidates and review artifacts stay in the
Git-ignored `tools/localization/benchmarks/` directory. Routine maintenance
should operate on the exact changed keys rather than invoking a full rewrite.
