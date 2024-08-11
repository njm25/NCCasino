# NCCasino Plugin for Minecraft

## Overview

NCCasino is a Minecraft plugin built on Bukkit/Paper, bringing popular casino games like Blackjack and Roulette to minecraft servers. The plugin is still under active development.

## Features

- **Blackjack**: The classic card game where players try to beat the dealer.
- **Roulette**: A game of chance with bets on where the ball lands on a spinning wheel.
- **Dealer Configuration**: Customize game type, currency, and chip denominations via the configuration file.
- **Inventory GUI**: All interactions are managed through a user-friendly inventory interface.

## Commands

- **/ncc create `<name>`**: Create a dealer at the player’s location.
- **/ncc list `(page)`**: List all dealers.
- **/ncc delete `<name>`**: Delete a dealer by name.
- **/ncc help**: Display help menu.
- **/ncc reload**: Reload the configuration file.

## Configuration

Customize dealers in the YAML configuration file. Adjust game type, currency material, and chip sizes.

### Example Configuration

```yaml
dealers:
  myDealer:
    game: Blackjack
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

## Contributing

We welcome contributions to improve NCCasino. Whether it's reporting issues, suggesting new features, or submitting code, your input is valuable. To learn more about how to contribute, [click here](deploy.md) to get started.
