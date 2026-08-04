---
name: nccasino-new-game
description: Reference for adding a new game/dealer-type to the NCCasino Minecraft plugin — the Server/Client architecture, PvP-shared-table vs PvE-per-player-match instancing, chair/bet/pick-phase conventions, GameTerminationPolicy, every wiring point (GameOptionsMenu, AdminMenu, Dealer, Server.localizedGameName), the admin settings sub-menu pattern, localization requirements across 5 languages, and permissions.yml wiring. Use this whenever the user wants to add a new casino game, a new dealer type, a new game mode (like PvP/PvE), or asks "how do I wire up a new game" in this repo — even if they don't say "skill" or name these files directly.
---

# Building a new game in NCCasino

NCCasino is a Bukkit/Paper plugin. A "game" is a dealer type: a mob in the
world that players right-click to open a shared inventory-based GUI. Every
game touches the same ~10 files in predictable ways. This skill is a
checklist and pattern reference distilled from building Rock Paper Scissors
(`org.nc.nccasino.games.RockPaperScissors`) — treat that package as the
canonical, most up-to-date example; Coin Flip
(`org.nc.nccasino.games.CoinFlip`) is the next-best reference for the same
architecture. Do not copy from Blackjack/Roulette/Mines' `*Inventory`/`*Table`
classes — those predate this architecture and are being phased out.

Before writing any code, read the two example games' Server and Client
classes in full. This skill tells you *what* to build and *where it plugs
in* — the examples show the actual code shape.

## 1. Decide the shape of the game first

Before touching any files, get explicit answers (ask the user if unclear) to:

- **How many seats, and does everyone need to agree on a bet, or does each
  player act independently?** This determines whether you need Coin
  Flip/RPS's chair-handshake-then-active-round state machine, or something
  simpler.
- **Is there a mode where a single player plays against the house (PvE)
  alongside a PvP mode?** If so, read §4 — this needs a different
  concurrency model, not just an `if (mode == PVE)` sprinkled through the
  PvP code.
- **What ends a round decisively, and is there a possible "no-op" outcome**
  like a tie that needs to loop instead of resolving? Map out every
  terminal state before writing `evaluate`/`resolve`-style methods.
- **What happens on disconnect at each phase** (pregame, mid-decision,
  after a winner is committed but before payout)? This maps directly onto
  `GameTerminationPolicy` (§5).

Getting this state machine right on paper first saves a lot of rework —
most of the actual bugs in this codebase's newest game came from UI/timing
details, not the state machine, precisely because the state machine was
planned before code was written.

## 2. Package and file layout

```
src/main/java/org/nc/nccasino/games/<GameName>/
    <GameName>Server.java   -- extends Server, owns authoritative state
    <GameName>Client.java   -- extends Client, implements TerminableSession, per-player GUI
    (any small value types, e.g. an enum like Throw.java, or a Mode enum)

src/main/java/org/nc/nccasino/components/
    <GameName>Menu.java     -- admin settings sub-menu (extends Menu)
```

Internal game-type string: a human-readable `"Title Case"` string (e.g.
`"Rock Paper Scissors"`) used verbatim as the `switch` key in every wiring
site in §6, as the value of `dealers.<internalName>.game` in config, and as
the localization-key suffix (kebab-case) for `game-options.<kebab-name>`.

## 3. The Server/Client architecture

Read [entities/Server.java](../../../src/main/java/org/nc/nccasino/entities/Server.java),
[entities/Client.java](../../../src/main/java/org/nc/nccasino/entities/Client.java), and
[entities/DealerInventory.java](../../../src/main/java/org/nc/nccasino/entities/DealerInventory.java)
before writing anything — they're the actual contract, this is just a map of it.

- **`Server`** (one instance per dealer mob, extends `DealerInventory`) is
  the authoritative game-state holder. `Client`s register with it via
  `getOrCreateClient(player)`. `onClientUpdate(Client, String eventType,
  Object data)` is the single entry point for every client action;
  `broadcastUpdate(eventType, data)` pushes to every registered `Client`.
- **`Client`** (one instance per player who has ever opened the dealer)
  owns that player's inventory rendering. `sendUpdateToServer(eventType,
  data)` talks up to the `Server`; `onServerUpdate(eventType, data)` is the
  single entry point for state pushed down from the `Server`.
- Both extend `DealerInventory`, which gives you `addItemAndLore`,
  `createPlayerHead`, `createEnchantedItem`, `denyAction`,
  `getKeyByValue(map, value)` (for slot→enum reverse lookup), and
  `playDefaultSound`.
- `Client` also gives you the **generic chip-betting row** for free
  (`initializeUI(...)`, slots 45–53, `betStack`, `hasEnoughWager`,
  `tryRemoveCurrencyFromInventory`) — reuse this rather than building your
  own currency-selection UI. A dedicated "submit bet" lever in your own
  slot layout sums `betStack` and hands the total to the server; that's a
  game-specific concern, the chip row itself isn't.

**Event names are just strings** matched in a `switch` on both sides —
there's no shared interface enforcing symmetry, so keep a mental (or
commented) list of every event your game defines and double check both
`Server.onClientUpdate`'s switch and `Client.onServerUpdate`'s switch
handle exactly the same set.

**Slot numbers must be unique per `SlotOption` enum entry.** `getKeyByValue`
does a linear scan of the map and returns the first key matching a given
slot number — if two `SlotOption`s share a slot value, clicks on that slot
resolve to whichever entry the `HashMap` happens to iterate first, which is
not deterministic-looking to a reader and easy to get wrong. Reuse a slot
across game *phases* (e.g. a lever that's a "submit bet" button pregame and
becomes a timer display once active) by writing different items into the
same slot number at different times, not by mapping two enum entries to the
same number.

## 4. PvP shared table vs. PvE per-player match

This is the one place a naive port of the PvP pattern breaks. If your game
only ever needs one shared table per dealer (like Coin Flip — genuinely two
humans, one seat each), skip this section.

If you're adding a mode where a single player plays against the house, and
you want **any number of players to do that concurrently on the same
dealer mob** (not one player hogging it), do **not** just make the existing
single-shared-state fields (`chairOneOccupant`, `betAmount`, `gameActive`,
etc.) conditionally behave differently for PvE — those fields are one
instance shared by *every* client of that `Server`, so two PvE players
would stomp on each other's rounds.

The fix used in RockPaperScissorsServer: extract all per-round state and
logic into a private, non-static inner class (`RpsMatch` there) that
represents *one table's worth of game*. The outer `Server` becomes a thin
dispatcher:

```java
private final RpsMatch sharedMatch = new RpsMatch(null);       // PvP: one table
private final Map<UUID, RpsMatch> pveMatches = new HashMap<>(); // PvE: one per player

private RpsMatch matchFor(UUID playerId) {
    if (mode == Mode.PLAYER_VS_DEALER) {
        return pveMatches.computeIfAbsent(playerId, RpsMatch::new);
    }
    return sharedMatch;
}

@Override
public void onClientUpdate(Client client, String eventType, Object data) {
    matchFor(client.getPlayer().getUniqueId()).handle(client, eventType, data);
}
```

Key points that made this work cleanly (see the real file for the full
implementation):

- The match's `owningPlayerId` field (null for the shared PvP table, a real
  UUID for a private PvE match) doubles as `isPve()` — you don't need a
  separate mode flag inside the match class at all.
- Replace every `broadcastUpdate(...)` call inside the extracted logic with
  a small `send(eventType, data)` helper on the match: `owningPlayerId ==
  null` → call the outer `broadcastUpdate` (everyone sees it, including
  spectators, exactly like PvP today); otherwise → push straight to
  `clients.get(owningPlayerId)`. Missing even one of these calls silently
  leaks a PvE player's private round to every other viewer of that dealer.
- `forfeitPlayer(UUID)`, `refundForShutdown(UUID)`, and
  `registerRidingSession(UUID)` (called from the `Client`'s
  `onSessionTerminated`) must route through `matchFor(playerId)` too — a
  disconnect/kick must resolve the *right* match, not always the shared
  one. This is the shape those methods need on the outer class:
  ```java
  void forfeitPlayer(UUID playerId) { matchFor(playerId).forfeitPlayer(playerId); }
  ```
- Because a "virtual" opponent (the house) never actually sits in chair 2,
  chair-2-related fields (`chairTwoOccupant`) simply stay `null` for every
  PvE match. Lean on that instead of special-casing: payout code that
  already does `if (winnerId != null && ...)` before crediting "player
  two" already correctly no-ops for the house without any PvE-specific
  code — the null occupant *is* the "no such player" signal. Only add an
  explicit `isPve()` branch where the house needs to actually *act* (e.g.
  auto-accepting the bet immediately since there's no second human, or
  auto-generating the house's own move) or where a PvP-only interaction
  needs to be blocked outright (sitting/leaving chair 2).
- A player who reaches timeout with no visible "opponent" in PvE always
  means *they* didn't act (the house only ever responds to the player's
  move, never acts first) — don't reuse a symmetric "did neither player
  act, void the round" check for PvE; it needs its own unconditional
  forfeit-to-house branch.
- Toggling PvP↔PvE (or any mode change) requires re-constructing the
  `Server` so the new instance re-reads config (mode is normally resolved
  once at construction, same as `currencyMode`). Swap it with
  `DealerInventory.updateInventory(dealerId, new
  YourServer(dealerId, plugin, internalName))` rather than the broader
  `plugin.reloadDealer(dealer)` if you want the *admin's own* settings menu
  to stay open through the swap (`reloadDealer` → `deleteAssociatedInventories`
  closes every `Menu` for that dealer, including the one the admin is
  currently looking at). But if you take that narrower path, you still
  need to manually close two things `reloadDealer` would have closed for
  you: any *other* player's live game view (`Client.getOpenInventories(internalName)`
  — always stale after the swap, since it points at the now-replaced
  `Server`) and any *other* admin's open menu for the same dealer
  (`Menu.getOpenInventories(dealerId)`, skipping the triggering player).
  Forgetting this leaves other players staring at a dead inventory.

For a genuinely single-player game with no PvP mode at all (a slots
machine, a solo dice game), you don't need this split — just give every
player their own private state from the start, the way Mines does with its
`Map<UUID, MinesTable>` dispatcher in
[games/Mines/MinesInventory.java](../../../src/main/java/org/nc/nccasino/games/Mines/MinesInventory.java)
(that file predates the `Server`/`Client` base classes, but the per-player
dispatch idea is the same one described above).

## 5. Disconnect/kick/shutdown handling: GameTerminationPolicy

Every game defines its own pure function in
[session/GameTerminationPolicy.java](../../../src/main/java/org/nc/nccasino/session/GameTerminationPolicy.java):

```java
public static TerminationAction yourGame(ExitReason reason, boolean gameActive) {
    if (reason == ExitReason.GAME_COMPLETED) return NO_ACTION;
    if (reason == ExitReason.KICKED) return FORFEIT;
    if (!gameActive || reason == ExitReason.PLUGIN_DISABLE) return REFUND;
    return RIDE_TO_RESULT;
}
```

This is deliberately Bukkit-agnostic (just an enum in, enum out) so it's
cheap to unit test — add a case to
[session/GameTerminationPolicyTest.java](../../../src/test/java/org/nc/nccasino/session/GameTerminationPolicyTest.java)
covering kicked/disconnected/plugin-disable/game-completed × every phase
your game has. Look at `coinFlip`/`rockPaperScissors` for the simple
two-outcome shape, `mines`/`dragon` for games with more phases
(cash-out-in-progress, etc).

Your `Client`'s `onSessionTerminated(UUID, ExitReason)` (implementing
`TerminableSession`) is where the `TerminationAction` result actually gets
carried out — `FORFEIT` removes the client and marks them ineligible for
any pending payout, `REFUND` either immediately refunds (pregame) or
triggers a shutdown-refund path, `RIDE_TO_RESULT` registers a
`TerminableSession` with `SessionRegistry` so the round resolves normally
by UUID even while that player is offline, delivered later as a
`PendingPayout` if they're still offline when it resolves. Copy the exact
shape of `CoinFlipClient`/`RockPaperScissorsClient`'s `onSessionTerminated`
— the ordering (route through `SessionRegistry` rather than resolving
inline, idempotency via a `sessionResolved` flag, resolving fresh
`Bukkit.getPlayer(uuid)` references instead of trusting cached `Player`
objects since Bukkit hands out a new object on reconnect) all exist to
handle specific races and are easy to silently break by simplifying.

## 6. Wiring checklist

A new game touches every one of these. Grep for your nearest example
game's `"Title Case"` string (e.g. `"Coin Flip"`) in each file to find
every site — there is no single registry, it's `switch` statements
repeated in each of these files:

| File | What to add |
|---|---|
| [entities/Menu.java](../../../src/main/java/org/nc/nccasino/entities/Menu.java) | Add a `SlotOption` enum entry for the game-picker button (e.g. `ROCK_PAPER_SCISSORS`), and one per custom settings-menu action if your settings menu needs more than the stock `EDIT_TIMER`. |
| [components/GameOptionsMenu.java](../../../src/main/java/org/nc/nccasino/components/GameOptionsMenu.java) | Both constructors: add a `slotMapping.put(SlotOption.X, slot)` (creation menu *and* edit menu have separate slot layouts — don't reuse the same slot number by copy-paste, count existing entries). `initializeMenu()`: add the icon+label in both the `editing` and non-`editing` branches. `handleCustomClick`'s switch: map the enum case to your game-type string. `localizedGameType`: add the switch case. |
| [entities/Dealer.java](../../../src/main/java/org/nc/nccasino/entities/Dealer.java) | Import your `Server` class. Three separate `switch (gameType)`/`switch (gameName)` blocks (`updateGameType`'s spawn-time switch, `switchGame`, and the second `updateGameType` overload) each need a case constructing `new YourServer(dealerId, plugin, internalName)`. Also `localizedGameType`'s switch. |
| [components/AdminMenu.java](../../../src/main/java/org/nc/nccasino/components/AdminMenu.java) | Four separate switches: the game-type icon selector in the main render, `getGameSettingsLore` (what shows in the admin overview's lore for this game — usually just the timer), `handleGameOptions` (constructs and opens your `YourGameMenu`), and `localizedGameName`. |
| [entities/Server.java](../../../src/main/java/org/nc/nccasino/entities/Server.java) | `localizedGameName`'s switch (used for the "Welcome to X" message). |
| [payout/PayoutMessages.java](../../../src/main/java/org/nc/nccasino/payout/PayoutMessages.java) | `gameLocalizationKey`'s switch, so pending (offline) payouts display your game's localized name correctly. |
| [listeners/DealerInteractListener.java](../../../src/main/java/org/nc/nccasino/listeners/DealerInteractListener.java) | `getGamePermission`'s switch — **easy to forget, and if you do, the permission check returns `null` and *every* player is silently denied access to the dealer**, regardless of what permissions they have. This is the actual gate that runs before a player is allowed to open the dealer at all. |
| [plugin.yml](../../../src/main/resources/plugin.yml) | A `nccasino.games.<lowercasename>` permission node (default `true`, listed as a child of `nccasino.games`) matching the string you just added to `getGamePermission`. |

Also worth a scan (rarely need changes, but verify): anywhere else that
does `switch (gameType)` on the game-name string with an explicit case
list rather than a sensible default — a `grep -rn '"Coin Flip"'` (or
whichever existing game you're closest to) across `src/main` will surface
every site including any you didn't expect.

## 7. Admin settings sub-menu

Every game gets its own `<GameName>Menu extends Menu` in
`components/`, opened from `AdminMenu.handleGameOptions`. Copy
[components/CoinFlipMenu.java](../../../src/main/java/org/nc/nccasino/components/CoinFlipMenu.java)
or [components/RockPaperScissorsMenu.java](../../../src/main/java/org/nc/nccasino/components/RockPaperScissorsMenu.java)
wholesale and adjust:

- `slotMapping`: `EXIT` (8), `RETURN` (0), `EDIT_TIMER` (1) are the stock
  layout — add new settings starting at slot 2.
- The timer edit flow (`handleEditTimer` → chat-prompt → `handleNumericInput`)
  closes the player's inventory and waits for a chat message; that's the
  right pattern for free-text input (numbers, names). For a simple toggle
  (like a mode switch), don't reuse that chat-prompt flow — apply the
  change immediately on click and refresh the item in place (see
  `RockPaperScissorsMenu.handleToggleMode`), it's much better UX than
  forcing a close/reopen for something that doesn't need typed input.
- If a settings change needs to swap the running `Server` instance (see
  §4's mode-toggle note) rather than just rewriting a config value the
  `Server` re-reads live (like the timer, which is read fresh from config
  on every round rather than cached), that's the one case where you need
  the manual `DealerInventory.updateInventory` + selective
  `closeStaleViewsExceptSelf`-style cleanup instead of the default
  `plugin.reloadDealer(dealer)` + `deleteAssociatedInventories` +
  `cleanup()` tail every other settings action uses.

## 8. Localization

**Every player-facing string goes through**
`plugin.getLocalization().text(player, key, placeholders...)` — never a
hardcoded English string, not even for a temporary/testing pass, because
of the next paragraph.

[localization/LocalizationService.java](../../../src/main/java/org/nc/nccasino/localization/LocalizationService.java)
validates at plugin startup (`validateLanguages()`) and
[LocalizationResourcesTest.java](../../../src/test/java/org/nc/nccasino/localization/LocalizationResourcesTest.java)
validates in CI that **every key in `en_US.yml` exists in all 4 other
locale files with the exact same `{placeholder}` names**. A key present
only in English silently falls back to English at runtime (harmless but
untranslated) — but a *missing or mismatched-placeholder* translation
fails the test outright. This means: write the English key, then
immediately add the same key (translated) to all 5 files before moving on,
rather than batching localization for the end — it's much easier to keep
placeholders in sync key-by-key than to reconstruct which of 40 new keys
need which `{amount}`/`{player}`/`{seconds}` after the fact.

Files: `src/main/resources/lang/{en_US,es_ES,pt_BR,de_DE,fr_FR}.yml`.
Convention per game, follow `coin-flip:` / `rock-paper-scissors:` in
`en_US.yml` as the template:

- `<game-key>:` block — all in-game UI strings (title, seat prompts, bet
  flow, game-specific action labels, result messages).
- `<game-key>-settings:` block — admin settings menu strings
  (`title` with a `{dealer}` placeholder, `edit-timer`,
  `invalid-option`, `invalid-settings-option`, `prompt-number`,
  `prompt-timer`, `timer-updated`, `dealer-not-found`, plus whatever your
  own settings add).
- `game-options.<game-key>` — one line, the picker-menu label, referenced
  from `GameOptionsMenu`/`AdminMenu`/`Dealer`/`Server`'s
  `localizedGameType`/`localizedGameName` switches (§6).

Run `./gradlew test` (specifically
`org.nc.nccasino.localization.LocalizationResourcesTest`) before
considering localization done, not just a visual read-through of the yml —
placeholder mismatches are easy to introduce (e.g. translating `{amount}`
into the display text instead of leaving the token intact) and only the
test catches them reliably.

## 9. Config conventions

Everything lives under `dealers.<internalName>.*` in the plugin's
`config.yml`, read live (no caching layer) via `Nccasino.java` getters —
follow the existing pattern of a getter with a safe fallback default, e.g.:

```java
public YourEnum getYourGameSetting(String internalName) {
    String raw = getConfig().getString("dealers." + internalName + ".your-key", "DEFAULT");
    try {
        return YourEnum.valueOf(raw);
    } catch (IllegalArgumentException e) {
        return YourEnum.DEFAULT;
    }
}
```

Shared keys every dealer already has: `.display-name`, `.game`, `.timer`,
`.animation-message`, `.currency.material`, `.currency.name`,
`.currency.mode`, `.chip-sizes.size1..5` — set once in
`Nccasino.saveDefaultDealerConfig`, don't duplicate that logic for a new
game. Game-specific keys (like Dragon Descent's `.default-columns` or
your new game's own settings) are read with their own getter and don't
need a shared default-writer unless you want the value visibly present in
`config.yml` immediately on dealer creation rather than only once the
admin actually opens the settings menu (a `getX` with a fallback is
usually enough — see how `getRockPaperScissorsMode` never writes a default
back to disk, it just returns `PLAYER_VS_PLAYER` if the key is absent).

`Server` resolves config-driven fields **once at construction** (see
`currencyMode`/`currencyName` in the base `Server` class, or a custom
`mode` field like RPS's) rather than re-reading on every access — this
means a settings change requires re-constructing the `Server` (see §4 and
§7) for the running game to pick it up, not just writing the new value to
config.

## 10. Verifying the game works

There is no way to launch an actual Minecraft client/server from an agent
session — say so explicitly rather than claiming the feature works from
compile success alone.

What you *can* and should do before calling it done:

1. `./gradlew compileJava compileTestJava` — clean compile.
2. `./gradlew test` — full suite, particularly
   `GameTerminationPolicyTest` and `LocalizationResourcesTest`.
3. Read back through your own `onClientUpdate`/`onServerUpdate` switches
   side-by-side and confirm every event either side sends has a matching
   case on the other side — there's no compiler check for this, a typo'd
   event-name string just silently does nothing.
4. If you added any reveal/animation UI with fixed inventory slots, sanity
   check the slot *layout* against the actual seat positions, not just
   against "which side is `myThrow`" — a viewer-relative UI (see the
   commit that fixed RPS's reveal slots) looks correct in code review but
   inverts for whichever seat isn't slot 0, since a fixed slot number is
   physically closer to one specific seat regardless of who's viewing it.
5. Tell the user explicitly what to manually test in a real server:
   the full happy path, every disconnect/kick scenario your
   `GameTerminationPolicy` case distinguishes, and (if you built a
   PvE/PvP split per §4) two players hitting the same dealer in PvE mode
   simultaneously to confirm they don't interfere with each other.
