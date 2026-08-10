package org.nc.nccasino.games.CoinFlip;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.currency.CurrencyMode;
import org.nc.nccasino.entities.Client;
import org.nc.nccasino.entities.Server;
import org.nc.nccasino.helpers.SoundHelper;
import org.nc.nccasino.session.ExitReason;
import org.nc.nccasino.session.GameTerminationPolicy;
import org.nc.nccasino.session.TerminationAction;
import org.nc.nccasino.session.SessionRegistry;
import org.nc.nccasino.session.TerminableSession;

public class CoinFlipClient extends Client implements TerminableSession {

    private enum SlotOption
    {
        HANDLE_CHAIR_1,
        HANDLE_CHAIR_2,
        HANDLE_SUBMIT_BET,
        LEAVE,
        TOGGLE_MODE,
        CASH_OUT,
        PICK_LEFT,
        PICK_RIGHT
    }
    protected final Map<SlotOption, Integer> slotMapping = new HashMap<>();

    protected Player chairOneOccupant;
    protected Player chairTwoOccupant;

    protected int betAmount = 0;
    /** PvE chain display only -- authoritative count lives server-side. */
    private int chainWins = 0;

    private final String clickHereToSit;
    private boolean gameActive = false;
    private boolean betAccepted = false;
    private boolean sessionResolved = false;
    /** Wager already deducted locally for an in-flight PLAYER_ACCEPT_BET, refunded if the server rejects it. */
    private int pendingAcceptAmount = 0;
    /** PvE only: local mirror of the locked-in pick, null until one is made for the current flip. */
    private Integer playerPick;
    /** Seeded from the config default at construction; mutable afterward via the in-game toggle button, independent of every other viewer of this dealer. */
    private CoinFlipMode mode;
    /** Admin setting: whether the in-game toggle button renders/functions at all. Dealer-wide, re-read fresh on every new Client (see onSessionTerminated). */
    private final boolean modeSwitchingEnabled;

    public CoinFlipClient(Server server, Player player, Nccasino plugin, String internalName) {
        super(server, player, plugin.getLocalization().text(player, "coin-flip.title"), plugin, internalName);
        SessionRegistry.register(player.getUniqueId(), this);
        this.clickHereToSit = text("coin-flip.click-sit");
        this.chairOneOccupant = null;
        this.chairTwoOccupant = null;
        // Not the dealer's static config default -- a returning player who
        // previously toggled away from it needs this fresh Client (closing
        // the inventory always drops the old one, see onSessionTerminated)
        // to agree with the server's own per-player view, or the client
        // renders/behaves as one mode while the server routes their actions
        // to the other.
        this.mode = ((CoinFlipServer) server).viewFor(player.getUniqueId());
        this.modeSwitchingEnabled = plugin.getCoinFlipModeSwitchingEnabled(internalName);

        slotMapping.put(SlotOption.HANDLE_CHAIR_1, 20);
        slotMapping.put(SlotOption.HANDLE_CHAIR_2, 24);
        slotMapping.put(SlotOption.LEAVE, 36);
        slotMapping.put(SlotOption.HANDLE_SUBMIT_BET, 44);
        slotMapping.put(SlotOption.TOGGLE_MODE, 4);
        slotMapping.put(SlotOption.CASH_OUT, 40);
        slotMapping.put(SlotOption.PICK_LEFT, 21);
        slotMapping.put(SlotOption.PICK_RIGHT, 23);

        addItemAndLore(Material.SPRUCE_DOOR, 1, text("coin-flip.leave"), slotMapping.get(SlotOption.LEAVE));
        populateGlassPattern();
        if (mode == CoinFlipMode.PLAYER_VS_DEALER) {
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
        // if inventory event clicked item is sunflower then get audio safely and play acoin noise
        if (inventory.getItem(slot) != null && inventory.getItem(slot).getType() == Material.SUNFLOWER) {
            if (SoundHelper.getSoundSafely("block.note_block.chime", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.MASTER, 1.0f, 1.0f);
        }

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
            case LEAVE:
                player.closeInventory();
                break;
            case TOGGLE_MODE:
                handleToggleModeClick();
                break;
            case CASH_OUT:
                if (mode == CoinFlipMode.PLAYER_VS_DEALER && gameActive && playerPick == null) {
                    sendUpdateToServer("PLAYER_CASH_OUT", null);
                }
                break;
            case PICK_LEFT:
                handlePick(0);
                break;
            case PICK_RIGHT:
                handlePick(1);
                break;
        }
    }

    @Override
    protected void handleClientInventoryClose() {
        // Route through the same idempotent path used for quit/kick rather
        // than resolving directly here — whichever of this or the quit
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
            // Still online -- this is just a GUI close, not a real
            // disconnect. In PvE, if we're sitting at a safe checkpoint
            // (awaiting a pick, chain pot live), auto-cash-out rather than
            // letting the "let it ride" disconnect policy kick in for a
            // round the player never actually walked away from.
            if (mode == CoinFlipMode.PLAYER_VS_DEALER && gameActive && playerPick == null) {
                sendUpdateToServer("PLAYER_CASH_OUT", null);
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
        // This Client instance is being discarded -- cancel its own
        // in-flight animation/delayed-completion tasks so a stale
        // ANIMATION_FINISHED send can't arrive later for whichever match a
        // *new* Client for this player ends up resolving to (e.g. after a
        // quick reconnect that also switched modes). The server's own
        // fallback timer resolves the round independently either way.
        cancelFlipTask();

        TerminationAction action = GameTerminationPolicy.coinFlip(reason, gameActive);
        if (action == TerminationAction.FORFEIT) {
            // Remove from the server's client registry first so this
            // client can't receive (and self-refund against) the
            // seat-leave broadcast forfeitPlayer causes — kicked players
            // forfeit unconditionally, no refund.
            server.removeClient(terminatedPlayerId);
            ((CoinFlipServer) server).forfeitPlayer(terminatedPlayerId);
        } else if (action == TerminationAction.REFUND && gameActive) {
            // The flip is already accepted and in-flight, but the
            // scheduled resolution (both the animation callback and the
            // server's own fallback timer) is about to be cancelled along
            // with everything else — refund both sides' stakes instead of
            // trying to let it ride to a result that will never come.
            ((CoinFlipServer) server).refundForShutdown(terminatedPlayerId);
            server.removeClient(terminatedPlayerId);
        } else {
            if (action == TerminationAction.REFUND && !gameActive) {
                // UUID, not object identity -- a reconnect hands Bukkit a
                // new Player instance for the same person, so a stale
                // chairOneOccupant/chairTwoOccupant reference captured
                // before the reconnect would otherwise never match and
                // leave a ghost seat nobody ever vacates.
                if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())) {
                    sendUpdateToServer("PLAYER_LEAVE_ONE", null);
                }
                if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())) {
                    sendUpdateToServer("PLAYER_LEAVE_TWO", null);
                }
            }
            // gameActive: let it ride — the bet already rode into the
            // round and resolves normally by UUID at payout time
            // (delivered as a pending payout if still offline then).
            if (gameActive) {
                CoinFlipServer coinFlipServer = (CoinFlipServer) server;
                coinFlipServer.registerRidingSession(terminatedPlayerId);
                if (mode == CoinFlipMode.PLAYER_VS_DEALER) {
                    // A PvE chain has no timer at its decision points, so a
                    // plain "ride to result" can otherwise become permanent:
                    // an uncapped win reopens a match with no client
                    // attached. Cash out now if the server is already at a
                    // safe checkpoint, or immediately after the committed
                    // flip finishes (win compounds first; loss still loses).
                    coinFlipServer.requestPveExitSettlement(terminatedPlayerId);
                }
            }
            server.removeClient(terminatedPlayerId);
        }
        ((CoinFlipServer) server).cleanupIdleMatch(terminatedPlayerId);
    }

    private void handleChairOne(){
        if (chairOneOccupant == null){
            chairOneOccupant = player;
            if (SoundHelper.getSoundSafely("block.wood.place", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE,SoundCategory.MASTER, 1.0f, 1.0f);
            sendUpdateToServer("PLAYER_SIT_ONE", null);
        }
        else if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);
            sendUpdateToServer("PLAYER_LEAVE_ONE", null);
        }
        else if (chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())) {
            if (SoundHelper.getSoundSafely("entity.player.hurt", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    private void handleChairTwo(){
        if (mode == CoinFlipMode.PLAYER_VS_DEALER) {
            if (SoundHelper.getSoundSafely("entity.zombie.hurt", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_HURT, SoundCategory.MASTER, 1.0f, 1.0f);
            return;
        }
        if (chairOneOccupant != null && chairTwoOccupant == null){
            if (chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
                denyAction(player, text("coin-flip.already-seated"));
                return;
            }
            chairTwoOccupant = player;
            if (SoundHelper.getSoundSafely("block.wood.place", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE,SoundCategory.MASTER, 1.0f, 1.0f);
            sendUpdateToServer("PLAYER_SIT_TWO", null);
        }
        else if(chairTwoOccupant != null){
            if(chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
                if (SoundHelper.getSoundSafely("block.wooden_door.close", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE,SoundCategory.MASTER, 1.0f, 1.0f);
                sendUpdateToServer("PLAYER_LEAVE_TWO", null);
            }
            else if(chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
                if (SoundHelper.getSoundSafely("entity.player.hurt", player) != null)player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, SoundCategory.MASTER, 1.0f, 1.0f);

            }
        }
    }

    private void handleSubmitBet() {
        if (chairOneOccupant!=null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())) {
            if(!betStack.isEmpty()){
                int totalBet = (int) betStack.stream().mapToDouble(Double::doubleValue).sum();
                if(totalBet > 0){
                    // Set locally rather than waiting on the server's echo --
                    // PvP gets one back via the PLAYER_SUBMIT_BET broadcast
                    // (harmless to overwrite with the same value again), but
                    // PvE skips that broadcast entirely and jumps straight
                    // to PLAYER_ACCEPT_BET, which would otherwise double a
                    // still-zero betAmount.
                    betAmount = totalBet;
                    sendUpdateToServer("PLAYER_SUBMIT_BET", totalBet);
                }
            }
            else {
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD:
                        player.sendMessage(text("coin-flip.invalid-action"));
                        break;
                    case VERBOSE:
                        player.sendMessage(text("coin-flip.no-bet"));
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
                betAccepted = handlePlayerTwoAccept(amount);
                if (betAccepted){
                    pendingAcceptAmount = amount;
                    sendUpdateToServer("PLAYER_ACCEPT_BET", betAccepted);
                }
            }
        }
    }

    /**
     * Visible any time the dealer is open, seated or not -- this is each
     * viewer's own personal choice of which independent system (the shared
     * PvP table, or their own private PvE match) they're currently looking
     * at, so there's nothing to gate it on. Blocked while a round of
     * whichever match the player is currently in is active, EXCEPT at a
     * safe PvE checkpoint (awaiting a pick) -- there the request still goes
     * through, since the server will auto-cash-out before switching rather
     * than deny. The server enforces the same guard authoritatively via
     * TOGGLE_MODE_DENIED for every case this local check can't already rule out.
     */
    private void handleToggleModeClick() {
        // The button itself isn't even rendered when this is disabled, but
        // the slot can still be clicked (it's just a decorative pane) --
        // silently no-op rather than sending a request the server would
        // just deny anyway.
        if (!modeSwitchingEnabled) return;
        boolean safeToAutoCashOut = mode == CoinFlipMode.PLAYER_VS_DEALER && gameActive && playerPick == null;
        if (gameActive && !safeToAutoCashOut) {
            denyToggleMode();
            return;
        }
        sendUpdateToServer("PLAYER_TOGGLE_MODE", null);
    }

    private void denyToggleMode() {
        if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
            case VERBOSE:
                player.sendMessage(text("coin-flip.mode-switch-denied"));
                break;
            case NONE:
                break;
        }
    }

    private void handleToggleModeDenied() {
        denyToggleMode();
    }

    /**
     * The server confirmed this player's own view switched -- resets local
     * state to a clean idle slate for whichever system (shared PvP table or
     * private PvE match) they just switched into. The follow-up GET_CHAIRS
     * snapshot the server sends right after this fills in the new match's
     * actual current chairs.
     */
    private void handleModeChanged(CoinFlipMode newMode) {
        cancelFlipTask();
        this.mode = newMode;
        chairOneOccupant = null;
        chairTwoOccupant = null;
        betAmount = 0;
        betStack.clear();
        gameActive = false;
        playerPick = null;
        chainWins = 0;
        populateGlassPattern();
        replaceBottomRow();
        hidePotChest();
        addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
        if (newMode == CoinFlipMode.PLAYER_VS_DEALER) {
            renderDealerSeat();
        } else {
            addItemAndLore(Material.OAK_STAIRS, 1, text("coin-flip.seat-unavailable"), slotMapping.get(SlotOption.HANDLE_CHAIR_2), text("coin-flip.sit-other-chair"));
        }
        if (SoundHelper.getSoundSafely("ui.button.click", player) != null)
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.0f);
        player.updateInventory();
    }

    /** PvE only: locks in a left/right pick while awaiting the flip. */
    private void handlePick(int pick) {
        if (mode != CoinFlipMode.PLAYER_VS_DEALER || !gameActive || playerPick != null) return;
        boolean seatedInChairOne = chairOneOccupant != null
            && chairOneOccupant.getUniqueId().equals(player.getUniqueId());
        if (!seatedInChairOne) return;

        playerPick = pick;
        sendUpdateToServer(pick == 0 ? "PLAYER_PICK_LEFT" : "PLAYER_PICK_RIGHT", null);
        clearPickButtons();
        updatePotChest();
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
            case "PLAYER_ACCEPT_REJECTED":
                handleAcceptRejected();
                break;
            case "UPDATE_TIMER":
                updateTimerUI((int) data);
                break;
            case "WINNER":
                handleWinner((Object[]) data);
                break;
            case "CHAIN_WIN":
                handleChainWin((Object[]) data);
                break;
            case "GET_CHAIRS":
                handleGetChairs(data);
                break;
            case "ANIMATION_FINISHED":
                handleAnimationFinished();
                break;
            case "MODE_CHANGED":
                handleModeChanged((CoinFlipMode) data);
                break;
            case "TOGGLE_MODE_DENIED":
                handleToggleModeDenied();
                break;
            default:
                break;
        }
    }

    private void handlePlayerOneSit(Object data){
        Player playerData = (Player) data; // Use the PlayerData wrapper class
        if(playerData.getUniqueId().equals(player.getUniqueId())){
            initializeUI(false, true,false);
            resetPlayerOneUI();
            if (mode == CoinFlipMode.PLAYER_VS_PLAYER) {
                addItemAndLore(Material.OAK_STAIRS, 1, text("coin-flip.player-two-seat"), slotMapping.get(SlotOption.HANDLE_CHAIR_2));
            }
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1),
                createPlayerHead(playerData.getUniqueId(), playerData.getDisplayName(), text("coin-flip.click-leave-chair")));
        }
        else{
            if (mode == CoinFlipMode.PLAYER_VS_PLAYER) {
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
        Player playerData2 = (Player) data; // Use the PlayerData wrapper class
        if (playerData2.getUniqueId().equals(player.getUniqueId())){

            if (betAmount == 0){
                addItemAndLore(Material.LEVER
                , 1
                , text("coin-flip.player-turn", "player", chairOneOccupant.getDisplayName())
                , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                , text("coin-flip.waiting-bet")
            );
            }
            else{
                addItemAndLore(Material.LEVER
                    , 1
                    , text("coin-flip.accept-bet")
                    , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                    , text("coin-flip.click-accept-bet")
                    , text("coin-flip.current", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount))
                );
            }

            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2),
                createPlayerHead(playerData2.getUniqueId(), playerData2.getDisplayName(), text("coin-flip.click-leave-chair")));
        }
        else {
            if(chairOneOccupant.getUniqueId().equals(player.getUniqueId()) && !gameActive && betAmount > 0){
                addItemAndLore(
                    Material.LEVER
                    , 1
                    , text("coin-flip.player-turn", "player", playerData2.getDisplayName())
                    , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                    , text("coin-flip.click-cancel-bet")
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
        if(chairTwoOccupant == null && mode == CoinFlipMode.PLAYER_VS_PLAYER){
            addItemAndLore(Material.OAK_STAIRS, 1, text("coin-flip.seat-unavailable"), slotMapping.get(SlotOption.HANDLE_CHAIR_2), text("coin-flip.sit-other-chair"));
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
                    , text("coin-flip.player-two-turn")
                    , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                    , text("coin-flip.click-cancel-bet")
                );
            }
            addItemAndLore(Material.OAK_STAIRS, 1, text("coin-flip.player-two-seat"), slotMapping.get(SlotOption.HANDLE_CHAIR_2));
        }
        chairTwoOccupant = null;
    }

    private void handleSubmitBet(int data){
        betAmount = data;
        if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            if(chairTwoOccupant == null){
                addItemAndLore(Material.LEVER, 1
                , text("coin-flip.player-two-turn")
                , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                , text("coin-flip.click-cancel-bet")
                );
            }
            else{
                addItemAndLore(Material.LEVER, 1
                , text("coin-flip.player-turn", "player", chairTwoOccupant.getDisplayName())
                , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
                , text("coin-flip.click-cancel-bet"));

            }
            bettingEnabled = false;
            replaceBottomRow();
        }
        else if(chairTwoOccupant != null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            addItemAndLore(Material.LEVER
            , 1, text("coin-flip.accept-bet")
            , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
            , text("coin-flip.click-accept-bet")
            , text("coin-flip.current", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount))
            );
        }
        if (SoundHelper.getSoundSafely("block.enchantment_table.use", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);
        updatePotChest();

    }

    private void handleCancelBet(){
        if(chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            undoAllBets();
            updateBetLore(53, 0);
            resetPlayerOneUI();
            bettingEnabled = true;
            betAmount = 0;
            initializeUI(rebetEnabled, bettingEnabled,false);
        }
        else if(chairTwoOccupant!= null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            resetPlayerTwoUI();
        }
        hidePotChest();
    }

    private void handleAcceptBet(Boolean accepted){
        if(accepted){
            pendingAcceptAmount = 0;
            // Mirrors the server's beginActiveRound: PvP doubles the pot
            // (the opponent/house matches the stake), PvE does not -- its
            // whole payout curve is the chain multiplier itself, starting
            // from the bare wager.
            betAmount = mode == CoinFlipMode.PLAYER_VS_DEALER ? betAmount : betAmount * 2;
            chainWins = 0;
            playerPick = null;
            gameActive = true;
            // In PvE this is the first broadcast the player's own submit
            // triggers (PLAYER_SUBMIT_BET is skipped entirely -- there's no
            // second human to show a "their turn" prompt to), so the chip
            // row needs clearing here rather than relying on that handler
            // having already done it.
            bettingEnabled = false;
            replaceBottomRow();
            updatePotChest();
            renderModeToggleButton();
            if (SoundHelper.getSoundSafely("block.enchantment_table.use", player) != null)player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.MASTER, 1.0f, 1.0f);

            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_1), headForOccupant(chairOneOccupant));
            if (chairTwoOccupant != null) {
                inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2), headForOccupant(chairTwoOccupant));
            }

            showPickButtonsIfSeated();
            player.updateInventory();
        }
    }

    /**
     * The server rejected an accept that already deducted this player's
     * wager locally -- e.g. chair one left in the same window the accept
     * was in flight. Refund what was taken and drop back to an idle chair
     * two view rather than leaving the currency simply gone.
     */
    private void handleAcceptRejected() {
        if (pendingAcceptAmount > 0) {
            creditPlayer(player, pendingAcceptAmount);
            pendingAcceptAmount = 0;
        }
        if (!betStack.isEmpty()) {
            betStack.pop();
        }
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
            case VERBOSE:
                player.sendMessage(text("coin-flip.invalid-action"));
                break;
            case NONE:
                break;
        }
        // The rejection can arrive after this player was reseated into
        // chair one in the same window (e.g. chair one left and this
        // player's own chair-two occupancy got promoted) -- reset whichever
        // seat's UI they're actually sitting in now, not always chair two's.
        if (chairOneOccupant != null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())) {
            resetPlayerOneUI();
        } else {
            resetPlayerTwoUI();
        }
    }

    /** Same head every other seating path uses: your own seat gets the "click to leave" lore, anyone else's doesn't. */
    private ItemStack headForOccupant(Player occupant) {
        return occupant.getUniqueId().equals(player.getUniqueId())
            ? createPlayerHead(occupant.getUniqueId(), occupant.getDisplayName(), text("coin-flip.click-leave-chair"))
            : createPlayerHead(occupant.getUniqueId(), occupant.getDisplayName());
    }

    protected Boolean handlePlayerTwoAccept(int amount) {
        double wagerAmount = 0;
        wagerAmount = amount;

        if (!hasEnoughWager(player, wagerAmount)) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case STANDARD:
                    player.sendMessage(text("coin-flip.invalid-action"));
                    break;
                case VERBOSE:
                    player.sendMessage(text("coin-flip.insufficient-currency"));
                    break;
                case NONE:
                    break;
            }
            if (SoundHelper.getSoundSafely("entity.villager.no", player) != null)
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
            return false;
        }

        if (wagerAmount <= 0) {
            // Possibly send a message to the user "Invalid action" or "Select a wager first."
            return false;
        }

        int units = org.nc.nccasino.currency.MoneyHelper.toWagerUnits(wagerAmount);
        boolean removed = units > 0 && tryRemoveCurrencyFromInventory(player, units);
		if (!removed) {
			switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
				case STANDARD:
				case VERBOSE:
					player.sendMessage(text("coin-flip.insufficient-currency"));
					break;
				case NONE:
					break;
			}
			return false;
		}
        if (SoundHelper.getSoundSafely("item.armor.equip_chain", player) != null)player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.MASTER, 1.0f, 1.0f);
        betStack.push(wagerAmount);
        return true;

    }

    private void updateTimerUI(int seconds) {
        if (seconds <= 0) {
            inventory.setItem(44, null);
            return;
        }

        ItemStack timerItem = new ItemStack(Material.CLOCK, Math.min(seconds, 64));
        ItemMeta meta = timerItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(text("coin-flip.time-left", "seconds", seconds));
            timerItem.setItemMeta(meta);
        }

        inventory.setItem(44, timerItem);
    }

    /**
     * PvE also renders the house's ZOMBIE_HEAD into whichever of the two
     * final slots the player didn't pick, immediately before playing the
     * flip animation -- there's no second seated human to place a real
     * head for, so this stands in for "the house's call" the same way
     * RPS's dealer throw is generated synchronously with no client of its own.
     */
    private void handleWinner(Object[] data){
        int winner = (data.length > 0 && data[0] instanceof Integer) ? (int) data[0] : 0;
        pendingRoundToken = (data.length > 1 && data[1] instanceof Integer) ? (int) data[1] : -1;
        inventory.setItem(44, null);
        startFlipAnimation(winner);
    }

    /**
     * A PvE win under the chain cap: reopens the pick phase with a
     * compounded pot and a streak worth reporting.
     */
    private void handleChainWin(Object[] data) {
        chainWins = (data.length > 0 && data[0] instanceof Integer) ? (int) data[0] : chainWins;
        betAmount = (data.length > 1 && data[1] instanceof Integer) ? (int) data[1] : betAmount;

        playerPick = null;
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD:
            case VERBOSE:
                player.sendMessage(text(
                    "coin-flip.chain-win",
                    "streak", chainWins,
                    "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount)
                ));
                break;
            case NONE:
                break;
        }
        populateGlassPattern();
        updatePotChest();
        showPickButtonsIfSeated();
        player.updateInventory();
    }

    private void handleAnimationFinished(){
        gameActive = false;
        betAccepted = false;
        betAmount = 0;
        chainWins = 0;
        playerPick = null;
        betStack.clear();
        populateGlassPattern();
        if(chairOneOccupant!=null && chairOneOccupant.getUniqueId().equals(player.getUniqueId())){
            bettingEnabled = true;
            initializeUI(rebetEnabled, bettingEnabled,false);
            updateBetLore(53, 0);
            resetPlayerOneUI();
        }
        else if(chairTwoOccupant!=null && chairTwoOccupant.getUniqueId().equals(player.getUniqueId())){
            resetPlayerTwoUI();
        }
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
        boolean iHavePicked = (dataArr.length > 5 && dataArr[5] instanceof Boolean) ? (boolean) dataArr[5] : false;
        if(timeLeft > 0){
            updateTimerUI(timeLeft);
        }
        if (chairOne == null && chairTwo == null) {
            addItemAndLore(Material.OAK_STAIRS, 1, clickHereToSit, slotMapping.get(SlotOption.HANDLE_CHAIR_1));
            if (mode == CoinFlipMode.PLAYER_VS_PLAYER) {
                addItemAndLore(Material.OAK_STAIRS, 1, text("coin-flip.seat-unavailable"), slotMapping.get(SlotOption.HANDLE_CHAIR_2), text("coin-flip.sit-other-chair"));
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
            if (mode == CoinFlipMode.PLAYER_VS_PLAYER) {
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
            // The snapshot only tells us whether a pick was already made,
            // not which side -- use -1 as an "unknown side" marker rather
            // than guessing 0/1, since startFlipAnimation's house-head
            // rendering would otherwise show the wrong side on a mid-round
            // GUI reopen.
            if (iHavePicked) {
                if (playerPick == null) {
                    playerPick = -1;
                }
            } else {
                playerPick = null;
                showPickButtonsIfSeated();
            }
            updatePotChest();
        }
    }


    private void clearHandleButton(){
        inventory.setItem(slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), null);
    }

    /**
     * PLAYER_VS_DEALER only: chair 2 is never sittable, so it permanently
     * shows the house's seat instead of a "click here to sit" prompt.
     */
    private void renderDealerSeat() {
        addItemAndLore(Material.ZOMBIE_HEAD, 1, text("coin-flip.the-dealer"), slotMapping.get(SlotOption.HANDLE_CHAIR_2));
    }

    /** PvE only: shows the left/right pick buttons while a round is active and no pick has been locked in yet. */
    private void showPickButtonsIfSeated() {
        if (mode != CoinFlipMode.PLAYER_VS_DEALER || playerPick != null) return;
        boolean seatedInChairOne = chairOneOccupant != null
            && chairOneOccupant.getUniqueId().equals(player.getUniqueId());
        if (!seatedInChairOne) return;

        addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, text("coin-flip.pick-left"), slotMapping.get(SlotOption.PICK_LEFT), text("coin-flip.click-pick"));
        addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, text("coin-flip.pick-right"), slotMapping.get(SlotOption.PICK_RIGHT), text("coin-flip.click-pick"));
    }

    private void clearPickButtons() {
        addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", slotMapping.get(SlotOption.PICK_LEFT));
        addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", slotMapping.get(SlotOption.PICK_RIGHT));
    }

    private void populateGlassPattern() {
        // Define materials
        Material blackPane = Material.BLACK_STAINED_GLASS_PANE;
        Material limePane = Material.LIME_STAINED_GLASS_PANE;
        String paneName = "";

        // Define slot positions for lime stained glass panes
        int[] limeSlots = {10, 11, 12, 13, 14, 15, 16, 19, 21, 22, 23, 25, 28, 29, 30, 32, 33, 34};

        // Define slot positions for black stained glass panes
        int[] blackSlots = new int[]{
            0, 1, 2, 3, 5, 6, 7, 8,
            9, 17,
            18, 26,
            27, 35,
            37, 38, 39, 40, 41, 42, 43,
            45, 46, 47, 48, 49, 50, 51, 52, 53
        };

        // Place black stained glass panes
        for (int slot : blackSlots) {
            addItemAndLore(blackPane, 1, paneName, slot);
        }

        // Place lime stained glass panes
        for (int slot : limeSlots) {
            addItemAndLore(limePane, 1, paneName, slot);
        }

        createCoin(31);
        renderModeToggleButton();
        if (gameActive) {
            showPickButtonsIfSeated();
        }
    }

    /**
     * Compass at slot 4, top row middle column -- always visible,
     * regardless of seating, since switching modes is each viewer's own
     * personal choice. Re-rendered here (rather than only at construction)
     * because populateGlassPattern repaints this same slot as a plain
     * border pane on every reset (new round, mode switch). Hidden entirely
     * (left as the plain border pane) when the admin has disabled player
     * mode switching for this dealer.
     */
    private void renderModeToggleButton() {
        if (!modeSwitchingEnabled) {
            addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, 1, "", slotMapping.get(SlotOption.TOGGLE_MODE));
            return;
        }
        CoinFlipMode target = mode == CoinFlipMode.PLAYER_VS_PLAYER ? CoinFlipMode.PLAYER_VS_DEALER : CoinFlipMode.PLAYER_VS_PLAYER;
        String currentLabel = text(mode == CoinFlipMode.PLAYER_VS_DEALER
            ? "coin-flip-settings.mode-pvd"
            : "coin-flip-settings.mode-pvp");
        String targetLabel = text(target == CoinFlipMode.PLAYER_VS_DEALER
            ? "coin-flip-settings.mode-pvd"
            : "coin-flip-settings.mode-pvp");
        String switchLine = text("coin-flip.mode-switch", "mode", targetLabel);

        // Matches the safe-checkpoint condition handleToggleModeClick uses
        // to let the switch through instead of denying it -- switching here
        // actually cashes you out first, so say so.
        boolean safeToAutoCashOut = mode == CoinFlipMode.PLAYER_VS_DEALER && gameActive && playerPick == null;
        if (safeToAutoCashOut) {
            String cashOutLine = text(
                "coin-flip.mode-switch-cashout-notice",
                "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount)
            );
            addItemAndLore(
                Material.COMPASS,
                1,
                text("coin-flip.mode-current", "mode", currentLabel),
                slotMapping.get(SlotOption.TOGGLE_MODE),
                switchLine,
                cashOutLine
            );
        } else {
            addItemAndLore(
                Material.COMPASS,
                1,
                text("coin-flip.mode-current", "mode", currentLabel),
                slotMapping.get(SlotOption.TOGGLE_MODE),
                switchLine
            );
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
            createPlayerHead(chairOneOccupant.getUniqueId(), chairOneOccupant.getDisplayName(), text("coin-flip.click-leave-chair")));
        }
        addItemAndLore(Material.LEVER, 1, text("coin-flip.submit-bet"), slotMapping.get(SlotOption.HANDLE_SUBMIT_BET), text("coin-flip.click-submit-bet"));
        hidePotChest();
    }

    private void resetPlayerTwoUI(){
        if(chairTwoOccupant!=null){
            inventory.setItem(slotMapping.get(SlotOption.HANDLE_CHAIR_2),
            createPlayerHead(chairTwoOccupant.getUniqueId(), chairTwoOccupant.getDisplayName(), text("coin-flip.click-leave-chair")));
        }
        addItemAndLore(Material.LEVER
            , 1
            , text("coin-flip.player-turn", "player", chairOneOccupant.getDisplayName())
            , slotMapping.get(SlotOption.HANDLE_SUBMIT_BET)
            , text("coin-flip.waiting-bet")
        );
        hidePotChest();
    }

    private void hidePotChest(){
        addItemAndLore(Material.BLACK_STAINED_GLASS_PANE, 1, "", 40);
    }

    /**
     * PvE has no shared pot to show a chest for -- shows the chain cash-out
     * button in its place while awaiting a pick, or a "round started"
     * marker once a pick is locked in and cash-out is no longer available.
     */
    private void updatePotChest(){
        if (mode == CoinFlipMode.PLAYER_VS_DEALER) {
            if (gameActive && playerPick == null) {
                renderCashOutButton();
            } else if (gameActive) {
                renderRoundStartedMarker();
            } else {
                hidePotChest();
            }
            return;
        }
        addItemAndLore(Material.CHEST, 1, text("coin-flip.pot"), 40, text("coin-flip.current", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount)));
    }

    /**
     * PvE-only cash-out button, reusing PvP's dead pot-chest slot. Icon
     * matches whatever currency actually gets paid out (Emerald for Vault,
     * else the configured item currency).
     */
    private void renderCashOutButton() {
        Material icon = currencyMode == CurrencyMode.VAULT ? Material.EMERALD : plugin.getCurrency(internalName);
        addItemAndLore(
            icon,
            1,
            text("coin-flip.cash-out"),
            40,
            text("coin-flip.cash-out-lore", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount))
        );
    }

    /** Purely visual -- signals cash-out isn't available right now (a pick is locked in, flip pending), same slot the cash-out button occupies between picks. */
    private void renderRoundStartedMarker() {
        addItemAndLore(
            Material.BARRIER,
            1,
            text("coin-flip.round-started"),
            40,
            text("coin-flip.round-started-wager", "amount", plugin.formatWagerDisplay(currencyMode, currencyName, betAmount))
        );
    }

    private int flipTask = -1;
    /** The delayed ANIMATION_FINISHED send scheduled at the end of the flip animation -- tracked separately from flipTask so it can still be cancelled after the repeating animation itself has already finished/cancel()ed. */
    private int finishTask = -1;
    private final int[] flipSlots = {22, 13, 4, 13, 22}; // Flip animation slots
    private final int[] finalSlots = {21, 23}; // Final decision slots
    private Material formerMaterial;
    /** Echoed back with ANIMATION_FINISHED so the server can ignore a callback for a since-superseded flip. */
    private int pendingRoundToken = -1;

    private void createCoin(int slot){
        if(inventory.getItem(slot) != null){
            formerMaterial = inventory.getItem(slot).getType();
        }
        addItemAndLore(Material.SUNFLOWER, 1, text("coin-flip.coin"), slot);
    }

    /**
     * The flip trajectory (flipSlots) passes through slot 4, which now
     * permanently hosts the mode-toggle button -- a blind material restore
     * there would drop the button's lore/click behavior until the next
     * full repaint. Re-render the button itself instead of a bare item
     * whenever the flip passes back through its slot.
     */
    private void restoreFlipSlot(int slot, Material formerMaterialAtSlot) {
        if (slot == slotMapping.get(SlotOption.TOGGLE_MODE)) {
            renderModeToggleButton();
        } else {
            addItemAndLore(formerMaterialAtSlot, 1, "", slot);
        }
    }

    private void cancelFlipTask() {
        if (flipTask != -1) {
            Bukkit.getScheduler().cancelTask(flipTask);
            flipTask = -1;
        }
        if (finishTask != -1) {
            Bukkit.getScheduler().cancelTask(finishTask);
            finishTask = -1;
        }
    }

    private void startFlipAnimation(int winner) {
        addItemAndLore(Material.LIME_STAINED_GLASS_PANE, 1, "", 31);
        if (flipTask != -1) return; // Prevent multiple animations from running

        // PvE also shows the house's call in the slot the player didn't
        // pick, right where the flip's own final coin will land in the
        // other slot -- there's no second seated human to place a real
        // head for, so this stands in for "the house's side" the same
        // moment the flip begins.
        if (mode == CoinFlipMode.PLAYER_VS_DEALER && (playerPick != null && (playerPick == 0 || playerPick == 1))) {
            int houseSlot = finalSlots[playerPick == 0 ? 1 : 0];
            addItemAndLore(Material.ZOMBIE_HEAD, 1, text("coin-flip.the-dealer"), houseSlot);
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            int index = 0;
            int lastSlot = -1;

            @Override
            public void run() {
                if (inventory == null) return;
                if (index < flipSlots.length) {
                    if (SoundHelper.getSoundSafely("ui.toast.in", player) != null)
                        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 3f, 1.0f);
                    if (SoundHelper.getSoundSafely("ui.toast.out", player) != null)
                        player.playSound(player.getLocation(), Sound.UI_TOAST_OUT, 3f, 1.0f);
                    int slot = flipSlots[index];

                    // Restore former material at the last slot
                    if (lastSlot != -1 && formerMaterial != null) {
                        restoreFlipSlot(lastSlot, formerMaterial);
                    }

                    // Save current material before placing the coin
                    if (inventory.getItem(slot) != null) {
                        formerMaterial = inventory.getItem(slot).getType();
                    }

                    createCoin(slot);
                    lastSlot = slot;
                    index++;
                } else {
                    // Restore final slot before placing the winning coin
                    if (lastSlot != -1 && formerMaterial != null) {
                        restoreFlipSlot(lastSlot, formerMaterial);
                    }

                    // Place the final coin in the winner's slot
                    int finalSlot = finalSlots[winner]; // 0 -> 21, 1 -> 23
                    createCoin(finalSlot);

                    flipTask = -1; // Reset task ID
                    cancel();
                    if (SoundHelper.getSoundSafely("block.note_block.chime", player) != null)
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 3f, 1.0f);

                    // Captured now, not read from the field inside the
                    // lambda -- a WINNER for a newer flip can legitimately
                    // overwrite pendingRoundToken before this delayed send
                    // fires, which would otherwise echo the wrong flip's
                    // token back.
                    int tokenAtSchedule = pendingRoundToken;
                    BukkitRunnable finishRunnable = new BukkitRunnable() {
                        @Override
                        public void run() {
                            finishTask = -1;
                            sendUpdateToServer("ANIMATION_FINISHED", new Object[]{winner, tokenAtSchedule});
                        }
                    };
                    finishRunnable.runTaskLater(plugin, 30L);
                    finishTask = finishRunnable.getTaskId();

                }
            }
        };
        runnable.runTaskTimer(plugin, 0L, 5L); // Runs every 2 ticks
        flipTask = runnable.getTaskId();
    }

    private String text(String key, Object... placeholders) {
        return plugin.getLocalization().text(player, key, placeholders);
    }

}
