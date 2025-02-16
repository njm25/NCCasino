package org.nc.nccasino.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Cat.Type;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.DealerInventory;

public class MobSelectionMenu extends DealerInventory {
    private final UUID ownerId;
    private final Consumer<Player> returnToAdmin;
    private final Nccasino plugin;
    private final String returnName;
    private final Mob dealer;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 45;
    private static final Map<Material, EntityType> spawnEggToEntity = new HashMap<>();
    private static final Map<EntityType, Material> entityToSpawnEgg = new HashMap<>();
    private static final List<Material> spawnEggList = new ArrayList<>();
    private UUID dealerId;
    private final Player player;
    private enum SlotOption {
        EXIT,
        RETURN,
        PAGE_TOGGLE,
        VARIANT,
        AGE_TOGGLE
    }

    private final Map<SlotOption, Integer> slotMapping = Map.of(
        SlotOption.EXIT, 53,
        SlotOption.RETURN, 45,
        SlotOption.PAGE_TOGGLE, 49,
        SlotOption.VARIANT, 47,
        SlotOption.AGE_TOGGLE, 51
    );

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
        
        put(Villager.Type.DESERT, Material.SAND);
        put(Villager.Type.JUNGLE, Material.JUNGLE_LOG);
        put(Villager.Type.PLAINS, Material.GRASS_BLOCK);
        put(Villager.Type.SAVANNA, Material.ACACIA_LOG);
        put(Villager.Type.SNOW, Material.SNOW_BLOCK);
        put(Villager.Type.SWAMP, Material.MANGROVE_LOG);
        put(Villager.Type.TAIGA, Material.SPRUCE_LOG);
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


    static {
        for (Material material : Material.values()) {
            if (material.name().endsWith("_SPAWN_EGG")) {
                try {
                    String entityName = material.name().replace("_SPAWN_EGG", "");
                    EntityType entityType = EntityType.valueOf(entityName);
    
                    // Exclude Wither / Ender Dragon
                    if (entityType != EntityType.ENDER_DRAGON && entityType != EntityType.WITHER) {
                        spawnEggToEntity.put(material, entityType);
                        spawnEggList.add(material);
    
                        // Build reverse map
                        entityToSpawnEgg.put(entityType, material);
                    }
                } catch (IllegalArgumentException ignored) {
                    // If material name doesn't match an EntityType, skip
                }
            }
        }
    
        // Sort the spawnEggList, etc. (unchanged)
        spawnEggList.sort((m1, m2) -> {
            String name1 = formatEntityName(spawnEggToEntity.get(m1).name());
            String name2 = formatEntityName(spawnEggToEntity.get(m2).name());
            return name1.compareToIgnoreCase(name2);
        });
    }

    public static Material getSpawnEggFor(EntityType type) {
        return entityToSpawnEgg.getOrDefault(type, Material.EGG);
    }

    public MobSelectionMenu(Player player, Nccasino plugin, UUID dealerId, Consumer<Player> returnToAdmin, String returnName) {
        super(player.getUniqueId(), 54, "Select Model for " +  Dealer.getInternalName((Mob) player.getWorld()
        .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
        .filter(entity -> entity instanceof Mob)
        .map(entity -> (Mob) entity)
        .filter(v -> Dealer.isDealer(v)
                    && Dealer.getUniqueId(v).equals(dealerId))
        .findFirst().orElse(null)));
        this.dealerId = dealerId;
        this.dealer = (Mob) player.getWorld()
        .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
        .filter(entity -> entity instanceof Mob)
        .map(entity -> (Mob) entity)
        .filter(v -> Dealer.isDealer(v)
                     && Dealer.getUniqueId(v).equals(this.dealerId))
        .findFirst().orElse(null);
        this.ownerId = player.getUniqueId();
        this.plugin = plugin;
        this.player=player;
        this.returnName = returnName;
        this.returnToAdmin = returnToAdmin;
        registerListener();
        updateMenu();
    }

    private void registerListener() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void updateMenu() {
        inventory.clear();
        int startIndex = (currentPage - 1) * PAGE_SIZE;

        // Populate spawn eggs
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

        // Navigation & Utility Buttons
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnName, slotMapping.get(SlotOption.RETURN));

        // Single Slot Pagination Button (Switches Between Next & Previous)
        if (currentPage > 1) {
            addItemAndLore(Material.ARROW, 1, "Previous Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        } else if (currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
            addItemAndLore(Material.ARROW, 1, "Next Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        }
        updateVariantButton();
        updateAgeButton(); 
    }

    @Override
    public void handleClick(int slot, Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !(event.getInventory().getHolder() instanceof MobSelectionMenu)) {
            return;
        }

        event.setCancelled(true);
        
        SlotOption option = getKeyByValue(slotMapping, slot);
        if(option!=null){
            switch (option) {
                case PAGE_TOGGLE:
                    if (currentPage > 1) {
                        currentPage--;
                    } else if (currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
                        currentPage++;
                    }
                    updateMenu();
                    playDefaultSound(player);
                    return;
                case EXIT: 
                    player.closeInventory();
                    playDefaultSound(player);
                    return;
                case RETURN: 
                    returnToAdmin.accept(player);
                    playDefaultSound(player);
                    return;
                case VARIANT:
                    handleVariantClick(player);
                    playDefaultSound(player);
                    break;
                case AGE_TOGGLE :
                    handleAgeToggle(player);
                    playDefaultSound(player);
                    return;
                    
            }
        }

        

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;


        if (!spawnEggToEntity.containsKey(clickedItem.getType())) return;

        EntityType selectedType = spawnEggToEntity.get(clickedItem.getType());
        String internalName = Dealer.getInternalName(dealer);


        playDefaultSound(player);
        // Restore dealer settings
        restoreDealerSettings(internalName,selectedType);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getInventory().getHolder() instanceof MobSelectionMenu) {
                if (event.getPlayer().getUniqueId().equals(ownerId)) {
                    HandlerList.unregisterAll(this);
                    super.delete();
                }
            }
        }, 4L);
    }

    private void restoreDealerSettings(String internalName, EntityType selectedType) {
        Location loc = dealer.getLocation();
        
        String name = plugin.getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
        String gameType = plugin.getConfig().getString("dealers." + internalName + ".game", "Menu");
        int timer = plugin.getConfig().getInt("dealers." + internalName + ".timer", 0);
        String anmsg = plugin.getConfig().getString("dealers." + internalName + ".animation-message");
        List<Integer> chipSizes = new ArrayList<>();

        ConfigurationSection chipSizeSection = plugin.getConfig().getConfigurationSection("dealers." + internalName + ".chip-sizes");
        if (chipSizeSection != null) {
            for (String key : chipSizeSection.getKeys(false)) {
                chipSizes.add(plugin.getConfig().getInt("dealers." + internalName + ".chip-sizes." + key));
            }
        }
        chipSizes.sort(Integer::compareTo);
        String currencyMaterial = plugin.getConfig().getString("dealers." + internalName + ".currency.material");
        String currencyName = plugin.getConfig().getString("dealers." + internalName + ".currency.name");
        ConfirmMenu confirmInventory = new ConfirmMenu(
            player,
            dealerId,
            "Reset config to default?",
            (uuid) -> {
                // Confirm action
                

                Bukkit.dispatchCommand(player, "ncc delete " + Dealer.getInternalName(dealer));
                
                plugin.saveDefaultDealerConfig(internalName);
                Dealer.spawnDealer(plugin, loc, name, internalName, gameType, selectedType);
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD -> player.sendMessage(ChatColor.GREEN + "Dealer changed.");
                    case VERBOSE  ->player.sendMessage(ChatColor.GREEN + "Dealer changed to " + ChatColor.YELLOW + formatEntityName(selectedType.name())+"§a.");
                    default -> {}
                }                player.closeInventory();
                if (!plugin.getConfig().contains("dealers." + internalName + ".stand-on-17") && gameType.equals("Blackjack")) {
                    plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                } else {
                    int hasStand17Config = plugin.getConfig().getInt("dealers." + internalName + ".stand-on-17");
                    if (hasStand17Config > 100 || hasStand17Config < 0) {
                        plugin.getConfig().set("dealers." + internalName + ".stand-on-17", 100);
                    }
                }

        
                this.delete();
            },
            (uuid) -> {
                // Dent action
                Bukkit.dispatchCommand(player, "ncc delete " + Dealer.getInternalName(dealer));
                Mob newDealer = Dealer.spawnDealer(plugin, loc, name, internalName, gameType, selectedType);
                Dealer.updateGameType(newDealer, gameType, timer, anmsg, name, chipSizes, currencyMaterial, currencyName);
                switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                    case STANDARD -> player.sendMessage(ChatColor.GREEN + "Dealer changed.");
                    case VERBOSE  ->player.sendMessage(ChatColor.GREEN + "Dealer changed to " + ChatColor.YELLOW + formatEntityName(selectedType.name())+"§a.");
                    default -> {}
                }
                player.closeInventory();
                this.delete();
            },
            plugin
            );
        
        player.openInventory(confirmInventory.getInventory());
    
    }

    private void handleVariantClick(Player player) {
        if (isComplicatedVariant(dealer)) {
               openVariantMenu(player, dealer);
           }
           else if (hasSingleVariant(dealer)) {
               cycleSingleVariant(player, dealer);
           }
           else {
               switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                   case STANDARD -> player.sendMessage("§cInvalid Action.");
                   case VERBOSE  -> player.sendMessage("§cNo variants to cycle for " + ChatColor.YELLOW+dealer.getType()+"§a.");
                   default -> {}
               }
           }
       }
   
    private void openVariantMenu(Player player, Mob dealer) {
           // You'll build out a Llama/Horse/TropicalFish inventory here
          // player.closeInventory();
         player.sendMessage(ChatColor.YELLOW + "[DEBUG] Opening specialized variant menu soon...");
     }

    private void cycleSingleVariant(Player player, Mob mob) {
        if (mob instanceof Cat cat) {
            Type current = cat.getCatType();// or cat.getVariant() if your version uses Cat.Variant
            int currentIndex = indexOf(CAT_TYPES, current);
            // Move to the next index
            int nextIndex = (currentIndex + 1) % CAT_TYPES.length;
            cat.setCatType(CAT_TYPES[nextIndex]);
            sendVariantUpdateMessage(player, CAT_TYPES[nextIndex],mob);
        }
        else if (mob instanceof Fox fox) {
            List<Fox.Type> variants = List.of(Fox.Type.values());
            Fox.Type curr = fox.getFoxType();
            int idx = variants.indexOf(curr);
            Fox.Type next = variants.get((idx + 1) % variants.size());
            fox.setFoxType(next);
            sendVariantUpdateMessage(player, next,mob);
        }
        else if (mob instanceof Frog frog) {
            // If you're forced to use Frog.Type, do that. 
            // Otherwise (if on 1.19+), you'd do frog.getVariant() and store FROG_VARIANTS
            Frog.Variant current = frog.getVariant();
            int currentIndex = indexOf(FROG_VARIANTS, current);
            int nextIndex = (currentIndex + 1) % FROG_VARIANTS.length;
            frog.setVariant(FROG_VARIANTS[nextIndex]);
            sendVariantUpdateMessage(player, FROG_VARIANTS[nextIndex],mob);
        }
        else if (mob instanceof Parrot parrot) {
            List<Parrot.Variant> variants = List.of(Parrot.Variant.values());
            Parrot.Variant curr = parrot.getVariant();
            int idx = variants.indexOf(curr);
            Parrot.Variant next = variants.get((idx + 1) % variants.size());
            parrot.setVariant(next);
            sendVariantUpdateMessage(player, next,mob);
        }
        else if (mob instanceof Rabbit rabbit) {
            List<Rabbit.Type> variants = List.of(Rabbit.Type.values());
            Rabbit.Type curr = rabbit.getRabbitType();
            int idx = variants.indexOf(curr);
            Rabbit.Type next = variants.get((idx + 1) % variants.size());
            rabbit.setRabbitType(next);
            sendVariantUpdateMessage(player, next,mob);
        }
        else if (mob instanceof Axolotl axolotl) {
            List<Axolotl.Variant> variants = List.of(Axolotl.Variant.values());
            Axolotl.Variant curr = axolotl.getVariant();
            int idx = variants.indexOf(curr);
            Axolotl.Variant next = variants.get((idx + 1) % variants.size());
            axolotl.setVariant(next);
            sendVariantUpdateMessage(player, next,mob);
        }
        else if (mob instanceof MushroomCow mooshroom) {
            // Mooshroom has a Mooshroom.Variant
            List<MushroomCow.Variant> variants = List.of(MushroomCow.Variant.values());
            MushroomCow.Variant curr = mooshroom.getVariant();
            int idx = variants.indexOf(curr);
            MushroomCow.Variant next = variants.get((idx + 1) % variants.size());
            mooshroom.setVariant(next);
            sendVariantUpdateMessage(player, next,mob);
        }
        else if (mob instanceof Panda panda) {
            if (panda.getMainGene() == Panda.Gene.BROWN) {
                // Switch back to normal
                panda.setMainGene(Panda.Gene.NORMAL);
                panda.setHiddenGene(Panda.Gene.NORMAL);
            } else {
                // Force both main and hidden gene to BROWN
                panda.setMainGene(Panda.Gene.BROWN);
                panda.setHiddenGene(Panda.Gene.BROWN);
            }
            sendVariantUpdateMessage(player, panda.getMainGene(),mob);
        }
        else if (mob instanceof Villager villager) {
            cycleVillagerType(player, villager);
        }
        else if (mob instanceof ZombieVillager zombieVillager) {
            cycleZombieVillagerType(player, zombieVillager);
        }
         else if (mob instanceof Sheep sheep) {
        List<DyeColor> colors = List.of(DyeColor.values());
        int index = colors.indexOf(sheep.getColor());
        sheep.setColor(colors.get((index + 1) % colors.size()));
        sendVariantUpdateMessage(player, sheep.getColor(),mob);
        }
        
        // Add more if you have more single-variant mob classes

        else {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§cMob type " +ChatColor.YELLOW+formatEntityName(mob.getType().toString())  + "§c does not have any variants.");
                default -> {}
            }
        }
        updateVariantButton();
    }
    
    private void cycleVillagerType(Player player, Villager villager) {
        Villager.Type currentBiome = villager.getVillagerType();
        int index = VILLAGER_BIOMES.indexOf(currentBiome);
        Villager.Type newBiome = VILLAGER_BIOMES.get((index + 1) % VILLAGER_BIOMES.size());
        villager.setVillagerType(newBiome);

        // Update item in the admin menu
        addItemAndLore(
            BIOME_MATERIALS.getOrDefault(newBiome, Material.GRASS_BLOCK),
            1,
            "Toggle " + formatEntityName(dealer.getType().toString()) + " Variant",
            slotMapping.get(SlotOption.VARIANT),
            "Current: §a" + formatEntityName(newBiome.toString())
        );

        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case VERBOSE -> player.sendMessage("§aVillager variant changed to " + ChatColor.YELLOW+ formatEntityName(newBiome.toString())+"§a.");
            default -> {}
        }
    }

    private void cycleZombieVillagerType(Player player, ZombieVillager zombieVillager) {
        Villager.Type currentBiome = zombieVillager.getVillagerType();
        int index = VILLAGER_BIOMES.indexOf(currentBiome);
        Villager.Type newBiome = VILLAGER_BIOMES.get((index + 1) % VILLAGER_BIOMES.size());
        zombieVillager.setVillagerType(newBiome);
        switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
            case VERBOSE  -> player.sendMessage("§aZombie Villager variant changed to " + ChatColor.YELLOW+ formatEntityName(newBiome.toString())+"§a.");
            default -> {}
        }
    }


    private void updateVariantButton() {
        int slot = slotMapping.get(SlotOption.VARIANT);
    
        if (dealer instanceof Villager villager) {
            Villager.Type vt = villager.getVillagerType();
            Material mat = BIOME_MATERIALS.getOrDefault(vt, Material.GRASS_BLOCK);
            addItemAndLore(
                mat, 
                1,
                "Toggle " + formatEntityName(dealer.getType().toString()) + " Variant",
                slot,
                "Current: §a" + formatEntityName(vt.toString())
            );
            return;
        }
    
        if (isComplicatedVariant(dealer)) {
            addItemAndLore(
                Material.WRITABLE_BOOK,
                1,
                "Open Variant Menu",
                slot,
                "Current: §a" + dealer.getType().toString()
            );
            return;
        }
    
        if (hasSingleVariant(dealer)) {
            addItemAndLore(
                Material.REPEATER,
                1,
                "Toggle " + formatEntityName(dealer.getType().toString()) + " Variant",
                slot,
                "Current: §a" + getVariantName(dealer)
            );
            return;
        }
    }

    private void handleAgeToggle(Player player) {
        if (dealer == null) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE ->player.sendMessage(ChatColor.RED + "Dealer not found!");
                default -> {}
            }
            return;
        }

        if (!isAgeable(dealer)&& !(dealer instanceof MagmaCube)&&!(dealer instanceof Slime)) {
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE ->player.sendMessage(ChatColor.RED + "This mob doesn't support baby/adult toggles.");
                default -> {}
            }
            return;
        }
        if(isAgeable(dealer)){
        // It's Ageable, so cast and toggle
        org.bukkit.entity.Ageable ageable = (org.bukkit.entity.Ageable) dealer;
        if (ageable.isAdult()) {
            ageable.setBaby();
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§a"+formatEntityName(ageable.getType().toString())+" set to "+ChatColor.YELLOW+ "baby§a.");
                default -> {}
            }
        } else {
            ageable.setAdult();
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§a"+formatEntityName(ageable.getType().toString())+" set to "+ChatColor.YELLOW+"adult§a.");
                default -> {}
            }
        }
    }
        if (dealer instanceof Slime slime&&!(dealer instanceof MagmaCube)) {
            int newSize = (slime.getSize() % 3) + 1; // Cycle through 1 → 2 → 3 → 1
            slime.setSize(newSize);
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§aSlime size set to " + ChatColor.YELLOW + getSizeCategory(newSize) + "§a.");
                default -> {}
            }
        } 
        else if (dealer instanceof MagmaCube magmaCube) {
            int newSize = (magmaCube.getSize() % 3) + 1;
            magmaCube.setSize(newSize);
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§aMagma Cube size set to " + ChatColor.YELLOW + getSizeCategory(newSize) + "§a.");
                default -> {}
            }
        }
        // Update the slot item so the player sees the new "Current Age"
        updateAgeButton();
    }

    private void updateAgeButton() {
        if (dealer == null) return;
    
        int slot = slotMapping.get(SlotOption.AGE_TOGGLE);
    
        // Check if this mob is "Ageable" in the Bukkit API
        if (isAgeable(dealer)) {
            org.bukkit.entity.Ageable ageable = (org.bukkit.entity.Ageable) dealer;
    
            // Display a MILK_BUCKET or something symbolic for toggling age
            Material icon = Material.MILK_BUCKET;
    
            String currentAgeText = ageable.isAdult() ? "Adult" : "Baby";
            addItemAndLore(
                icon,
                1,
                "Toggle Age",
                slot,
                "Current: §a" + currentAgeText
            );
        }
        else if (dealer instanceof Slime slime&&!(dealer instanceof MagmaCube)) {
            int currentSize = slime.getSize();
            addItemAndLore(
                Material.SLIME_BALL,
                1,
                "Toggle Size",
                slot,
                "Current: §a" + getSizeCategory(currentSize)
            );
        }
        else if (dealer instanceof MagmaCube magmaCube) {
            int currentSize = magmaCube.getSize();
            addItemAndLore(
                Material.MAGMA_CREAM,
                1,
                "Toggle Size",
                slot,
                "Current: §a" + getSizeCategory(currentSize)
            );
        }


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

    private boolean isAgeable(Mob mob) {
        return mob instanceof org.bukkit.entity.Ageable&&
        !(mob instanceof Parrot) &&
        !(mob instanceof Frog) &&
        !(mob instanceof PiglinBrute) &&
        !(mob instanceof WanderingTrader);
    }

    private String getVariantName(Mob mob) {
        if (mob instanceof Cat cat) {
            return formatEntityName(cat.getCatType().toString()); // Or cat.getVariant() if applicable
        } else if (mob instanceof Fox fox) {
            return formatEntityName(fox.getFoxType().toString());
        } else if (mob instanceof Frog frog) {
            return formatEntityName(frog.getVariant().toString());
        } else if (mob instanceof Parrot parrot) {
            return formatEntityName(parrot.getVariant().toString());
        } else if (mob instanceof Rabbit rabbit) {
            return formatEntityName(rabbit.getRabbitType().toString());
        } else if (mob instanceof Axolotl axolotl) {
            return formatEntityName(axolotl.getVariant().toString());
        } else if (mob instanceof MushroomCow mooshroom) {
            return formatEntityName(mooshroom.getVariant().toString());
        } else if (mob instanceof Panda panda) {
            return formatEntityName(panda.getMainGene().toString());
        } else if (mob instanceof Villager villager) {
            return formatEntityName(villager.getVillagerType().toString());
        } else if (mob instanceof ZombieVillager zombieVillager) {
            return formatEntityName(zombieVillager.getVillagerType().toString());
        } else if (mob instanceof Sheep sheep) {
            return formatEntityName(sheep.getColor().toString());
        }

        return "Unknown";
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
            || (mob instanceof Sheep);
    }

        private boolean isComplicatedVariant(Mob mob) {
        return (mob instanceof Llama)
            || (mob instanceof Horse)
            || (mob instanceof TropicalFish);
    }

    private String getSizeCategory(int size) {
        if (size <= 1) return "Small";
        if (size == 2) return "Medium";
        return "Large"; // Default for size 3 and above
    }

    private static String formatEntityName(String entityName) {
        return Arrays.stream(entityName.toLowerCase().replace("_", " ").split(" "))
                     .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                     .collect(Collectors.joining(" "));
    }

       private static <E> int indexOf(E[] arr, E value) {
        for (int i = 0; i < arr.length; i++) {
            if (Objects.equals(arr[i], value)) {
                return i;
            }
        }
        return 0; // fallback
    }
}
