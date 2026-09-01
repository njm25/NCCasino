package org.nc.nccasino.entities;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.currency.CurrencyProvider;
import org.nc.nccasino.currency.MoneyHelper;
import org.nc.nccasino.currency.VaultCurrencyProvider;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.payout.BankedCurrency;
import org.nc.nccasino.payout.OverflowBankService;
import org.nc.nccasino.payout.ItemDeliveryOutcome;
import org.nc.nccasino.payout.UnsettledPayouts;

public abstract class Server extends DealerInventory {

    protected final Nccasino plugin;

    protected final Map<UUID, Client> clients = new HashMap<>();

    protected boolean bettingEnabled = true;

    protected SessionState serverState = SessionState.LOBBY;

    protected String internalName;
    /** Resolved once at init for formatWagerDisplay. */
    protected final CurrencyMode currencyMode;
    protected final String currencyName;

    protected final Map<UUID, SessionState> clientStates = new HashMap<>();

    public enum GameState { WAITING, RUNNING, PAUSED }

    protected GameState gameState = GameState.WAITING;

    public Server(UUID dealerId, Nccasino plugin, String internalName) {
        super(dealerId, 9, "");
        this.plugin = plugin;
        this.internalName = internalName;
        this.currencyMode = plugin.getCurrencyMode(internalName);
        this.currencyName = plugin.getCurrencyName(internalName);
        registerListener();
    }

    public GameState getGameState() {
        return gameState;
    }

    protected void setGameState(GameState newState) {
        gameState = newState;
    }

    public enum SessionState {
        LOBBY,
        IN_PROGRESS,
        FINISHED
        // etc.
    }

    public void setServerState(SessionState newState) {
        SessionState oldState = this.serverState;
        this.serverState = newState;

        // Broadcast update to all clients
        broadcastServerStateUpdate(oldState, newState);
    }

    public SessionState getServerState() {
        return serverState;
    }

    public void setClientState(UUID playerUuid, SessionState newState) {
        SessionState oldState = clientStates.getOrDefault(playerUuid, SessionState.LOBBY);
        clientStates.put(playerUuid, newState);

        // Notify just that single client
        sendStateUpdateToClient(playerUuid, oldState, newState);
    }

    public SessionState getClientState(UUID playerUuid) {
        return clientStates.getOrDefault(playerUuid, SessionState.LOBBY);
    }

    protected void sendStateUpdateToClient(UUID playerUuid, SessionState oldState, SessionState newState) {
        Client client = clients.get(playerUuid);
        if (client != null) {
            client.onServerStateChange(oldState, newState);
        }
    }

    protected void broadcastServerStateUpdate(SessionState oldState, SessionState newState) {
        for (Client client : clients.values()) {
            client.onServerStateChange(oldState, newState);
        }
    }

    protected void handleServerOpen(Player player) {
        Client client = getOrCreateClient(player);
        if (client != null) {
            String gameType = plugin.getConfig().getString("dealers." + internalName + ".game");

            switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
                case STANDARD:{
                    break;}
                case VERBOSE:{
                    player.sendMessage(plugin.getLocalization().text(
                        player,
                        "game.welcome",
                        "game",
                        localizedGameName(player, gameType)
                    ));
                    break;     
                }
                    case NONE:{
                    break;
                }
            } 
            player.openInventory(client.getInventory());
        } else {
        }
    }

    public Client getOrCreateClient(Player player) {
        UUID uuid = player.getUniqueId();
        Client existing = clients.get(uuid);
        if (existing != null) {
            if (isStale(existing, player)) {
                // A previous session's Client is still registered, holding
                // a dead Player reference (e.g. cleanup never ran because
                // they disconnected rather than closing the inventory).
                // Reusing it would silently fail to message/pay the actual
                // reconnected player, so discard it before creating fresh.
                removeClient(uuid);
            } else {
                return existing;
            }
        }

        // Create a new client using the abstract factory method
        Client newClient = createClientForPlayer(player);
        clients.put(uuid, newClient);

        // Optionally, store an initial session state for them
        clientStates.put(uuid, SessionState.LOBBY);

        return newClient;
    }

    /**
     * A cached Client is only safe to reuse if it still refers to the
     * exact Player object of the current login session. UUID equality
     * alone isn't enough — Bukkit hands out a brand-new Player object on
     * every reconnect, so an old, disconnected Player reference can share
     * the same UUID while being otherwise dead.
     */
    private boolean isStale(Client existing, Player currentPlayer) {
        Player cachedPlayer = existing.getPlayer();
        return cachedPlayer == null || !cachedPlayer.isOnline() || !cachedPlayer.equals(currentPlayer);
    }

    protected abstract Client createClientForPlayer(Player player);

    public void removeClient(UUID uuid) {
        // Remove from the states map if desired
        clientStates.remove(uuid);

        // Remove from client map
        Client client = clients.remove(uuid);
        if (client != null) { 
            client.cleanup();
        }
    }

    @EventHandler
    public void handleInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() == this) {
            Player player = (Player) event.getPlayer();
    
            event.setCancelled(true); // Prevents the server inventory from actually opening
    
            handleServerOpen(player);
        }
    }

    public abstract void onClientUpdate(Client client, String eventType, Object data);

    protected void broadcastUpdate(String eventType, Object data) {
        for (Client client : clients.values()) {
            client.onServerUpdate(eventType, data);
        }
    }

    protected void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    protected void unregisterListener() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public void delete() {
        // Let the parent class handle any standard cleanup
        super.delete();

        // Clean up each client
        for (Client client : clients.values()) {
            client.cleanup();
        }
        clients.clear();
        clientStates.clear();

        // Unregister from Bukkit events
        unregisterListener();
    }

    protected void playCountdownSound() {
        for (Client client : clients.values()) {
            Player player = client.getPlayer();
            if (player != null) {
                if (SoundHelper.getSoundSafely("block.note_block.hat",player) != null) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }
            }
        }
    }

    public boolean hasClient(UUID playerUuid) {
        return clients.containsKey(playerUuid);
    }

    private String localizedGameName(Player player, String gameType) {
        if (gameType == null) {
            return String.valueOf(gameType);
        }
        return switch (gameType) {
            case "Blackjack" -> plugin.getLocalization().text(player, "game-options.blackjack");
            case "Roulette" -> plugin.getLocalization().text(player, "game-options.roulette");
            case "Mines" -> plugin.getLocalization().text(player, "game-options.mines");
            case "Baccarat" -> plugin.getLocalization().text(player, "game-options.baccarat");
            case "Coin Flip" -> plugin.getLocalization().text(player, "game-options.coin-flip");
            case "Rock Paper Scissors" -> plugin.getLocalization().text(player, "game-options.rock-paper-scissors");
            case "Dragon Descent" -> plugin.getLocalization().text(player, "game-options.dragon-descent");
            case "Slots" -> plugin.getLocalization().text(player, "game-options.slots");
            case "Test Game" -> plugin.getLocalization().text(player, "game-options.test-game");
            default -> gameType;
        };
    }
        
    public void sendPayoutMessage(Player player, double payout, boolean isWinner, double profit) {
		switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
			case STANDARD:
				player.sendMessage(isWinner
						? plugin.getLocalization().text(
							player,
							"payout.paid",
							"amount",
							plugin.formatWagerDisplay(currencyMode, currencyName, payout)
						)
						: plugin.getLocalization().text(player, "payout.lost"));
				break;
			case VERBOSE:
				player.sendMessage(isWinner
						? plugin.getLocalization().text(
							player,
							"payout.paid-with-profit",
							"amount",
							plugin.formatWagerDisplay(currencyMode, currencyName, payout),
							"profit",
							plugin.formatWagerDisplay(currencyMode, currencyName, profit)
						)
						: plugin.getLocalization().text(player, "payout.lost"));
				break;
			case NONE:
				break;
		}
    }
    
    public void applyWinEffects(Player player) {
        if (player != null) {
            player.getWorld().spawnParticle(Particle.GLOW, player.getLocation(), 50);
            playRandomPitchSound(player);
        }
    }
    
    public void applyLoseEffects(Player player) {
        if (player != null) {
            if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1.0f, 1.0f);
            }
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20);
        }
    }
    
    protected void playRandomPitchSound(Player player) {
        if (SoundHelper.getSoundSafely("entity.player.levelup", player) != null) {
            Random random = new Random();
            float[] possiblePitches = {0.5f, 0.8f, 1.2f, 1.5f, 1.8f, 0.7f, 0.9f, 1.1f, 1.4f, 1.9f};
            for (int i = 0; i < 3; i++) {
                float chosenPitch = possiblePitches[random.nextInt(possiblePitches.length)];
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1.0f, chosenPitch);
            }
        }
    }

    
    protected void creditPlayer(Player player, double amount) {
		Material currencyMaterial = plugin.getCurrency(internalName);
		if (currencyMaterial == null) {
			player.sendMessage(plugin.getLocalization().text(player, "errors.currency-unavailable"));
			return;
		}

		CurrencyProvider provider = getCurrencyProvider();
		if (provider != null && provider.getMode() == CurrencyMode.VAULT) {
			if (provider instanceof VaultCurrencyProvider vaultProvider) {
				java.math.BigDecimal payout = MoneyHelper.clampNonNegative(MoneyHelper.bd(amount));
				if (payout.compareTo(java.math.BigDecimal.ZERO) <= 0) {
					return;
				}
				// deposit()'s boolean return exists specifically so a caller
				// that owes this amount unconditionally must not treat a
				// false return as success -- queue it durably instead of
				// letting a failed Vault deposit silently vanish the money.
				boolean delivered = vaultProvider.deposit(player, internalName, payout);
				if (!delivered) {
					queueFailedDepositPayout(player.getUniqueId(), amount, currencyMaterial);
				}
				return;
			}
		}

		int toGive = (int) amount;
		if (toGive <= 0) {
			return;
		}

		if (provider != null) {
			// STANDARD: keep existing behavior (provider-backed with leftover drop).
			if (provider.getMode() == CurrencyMode.STANDARD) {
				int before = provider.getBalance(player, internalName);
				provider.deposit(player, internalName, toGive);
				int after = provider.getBalance(player, internalName);

				int actuallyAdded = Math.max(0, after - before);
				int leftoverAmount = toGive - actuallyAdded;

				if (leftoverAmount > 0) {
					switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
						case STANDARD:{
							player.sendMessage(plugin.getLocalization().text(
								player,
								"betting.inventory-full",
								"amount",
								plugin.formatWagerDisplay(currencyMode, currencyName, leftoverAmount)
							));

							break;}
						case VERBOSE:{
							player.sendMessage(plugin.getLocalization().text(
								player,
								"betting.inventory-full",
								"amount",
								plugin.formatWagerDisplay(currencyMode, currencyName, leftoverAmount)
							));
							break;     
						}
							case NONE:{
							break;
						}
					} 
					dropExcessItems(player, leftoverAmount, currencyMaterial);
				}
				return;
			}

			// CUSTOM (or any non-STANDARD except VAULT): rely solely on the provider, no item fallback.
			boolean delivered = provider.deposit(player, internalName, toGive);
			if (!delivered) {
				queueFailedDepositPayout(player.getUniqueId(), toGive, currencyMaterial);
			}
			return;
		}

		// No provider available: legacy item behavior.
		int fullStacks = toGive / 64;
		int remainder = toGive % 64;
		int totalLeftoverAmount = 0;
		HashMap<Integer, ItemStack> leftover;

		// Try adding full stacks
		for (int i = 0; i < fullStacks; i++) {
			ItemStack stack = new ItemStack(currencyMaterial, 64);
			leftover = player.getInventory().addItem(stack);
			if (!leftover.isEmpty()) {
				totalLeftoverAmount += leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
			}
		}

		// Try adding remainder
		if (remainder > 0) {
			ItemStack remainderStack = new ItemStack(currencyMaterial, remainder);
			leftover = player.getInventory().addItem(remainderStack);
			if (!leftover.isEmpty()) {
				totalLeftoverAmount += leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
			}
		}

		if (totalLeftoverAmount > 0) {
			switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
				case STANDARD:{
					player.sendMessage(plugin.getLocalization().text(
						player,
						"betting.inventory-full",
						"amount",
						plugin.formatWagerDisplay(currencyMode, currencyName, totalLeftoverAmount)
					));

					break;}
				case VERBOSE:{
					player.sendMessage(plugin.getLocalization().text(
						player,
						"betting.inventory-full",
						"amount",
						plugin.formatWagerDisplay(currencyMode, currencyName, totalLeftoverAmount)
					));
					break;     
				}
					case NONE:{
					break;
				}
			} 
			dropExcessItems(player, totalLeftoverAmount, currencyMaterial);
		}
    }

    protected void dropExcessItems(Player player, int amount, Material currencyMaterial) {
        if (amount <= 0 || currencyMaterial == null) {
            return;
        }
        OverflowBankService bank = plugin.getOverflowBankService();
        if (bank == null) {
            retainUnsettledPayout(player, amount, currencyMaterial);
            return;
        }
        // Deliver what fits, drop only within the configured cap, bank the
        // rest. Anything the bank could not record becomes a durable pending
        // payout -- never an uncapped drop, and never merely a log line.
        ItemDeliveryOutcome outcome = bank.deliver(
            player, new BankedCurrency(currencyMode, currencyMaterial.name(), currencyName), amount);
        if (!outcome.settled()) {
            retainUnsettledPayout(player, outcome.unsettled(), currencyMaterial);
        }
    }

    /**
     * Records a remainder that reached neither the player nor the bank as a
     * retryable obligation. The amount is by construction undelivered, so
     * retaining it cannot double-pay.
     */
    private void retainUnsettledPayout(Player player, long amount, Material currencyMaterial) {
        UnsettledPayouts.retain(
            plugin,
            player.getUniqueId(),
            resolveGameType(),
            internalName,
            currencyMode,
            currencyMaterial.name(),
            currencyName,
            amount);
    }

    /** The dealer's configured game, used to label a retained obligation. */
    private String resolveGameType() {
        String configured = plugin.getConfig().getString("dealers." + internalName + ".game");
        return configured == null ? "NCCasino" : configured;
    }

    /**
     * Durably queues a payout this player was owed but a live Vault/CUSTOM
     * provider deposit failed to deliver. Unlike {@link #retainUnsettledPayout}
     * (item currency, whole units only), this keeps the amount as a
     * fractional double -- Vault currency can be fractional, and truncating
     * it here would silently lose cents. The dealer must never be treated as
     * having settled this money while the player received nothing and no
     * durable obligation exists.
     */
    private void queueFailedDepositPayout(java.util.UUID playerId, double amount, Material currencyMaterial) {
        if (amount <= 0) {
            return;
        }
        if (plugin.getPendingPayoutStore() == null) {
            plugin.getLogger().severe("[NCCasino] " + resolveGameType() + " payout of " + amount
                + " for " + playerId + " failed to deliver and could not be durably retained"
                + " -- money genuinely lost.");
            return;
        }
        org.nc.nccasino.payout.PendingPayout payout = org.nc.nccasino.payout.PendingPayout.create(
            playerId,
            resolveGameType(),
            internalName,
            currencyMode,
            currencyMaterial.name(),
            currencyName,
            amount,
            org.nc.nccasino.payout.PayoutMessages.committedResultContext(resolveGameType())
        );
        boolean persisted = plugin.getPendingPayoutStore().addPendingPayout(payout);
        if (!persisted) {
            plugin.getLogger().severe("[NCCasino] " + resolveGameType() + " payout of " + amount
                + " for " + playerId + " failed to deliver AND failed to persist as a pending payout"
                + " -- money genuinely lost.");
        }
    }

	// Thin wrapper around the CurrencyManager to obtain the provider for this server.
	private CurrencyProvider getCurrencyProvider() {
		if (plugin.getCurrencyManager() == null) {
			return null;
		}

		return plugin.getCurrencyManager().getProvider(internalName);
	}
}
