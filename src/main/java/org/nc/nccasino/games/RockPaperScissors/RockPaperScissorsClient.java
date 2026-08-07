package org.nc.nccasino.games.RockPaperScissors;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.GameTerminationPolicy;
import org.nc.nccasino.session.TerminationAction;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

public class RockPaperScissorsClient extends Client implements TerminableSession {

    private enum SlotOption
    {
        HANDLE_CHAIR_1,
        HANDLE_CHAIR_2,
        HANDLE_SUBMIT_BET,
        CHOOSE_ROCK,
        CHOOSE_PAPER,
        CHOOSE_SCISSORS,
        LEAVE
    }
    protected final Map<SlotOption, Integer> slotMapping = new HashMap<>();

    private static final int STATUS_SLOT = 13;
    /** Vertical throw columns mirrored across the inventory's center column. */
    private static final int[] CHAIR_ONE_THROW_SLOTS = { 12, 21, 30 };
    private static final int[] CHAIR_TWO_THROW_SLOTS = { 14, 23, 32 };
    private static final int[] CHAIR_ONE_ORBIT_SLOTS = { 10, 11, 12, 21, 30, 29, 28, 19 };
    private static final int[] CHAIR_TWO_ORBIT_SLOTS = { 14, 15, 16, 25, 34, 33, 32, 23 };
    private static final int CADENCE_VISIBLE_TICKS = 10;
    private static final int CADENCE_BLANK_TICKS = 2;
    private static final int CADENCE_TO_SHOOT_TICKS =
        3 * (CADENCE_VISIBLE_TICKS + CADENCE_BLANK_TICKS);
    /** Keeps the complete reveal window at the server's existing 70 ticks. */
    private static final int RESULT_HOLD_TICKS = 70 - CADENCE_TO_SHOOT_TICKS;
    private static final long WINNER_ORBIT_INTERVAL_TICKS = 2L;
    private static final float[] WINNER_ORBIT_DING_PITCHES = { 1.4f, 1.7f, 2.0f };

    protected Player chairOneOccupant;
    protected Player chairTwoOccupant;

    protected int betAmount = 0;

    private final String clickHereToSit;
    private boolean gameActive = false;
    private boolean myChoiceLocked = false;
    private boolean opponentLockedIn = false;
    private boolean sessionResolved = false;
    /** Resolved once at construction, same as the server -- reflects the config at the moment this GUI was opened. */
    private final RpsMode mode;

    public RockPaperScissorsClient(Server server, Player player, Nccasino plugin, String internalName) {
        super(server, player, plugin.getLocalization().text(player, "rock-paper-scissors.title"), plugin, internalName);
        SessionRegistry.register(player.getUniqueId(), this);
        this.clickHereToSit = text("rock-paper-scissors.click-sit");
        this.chairOneOccupant = null;
        this.chairTwoOccupant = null;
        this.mode = plugin.getRockPaperScissorsMode(internalName);

        slotMapping.put(SlotOption.HANDLE_CHAIR_1, 20);
        slotMapping.put(SlotOption.HANDLE_CHAIR_2, 24);
        slotMapping.put(SlotOption.LEAVE, 36);
        slotMapping.put(SlotOption.HANDLE_SUBMIT_BET, 44);
        setChoiceSlotMapping(CHAIR_ONE_THROW_SLOTS);

        addItemAndLore(Material.SPRUCE_DOOR, 1, text("rock-paper-scissors.leave"), slotMapping.get(SlotOption.LEAVE));
        populateGlassPattern();
        if (mode == RpsMode.PLAYER_VS_DEALER) {
            renderDealerSeat();
        }
        sendUpdateToServer("GET_CHAIRS", null);
    }

    /*
     *
     * CLIENT INTERACTION
     *
     */

    @Override
    protected void handleClientSpecificClick(int slot, Player player, InventoryClickEvent event) {
        SlotOption option = getKeyByValue(slotMapping, slot);
        if (option == null) return;
        switch(option)
        {
            case HANDLE_CHAIR_1:
                if (gameActive) return;
                handleChairOne();
                break;
            case HANDLE_CHAIR_2:
                if (gameActive) return;
                handleChairTwo();
                break;
            case HANDLE_SUBMIT_BET:
                if (gameActive) return;
                handleSubmitBet();
                break;
            case CHOOSE_ROCK:
                handleChoose(Throw.ROCK);
                break;
            case CHOOSE_PAPER:
                handleChoose(Throw.PAPER);
                break;
            case CHOOSE_SCISSORS:
                handleChoose(Throw.SCISSORS);
                break;
            case LEAVE:
                player.closeInventory();
                break;
        }
    }

    @Override
    protected void handleClientInventoryClose() {
        // Route through the same idempotent path used for quit/kick rather
        // than resolving directly here -- whichever of this or the quit
        // event fires first "wins" and the other becomes a safe no-op, and
        // consumeQuitReason still correctly reports KICKED here even if
        // this fires first, since the kick is marked as soon as
        // PlayerKickEvent itself fires.
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!SessionRegistry.isRegistered(playerId, this)) {
                return;
            }
            if (!player.isOnline()) {
                ExitReason reason = SessionRegistry.consumeQuitReason(playerId);
                SessionRegistry.terminatePlayerSession(playerId, reason);
                return;
            }
            SessionRegistry.terminateSession(playerId, this, ExitReason.DISCONNECTED);
        });
    }

    /**
     * Authoritative disconnect/kick resolution, reached via SessionRegistry
     * regardless of whether this fires from PlayerQuitEvent or from this
     * client's own InventoryCloseEvent.
     */
    @Override
    public void onSessionTerminated(UUID terminatedPlayerId, ExitReason reason) {
        if (sessionResolved) {
            return; // already resolved through another path
        }
        sessionResolved = true;

        TerminationAction action = GameTerminationPolicy.rockPaperScissors(reason, gameActive);
        if (action == TerminationAction.FORFEIT) {
            server.removeClient(terminatedPlayerId);
            ((RockPaperScissorsServer) server).forfeitPlayer(terminatedPlayerId);
        } else if (action == TerminationAction.REFUND && gameActive) {
            ((RockPaperScissorsServer) server).refundForShutdown(terminatedPlayerId);
            server.removeClient(terminatedPlayerId);
        } else {
            if (action == TerminationAction.REFUND && !gameActive) {
                if (player == chairOneOccupant) {
                    sendUpdateToServer("PLAYER_LEAVE_ONE", null);
                }
                if (player == chairTwoOccupant) {
                    sendUpdateToServer("PLAYER_LEAVE_TWO", null);
                }
            }
            // gameActive: let it ride -- the accepted round (including any
            // tie-rethrow) resolves normally by UUID at payout time
            // (delivered as a pending payout if still offline then).
            if (gameActive) {
                ((RockPaperScissorsServer) server).registerRidingSession(terminatedPlayerId);
            }
            server.removeClient(terminatedPlayerId);
        }
    }

    private void handleChairOne(){
        if (chairOneOccupant == null){
            chairOneOccupant = player;
            if (SoundHelper.getSoundSafely("block.wood.place", player) != null) player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, SoundCategory.MASTER, 1.0f, 1.0f);
            sendUpdateToServer("PLAYER_SIT_ONE", null);
        }
        else if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null) player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.MASTER, 1.0f, 1.0f);
            sendUpdateToServer("PLAYER_LEAVE_ONE", null);
        }
        else if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())) {
            if (SoundHelper.getSoundSafely("entity.player.hurt", player) != null) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    private void handleChairTwo(){
        if (mode == RpsMode.PLAYER_VS_DEALER) {
            if (SoundHelper.getSoundSafely("entity.zombie.hurt", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_HURT, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }
        if (chairOneOccupant != null && chairTwoOccupant == null){
            if (chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
                denyAction(player, text("rock-paper-scissors.already-seated"));
                return;
            }
            chairTwoOccupant = player;
            if (SoundHelper.getSoundSafely("block.wood.place", player) != null) player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, SoundCategory.MASTER, 1.0f, 1.0f);
            sendUpdateToServer("PLAYER_SIT_TWO", null);
        }
        else if(chairTwoOccupant != null){
            if(chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
                if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null) player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.MASTER, 1.0f, 1.0f);
                sendUpdateToServer("PLAYER_LEAVE_TWO", null);
            }
            else if(chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
                if (SoundHelper.getSoundSafely("entity.player.hurt", player) != null) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    private void handleSubmitBet() {
        if (chairOneOccupant!=null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())) {
            if(!betStack.isEmpty()){
                int totalBet = (int) betStack.stream().mapToDouble(Double::doubleValue).sum();
                if(totalBet > 0){
                    sendUpdateToServer("PLAYER_SUBMIT_BET", totalBet);
                }
            }
            else {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("rock-paper-scissors.invalid-action"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("rock-paper-scissors.no-bet"));
                        break;
                    case NONE:
                        break;
                }
                if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        } else if (chairTwoOccupant !=null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())) {
            if(betAmount>0){
                int amount = betAmount;
                boolean betAccepted = handlePlayerTwoAccept(amount);
                if (betAccepted){
                    sendUpdateToServer("PLAYER_ACCEPT_BET", betAccepted);
                }
            }
        }
    }

    private void handleChoose(Throw choice) {
        if (!gameActive || myChoiceLocked) return;
        boolean seated = (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(player.getUniqueId()))
            || (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId()));
        if (!seated) return;

        myChoiceLocked = true;
        sendUpdateToServer("PLAYER_CHOOSE", choice);
        clearChoiceButtons();
        updateStatusIndicator();
        if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        // The server may resolve (and broadcast a reset) synchronously
        // within this same click before Bukkit finishes processing it --
        // force a resync so the clicking player's own view doesn't stall
        // on stale slots the way it would without this.
        player.updateInventory();
    }

    /*
     *
     *
     *  SERVER INTERACTION
     *
     *
     */

    @Override
    public void onServerUpdate(String eventType, Object data) {
        switch(eventType) {
            case "PLAYER_SIT_ONE":
                handlePlayerOneSit(data);
                break;
            case "PLAYER_SIT_TWO":
                handlePlayerTwoSit(data);
                break;
            case "PLAYER_LEAVE_ONE":
                handlePlayerOneLeave();
                break;
            case "PLAYER_LEAVE_TWO":
                handlePlayerTwoLeave();
                break;
            case "PLAYER_SUBMIT_BET":
                handleSubmitBet((int) data);
                break;
            case "PLAYER_CANCEL_BET":
                handleCancelBet();
                break;
            case "PLAYER_ACCEPT_BET":
                handleAcceptBet((Boolean) data);
                break;
            case "UPDATE_TIMER":
                updateTimerUI((int) data);
                break;
            case "OPPONENT_LOCKED_IN":
                handleOpponentLockedIn();
                break;
            case "REVEAL":
                handleReveal((Object[]) data);
                break;
            case "TIE_REVEAL":
                handleTieReveal((Object[]) data);
                break;
            case "RETHROW":
                handleRethrow();
                break;
            case "FORFEIT_TIMEOUT":
                handleForfeitTimeout((int) data);
                break;
            case "ROUND_VOID":
                handleRoundVoid();
                break;
            case "GET_CHAIRS":
                handleGetChairs(data);
                break;
            case "ANIMATION_FINISHED":
                handleAnimationFinished();
                break;
            default:
                break;
        }
    }

    private void handlePlayerOneSit(Object data){
        Player playerData = (Player) data;
        if(playerData.getUniqueId().equals(player.getUniqueId())){
            initializeUI(false, true, false);
            resetPlayerOneUI();
            if (mode == RpsMode.PLAYER_VS_PLAYER) {
                addItemAndLore(Material.OAK_STAIRS, 1, text("rock-paper-scissors.player-two-seat"), slotMapping.get(SlotOption.HANDLE_CHAIR_2));
            }
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1),
                createPlayerHead(playerData.getUniqueId(), playerData.getDisplayName(), text("rock-paper-scissors.click-leave-chair")));
        }
        else{
            if (mode == RpsMode.PLAYER_VS_PLAYER) {
                addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
            }
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1),
                createPlayerHead(playerData.getUniqueId(), playerData.getDisplayName()));
        }
        hidePotChest();
        betAmount = 0;
        chairOneOccupant = playerData;
    }

    private void handlePlayerTwoSit(Object data){
        Player playerData2 = (Player) data;
        if (playerData2.getUniqueId().equals(player.getUniqueId())){

            if (betAmount == 0){
                addItemAndLore(Material.LEVER
                , 1
                , text("rock-paper-scissors.player-turn", "player", chairOneOccupant.getDisplayName())
                , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                , text("rock-paper-scissors.waiting-bet")
            );
            }
            else{
                addItemAndLore(Material.LEVER
                    , 1
                    , text("rock-paper-scissors.accept-bet")
                    , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                    , text("rock-paper-scissors.click-accept-bet")
                    , text("rock-paper-scissors.current", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount))
                );
            }

            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2),
                createPlayerHead(playerData2.getUniqueId(), playerData2.getDisplayName(), text("rock-paper-scissors.click-leave-chair")));
        }
        else {
            if(chairOneOccupant.getUniqueId().equals(player.getUniqueId()) && !gameActive && betAmount > 0){
                addItemAndLore(
                    Material.LEVER
                    , 1
                    , text("rock-paper-scissors.player-turn", "player", playerData2.getDisplayName())
                    , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                    , text("rock-paper-scissors.click-cancel-bet")
                );
            }
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2),
                createPlayerHead(playerData2.getUniqueId(), playerData2.getDisplayName()));
        }
        chairTwoOccupant = playerData2;
    }

    private void handlePlayerOneLeave(){
        if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            clearBettingRow();
            replaceBottomRow();
            if(!betStack.isEmpty()){
                undoAllBets();
                updateBetLore(53, 0);
            }
            clearHandleButton();
        }
        hidePotChest();
        chairOneOccupant = null;
        addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
        if(chairTwoOccupant == null && mode == RpsMode.PLAYER_VS_PLAYER){
            addItemAndLore(Material.OAK_STAIRS, 1, text("rock-paper-scissors.seat-unavailable"), slotMapping.get(SlotOption.HANDLE_CHAIR_2), text("rock-paper-scissors.sit-other-chair"));
        }
    }

    private void handlePlayerTwoLeave(){

        addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        if(chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            clearHandleButton();
        }
        else if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            if(!gameActive && betAmount > 0){
                addItemAndLore(
                    Material.LEVER
                    , 1
                    , text("rock-paper-scissors.player-two-turn")
                    , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                    , text("rock-paper-scissors.click-cancel-bet")
                );
            }
            addItemAndLore(Material.OAK_STAIRS, 1, text("rock-paper-scissors.player-two-seat"), slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        }
        chairTwoOccupant = null;
    }

    private void handleSubmitBet(int data){
        betAmount = data;
        if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            if(chairTwoOccupant == null){
                addItemAndLore(Material.LEVER, 1
                , text("rock-paper-scissors.player-two-turn")
                , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                , text("rock-paper-scissors.click-cancel-bet")
                );
            }
            else{
                addItemAndLore(Material.LEVER, 1
                , text("rock-paper-scissors.player-turn", "player", chairTwoOccupant.getDisplayName())
                , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                , text("rock-paper-scissors.click-cancel-bet"));

            }
            bettingEnabled = false;
            replaceBottomRow();
        }
        else if(chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            addItemAndLore(Material.LEVER
            , 1, text("rock-paper-scissors.accept-bet")
            , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
            , text("rock-paper-scissors.click-accept-bet")
            , text("rock-paper-scissors.current", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount))
            );
        }
        if (SoundHelper.getSoundSafely("block.enchantment_table.use", player) != null) player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);
        updatePotChest();

    }

    private void handleCancelBet(){
        if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            undoAllBets();
            updateBetLore(53, 0);
            resetPlayerOneUI();
            bettingEnabled = true;
            betAmount = 0;
            initializeUI(rebetEnabled, bettingEnabled, false);
        }
        else if(chairTwoOccupant!= null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            resetPlayerTwoUI();
        }
        hidePotChest();
    }

    private void handleAcceptBet(Boolean accepted){
        if(accepted){
            betAmount = betAmount * 2;
            gameActive = true;
            myChoiceLocked = false;
            opponentLockedIn = false;
            // In PvE this is the first broadcast the player's own submit
            // triggers (PLAYER_SUBMIT_BET is skipped entirely -- there's no
            // second human to show a "their turn" prompt to), so the chip
            // row needs clearing here rather than relying on that handler
            // having already done it.
            bettingEnabled = false;
            replaceBottomRow();
            updatePotChest();
            if (SoundHelper.getSoundSafely("block.enchantment_table.use", player) != null) player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);

            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1),
                createPlayerHead(chairOneOccupant.getUniqueId(), chairOneOccupant.getDisplayName()));
            if (chairTwoOccupant != null) {
                inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2),
                    createPlayerHead(chairTwoOccupant.getUniqueId(), chairTwoOccupant.getDisplayName()));
            }

            showChoiceButtonsIfSeated();
            updateStatusIndicator();
            player.updateInventory();
        }
    }

    protected Boolean handlePlayerTwoAccept(int amount) {
        double wagerAmount = amount;

        if (!hasEnoughWager(player, wagerAmount)) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("rock-paper-scissors.invalid-action"));
                    break;
                case VERBOSE:
                    player.sendMessage(text("rock-paper-scissors.insufficient-currency"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return false;
        }

        if (wagerAmount <= 0) {
            return false;
        }

        int units = org.nc.nccasino.currency.MoneyHelper.toWagerUnits(wagerAmount);
        boolean removed = units > 0 && tryRemoveCurrencyFromInventory(player, units);
        if (!removed) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                case VERBOSE:
                    player.sendMessage(text("rock-paper-scissors.insufficient-currency"));
                    break;
                case NONE:
                    break;
            }
            return false;
        }
        if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null) player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        betStack.push(wagerAmount);
        return true;

    }

    private void updateTimerUI(int seconds) {
        int clockSlot = slotMapping.get(SlotOption.HANDLE_SUBMIT_BET);
        if (seconds <= 0) {
            inventory.setItem(clockSlot, null);
            return;
        }

        ItemStack timerItem = new ItemStack(Material.CLOCK, Math.min(seconds, 64));
        ItemMeta meta = timerItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text("rock-paper-scissors.time-to-choose", "seconds", seconds));
            timerItem.setItemMeta(meta);
        }

        inventory.setItem(clockSlot, timerItem);
    }

    private void handleOpponentLockedIn() {
        opponentLockedIn = true;
        updateStatusIndicator();
    }

    /**
     * Purely mechanical reset back to fresh choice buttons for a new throw
     * within the same accepted round. The "tie" narrative itself (message,
     * sound) already played out during the TIE_REVEAL animation -- this
     * just fires once that animation's window has elapsed.
     */
    private void handleRethrow() {
        stopWinnerOrbit();
        myChoiceLocked = false;
        opponentLockedIn = false;
        populateGlassPattern();
        updatePotChest();
        showChoiceButtonsIfSeated();
        updateStatusIndicator();
        player.updateInventory();
    }

    private void handleForfeitTimeout(int winner) {
        boolean iAmSeated = (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(player.getUniqueId()))
            || (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId()));
        if (iAmSeated && !myChoiceLocked) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                case VERBOSE:
                    player.sendMessage(text("rock-paper-scissors.forfeit-no-choice"));
                    break;
                case NONE:
                    break;
            }
        }
        clearChoiceButtons();
        clearStatusIndicator();
    }

    private void handleRoundVoid() {
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
            case VERBOSE:
                player.sendMessage(text("rock-paper-scissors.round-voided"));
                break;
            case NONE:
                break;
        }
        resetAfterRound();
    }

    /**
     * Reveal slots are anchored to the chairs themselves (chair 1's throw
     * always lands next to chair 1's seat, chair 2's next to chair 2's),
     * not to "me vs. opponent" -- a viewer-relative framing would put
     * chair 2's own throw in the slot sitting next to chair 1's head for
     * whoever's actually seated in chair 2, making it look swapped.
     */
    private void handleReveal(Object[] data) {
        Throw chairOneThrow = (Throw) data[0];
        Throw chairTwoThrow = (Throw) data[1];
        int winner = (int) data[2];

        clearStatusIndicator();
        startRevealAnimation(chairOneThrow, chairTwoThrow, winner);
    }

    private void handleTieReveal(Object[] data) {
        Throw chairOneThrow = (Throw) data[0];
        Throw chairTwoThrow = (Throw) data[1];

        clearStatusIndicator();
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
            case VERBOSE:
                player.sendMessage(text("rock-paper-scissors.tie"));
                break;
            case NONE:
                break;
        }
        if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);

        startTieRevealAnimation(chairOneThrow, chairTwoThrow);
    }

    /*
     *
     *
     * UI FUNCTIONS
     *
     *
     *
     */

    private void handleGetChairs(Object data){
        Object[] dataArr = (Object[]) data;
        Player chairOne = (dataArr.length > 0 && dataArr[0] instanceof Player) ? (Player) dataArr[0] : null;
        Player chairTwo = (dataArr.length > 1 && dataArr[1] instanceof Player) ? (Player) dataArr[1] : null;
        int betAmountValue = (dataArr.length > 2 && dataArr[2] instanceof Integer) ? (int) dataArr[2] : 0;
        gameActive = (dataArr.length > 3 && dataArr[3] instanceof Boolean) ? (boolean) dataArr[3] : false;
        int timeLeft = (dataArr.length > 4 && dataArr[4] instanceof Integer) ? (int) dataArr[4] : 0;
        myChoiceLocked = (dataArr.length > 5 && dataArr[5] instanceof Boolean) ? (boolean) dataArr[5] : false;
        opponentLockedIn = (dataArr.length > 6 && dataArr[6] instanceof Boolean) ? (boolean) dataArr[6] : false;

        if(timeLeft > 0){
            updateTimerUI(timeLeft);
        }
        if (chairOne == null && chairTwo == null) {
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
            if (mode == RpsMode.PLAYER_VS_PLAYER) {
                addItemAndLore(Material.OAK_STAIRS, 1, text("rock-paper-scissors.seat-unavailable"), slotMapping.get(SlotOption.HANDLE_CHAIR_2), text("rock-paper-scissors.sit-other-chair"));
            }
        } else if (chairOne != null && chairTwo != null) {
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1),
                createPlayerHead(chairOne.getUniqueId(), chairOne.getDisplayName()));
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2),
                createPlayerHead(chairTwo.getUniqueId(), chairTwo.getDisplayName()));

            chairOneOccupant = chairOne;
            chairTwoOccupant = chairTwo;

        } else if (chairOne != null) {
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1),
                createPlayerHead(chairOne.getUniqueId(), chairOne.getDisplayName()));
            chairOneOccupant = chairOne;
            if (mode == RpsMode.PLAYER_VS_PLAYER) {
                addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_2));
            }
        } else {
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
            if (chairTwo != null)
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2),
                createPlayerHead(chairTwo.getUniqueId(), chairTwo.getDisplayName()));
        }
        if(betAmountValue!=0){
            this.betAmount = betAmountValue;
            updatePotChest();
        }
        if (gameActive) {
            showChoiceButtonsIfSeated();
            updateStatusIndicator();
        }
    }


    private void clearHandleButton(){
        inventory.setItem(slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), null);
    }

    /**
     * PLAYER_VS_DEALER only: chair 2 is never sittable, so it permanently
     * shows the house's seat instead of a "click here to sit" prompt.
     * Nothing else ever writes to this slot in that mode, so this only
     * needs to run once.
     */
    private void renderDealerSeat() {
        addItemAndLore(Material.ZOMBIE_HEAD, 1, text("rock-paper-scissors.the-dealer"), slotMapping.get(SlotOption.HANDLE_CHAIR_2));
    }

    private void showChoiceButtonsIfSeated() {
        boolean seatedInChairOne = chairOneOccupant != null
            && chairOneOccupant.getUniqueId().equals(player.getUniqueId());
        boolean seatedInChairTwo = chairTwoOccupant != null
            && chairTwoOccupant.getUniqueId().equals(player.getUniqueId());
        boolean seated = seatedInChairOne || seatedInChairTwo;
        if (!seated || myChoiceLocked) {
            return;
        }
        // Each player chooses from the column immediately inside their own
        // head. Chair 1 uses the left column; chair 2 sees the mirrored
        // right column. The opposite column remains blank until the shared
        // Rock-Paper-Scissors-SHOOT reveal begins.
        setChoiceSlotMapping(seatedInChairTwo ? CHAIR_TWO_THROW_SLOTS : CHAIR_ONE_THROW_SLOTS);
        addItemAndLore(Material.COBBLESTONE, 1, text("rock-paper-scissors.choose-rock"), slotMapping.get(SlotOption.CHOOSE_ROCK), text("rock-paper-scissors.click-choose"));
        addItemAndLore(Material.PAPER, 1, text("rock-paper-scissors.choose-paper"), slotMapping.get(SlotOption.CHOOSE_PAPER), text("rock-paper-scissors.click-choose"));
        addItemAndLore(Material.SHEARS, 1, text("rock-paper-scissors.choose-scissors"), slotMapping.get(SlotOption.CHOOSE_SCISSORS), text("rock-paper-scissors.click-choose"));
    }

    private void clearChoiceButtons() {
        clearThrowColumns();
    }

    private void setChoiceSlotMapping(int[] slots) {
        slotMapping.put(SlotOption.CHOOSE_ROCK, slots[Throw.ROCK.ordinal()]);
        slotMapping.put(SlotOption.CHOOSE_PAPER, slots[Throw.PAPER.ordinal()]);
        slotMapping.put(SlotOption.CHOOSE_SCISSORS, slots[Throw.SCISSORS.ordinal()]);
    }

    private void clearThrowColumns() {
        for (int slot : CHAIR_ONE_THROW_SLOTS) {
            addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", slot);
        }
        for (int slot : CHAIR_TWO_THROW_SLOTS) {
            addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", slot);
        }
    }

    private void updateStatusIndicator() {
        if (myChoiceLocked && opponentLockedIn) {
            addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", STATUS_SLOT);
        } else if (myChoiceLocked) {
            addItemAndLore(Material.CLOCK, 1, text("rock-paper-scissors.waiting-for-opponent"), STATUS_SLOT);
        } else if (opponentLockedIn) {
            addItemAndLore(Material.BELL, 1, text("rock-paper-scissors.opponent-locked-in"), STATUS_SLOT);
        } else {
            addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", STATUS_SLOT);
        }
    }

    private void clearStatusIndicator() {
        addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", STATUS_SLOT);
    }

    private void populateGlassPattern() {
        Material blackPane = Material.BLACK_STAINED_GLASS_PANE;
        Material limePane = Material.LIME_STAINED_GLASS_PANE;
        String paneName = "";

        int[] limeSlots = {10, 11, 12, 13, 14, 15, 16, 19, 21, 22, 23, 25, 28, 29, 30, 31, 32, 33, 34};

        int[] blackSlots = new int[]{
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            37, 38, 39, 40, 41, 42, 43,
            45, 46, 47, 48, 49, 50, 51, 52, 53
        };

        for (int slot : blackSlots) {
            addItemAndLore(blackPane, 1, paneName, slot);
        }

        for (int slot : limeSlots) {
            addItemAndLore(limePane, 1, paneName, slot);
        }
    }

    private void replaceBottomRow() {
        Material blackPane = Material.BLACK_STAINED_GLASS_PANE;
        String paneName = "";
        for(int i = 45; i < 54; i++){
            addItemAndLore(blackPane, 1, paneName, i);
        }
    }

    private void resetPlayerOneUI(){
        if(chairOneOccupant!=null){
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1),
            createPlayerHead(chairOneOccupant.getUniqueId(), chairOneOccupant.getDisplayName(), text("rock-paper-scissors.click-leave-chair")));
        }
        addItemAndLore(Material.LEVER, 1, text("rock-paper-scissors.submit-bet"), slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), text("rock-paper-scissors.click-submit-bet"));
        hidePotChest();
    }

    private void resetPlayerTwoUI(){
        if(chairTwoOccupant!=null){
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2),
            createPlayerHead(chairTwoOccupant.getUniqueId(), chairTwoOccupant.getDisplayName(), text("rock-paper-scissors.click-leave-chair")));
        }
        addItemAndLore(Material.LEVER
            , 1
            , text("rock-paper-scissors.player-turn", "player", chairOneOccupant.getDisplayName())
            , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
            , text("rock-paper-scissors.waiting-bet")
        );
        hidePotChest();
    }

    private void hidePotChest(){
        addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, 1, "", 40);
    }

    /** PvE has no shared pot to show -- the payout is just your stake doubled or lost, so the chest stays hidden. */
    private void updatePotChest(){
        if (mode == RpsMode.PLAYER_VS_DEALER) {
            hidePotChest();
            return;
        }
        addItemAndLore(Material.CHEST, 1, text("rock-paper-scissors.pot"), 40, text("rock-paper-scissors.current", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount)));
    }

    private void resetAfterRound() {
        stopWinnerOrbit();
        gameActive = false;
        myChoiceLocked = false;
        opponentLockedIn = false;
        betAmount = 0;
        betStack.clear();
        populateGlassPattern();
        if(chairOneOccupant!=null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            bettingEnabled = true;
            initializeUI(rebetEnabled, bettingEnabled, false);
            updateBetLore(53, 0);
            resetPlayerOneUI();
        }
        else if(chairTwoOccupant!=null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            resetPlayerTwoUI();
        }
    }

    private void handleAnimationFinished(){
        resetAfterRound();
    }

    private int revealTaskId = -1;
    private int winnerOrbitTaskId = -1;

    private Material materialFor(Throw t) {
        return switch (t) {
            case ROCK -> Material.COBBLESTONE;
            case PAPER -> Material.PAPER;
            case SCISSORS -> Material.SHEARS;
        };
    }

    private String throwLabel(Throw t) {
        return switch (t) {
            case ROCK -> text("rock-paper-scissors.choose-rock");
            case PAPER -> text("rock-paper-scissors.choose-paper");
            case SCISSORS -> text("rock-paper-scissors.choose-scissors");
        };
    }

    private void startRevealAnimation(Throw chairOneThrow, Throw chairTwoThrow, int winner) {
        startThrowPulse(chairOneThrow, chairTwoThrow, winner, false);
    }

    /**
     * Same cycle-then-settle animation as a decisive reveal, but with no
     * winner to highlight and nothing sent back to the server afterward --
     * the server resolves a tie on its own fixed delay (matching this
     * animation's total length) since there's no payout riding on it.
     */
    private void startTieRevealAnimation(Throw chairOneThrow, Throw chairTwoThrow) {
        startThrowPulse(chairOneThrow, chairTwoThrow, -1, true);
    }

    /**
     * Performs the familiar spoken cadence:
     * ROCK -> blank -> PAPER -> blank -> SCISSORS -> blank -> SHOOT.
     *
     * Both chair columns pulse in unison. Until SHOOT they show identical
     * public cadence items, so neither player's locked choice is leaked.
     * The final phase places each real throw in the row matching that throw.
     */
    private void startThrowPulse(Throw chairOneThrow, Throw chairTwoThrow, int winner, boolean tie) {
        if (revealTaskId != -1) return;

        BukkitRunnable pulse = new BukkitRunnable() {
            private int phase = 0;
            private int ticksUntilNextPhase = 0;

            @Override
            public void run() {
                if (inventory == null) {
                    revealTaskId = -1;
                    cancel();
                    return;
                }
                if (ticksUntilNextPhase > 0) {
                    ticksUntilNextPhase--;
                    return;
                }

                clearThrowColumns();
                switch (phase) {
                    case 0 -> {
                        showCadenceThrow(Throw.ROCK);
                        ticksUntilNextPhase = CADENCE_VISIBLE_TICKS - 1;
                    }
                    case 1, 3, 5 -> {
                        // The empty beat between each spoken word is
                        // intentionally represented by the background panes.
                        ticksUntilNextPhase = CADENCE_BLANK_TICKS - 1;
                    }
                    case 2 -> {
                        showCadenceThrow(Throw.PAPER);
                        ticksUntilNextPhase = CADENCE_VISIBLE_TICKS - 1;
                    }
                    case 4 -> {
                        showCadenceThrow(Throw.SCISSORS);
                        ticksUntilNextPhase = CADENCE_VISIBLE_TICKS - 1;
                    }
                    case 6 -> {
                        showFinalThrows(chairOneThrow, chairTwoThrow, winner, tie);
                        playShootSound();
                        if (!tie) {
                            Throw winningThrow = winner == 0 ? chairOneThrow : chairTwoThrow;
                            startWinnerOrbit(winner, winningThrow);
                        }
                        revealTaskId = -1;
                        cancel();

                        if (tie) {
                            if (SoundHelper.getSoundSafely("block.note_block.bass", player) != null)
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 1.5f, 0.8f);
                        } else {
                            if (isViewerWinner(winner)) {
                                if (SoundHelper.getSoundSafely("block.note_block.chime", player) != null)
                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 1.5f, 1.2f);
                            } else {
                                if (SoundHelper.getSoundSafely("entity.generic.explode", player) != null)
                                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 1.0f, 1.0f);
                            }
                            Bukkit.getScheduler().runTaskLater(
                                plugin,
                                () -> sendUpdateToServer("ANIMATION_FINISHED", winner),
                                RESULT_HOLD_TICKS
                            );
                        }
                        return;
                    }
                    default -> {
                        revealTaskId = -1;
                        cancel();
                        return;
                    }
                }
                phase++;
            }
        };
        pulse.runTaskTimer(plugin, 0L, 1L);
        revealTaskId = pulse.getTaskId();
    }

    private void showCadenceThrow(Throw cadenceThrow) {
        int row = cadenceThrow.ordinal();
        addItemAndLore(materialFor(cadenceThrow), 1, throwLabel(cadenceThrow), CHAIR_ONE_THROW_SLOTS[row]);
        addItemAndLore(materialFor(cadenceThrow), 1, throwLabel(cadenceThrow), CHAIR_TWO_THROW_SLOTS[row]);

        Sound sound = switch (cadenceThrow) {
            case ROCK -> Sound.BLOCK_STONE_PLACE;
            case PAPER -> Sound.ITEM_BOOK_PAGE_TURN;
            case SCISSORS -> Sound.ITEM_SHEARS_SNIP;
        };
        float pitch = switch (cadenceThrow) {
            case ROCK -> 0.8f;
            case PAPER, SCISSORS -> 1.1f;
        };
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, pitch);
    }

    private void showFinalThrows(Throw chairOneThrow, Throw chairTwoThrow, int winner, boolean tie) {
        ChatColor chairOneColor = tie ? ChatColor.YELLOW : winner == 0 ? ChatColor.GREEN : ChatColor.WHITE;
        ChatColor chairTwoColor = tie ? ChatColor.YELLOW : winner == 1 ? ChatColor.GREEN : ChatColor.WHITE;
        addItemAndLore(
            materialFor(chairOneThrow),
            1,
            throwLabel(chairOneThrow),
            chairOneColor,
            CHAIR_ONE_THROW_SLOTS[chairOneThrow.ordinal()]
        );
        addItemAndLore(
            materialFor(chairTwoThrow),
            1,
            throwLabel(chairTwoThrow),
            chairTwoColor,
            CHAIR_TWO_THROW_SLOTS[chairTwoThrow.ordinal()]
        );
    }

    private void startWinnerOrbit(int winner, Throw winningThrow) {
        stopWinnerOrbit();
        int[] orbitSlots = winner == 0 ? CHAIR_ONE_ORBIT_SLOTS : CHAIR_TWO_ORBIT_SLOTS;
        int initialSlot = (winner == 0 ? CHAIR_ONE_THROW_SLOTS : CHAIR_TWO_THROW_SLOTS)
            [winningThrow.ordinal()];
        int initialIndex = orbitIndexOf(orbitSlots, initialSlot);
        ItemStack cometItem = inventory.getItem(initialSlot);
        if (initialIndex < 0 || cometItem == null) {
            return;
        }

        boolean isWinner = isViewerWinner(winner);
        renderWinnerOrbit(orbitSlots, initialIndex, cometItem);

        int[] loserOrbitSlots = winner == 0 ? CHAIR_TWO_ORBIT_SLOTS : CHAIR_ONE_ORBIT_SLOTS;
        for (int slot : loserOrbitSlots) {
            addItemAndLore(Material.RED_STAINED_GLASS_PANE, 1, "", slot);
        }

        BukkitRunnable orbit = new BukkitRunnable() {
            private int cometIndex = initialIndex;
            private int dingStep = 0;

            @Override
            public void run() {
                if (!gameActive) {
                    stopWinnerOrbit();
                    return;
                }
                cometIndex = (cometIndex + 1) % orbitSlots.length;
                renderWinnerOrbit(orbitSlots, cometIndex, cometItem);
                if (isWinner) {
                    float pitch = WINNER_ORBIT_DING_PITCHES[dingStep];
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, pitch);
                    dingStep = (dingStep + 1) % WINNER_ORBIT_DING_PITCHES.length;
                }
            }
        };
        orbit.runTaskTimer(
            plugin,
            WINNER_ORBIT_INTERVAL_TICKS,
            WINNER_ORBIT_INTERVAL_TICKS
        );
        winnerOrbitTaskId = orbit.getTaskId();
    }

    private boolean isViewerWinner(int winner) {
        Player winningOccupant = winner == 0 ? chairOneOccupant : chairTwoOccupant;
        return winningOccupant != null && winningOccupant.getUniqueId().equals(player.getUniqueId());
    }

    private int orbitIndexOf(int[] orbitSlots, int slot) {
        for (int i = 0; i < orbitSlots.length; i++) {
            if (orbitSlots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void renderWinnerOrbit(int[] orbitSlots, int cometIndex, ItemStack cometItem) {
        for (int slot : orbitSlots) {
            addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", slot);
        }

        setOrbitPane(orbitSlots, cometIndex - 3, Material.PINK_STAINED_GLASS_PANE);
        setOrbitPane(orbitSlots, cometIndex - 2, Material.PURPLE_STAINED_GLASS_PANE);
        setOrbitPane(orbitSlots, cometIndex - 1, Material.MAGENTA_STAINED_GLASS_PANE);
        inventory.setItem(orbitSlots[cometIndex], cometItem.clone());
    }

    private void setOrbitPane(int[] orbitSlots, int index, Material material) {
        int wrappedIndex = Math.floorMod(index, orbitSlots.length);
        addItemAndLore(material, 1, "", orbitSlots[wrappedIndex]);
    }

    private void stopWinnerOrbit() {
        if (winnerOrbitTaskId != -1) {
            Bukkit.getScheduler().cancelTask(winnerOrbitTaskId);
            winnerOrbitTaskId = -1;
        }
    }

    private void playShootSound() {
        if (SoundHelper.getSoundSafely("entity.firework_rocket.blast", player) != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.MASTER, 1.4f, 1.0f);
        }
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }

}
