package org.nc.nccasino.components;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;
import org.nc.nccasino.entities.JockeyNode;
import org.nc.nccasino.entities.JockeyManager;

import java.util.*;
import java.util.function.Consumer;

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
        super(player, plugin, jockey.getId(), "Jockey Options", 54, returnName, returnCallback);
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
        slotMapping.put(SlotOption.PAGE_TOGGLE, 49);
        
        if (isAgeable(jockey.getMob()) || jockey.getMob() instanceof Slime || jockey.getMob() instanceof MagmaCube) {
            slotMapping.put(SlotOption.AGE_TOGGLE, 51);
        }
        
        if (isComplicatedVariant(jockey.getMob()) || hasSingleVariant(jockey.getMob())) {
            slotMapping.put(SlotOption.VARIANT, 47);
        }

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
                
                // Store references before changing anything
                Mob oldMob = jockey.getMob();
                JockeyNode parentNode = jockey.getParent();
                JockeyNode childNode = jockey.getChild();
                
                // First, detach any child to prevent it from being removed with the old mob
                if (childNode != null) {
                    childNode.unmount();
                }
                
                // Spawn new mob near the parent (temporary location)
                Mob parentMob = parentNode != null ? parentNode.getMob() : jockeyManager.getDealer();
                Mob newMob = (Mob) parentMob.getWorld().spawnEntity(parentMob.getLocation(), selectedType);
                
                // Get the dealer's name
                String dealerName = jockeyManager.getDealer().getCustomName();
                if (dealerName == null || dealerName.isEmpty()) {
                    dealerName = "Dealer";
                }
                
                // Update the jockey with the new mob
                if (jockeyManager.changeMobType(jockey.getPosition(), newMob)) {
                    // Set the dealer's name
                    newMob.setCustomName(dealerName);
                    newMob.setCustomNameVisible(true);
                    
                    // First attach this mob to its parent
                    if (parentNode != null) {
                        parentMob.addPassenger(newMob);
                    }
                    
                    // Then reattach the child if it existed
                    if (childNode != null) {
                        Mob childMob = childNode.getMob();
                        newMob.addPassenger(childMob);
                        childNode.setParent(jockey);
                        jockey.setChild(childNode);
                    }
                    
                    player.sendMessage("§aChanged jockey to " + formatEntityName(selectedType.name()));
                    playDefaultSound(player);
                    if (returnCallback != null) {
                        returnCallback.accept(player);
                    }
                } else {
                    player.sendMessage("§cFailed to change jockey");
                    newMob.remove();
                    
                    // If we failed, reattach the child to maintain stack
                    if (childNode != null) {
                        childNode.mountOn(jockey);
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

        if (isComplicatedVariant(mob)) {
            List<String> details = getComplexVariantDetails(mob);
            addItemAndLore(
                Material.WRITABLE_BOOK,
                1,
                "Open " + formatEntityName(mob.getType().toString()) + " Variant Menu",
                slot,
                details.toArray(new String[0])
            );
            return;
        }

        if (hasSingleVariant(mob)) {
            Material variantItem = getVariantItem(mob);
            addItemAndLore(
                variantItem,
                1,
                "Edit " + formatEntityName(mob.getType().toString()) + " Variant",
                slot,
                "Current: §a" + getVariantName(mob)
            );
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
        if (mob instanceof Fox) return Material.SWEET_BERRIES;
        if (mob instanceof Frog) return Material.LILY_PAD;
        if (mob instanceof Cat) return Material.STRING;
        if (mob instanceof Parrot) return Material.COOKIE;
        if (mob instanceof Rabbit) return Material.CARROT;
        if (mob instanceof Axolotl) return Material.WATER_BUCKET;
        if (mob instanceof MushroomCow mooshroom) return Material.RED_MUSHROOM;
        if (mob instanceof Panda panda) return Material.BAMBOO;
        if (mob instanceof Villager || mob instanceof ZombieVillager) return Material.EMERALD;
        if (mob instanceof Sheep) return Material.WHITE_WOOL;
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
        if (mob instanceof Fox fox) {
            Fox.Type[] types = Fox.Type.values();
            fox.setFoxType(types[(fox.getFoxType().ordinal() + 1) % types.length]);
        } else if (mob instanceof Frog frog) {
            Frog.Variant[] variants = Frog.Variant.values();
            frog.setVariant(variants[(frog.getVariant().ordinal() + 1) % variants.length]);
        } else if (mob instanceof Cat cat) {
            Cat.Type[] types = Cat.Type.values();
            cat.setCatType(types[(cat.getCatType().ordinal() + 1) % types.length]);
        } else if (mob instanceof Parrot parrot) {
            Parrot.Variant[] variants = Parrot.Variant.values();
            parrot.setVariant(variants[(parrot.getVariant().ordinal() + 1) % variants.length]);
        } else if (mob instanceof Rabbit rabbit) {
            Rabbit.Type[] types = Rabbit.Type.values();
            rabbit.setRabbitType(types[(rabbit.getRabbitType().ordinal() + 1) % types.length]);
        } else if (mob instanceof Axolotl axolotl) {
            Axolotl.Variant[] variants = Axolotl.Variant.values();
            axolotl.setVariant(variants[(axolotl.getVariant().ordinal() + 1) % variants.length]);
        } else if (mob instanceof MushroomCow mooshroom) {
            MushroomCow.Variant[] variants = MushroomCow.Variant.values();
            mooshroom.setVariant(variants[(mooshroom.getVariant().ordinal() + 1) % variants.length]);
        } else if (mob instanceof Panda panda) {
            Panda.Gene[] genes = Panda.Gene.values();
            panda.setMainGene(genes[(panda.getMainGene().ordinal() + 1) % genes.length]);
        } else if (mob instanceof Villager villager) {
            Villager.Type[] types = Villager.Type.values();
            villager.setVillagerType(types[(villager.getVillagerType().ordinal() + 1) % types.length]);
        } else if (mob instanceof ZombieVillager zombie) {
            Villager.Type[] types = Villager.Type.values();
            zombie.setVillagerType(types[(zombie.getVillagerType().ordinal() + 1) % types.length]);
        } else if (mob instanceof Sheep sheep) {
            DyeColor[] colors = DyeColor.values();
            sheep.setColor(colors[(sheep.getColor().ordinal() + 1) % colors.length]);
        } else if (mob instanceof Wolf wolf) {
            DyeColor[] colors = DyeColor.values();
            wolf.setCollarColor(colors[(wolf.getCollarColor().ordinal() + 1) % colors.length]);
        }
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
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase().replace("_", " ");
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
} 