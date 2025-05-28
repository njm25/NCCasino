package org.nc.nccasino.components;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.entity.Cat.Type;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.JockeyNode;
import org.nc.nccasino.entities.JockeyManager;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JockeyOptionsMenu extends Menu {
    private final JockeyNode jockey;
    private final Nccasino plugin;
    private final String returnName;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 45;
    private final JockeyManager jockeyManager;
    
    private static final Map<Material, EntityType> spawnEggToEntity = new HashMap<>();
    private static final Map<EntityType, Material> entityToSpawnEgg = new HashMap<>();
    private static final List<Material> spawnEggList = new ArrayList<>();

    private static final List<Villager.Type> VILLAGER_BIOMES = Arrays.asList(
        Villager.Type.DESERT,
        Villager.Type.JUNGLE,
        Villager.Type.PLAINS,
        Villager.Type.SAVANNA,
        Villager.Type.SNOW,
        Villager.Type.SWAMP,
        Villager.Type.TAIGA
    );
    
    private static final Map<Villager.Type, Material> BIOME_MATERIALS = new HashMap<>() {{
        put(Villager.Type.DESERT, Material.CACTUS);
        put(Villager.Type.JUNGLE, Material.JUNGLE_LOG);
        put(Villager.Type.PLAINS, Material.OAK_LOG);
        put(Villager.Type.SAVANNA, Material.ACACIA_LOG);
        put(Villager.Type.SNOW, Material.PALE_OAK_LOG);
        put(Villager.Type.SWAMP, Material.MANGROVE_LOG);
        put(Villager.Type.TAIGA, Material.SPRUCE_LOG);
    }};

    private static final Map<DyeColor, Material> DYE_MATERIAL_MAP = new HashMap<>() {{
        put(DyeColor.WHITE, Material.WHITE_DYE);
        put(DyeColor.ORANGE, Material.ORANGE_DYE);
        put(DyeColor.MAGENTA, Material.MAGENTA_DYE);
        put(DyeColor.LIGHT_BLUE, Material.LIGHT_BLUE_DYE);
        put(DyeColor.YELLOW, Material.YELLOW_DYE);
        put(DyeColor.LIME, Material.LIME_DYE);
        put(DyeColor.PINK, Material.PINK_DYE);
        put(DyeColor.GRAY, Material.GRAY_DYE);
        put(DyeColor.LIGHT_GRAY, Material.LIGHT_GRAY_DYE);
        put(DyeColor.CYAN, Material.CYAN_DYE);
        put(DyeColor.PURPLE, Material.PURPLE_DYE);
        put(DyeColor.BLUE, Material.BLUE_DYE);
        put(DyeColor.BROWN, Material.BROWN_DYE);
        put(DyeColor.GREEN, Material.GREEN_DYE);
        put(DyeColor.RED, Material.RED_DYE);
        put(DyeColor.BLACK, Material.BLACK_DYE);
    }};

    private static final Map<Class<? extends Mob>, Material[]> VARIANT_ITEMS = new HashMap<>() {{
        put(Cat.class, new Material[]{
            Material.BROWN_GLAZED_TERRACOTTA, Material.PINK_GLAZED_TERRACOTTA, Material.ORANGE_GLAZED_TERRACOTTA, Material.LIGHT_GRAY_GLAZED_TERRACOTTA, 
            Material.GRAY_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA, Material.YELLOW_GLAZED_TERRACOTTA, Material.LIGHT_BLUE_GLAZED_TERRACOTTA,
            Material.WHITE_GLAZED_TERRACOTTA, Material.PURPLE_GLAZED_TERRACOTTA, Material.BLACK_GLAZED_TERRACOTTA
        }); // 11 variants
    
        put(Fox.class, new Material[]{
            Material.ORANGE_DYE, Material.WHITE_DYE
        }); // 2 variants
    
        put(Frog.class, new Material[]{
            Material.ORANGE_DYE, Material.GRAY_DYE, Material.GREEN_DYE
        }); // 3 variants
    
        put(Parrot.class, new Material[]{
            Material.RED_DYE, Material.BLUE_DYE, Material.GREEN_DYE, Material.CYAN_DYE, Material.GRAY_DYE
        }); // 5 variants
    
        put(Rabbit.class, new Material[]{
            Material.BROWN_DYE, Material.WHITE_DYE, Material.BLACK_DYE, Material.BIRCH_LOG, 
            Material.YELLOW_DYE, Material.FROGSPAWN, Material.BONE
        }); // 6 variants
    
        put(Axolotl.class, new Material[]{
            Material.PINK_DYE, Material.BROWN_DYE, Material.YELLOW_DYE, Material.CYAN_DYE, Material.BLUE_DYE
        }); // 5 variants
    
        put(MushroomCow.class, new Material[]{
            Material.RED_MUSHROOM, Material.BROWN_MUSHROOM
        }); // 2 variants
    
        put(Panda.class, new Material[]{
            Material.BLACK_WOOL, Material.BROWN_WOOL
        }); // 2 variants
    
        put(Sheep.class, new Material[]{
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL
        }); // 16 colors
    }};

    private static final Cat.Type[] CAT_TYPES = {
        Cat.Type.TABBY,
        Cat.Type.BLACK,
        Cat.Type.RED,
        Cat.Type.SIAMESE,
        Cat.Type.BRITISH_SHORTHAIR,
        Cat.Type.CALICO,
        Cat.Type.PERSIAN,
        Cat.Type.RAGDOLL,
        Cat.Type.WHITE,
        Cat.Type.JELLIE,
        Cat.Type.ALL_BLACK
    };

    private static final Frog.Variant[] FROG_VARIANTS = {
        Frog.Variant.TEMPERATE,
        Frog.Variant.WARM,
        Frog.Variant.COLD
    };  

    private static final List<DyeColor> SHEEP_COLOR_ORDER = List.of(
        DyeColor.WHITE, // Default
        // Smooth ROYGBIV with blending colors
        DyeColor.RED, DyeColor.PINK, DyeColor.ORANGE, 
        DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN, 
        DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.BLUE, 
        DyeColor.PURPLE, DyeColor.MAGENTA,
        // Remaining colors
        DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.BROWN, DyeColor.BLACK
    );

    static {
        // Initialize spawn egg mappings
        for (Material material : Material.values()) {
            if (material.name().endsWith("_SPAWN_EGG")) {
                String entityName = material.name().replace("_SPAWN_EGG", "");
                try {
                    EntityType entityType = EntityType.valueOf(entityName);
                    if (entityType.getEntityClass() != null && Mob.class.isAssignableFrom(entityType.getEntityClass())) {
                        spawnEggToEntity.put(material, entityType);
                        entityToSpawnEgg.put(entityType, material);
                        spawnEggList.add(material);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public JockeyOptionsMenu(Player player, Nccasino plugin, JockeyNode jockey, String returnName, Consumer<Player> returnCallback) {
        super(player, plugin, jockey.getId(), "Jockey Options", 54, returnName, (p) -> {
            // Schedule the return with a delay to ensure proper updates
            new BukkitRunnable() {
                @Override
                public void run() {
                    // First update the mobSettingsMenu if it exists
                    MobSettingsMenu temp = MobSettingsMenu.jockeyInventories.get(p.getUniqueId());
                    if (temp != null) {
                        // Create a fresh JockeyManager to ensure we have the current state
                        temp.refreshJockeyManager();
                        temp.initializeMenu();
                        p.openInventory(temp.getInventory());
                    } else if (returnCallback != null) {
                        returnCallback.accept(p);
                    }
                }
            }.runTaskLater(plugin, 2L); // 2 tick delay (0.1 seconds)
        });
        this.jockey = jockey;
        this.plugin = plugin;
        this.returnName = returnName;

        // Get the root jockey manager from the dealer
        JockeyNode root = jockey;
        while (root.getParent() != null && root.getParent().getParent() != null) {
            root = root.getParent();
        }
        Mob dealerMob = root.getParent() == null ? root.getMob() : root.getParent().getMob();
        this.jockeyManager = new JockeyManager(dealerMob);

        slotMapping.put(SlotOption.EXIT, 53);
        slotMapping.put(SlotOption.RETURN, 45);
        slotMapping.put(SlotOption.PAGE_TOGGLE, 50);
        
        if (isAgeable(jockey.getMob()) || jockey.getMob() instanceof Slime || jockey.getMob() instanceof MagmaCube) {
            slotMapping.put(SlotOption.AGE_TOGGLE, 52);
        }
        
        if (isComplicatedVariant(jockey.getMob()) || hasSingleVariant(jockey.getMob())) {
            slotMapping.put(SlotOption.VARIANT, 46);
        }

        // Add delete button
        slotMapping.put(SlotOption.DELETE, 48);

        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        inventory.clear();

        // Populate spawn eggs for mob selection
        int startIndex = (currentPage - 1) * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && startIndex + i < spawnEggList.size(); i++) {
            Material material = spawnEggList.get(startIndex + i);
            EntityType entityType = spawnEggToEntity.get(material);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + formatEntityName(entityType.name()));
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }

        // Add navigation and control buttons
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));

        // Add delete button
        addItemAndLore(Material.BARRIER, 1, "Delete Jockey", slotMapping.get(SlotOption.DELETE), "Click to delete this jockey");

        // Page toggle button
        if (currentPage > 1) {
            addItemAndLore(Material.ARROW, 1, "Previous Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        } else if ((startIndex + PAGE_SIZE) < spawnEggList.size()) {
            addItemAndLore(Material.ARROW, 1, "Next Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        }

        // Add age toggle if applicable
        if (isAgeable(jockey.getMob()) || jockey.getMob() instanceof Slime || jockey.getMob() instanceof MagmaCube) {
            updateAgeButton();
        }

        // Add variant button if applicable
        if (isComplicatedVariant(jockey.getMob()) || hasSingleVariant(jockey.getMob())) {
            slotMapping.put(SlotOption.VARIANT, 46);
            updateVariantButton();
        }
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        // First check if it's a mapped slot option
        SlotOption option = getKeyByValue(slotMapping, slot);
        if (option != null) {
        switch (option) {
                case PAGE_TOGGLE:
                    if (currentPage > 1) {
                        currentPage--;
                    } else if (currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
                        currentPage++;
                    }
                    initializeMenu();
                    playDefaultSound(player);
                    return;
                case EXIT:
                    player.closeInventory();
                    return;
                case RETURN:
                    executeReturn(player);
                    return;
                case AGE_TOGGLE:
                    handleAgeToggle(player);
                    return;
                case VARIANT:
                    handleVariantClick(player);
                    return;
                case DELETE:
                    handleDelete(player);
                    return;
                default:
                
        }
            return;
        }

        // If not a mapped slot, check if it's a spawn egg slot
        if (slot >= 0 && slot < 45 && event.getCurrentItem() != null) {
            Material clickedMaterial = event.getCurrentItem().getType();
            EntityType selectedType = spawnEggToEntity.get(clickedMaterial);
            if (selectedType != null) {
                // Calculate the actual index based on the current page
                int actualIndex = (currentPage - 1) * PAGE_SIZE + slot;
                if (actualIndex >= spawnEggList.size()) return;
                
                // Store the original location and rotation
                Location originalLoc = jockey.getMob().getLocation().clone();
                float originalYaw = jockey.getMob().getLocation().getYaw();
                
                // Find the bottom-most mob in the stack
                Mob bottomMob = jockey.getMob();
                while (bottomMob.getVehicle() instanceof Mob) {
                    bottomMob = (Mob) bottomMob.getVehicle();
                }
                
                // Store all mobs in the stack from bottom to top
                List<Mob> stackMobs = new ArrayList<>();
                Mob current = bottomMob;
                while (current != null) {
                    stackMobs.add(current);
                    if (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
                        current = (Mob) current.getPassengers().get(0);
                    } else {
                        current = null;
                    }
                }
                
                // Store armor stand info if present
                ArmorStand armorStand = null;
                String armorStandName = null;
                for (Entity passenger : jockeyManager.getDealer().getPassengers()) {
                    if (passenger instanceof ArmorStand) {
                        armorStand = (ArmorStand) passenger;
                        armorStandName = armorStand.getCustomName();
                        break;
                    }
                }
                
                // Find the index of our jockey in the stack
                int jockeyIndex = -1;
                for (int i = 0; i < stackMobs.size(); i++) {
                    if (stackMobs.get(i).equals(jockey.getMob())) {
                        jockeyIndex = i;
                        break;
                    }
                }
                
                if (jockeyIndex == -1) {
                    player.sendMessage("§cFailed to find jockey in stack");
                    return;
                }
                
                // Temporarily unmount everything
                for (Mob mob : stackMobs) {
                    if (mob.getVehicle() != null) {
                        mob.getVehicle().removePassenger(mob);
                    }
                    for (Entity passenger : new ArrayList<>(mob.getPassengers())) {
                        mob.removePassenger(passenger);
                    }
                }
                
                // If there was an armor stand, remove it
                if (armorStand != null) {
                    armorStand.remove();
                }
                
                // Spawn new mob at the exact original location
                Mob newMob = (Mob) jockey.getMob().getWorld().spawnEntity(originalLoc, selectedType);
                newMob.setAI(false);
                newMob.setPersistent(true);
                newMob.setRemoveWhenFarAway(false);
                newMob.setGravity(false);
                newMob.setSilent(true);
                newMob.setCollidable(false);
                newMob.setInvulnerable(true);
                newMob.teleport(originalLoc);
                newMob.setRotation(originalYaw, 0);
                
                // Get the dealer's name
                String dealerName = jockeyManager.getDealer().getCustomName();
                if (dealerName == null || dealerName.isEmpty()) {
                    dealerName = "Dealer";
                }
                
                // Update the jockey with the new mob
                if (jockeyManager.changeMobType(jockey.getPosition(), newMob)) {
                    // Set the dealer's name
                    newMob.setCustomName(dealerName);
                    newMob.setCustomNameVisible(false);
                    
                    // Replace the old mob in our stack list with the new one
                    stackMobs.set(jockeyIndex, newMob);
                    
                    // Remount everything in order from bottom to top
                    for (int i = 0; i < stackMobs.size() - 1; i++) {
                        Mob currentMob = stackMobs.get(i);
                        Mob nextMob = stackMobs.get(i + 1);
                        currentMob.addPassenger(nextMob);
                        currentMob.setCustomNameVisible(false);
                    }
                    
                    // Check if we need to respawn the armor stand
                    boolean hasVehicles = jockeyManager.getDealer().getVehicle() != null;
                    boolean hasPassengers = false;
                    for (Entity passenger : jockeyManager.getDealer().getPassengers()) {
                        if (passenger instanceof Mob) {
                            hasPassengers = true;
                            break;
                        }
                    }
                    
                    if (hasVehicles && !hasPassengers) {
                        // Spawn new armor stand at dealer location
                        ArmorStand newArmorStand = (ArmorStand) jockeyManager.getDealer().getWorld().spawnEntity(originalLoc, EntityType.ARMOR_STAND);
                        newArmorStand.setVisible(false);
                        newArmorStand.setGravity(false);
                        newArmorStand.setSmall(true);
                        newArmorStand.setMarker(true);
                        newArmorStand.setCustomName(armorStandName != null ? armorStandName : dealerName);
                        newArmorStand.setCustomNameVisible(true);
                        
                        // Add armor stand as passenger to dealer
                        jockeyManager.getDealer().addPassenger(newArmorStand);
                        jockeyManager.getDealer().setCustomNameVisible(false);
                    }
                    
                    // Refresh the jockey manager to ensure all relationships are correct
                    jockeyManager.refresh();
                    
                    player.sendMessage("§aChanged jockey to " + formatEntityName(selectedType.name()));
                    playDefaultSound(player);
                    
                    // Return to mobSettingsMenu after successful mob change
                    executeReturn(player);
                } else {
                    player.sendMessage("§cFailed to change jockey");
                    newMob.remove();
                    
                    // If we failed, remount everything in original order
                    for (int i = 0; i < stackMobs.size() - 1; i++) {
                        stackMobs.get(i).addPassenger(stackMobs.get(i + 1));
                    }
                    
                    // Restore armor stand if it existed
                    if (armorStand != null) {
                        ArmorStand newArmorStand = (ArmorStand) jockeyManager.getDealer().getWorld().spawnEntity(originalLoc, EntityType.ARMOR_STAND);
                        newArmorStand.setVisible(false);
                        newArmorStand.setGravity(false);
                        newArmorStand.setSmall(true);
                        newArmorStand.setMarker(true);
                        newArmorStand.setCustomName(armorStandName);
                        newArmorStand.setCustomNameVisible(true);
                        jockeyManager.getDealer().addPassenger(newArmorStand);
                    }
                }
            }
        }
    }

    private void handleVariantClick(Player player) {
        Mob mob = jockey.getMob();
        if (isComplicatedVariant(mob)) {
            openVariantMenu(player, mob);
        } else if (hasSingleVariant(mob)) {
            cycleSingleVariant(player, mob);
        }
        initializeMenu();
    }

    private void openVariantMenu(Player player, Mob mob) {
        ComplexVariantMenu cvm = new ComplexVariantMenu(
            player,
            plugin,
            jockey.getId(),
            "Return to Options",
            (p) -> p.openInventory(this.getInventory()),
            mob
        );
        player.openInventory(cvm.getInventory());
    }

    private void handleAgeToggle(Player player) {
        Mob mob = jockey.getMob();
        if (mob instanceof Ageable ageable) {
            if (ageable.isAdult()) {
                ageable.setBaby();
            } else {
                ageable.setAdult();
            }
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§a" + formatEntityName(mob.getType().toString()) + " age set to " + ChatColor.YELLOW + (ageable.isAdult() ? "adult" : "baby") + "§a.");
                default -> {}
            }
        } else if (mob instanceof Slime slime) {
            int currentSize = slime.getSize();
            int newSize = currentSize == 4 ? 1 : currentSize + 1;
            slime.setSize(newSize);
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§a" + formatEntityName(slime.getType().toString()) + " size set to " + ChatColor.YELLOW + newSize + "§a.");
                default -> {}
            }
        }
        updateAgeButton();
    }

    private void updateVariantButton() {
        Mob mob = jockey.getMob();
        int slot = slotMapping.get(SlotOption.VARIANT);
    
        if (mob instanceof Villager villager) {
            Villager.Type vt = villager.getVillagerType();
            Material mat = BIOME_MATERIALS.getOrDefault(vt, Material.GRASS_BLOCK);
            addItemAndLore(
                mat, 
                1,
                "Edit " + formatEntityName(mob.getType().toString()) + " Variant",
                slot,
                "Current: §a" + formatEntityName(vt.toString())
            );
            return;
        }
        else if (mob instanceof ZombieVillager villager) {
            Villager.Type vt = villager.getVillagerType();
            Material mat = BIOME_MATERIALS.getOrDefault(vt, Material.GRASS_BLOCK);
            addItemAndLore(
                mat, 
                1,
                "Edit " + formatEntityName(mob.getType().toString()) + " Variant",
                slot,
                "Current: §a" + formatEntityName(vt.toString())
            );
            return;
        }
        else if (isComplicatedVariant(mob)) {
            List<String> details = getComplexVariantDetails(mob);
            addItemAndLore(
                Material.WRITABLE_BOOK,
                1,
                "Open "+ formatEntityName(mob.getType().toString())+" Variant Menu",
                slot,
                details.toArray(new String[0])
            );
            return;
        }
        else if (mob instanceof Wolf wolf) {
            if (!wolf.isTamed()) {
                addItemAndLore(
                    Material.BARRIER,
                    1,
                    "Edit " + formatEntityName(mob.getType().toString()) + " Collar Color",
                    slot,
                    "Current: §aNone"
                );
                return;
            }
        
            DyeColor currentColor = wolf.getCollarColor();
            Material dyeMaterial = DYE_MATERIAL_MAP.getOrDefault(currentColor, Material.GRAY_DYE);
            
            addItemAndLore(
                dyeMaterial,
                1,
                "Edit " + formatEntityName(mob.getType().toString()) + " Collar Color",
                slot,
                "Current: §a" + formatEntityName(currentColor.toString())
            );
            return;
        }
        else if (hasSingleVariant(mob)) {
            Material variantItem = getVariantItem(mob);
            addItemAndLore(
                variantItem,
                1,
                "Edit " + formatEntityName(mob.getType().toString()) + " Variant",
                slot,
                "Current: §a" + getVariantName(mob)
            );
            return;
        }
    }

    private void updateAgeButton() {
        Mob mob = jockey.getMob();
        if (mob == null) return;

        int slot = slotMapping.get(SlotOption.AGE_TOGGLE);
        String currentState = getCurrentSizeOrAge(mob);

        if (isAgeable(mob)) {
            addItemAndLore(
                Material.CLOCK,
                1,
                "Toggle Age",
                slot,
                currentState
            );
        } else if (mob instanceof Slime || mob instanceof MagmaCube) {
            addItemAndLore(
                Material.SLIME_BALL,
                1,
                "Change Size",
                slot,
                currentState
            );
        }
    }

    private boolean isComplicatedVariant(Mob mob) {
        return (mob instanceof Llama)
            || (mob instanceof Horse)
            || (mob instanceof TropicalFish);
    }

    private boolean hasSingleVariant(Mob mob) {
        return (mob instanceof Cat)
            || (mob instanceof Fox)
            || (mob instanceof Frog)
            || (mob instanceof Parrot)
            || (mob instanceof Rabbit)
            || (mob instanceof Axolotl)
            || (mob instanceof MushroomCow)
            || (mob instanceof Panda)
            || (mob instanceof Villager)
            || (mob instanceof ZombieVillager)
            || (mob instanceof Sheep)
            || (mob instanceof Wolf);
    }

    private boolean isAgeable(Mob mob) {
        return (mob instanceof Ageable)
            && !(mob instanceof Parrot)
            && !(mob instanceof Frog)
            && !(mob instanceof PiglinBrute)
            && !(mob instanceof WanderingTrader);
    }

    private String getCurrentSizeOrAge(Mob mob) {
        if (mob instanceof Ageable ageable) {
            return "Current: §a" + (ageable.isAdult() ? "Adult" : "Baby");
        } else if (mob instanceof Slime slime) {
            return "Current Size: §a" + slime.getSize();
                }
        return "";
    }

    private Material getVariantItem(Mob mob) {
        if (mob instanceof Cat cat) {
            Material[] items = VARIANT_ITEMS.get(Cat.class);
            return items[indexOf(CAT_TYPES, cat.getCatType())];
        }
        if (mob instanceof Fox fox) {
            Material[] items = VARIANT_ITEMS.get(Fox.class);
            return items[fox.getFoxType().ordinal()];
        }
        if (mob instanceof Frog frog) {
            Material[] items = VARIANT_ITEMS.get(Frog.class);
            return items[indexOf(FROG_VARIANTS, frog.getVariant())];
        }
        if (mob instanceof Parrot parrot) {
            Material[] items = VARIANT_ITEMS.get(Parrot.class);
            return items[parrot.getVariant().ordinal()];
        }
        if (mob instanceof Rabbit rabbit) {
            Material[] items = VARIANT_ITEMS.get(Rabbit.class);
            return items[rabbit.getRabbitType().ordinal()];
        }
        if (mob instanceof Axolotl axolotl) {
            Material[] items = VARIANT_ITEMS.get(Axolotl.class);
            return items[axolotl.getVariant().ordinal()];
        }
        if (mob instanceof MushroomCow mooshroom) {
            Material[] items = VARIANT_ITEMS.get(MushroomCow.class);
            return items[mooshroom.getVariant().ordinal()];
        }
        if (mob instanceof Panda panda) {
            Material[] items = VARIANT_ITEMS.get(Panda.class);
            return items[panda.getMainGene() == Panda.Gene.NORMAL ? 0 : 1];
        }
        if (mob instanceof Sheep sheep) {
            Material[] items = VARIANT_ITEMS.get(Sheep.class);
            return items[SHEEP_COLOR_ORDER.indexOf(sheep.getColor())];
        }
        if (mob instanceof Villager || mob instanceof ZombieVillager) return Material.EMERALD;
        if (mob instanceof Wolf) return Material.BONE;
        return Material.NAME_TAG;
    }

    private String getVariantName(Mob mob) {
        if (mob instanceof Fox fox) return formatEntityName(fox.getFoxType().toString());
        if (mob instanceof Frog frog) return formatEntityName(frog.getVariant().toString());
        if (mob instanceof Cat cat) return formatEntityName(cat.getCatType().toString());
        if (mob instanceof Parrot parrot) return formatEntityName(parrot.getVariant().toString());
        if (mob instanceof Rabbit rabbit) return formatEntityName(rabbit.getRabbitType().toString());
        if (mob instanceof Axolotl axolotl) return formatEntityName(axolotl.getVariant().toString());
        if (mob instanceof MushroomCow mooshroom) return formatEntityName(mooshroom.getVariant().toString());
        if (mob instanceof Panda panda) return formatEntityName(panda.getMainGene().toString());
        if (mob instanceof Villager villager) return formatEntityName(villager.getVillagerType().toString());
        if (mob instanceof ZombieVillager zombie) return formatEntityName(zombie.getVillagerType().toString());
        if (mob instanceof Sheep sheep) return formatEntityName(sheep.getColor().toString());
        if (mob instanceof Wolf wolf) return formatEntityName(wolf.getCollarColor().toString());
        return "Unknown";
    }

    private void cycleSingleVariant(Player player, Mob mob) {
        if (mob instanceof Cat cat) {
            int currentIndex = indexOf(CAT_TYPES, cat.getCatType());
            Type newType = CAT_TYPES[(currentIndex + 1) % CAT_TYPES.length];
            cat.setCatType(newType);
            sendVariantUpdateMessage(player, newType, mob);
        } else if (mob instanceof Fox fox) {
            Fox.Type newType = fox.getFoxType() == Fox.Type.RED ? Fox.Type.SNOW : Fox.Type.RED;
            fox.setFoxType(newType);
            sendVariantUpdateMessage(player, newType, mob);
        } else if (mob instanceof Frog frog) {
            int currentIndex = indexOf(FROG_VARIANTS, frog.getVariant());
            Frog.Variant newVariant = FROG_VARIANTS[(currentIndex + 1) % FROG_VARIANTS.length];
            frog.setVariant(newVariant);
            sendVariantUpdateMessage(player, newVariant, mob);
        } else if (mob instanceof Parrot parrot) {
            Parrot.Variant newVariant = Parrot.Variant.values()[(parrot.getVariant().ordinal() + 1) % Parrot.Variant.values().length];
            parrot.setVariant(newVariant);
            sendVariantUpdateMessage(player, newVariant, mob);
        } else if (mob instanceof Rabbit rabbit) {
            Rabbit.Type newType = Rabbit.Type.values()[(rabbit.getRabbitType().ordinal() + 1) % Rabbit.Type.values().length];
            rabbit.setRabbitType(newType);
            sendVariantUpdateMessage(player, newType, mob);
        } else if (mob instanceof Axolotl axolotl) {
            Axolotl.Variant newVariant = Axolotl.Variant.values()[(axolotl.getVariant().ordinal() + 1) % Axolotl.Variant.values().length];
            axolotl.setVariant(newVariant);
            sendVariantUpdateMessage(player, newVariant, mob);
        } else if (mob instanceof MushroomCow mushroomCow) {
            MushroomCow.Variant newVariant = mushroomCow.getVariant() == MushroomCow.Variant.RED ? MushroomCow.Variant.BROWN : MushroomCow.Variant.RED;
            mushroomCow.setVariant(newVariant);
            sendVariantUpdateMessage(player, newVariant, mob);
        } else if (mob instanceof Panda panda) {
            Panda.Gene newGene = panda.getMainGene() == Panda.Gene.NORMAL ? Panda.Gene.BROWN : Panda.Gene.NORMAL;
            panda.setMainGene(newGene);
            panda.setHiddenGene(newGene);
            sendVariantUpdateMessage(player, newGene, mob);
        } else if (mob instanceof Villager villager) {
            Villager.Type currentBiome = villager.getVillagerType();
            int index = VILLAGER_BIOMES.indexOf(currentBiome);
            Villager.Type newBiome = VILLAGER_BIOMES.get((index + 1) % VILLAGER_BIOMES.size());
            villager.setVillagerType(newBiome);
            sendVariantUpdateMessage(player, newBiome, mob);
        } else if (mob instanceof ZombieVillager zombieVillager) {
            Villager.Type currentBiome = zombieVillager.getVillagerType();
            int index = VILLAGER_BIOMES.indexOf(currentBiome);
            Villager.Type newBiome = VILLAGER_BIOMES.get((index + 1) % VILLAGER_BIOMES.size());
            zombieVillager.setVillagerType(newBiome);
            sendVariantUpdateMessage(player, newBiome, mob);
        } else if (mob instanceof Sheep sheep) {
            int currentIndex = SHEEP_COLOR_ORDER.indexOf(sheep.getColor());
            DyeColor newColor = SHEEP_COLOR_ORDER.get((currentIndex + 1) % SHEEP_COLOR_ORDER.size());
            sheep.setColor(newColor);
            sendVariantUpdateMessage(player, newColor, mob);
        } else if (mob instanceof Wolf wolf) {
            List<DyeColor> colors = List.of(DyeColor.values());
            
            if (!wolf.isTamed()) {
                // If untamed, tame and set the first collar color
                wolf.setTamed(true);
                wolf.setCollarColor(colors.get(0));
                sendVariantUpdateMessage(player, colors.get(0), mob);
            } else {
                // If tamed, cycle through the colors, untame if removing the collar
                int index = colors.indexOf(wolf.getCollarColor());
                if (index == colors.size() - 1) {
                    wolf.setTamed(false); // If cycling past last color, untame the wolf
                    sendVariantUpdateMessage(player, "Untamed", mob);
                } else {
                    DyeColor nextColor = colors.get((index + 1) % colors.size());
                    wolf.setCollarColor(nextColor);
                    sendVariantUpdateMessage(player, nextColor, mob);
                }
            }
        }
        
        initializeMenu();
    }

    private void sendVariantUpdateMessage(Player player, Object variantObj, Mob mob) {
        String variantName;
        if (variantObj instanceof Enum<?> e) {
            // True Enum, so we can do .name()
            variantName = e.name();
        } else {
            // Might be CraftCat$CraftType, so fallback to .toString()
            variantName = variantObj.toString();
        }
    
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case VERBOSE  -> player.sendMessage("§a"+formatEntityName(mob.getType().toString())+" variant changed to " + ChatColor.YELLOW + formatEntityName(variantName)+"§a.");
            default -> {}
        }
    }

    private static <E> int indexOf(E[] arr, E value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(value)) return i;
        }
        return 0;
    }

    private List<String> getComplexVariantDetails(Mob mob) {
        List<String> details = new ArrayList<>();
        if (mob instanceof Horse horse) {
            details.add("Color: §a" + formatEntityName(horse.getColor().toString()));
            details.add("Style: §a" + formatEntityName(horse.getStyle().toString()));
        } else if (mob instanceof Llama llama) {
            details.add("Color: §a" + formatEntityName(llama.getColor().toString()));
        } else if (mob instanceof TropicalFish fish) {
            details.add("Pattern: §a" + formatEntityName(fish.getPattern().toString()));
            details.add("Body Color: §a" + formatEntityName(fish.getBodyColor().toString()));
            details.add("Pattern Color: §a" + formatEntityName(fish.getPatternColor().toString()));
        }
        return details;
    }

    private String formatEntityName(String name) {
        return Arrays.stream(name.toLowerCase().replace("_", " ").split(" "))
                     .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                     .collect(Collectors.joining(" "));
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
        // All click handling is done in handleClick
    }

    @Override
    public void cleanup() {
        HandlerList.unregisterAll(this);
        this.delete();
    }

    @SuppressWarnings("unused")
    private void handleDelete(Player player) {
        // Get the position of this jockey in the stack
        int position = jockey.getPosition();

        // Store the absolute bottom location BEFORE any modifications
        Mob bottomMost = jockeyManager.getDealer();
        while (bottomMost.getVehicle() instanceof Mob) {
            bottomMost = (Mob) bottomMost.getVehicle();
        }
        final Location bottomLocation = bottomMost.getLocation().clone();
        final float bottomYaw = bottomMost.getLocation().getYaw();
        
        //System.out.println("Initial bottom location: " + bottomLocation);
        //System.out.println("Deleting mob: " + jockey.getMob().getType() + " UUID: " + jockey.getMob().getUniqueId());

        // Remove any existing armor stand
        final ArmorStand armorStand;
        final String armorStandName;
        {
            ArmorStand tempArmorStand = null;
            String tempArmorStandName = null;
            for (Entity passenger : jockeyManager.getDealer().getPassengers()) {
                if (passenger instanceof ArmorStand) {
                    tempArmorStand = (ArmorStand) passenger;
                    tempArmorStandName = tempArmorStand.getCustomName();
                    tempArmorStand.remove();
                    break;
                }
            }
            armorStand = tempArmorStand;
            armorStandName = tempArmorStandName;
        }

        // Build the complete stack list in order: bottom vehicles -> dealer -> passengers
        List<Mob> allMobs = new ArrayList<>();
        
        // First add vehicles from bottom up
        Mob current = bottomMost;
        while (current != null) {
            allMobs.add(current);
            //System.out.println("Added to stack (vehicles): " + current.getType() + " UUID: " + current.getUniqueId());
            if (current.getUniqueId().equals(jockeyManager.getDealer().getUniqueId())) {
                break;
            }
            if (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
                current = (Mob) current.getPassengers().get(0);
            } else {
                break;
            }
        }
        
        // Then add passengers
        current = jockeyManager.getDealer();
        while (!current.getPassengers().isEmpty() && current.getPassengers().get(0) instanceof Mob) {
            current = (Mob) current.getPassengers().get(0);
            allMobs.add(current);
            //System.out.println("Added to stack (passengers): " + current.getType() + " UUID: " + current.getUniqueId());
        }

        // Find our target mob's index
        int jockeyIndex = -1;
        for (int i = 0; i < allMobs.size(); i++) {
            if (allMobs.get(i).getUniqueId().equals(jockey.getMob().getUniqueId())) {
                jockeyIndex = i;
                //System.out.println("Found jockey at index: " + i);
                break;
            }
        }

        if (jockeyIndex == -1) {
            player.sendMessage("§cFailed to find jockey in stack");
            return;
        }

        // Check if we're deleting the bottom mob
        boolean isDeletingBottom = jockeyIndex == 0;
        //System.out.println("Is deleting bottom: " + isDeletingBottom);

        // Unmount everything in the stack
        for (Mob mob : allMobs) {
            if (mob.getVehicle() != null) {
                //System.out.println("Unmounting: " + mob.getType() + " from vehicle: " + mob.getVehicle().getType());
                mob.leaveVehicle();
            }
            for (Entity passenger : new ArrayList<>(mob.getPassengers())) {
                //System.out.println("Removing passenger from: " + mob.getType() + " passenger: " + passenger.getType());
                mob.removePassenger(passenger);
            }
        }

        // Remove the jockey from our list and from the world
        allMobs.remove(jockeyIndex);
        jockey.getMob().remove();

        // Remove from JockeyManager
        jockeyManager.removeJockey(position);

        if (!allMobs.isEmpty()) {
            // If we deleted the bottom mob, we need to ensure the new bottom mob gets to the right spot
            if (isDeletingBottom) {
                Mob newBottom = allMobs.get(0);
                //System.out.println("New bottom mob: " + newBottom.getType() + " UUID: " + newBottom.getUniqueId());
                //System.out.println("Target bottom location: " + bottomLocation);
                
                // Force the new bottom mob to the correct location
                newBottom.teleport(bottomLocation);
                newBottom.setRotation(bottomYaw, 0);
                
                // Wait a tick to ensure teleport is processed
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        // Double-check position and force if needed
                        if (!newBottom.getLocation().equals(bottomLocation)) {
                            //System.out.println("Bottom mob not at target location, forcing...");
                            //System.out.println("Current: " + newBottom.getLocation());
                            //System.out.println("Target: " + bottomLocation);
                            newBottom.teleport(bottomLocation);
                            newBottom.setRotation(bottomYaw, 0);
                        }
                    }
                }.runTaskLater(plugin, 1L);
            }

            // Reposition all mobs starting from the bottom
            for (int i = 0; i < allMobs.size(); i++) {
                Mob mob = allMobs.get(i);
                Location newLoc;
                if (i == 0) {
                    // First mob always goes to original bottom location
                    newLoc = bottomLocation.clone();
                    //System.out.println("Setting bottom mob location: " + newLoc + " for mob: " + mob.getType());
                } else {
                    // Other mobs stack on top of the previous mob
                    Mob below = allMobs.get(i - 1);
                    newLoc = below.getLocation().clone();
                    newLoc.add(0, below.getHeight(), 0);
                    //System.out.println("Setting mob location: " + newLoc + " for mob: " + mob.getType() + " above: " + below.getType());
                }
                mob.teleport(newLoc);
                mob.setRotation(bottomYaw, 0);
            }

            // Wait a tick before remounting to ensure all teleports are processed
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Remount everything in order from bottom to top
                    for (int i = 0; i < allMobs.size() - 1; i++) {
                        Mob lower = allMobs.get(i);
                        Mob upper = allMobs.get(i + 1);
                        //System.out.println("Mounting: " + upper.getType() + " on: " + lower.getType());
                        lower.addPassenger(upper);
                        lower.setCustomNameVisible(false);
                    }

                    // Check if we need to respawn the armor stand
                    boolean hasVehicles = jockeyManager.getDealer().getVehicle() != null;
                    boolean hasPassengers = false;
                    for (Entity passenger : jockeyManager.getDealer().getPassengers()) {
                        if (passenger instanceof Mob) {
                            hasPassengers = true;
                            break;
                        }
                    }

                    if (hasVehicles && !hasPassengers) {
                        // Spawn new armor stand at dealer location
                        ArmorStand newArmorStand = (ArmorStand) jockeyManager.getDealer().getWorld().spawnEntity(bottomLocation, EntityType.ARMOR_STAND);
                        newArmorStand.setVisible(false);
                        newArmorStand.setGravity(false);
                        newArmorStand.setSmall(true);
                        newArmorStand.setMarker(true);
                        newArmorStand.setCustomName(armorStandName != null ? armorStandName : jockeyManager.getDealer().getCustomName());
                        newArmorStand.setCustomNameVisible(true);

                        // Add armor stand as passenger to dealer
                        jockeyManager.getDealer().addPassenger(newArmorStand);
                        jockeyManager.getDealer().setCustomNameVisible(false);
                    }

                    // Refresh the jockey manager to ensure all relationships are correct
                    jockeyManager.refresh();
                }
            }.runTaskLater(plugin, 1L);
        }

        // Send feedback to player
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case STANDARD -> player.sendMessage("§aJockey deleted.");
            case VERBOSE -> player.sendMessage("§aJockey " + ChatColor.YELLOW + jockey.getMob().getType().name() + "§a deleted.");
            default -> {}
        }

        // Return to mobSettingsMenu
        executeReturn(player);
    }
} 