# NCCasino Plugin for Minecraft

## Overview

NCCasino is a Spigot/Paper plugin that brings casino games to Minecraft servers. Each game is run by a dealer: a mob, or a Citizens NPC, that players right-click to play. Join the official Discord [here](https://discord.gg/PaN3Dd4pD8).

## Games

Blackjack, Roulette, Baccarat, Mines, Dragon Descent, Coin Flip (against another player or the dealer), Rock Paper Scissors (against another player or the dealer) and Slots.

## Requirements

- Minecraft 1.21 (1.21.0) through 26.3, on Spigot or Paper.
  - On servers older than a game feature, that feature is simply left out. For example, mobs the server cannot spawn are hidden from the dealer pickers.
- Java 21 or newer (or whatever your server version needs).
- Optional: [Vault](https://www.spigotmc.org/resources/vault.34315/) with an economy plugin, for dealers that play for money instead of items.
- Optional: [Citizens](https://www.spigotmc.org/resources/citizens.13811/), for NPC dealers. Use the Citizens build made for your server version:
  - On 26.3, use Citizens 2.0.44 or newer; 2.0.43 does not load there.
  - On Spigot 1.21.11, Citizens 2.0.43 cannot spawn player-type NPCs (a Citizens bug); 2.0.41 works.

## Installing and upgrading

1. Stop the server, put the NCCasino JAR in `plugins/`, and start the server.
2. When upgrading, remove the old NCCasino JAR first. Your dealers, settings and player data are kept:
   - New options are added to your existing `config.yml`, and a backup of the old file is saved next to it. `config.reference.yml` explains every option.
   - Old full language files are moved to `lang/legacy-backup-<date>/`; NCCasino uses its built-in translations.

A full restart is the safest way to update. `/reload` and `/ncc reload` are supported too: seated players get their stakes back, and repeated reloads no longer use up the server's memory. A crash (not a normal stop) can't finish rounds in progress: item stakes and winnings follow the last world save, and Vault stakes of an unfinished round are lost.

## Commands

- **/ncc create `<name>`**: Create a dealer at your location.
- **/ncc list `(page)`**: List all dealers.
- **/ncc delete `<name>`**: Delete a dealer by name. `/ncc delete *` deletes every dealer. Both also remove dealers whose mob or NPC is gone.
- **/ncc reload**: Reload the configuration. Seated players get their stakes back.
- **/ncc claim**: Collect winnings that were banked because they did not fit in your inventory.
- **/ncc help**: List the commands you can use.

After `/ncc create`, pick the dealer's game from the menu that opens. Sneak + right-click a dealer to open its admin menu (game, mob, currency, chip sizes, per-game settings, budget); players without the admin permission get their own menu (preferences and language) instead. To make a Citizens NPC a dealer, bind it from a dealer's admin menu.

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `nccasino.use` | everyone | the `/ncc` command itself |
| `nccasino.commands.claim` | everyone | `/ncc claim` |
| `nccasino.commands.help` | everyone | `/ncc help` (lists only what the player may use) |
| `nccasino.commands.create`, `.delete`, `.list`, `.reload` | op | the admin subcommands |
| `nccasino.adminmenu` | op | a dealer's admin menu |
| `nccasino.playermenu` | everyone | the player menu (preferences, language) |
| `nccasino.games.<game>` | everyone | playing a game: `blackjack`, `roulette`, `baccarat`, `mines`, `dragon`, `coinflip`, `rockpaperscissors`, `slots` |

`nccasino.commands` and `nccasino.games` grant every permission in their group.

## Languages

NCCasino ships in 90 languages, including right-to-left Arabic, Persian, Hebrew and Yiddish. Each player sees it in their Minecraft language automatically, or picks one in Preferences. `language.default` in `config.yml` sets the language for everyone else, and `language.detect-client: false` turns detection off.

To change a single line, put it in `plugins/NCCasino/lang/overrides/<locale>.yml`, using the same key as the built-in catalog.

## Configuration

Dealers are stored in `config.yml` under `dealers`, and most settings are easier to change from the admin menu. A dealer looks like this:

```yaml
dealers:
  myDealer:
    game: Blackjack
    timer: 10
    currency:
      material: EMERALD
      name: Emerald
    chip-sizes:
      size1: 1
      size2: 5
      size3: 10
      size4: 25
      size5: 50
```

- **Item currency** is matched by material only: any emerald counts as an emerald chip, whatever its name. Pick a material players can't get cheaply.
- **Money dealers**: with Vault and an economy plugin installed, set a dealer's currency to Vault in its admin menu (`currency.mode: VAULT`). Without an economy, such a dealer plays for its item currency, and the console says so at startup.
- **Dealer budgets**: by default a dealer can pay out without limit. `budget.mode: LIMITED` gives it a bankroll, with optional refills; wagers it could not cover are refused up front. See [docs/DEALER_BUDGET_SCHEMA.md](docs/DEALER_BUDGET_SCHEMA.md).
- **Winnings that do not fit**: the `payouts` section decides whether item winnings that do not fit in a player's inventory are banked (claimed with `/ncc claim` or delivered automatically) or dropped. Nothing is ever deleted.

## Contributing

We welcome contributions to improve NCCasino. Whether it's reporting issues, suggesting new features, or submitting code, your input is valuable. To learn more about how to contribute, [click here](deploy.md) to get started.
