# NCCasino Plugin for Minecraft

## Overview

NCCasino is a Minecraft plugin built on Bukkit/Paper MC 1.21, bringing popular casino games like Blackjack and Roulette to minecraft servers. The plugin is still under active development. Join the official Discord [here](https://discord.gg/PaN3Dd4pD8).

## Features

- **Blackjack**: The classic card game where players try to beat the dealer.
![image](https://github.com/user-attachments/assets/10f67401-cb4b-473e-b638-cbda921d4a6d)
- **Roulette**: A game of chance with bets on where the ball lands on a spinning wheel.
![image](https://github.com/user-attachments/assets/1e9f1afd-6f14-4512-889f-ed1dcb79aeec) 
![image](https://github.com/user-attachments/assets/86c04ed7-1c03-49df-ba21-95f943e36aee)
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
