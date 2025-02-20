package org.nc.nccasino.components;

import java.io.File;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Dealer;
import org.nc.nccasino.entities.DealerInventory;
import org.nc.nccasino.entities.Menu;

public class MobSelectionMenu extends Menu {
    private final Mob dealer;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 45;
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
)   ;


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
    
        // Manually add the Giant with a Zombie Head
        spawnEggToEntity.put(Material.ZOMBIE_HEAD, EntityType.GIANT);
        entityToSpawnEgg.put(EntityType.GIANT, Material.ZOMBIE_HEAD);
        spawnEggList.add(Material.ZOMBIE_HEAD);
        spawnEggToEntity.put(Material.ENCHANTED_BOOK, EntityType.ILLUSIONER);
        entityToSpawnEgg.put(EntityType.ILLUSIONER, Material.ENCHANTED_BOOK);
        spawnEggList.add(Material.ENCHANTED_BOOK);
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
        super(
            player, 
            plugin, 
            dealerId, 
                Dealer.getInternalName((Mob) player.getWorld()
                .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
                .filter(entity -> entity instanceof Mob)
                .map(entity -> (Mob) entity)
                .filter(v -> Dealer.isDealer(v) && Dealer.getUniqueId(v).equals(dealerId)).findFirst().orElse(null))
                + "'s Mob Settings"
                , 
            54, 
            returnName, 
            returnToAdmin
        ); 
        this.dealer = (Mob) player.getWorld()
        .getNearbyEntities(player.getLocation(), 5, 5, 5).stream()
        .filter(entity -> entity instanceof Mob)
        .map(entity -> (Mob) entity)
        .filter(v -> Dealer.isDealer(v)
                     && Dealer.getUniqueId(v).equals(this.dealerId))
        .findFirst().orElse(null);

     

        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        inventory.clear();
        int startIndex = (currentPage - 1) * PAGE_SIZE;

        // Populate spawn eggs
        for (int i = 0; i < PAGE_SIZE && startIndex + i < spawnEggList.size(); i++) {
            Material material = spawnEggList.get(startIndex + i);
            EntityType entityType = spawnEggToEntity.get(material);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (entityType == EntityType.ILLUSIONER) {
                    meta.setDisplayName(ChatColor.YELLOW + "Illusioner");
                    meta.setLore(null); // Remove default enchantment lore
                    item.setItemMeta(meta);
                } else {
                    meta.setDisplayName(ChatColor.YELLOW + formatEntityName(entityType.name()));
                    item.setItemMeta(meta);
                }
            }
            inventory.setItem(i, item);
        }
        slotMapping.put(SlotOption.EXIT, 53);
        slotMapping.put(SlotOption.RETURN, 45);
       
        slotMapping.put(SlotOption.PAGE_TOGGLE, 49);
        // Navigation & Utility Buttons
        addItemAndLore(Material.SPRUCE_DOOR, 1, "Exit", slotMapping.get(SlotOption.EXIT));
        addItemAndLore(Material.MAGENTA_GLAZED_TERRACOTTA, 1, "Return to " + returnMessage, slotMapping.get(SlotOption.RETURN));

        // Single Slot Pagination Button (Switches Between Next & Previous)
        if (currentPage > 1) {
            addItemAndLore(Material.ARROW, 1, "Previous Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        } else if (currentPage < (int) Math.ceil(spawnEggList.size() / (double) PAGE_SIZE)) {
            addItemAndLore(Material.ARROW, 1, "Next Page", slotMapping.get(SlotOption.PAGE_TOGGLE));
        }
 
        if(isComplicatedVariant(dealer)||hasSingleVariant(dealer)){
            slotMapping.put(SlotOption.VARIANT, 47);
            updateVariantButton();
        }
        if (isAgeable(dealer) || dealer instanceof Slime || dealer instanceof MagmaCube) {
            slotMapping.put(SlotOption.AGE_TOGGLE, 51);
            updateAgeButton(); 
        }
        
        
        
    }

    @Override
    protected void handleCustomClick(SlotOption option, Player player, InventoryClickEvent event) {
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
            case VARIANT:
                handleVariantClick(player);
                playDefaultSound(player);
                break;
            case AGE_TOGGLE:
                handleAgeToggle(player);
                playDefaultSound(player);
                return;
            default:
                return;
        }
    }
    

    public void handleEntityClick(Player player, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !spawnEggToEntity.containsKey(clickedItem.getType())) return;
    
        EntityType selectedType = spawnEggToEntity.get(clickedItem.getType());
        String internalName = Dealer.getInternalName(dealer);
    
        playDefaultSound(player);
        restoreDealerSettings(internalName, selectedType,player);
    }
    
    

    private void restoreDealerSettings(String internalName, EntityType selectedType, Player player) {
        File dealersFile = new File(plugin.getDataFolder(), "data/dealers.yaml");
        if (!dealersFile.exists()) {
            player.sendMessage(ChatColor.RED + "Dealers data file not found!");
            return;
        }

         FileConfiguration dealersConfig = YamlConfiguration.loadConfiguration(dealersFile);

         // Read dealer location from dealers.yaml
         String worldName = dealersConfig.getString("dealers." + internalName + ".world");
         double x = dealersConfig.getDouble("dealers." + internalName + ".X");
         double y = dealersConfig.getDouble("dealers." + internalName + ".Y");
         double z = dealersConfig.getDouble("dealers." + internalName + ".Z");

        if (worldName == null) {
            player.sendMessage(ChatColor.RED + "Failed to retrieve dealer location.");
            return;
        }
    
        var world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "World '" + worldName + "' does not exist.");
            return;
        }
        
        Location loc = new Location(world, x, y, z);
        
        String name = plugin.getConfig().getString("dealers." + internalName + ".display-name", "Dealer");
        String gameType = plugin.getConfig().getString("dealers." + internalName + ".game", "Menu");
        int timer = plugin.getConfig().getInt("dealers." + internalName + ".timer", 30);
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
                   Mob oldDealer = plugin.getDealerByInternalName(internalName);
                    if (oldDealer != null) {
                        Dealer.removeDealer(oldDealer);
                        DealerInventory.unregisterAllListeners(oldDealer);
                    }
                    AdminMenu.deleteAssociatedAdminInventories(player);                
                plugin.saveDefaultDealerConfig(internalName);
                Dealer.spawnDealer(plugin, loc, name, internalName, gameType, selectedType);
                dealersConfig.set("dealers." + internalName + ".world", worldName);
                dealersConfig.set("dealers." + internalName + ".X", x);
                dealersConfig.set("dealers." + internalName + ".Y", y);
                dealersConfig.set("dealers." + internalName + ".Z", z);
                try {
                    dealersConfig.save(dealersFile);
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Failed to save dealer data: " + e.getMessage());
                }
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
                if (!plugin.getConfig().contains("dealers." + internalName + ".default-mines") && gameType.equals("Mines")) {
                    plugin.getConfig().set("dealers." + internalName + ".default-mines", 3);
                } else {
                    int deafultMines = plugin.getConfig().getInt("dealers." + internalName + ".default-mines");
                    if (deafultMines > 24 || deafultMines < 1) {
                        plugin.getConfig().set("dealers." + internalName + ".default-mines", 3);
                    }
                }
        
                this.delete();
            },
            (uuid) -> {
                // Dent action

                Mob oldDealer = plugin.getDealerByInternalName(internalName);
                if (oldDealer != null) {
                    Dealer.removeDealer(oldDealer);
                    DealerInventory.unregisterAllListeners(oldDealer);
                }
                AdminMenu.deleteAssociatedAdminInventories(player);
                Mob newDealer = Dealer.spawnDealer(plugin, loc, name, internalName, gameType, selectedType);
                Dealer.updateGameType(newDealer, gameType, timer, anmsg, name, chipSizes, currencyMaterial, currencyName);
                dealersConfig.set("dealers." + internalName + ".world", worldName);
                dealersConfig.set("dealers." + internalName + ".X", x);
                dealersConfig.set("dealers." + internalName + ".Y", y);
                dealersConfig.set("dealers." + internalName + ".Z", z);
                try {
                    dealersConfig.save(dealersFile);
                } catch (Exception e) {
                    player.sendMessage(ChatColor.RED + "Failed to save dealer data: " + e.getMessage());
                }
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
        ComplexVariantMenu cvm = new ComplexVariantMenu(
            player,
            plugin,
            dealerId,
            "Return to Mob Selection",
            // This is your return callback: re-open MobSelectionMenu
            (p) -> {
                // Open this same MobSelectionMenu again
                p.openInventory(MobSelectionMenu.this.getInventory());
            },
            dealer
        );
        player.openInventory(cvm.getInventory());
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
            int index = SHEEP_COLOR_ORDER.indexOf(sheep.getColor());
            sheep.setColor(SHEEP_COLOR_ORDER.get((index + 1) % SHEEP_COLOR_ORDER.size()));
            sendVariantUpdateMessage(player, sheep.getColor(), mob);            
        }else if (mob instanceof Wolf wolf) {
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
            "Edit " + formatEntityName(dealer.getType().toString()) + " Variant",
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
                "Edit " + formatEntityName(dealer.getType().toString()) + " Variant",
                slot,
                "Current: §a" + formatEntityName(vt.toString())
            );
            return;
        }
        else if  (dealer instanceof ZombieVillager villager) {
            Villager.Type vt = villager.getVillagerType();
            Material mat = BIOME_MATERIALS.getOrDefault(vt, Material.GRASS_BLOCK);
            addItemAndLore(
                mat, 
                1,
                "Edit " + formatEntityName(dealer.getType().toString()) + " Variant",
                slot,
                "Current: §a" + formatEntityName(vt.toString())
            );
            return;
        }
    
        else if (isComplicatedVariant(dealer)) {
            List<String> details = getComplexVariantDetails(dealer);

            addItemAndLore(
                Material.WRITABLE_BOOK,
                1,
                "Open "+ formatEntityName(dealer.getType().toString())+" Variant Menu",
                slot,
                details.toArray(new String[0])
            );
            return;
        }
        else if (dealer instanceof Wolf wolf) {
            if (!wolf.isTamed()) {
                addItemAndLore(
                    Material.BARRIER,
                    1,
                    "Edit " + formatEntityName(dealer.getType().toString()) + " Collar Color",
                    slotMapping.get(SlotOption.VARIANT),
                     "Current: §aNone"
                );
                return;
            }
        
            DyeColor currentColor = wolf.getCollarColor();
            Material dyeMaterial = DYE_MATERIAL_MAP.getOrDefault(currentColor, Material.GRAY_DYE);
            
            addItemAndLore(
                dyeMaterial,
                1,
                "Edit " + formatEntityName(dealer.getType().toString()) + " Collar Color",
                slotMapping.get(SlotOption.VARIANT),
                "Current: §a" + formatEntityName(currentColor.toString())
            );
            return;
        }
        
        else  if (hasSingleVariant(dealer)) {
            Material variantItem = getVariantItem(dealer);
            addItemAndLore(
                variantItem,
                1,
                "Edit " + formatEntityName(dealer.getType().toString()) + " Variant",
                slot,
                "Current: §a" + getVariantName(dealer)
            );
            return;
        }
    }

    private Material getVariantItem(Mob mob) {
        // Check manually instead of using VARIANT_ITEMS.get(mob.getClass())
        if (mob instanceof Cat cat) {
            return VARIANT_ITEMS.get(Cat.class)[indexOf(CAT_TYPES, cat.getCatType())];
        } else if (mob instanceof Fox fox) {
            return VARIANT_ITEMS.get(Fox.class)[indexOf(Fox.Type.values(), fox.getFoxType())];
        } else if (mob instanceof Frog frog) {
            return VARIANT_ITEMS.get(Frog.class)[indexOf(FROG_VARIANTS, frog.getVariant())];
        } else if (mob instanceof Parrot parrot) {
            return VARIANT_ITEMS.get(Parrot.class)[indexOf(Parrot.Variant.values(), parrot.getVariant())];
        } else if (mob instanceof Rabbit rabbit) {
            return VARIANT_ITEMS.get(Rabbit.class)[indexOf(Rabbit.Type.values(), rabbit.getRabbitType())];
        } else if (mob instanceof Axolotl axolotl) {
            return VARIANT_ITEMS.get(Axolotl.class)[indexOf(Axolotl.Variant.values(), axolotl.getVariant())];
        } else if (mob instanceof MushroomCow mooshroom) {
            return VARIANT_ITEMS.get(MushroomCow.class)[indexOf(MushroomCow.Variant.values(), mooshroom.getVariant())];
        } else if (mob instanceof Panda panda) {
            return VARIANT_ITEMS.get(Panda.class)[panda.getMainGene() == Panda.Gene.BROWN ? 1 : 0];
        } else if (mob instanceof Sheep sheep) {
            return VARIANT_ITEMS.get(Sheep.class)[indexOf(DyeColor.values(), sheep.getColor())];
        }
    
        return Material.BARRIER; // Default if not found
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
            int newSize = (slime.getSize() % 30) + 1; // Cycle through 1 → 2 → 3 → 1
            slime.setSize(newSize);
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§aSlime size set to " + ChatColor.YELLOW + newSize + "§a.");
                default -> {}
            }
        } 
        else if (dealer instanceof MagmaCube magmaCube) {
            int newSize = (magmaCube.getSize() % 30) + 1;
            magmaCube.setSize(newSize);
            switch (plugin.getPreferences(player.getUniqueId()).getMessageSetting()) {
                case VERBOSE -> player.sendMessage("§aMagma Cube size set to " + ChatColor.YELLOW + newSize + "§a.");
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
            boolean isAdult = ageable.isAdult();
            Material icon = isAdult ? Material.DRAGON_HEAD : Material.DRAGON_EGG;
    
            String currentAgeText = ageable.isAdult() ? "Adult" : "Baby";
            addItemAndLore(
                icon,
                1,
                "Edit Age",
                slot,
                "Current: §a" + currentAgeText
            );
        }
        else if (dealer instanceof Slime slime&&!(dealer instanceof MagmaCube)) {
            int currentSize = slime.getSize();
            addItemAndLore(
                Material.SLIME_BALL,
                1,
                "Edit Size",
                slot,
                "Current: §a" + currentSize
            );
        }
        else if (dealer instanceof MagmaCube magmaCube) {
            int currentSize = magmaCube.getSize();
            addItemAndLore(
                Material.MAGMA_CREAM,
                1,
                "Edit Size",
                slot,
                "Current: §a" + currentSize
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
        }else if (mob instanceof Wolf wolf) {
            return formatEntityName(wolf.getCollarColor().toString());
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
            || (mob instanceof Sheep)
            || (mob instanceof Wolf);
            
    }

        private boolean isComplicatedVariant(Mob mob) {
        return (mob instanceof Llama)
            || (mob instanceof Horse)
            || (mob instanceof TropicalFish);
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

    private List<String> getComplexVariantDetails(Mob mob) {
        List<String> details = new ArrayList<>();
        if (mob instanceof Llama llama) {
            details.add("Current Color: §a" + formatEntityName(llama.getColor().toString()));
            details.add("Current Decor: §a" + getLlamaCarpetName(llama));
        } else if (mob instanceof Horse horse) {
            details.add("Current Color: §a" + formatEntityName(horse.getColor().toString()));
            details.add("Current Style: §a" + formatEntityName(horse.getStyle().toString()));
        } else if (mob instanceof TropicalFish fish) {
            details.add("Current Pattern: §a" + formatEntityName(fish.getPattern().toString()));
            details.add("Current Body Color: §a" + formatEntityName(fish.getBodyColor().toString()));
            details.add("Current Pattern Color: §a" + formatEntityName(fish.getPatternColor().toString()));
        }
        return details;
        }
    
    private String getLlamaCarpetName(Llama llama) {
        if (llama.getInventory().getDecor() != null) {
            return formatEntityName(llama.getInventory().getDecor().getType().toString().replace("_CARPET", ""));
        }
        return "None";
    }
    
}
