package org.nc.nccasino.components;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.inventory.LlamaInventory;
import org.bukkit.entity.Mob;
import org.bukkit.entity.TropicalFish;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.nc.nccasino.Nccasino;
import org.nc.nccasino.entities.Menu;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ComplexVariantMenu extends Menu {

    // For Llama color
    private static final Llama.Color[] LLAMA_COLORS = Llama.Color.values();

    // For Llama decoration: all carpet types, plus "none"
    private static final Material[] CARPETS_WITH_NONE = {
        null, // represents "no decor"
        Material.WHITE_CARPET,
        Material.ORANGE_CARPET,
        Material.MAGENTA_CARPET,
        Material.LIGHT_BLUE_CARPET,
        Material.YELLOW_CARPET,
        Material.LIME_CARPET,
        Material.PINK_CARPET,
        Material.GRAY_CARPET,
        Material.LIGHT_GRAY_CARPET,
        Material.CYAN_CARPET,
        Material.PURPLE_CARPET,
        Material.BLUE_CARPET,
        Material.BROWN_CARPET,
        Material.GREEN_CARPET,
        Material.RED_CARPET,
        Material.BLACK_CARPET
    };
    private static final Map<Llama.Color, Material> LLAMA_TERRACOTTA_MAP = new HashMap<>() {{
        put(Llama.Color.CREAMY, Material.TERRACOTTA);
        put(Llama.Color.WHITE, Material.WHITE_TERRACOTTA);
        put(Llama.Color.BROWN, Material.BROWN_TERRACOTTA);
        put(Llama.Color.GRAY, Material.GRAY_TERRACOTTA);
    }};
    
    // For Horse
    private static final Horse.Color[] HORSE_COLORS = Horse.Color.values();
    // Horse Color → Material Mapping
    private static final Map<Horse.Color, Material> HORSE_COLOR_MAP = new HashMap<>() {{
        put(Horse.Color.WHITE, Material.WHITE_CONCRETE);
        put(Horse.Color.CREAMY, Material.YELLOW_TERRACOTTA);
        put(Horse.Color.CHESTNUT, Material.TERRACOTTA);
        put(Horse.Color.BROWN, Material.BROWN_TERRACOTTA);
        put(Horse.Color.DARK_BROWN, Material.BLACK_TERRACOTTA);
        put(Horse.Color.BLACK, Material.BLACK_CONCRETE_POWDER);
        put(Horse.Color.GRAY, Material.GRAY_TERRACOTTA);
    }};

    private static final Horse.Style[] HORSE_STYLES = Horse.Style.values();
    // Horse Style → Material Mapping
    private static final Map<Horse.Style, Material> HORSE_STYLE_MAP = new HashMap<>() {{
        put(Horse.Style.NONE, Material.BARRIER);
        put(Horse.Style.WHITE, Material.WHITE_WOOL);
        put(Horse.Style.WHITEFIELD, Material.WHITE_GLAZED_TERRACOTTA);
        put(Horse.Style.WHITE_DOTS, Material.PUMPKIN_SEEDS);
        put(Horse.Style.BLACK_DOTS, Material.MELON_SEEDS);
    }};

        // Tropical Fish Pattern -> Glazed Terracotta Mapping
        private static final Map<TropicalFish.Pattern, Material> FISH_PATTERN_TERRACOTTA_MAP = new HashMap<>() {{
            put(TropicalFish.Pattern.KOB, Material.WHITE_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.SUNSTREAK, Material.RED_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.SNOOPER, Material.ORANGE_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.DASHER, Material.YELLOW_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.BRINELY, Material.LIME_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.SPOTTY, Material.GREEN_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.FLOPPER, Material.BLUE_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.STRIPEY, Material.PURPLE_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.GLITTER, Material.PINK_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.BLOCKFISH, Material.LIGHT_GRAY_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.BETTY, Material.GRAY_GLAZED_TERRACOTTA);
            put(TropicalFish.Pattern.CLAYFISH, Material.BLACK_GLAZED_TERRACOTTA);

        }};

        private static final TropicalFish.Pattern[] FISH_PATTERNS = {
            TropicalFish.Pattern.KOB,        // White
            TropicalFish.Pattern.SUNSTREAK,  // Red
            TropicalFish.Pattern.SNOOPER,    // Orange
            TropicalFish.Pattern.DASHER,     // Yellow
            TropicalFish.Pattern.BRINELY,    // Lime
            TropicalFish.Pattern.SPOTTY,     // Green
            TropicalFish.Pattern.FLOPPER,    // Blue
            TropicalFish.Pattern.STRIPEY,    // Purple
            TropicalFish.Pattern.GLITTER,    // Pink
            TropicalFish.Pattern.BLOCKFISH,  // Light Gray
            TropicalFish.Pattern.BETTY,      // Gray
            TropicalFish.Pattern.CLAYFISH    // Black
        };
        
    private static final DyeColor[] DYE_COLORS = DyeColor.values();
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
    private final Mob dealer;

    public ComplexVariantMenu(
        org.bukkit.entity.Player player,
        Nccasino plugin,
        UUID dealerId,
        String returnMessage,
        Consumer<org.bukkit.entity.Player> returnCallback,
        Mob dealer
    ) {
        // 9-slot menu for simplicity, same style as TestMenu
        super(player, plugin, dealerId, "Complex Variant Menu", 9, returnMessage, returnCallback);

        // Grab the actual dealer instance
        this.dealer = dealer;
        // Let’s define the slots we’ll use. In your SlotOption enum, you might not
        // have a perfect name for each, so we’ll re-use the *TEST_OPTION_* placeholders.
        // Or you can add new ones if you prefer.
        slotMapping.put(SlotOption.COMPLEX_VAR_1, 1);   // Llama Color / Horse Color / Fish Pattern
        slotMapping.put(SlotOption.COMPLEX_VAR_2, 2);   // Llama Decor / Horse Style / Fish BodyColor
        slotMapping.put(SlotOption.COMPLEX_VAR_3, 3); // Possibly used only for Fish PatternColor

        // Provide Exit & Return in same style as your TestMenu
        addExitReturn();

        // Build items
        initializeMenu();
    }

    @Override
    protected void initializeMenu() {
        // Clear everything out so we can rebuild each time
        inventory.clear();

        if (dealer instanceof Llama llama) {
            // 1) Llama Color
            addItemAndLore(
                LLAMA_TERRACOTTA_MAP.get(llama.getColor()),
                1,
                "Cycle Llama Color",
                slotMapping.get(SlotOption.COMPLEX_VAR_1),
                "Current: §a" + formatName(llama.getColor().name())
            );

            // 2) Llama Decor (the “towel”)
            // Check which carpet we have in the Llama’s inventory
            Material currentCarpet = getLlamaCarpet((LlamaInventory) llama.getInventory());
            String carpetName = currentCarpet == null ? "None" : formatName(currentCarpet.name().replace("_CARPET", ""));
            addItemAndLore(
                currentCarpet != null ? currentCarpet : Material.BARRIER,
                1,
                "Cycle Llama Decor",
                slotMapping.get(SlotOption.COMPLEX_VAR_2),
                "Current: §a" + carpetName
            );
        }
        else if (dealer instanceof Horse horse) {
            // 1) Horse Color
            addItemAndLore(
                HORSE_COLOR_MAP.get(horse.getColor()),
                1,
                "Cycle Horse Color",
                slotMapping.get(SlotOption.COMPLEX_VAR_1),
                "Current: §a" + formatName(horse.getColor().name())
            );

            // 2) Horse Style
            addItemAndLore(
                HORSE_STYLE_MAP.get(horse.getStyle()),
                1,
                "Cycle Horse Style",
                slotMapping.get(SlotOption.COMPLEX_VAR_2),
                "Current: §a" + formatName(horse.getStyle().name())
            );
        }
        else if (dealer instanceof TropicalFish fish) {
            // 1) Fish Pattern
            addItemAndLore(
                FISH_PATTERN_TERRACOTTA_MAP.get(fish.getPattern()),
                1,
                "Cycle Fish Pattern",
                slotMapping.get(SlotOption.COMPLEX_VAR_1),
                "Current: §a" + formatName(fish.getPattern().name())
            );
            // 2) Fish Body Color
            addItemAndLore(
                DYE_MATERIAL_MAP.get(fish.getBodyColor()),
                1,
                "Cycle Body Color",
                slotMapping.get(SlotOption.COMPLEX_VAR_2),
                "Current: §a" + formatName(fish.getBodyColor().name())
            );
            // 3) Fish Pattern Color
            addItemAndLore(
                DYE_MATERIAL_MAP.get(fish.getPatternColor()),
                1,
                "Cycle Pattern Color",
                slotMapping.get(SlotOption.COMPLEX_VAR_3),
                "Current: §a" + formatName(fish.getPatternColor().name())
            );
        }
        else {
            // If it's not one of those complicated mobs, show “no variants” in slot #2
            addItemAndLore(
                Material.BARRIER,
                1,
                "No Complex Variants",
                slotMapping.get(SlotOption.COMPLEX_VAR_1),
                ChatColor.RED + "Mob is not Llama, Horse, or TropicalFish."
            );
        }
        addExitReturn();

    }

    @Override
    protected void handleCustomClick(SlotOption option, org.bukkit.entity.Player player, InventoryClickEvent event) {
        // Llama
        if (dealer instanceof Llama llama) {
            if (option == SlotOption.COMPLEX_VAR_1) {
                cycleLlamaColor(player, llama);
            }
            else if (option == SlotOption.COMPLEX_VAR_2) {
                cycleLlamaDecor(player, llama);
            }
        }
        // Horse
        else if (dealer instanceof Horse horse) {
            if (option == SlotOption.COMPLEX_VAR_1) {
                cycleHorseColor(player, horse);
            }
            else if (option == SlotOption.COMPLEX_VAR_2) {
                cycleHorseStyle(player, horse);
            }
        }
        // TropicalFish
        else if (dealer instanceof TropicalFish fish) {
            if (option == SlotOption.COMPLEX_VAR_1) {
                cycleFishPattern(player, fish);
            }
            else if (option == SlotOption.COMPLEX_VAR_2) {
                cycleFishBodyColor(player, fish);
            }
            else if (option == SlotOption.COMPLEX_VAR_3) {
                cycleFishPatternColor(player, fish);
            }
        }
        // Catch-all
        else {
            player.sendMessage(ChatColor.RED + "No complex variants for this mob.");
        }

        // Refresh menu to update “Current: ...” text
        initializeMenu();
        // Optional: play a click sound
        playDefaultSound(player);
    }

    // ------------------------------------------------------------------------
    // Llama logic
    // ------------------------------------------------------------------------
    private void cycleLlamaColor(org.bukkit.entity.Player player, Llama llama) {
        Llama.Color current = llama.getColor();
        int idx = indexOf(LLAMA_COLORS, current);
        int next = (idx + 1) % LLAMA_COLORS.length;
        llama.setColor(LLAMA_COLORS[next]);
        sendChangeMessage(player, "Llama color changed" , LLAMA_COLORS[next].name());
    }

    private void cycleLlamaDecor(org.bukkit.entity.Player player, Llama llama) {
        // Figure out the Llama’s current carpet
        LlamaInventory inv = (LlamaInventory) llama.getInventory();
        Material currentCarpet = getLlamaCarpet(inv);

        // Find which index we are in CARPETS_WITH_NONE
        int idx = indexOf(CARPETS_WITH_NONE, currentCarpet);
        int next = (idx + 1) % CARPETS_WITH_NONE.length;
        Material newCarpet = CARPETS_WITH_NONE[next];

        // If newCarpet is null, that means “no decor,” so setDecor(null).
        if (newCarpet == null) {
            inv.setDecor(null);
            sendChangeMessage(player, "Llama decor changed","empty");
        } else {
            // Place that carpet in the “decor” slot
            inv.setDecor(new ItemStack(newCarpet));
            sendChangeMessage(player, "Llama decor changed" , newCarpet.name());
        }
    }

    private Material getLlamaCarpet(LlamaInventory llamaInv) {
        // The “decor” item is stored as “.getDecor()”
        ItemStack decor = llamaInv.getDecor();
        if (decor == null) return null;
        return decor.getType();
    }

    // ------------------------------------------------------------------------
    // Horse logic
    // ------------------------------------------------------------------------
    private void cycleHorseColor(org.bukkit.entity.Player player, Horse horse) {
        Horse.Color current = horse.getColor();
        int idx = indexOf(HORSE_COLORS, current);
        int next = (idx + 1) % HORSE_COLORS.length;
        horse.setColor(HORSE_COLORS[next]);
        sendChangeMessage(player, "Horse color changed" , HORSE_COLORS[next].name());
    }

    private void cycleHorseStyle(org.bukkit.entity.Player player, Horse horse) {
        Horse.Style current = horse.getStyle();
        int idx = indexOf(HORSE_STYLES, current);
        int next = (idx + 1) % HORSE_STYLES.length;
        horse.setStyle(HORSE_STYLES[next]);
        sendChangeMessage(player, "Horse style changed" , HORSE_STYLES[next].name());
    }

    // ------------------------------------------------------------------------
    // Tropical Fish logic
    // ------------------------------------------------------------------------
    private void cycleFishPattern(org.bukkit.entity.Player player, TropicalFish fish) {
        TropicalFish.Pattern current = fish.getPattern();
        int idx = indexOf(FISH_PATTERNS, current);
        int next = (idx + 1) % FISH_PATTERNS.length;
        fish.setPattern(FISH_PATTERNS[next]);
        sendChangeMessage(player, "Fish pattern changed" , FISH_PATTERNS[next].name());
    }

    private void cycleFishBodyColor(org.bukkit.entity.Player player, TropicalFish fish) {
        DyeColor current = fish.getBodyColor();
        int idx = indexOf(DYE_COLORS, current);
        int next = (idx + 1) % DYE_COLORS.length;
        fish.setBodyColor(DYE_COLORS[next]);
        sendChangeMessage(player, "Body color changed" , DYE_COLORS[next].name());
    }

    private void cycleFishPatternColor(org.bukkit.entity.Player player, TropicalFish fish) {
        DyeColor current = fish.getPatternColor();
        int idx = indexOf(DYE_COLORS, current);
        int next = (idx + 1) % DYE_COLORS.length;
        fish.setPatternColor(DYE_COLORS[next]);
        sendChangeMessage(player, "Pattern color changed" ,DYE_COLORS[next].name());
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------
    private void sendChangeMessage(org.bukkit.entity.Player player, String info,String verb) {
        switch(plugin.getPreferences(player.getUniqueId()).getMessageSetting()){
            case STANDARD:{
                player.sendMessage(ChatColor.GREEN + info+".");
                break;}
            case VERBOSE:{
                player.sendMessage(ChatColor.GREEN + info+" to "+ChatColor.YELLOW+formatName(verb)+ChatColor.GREEN+".");
                break;}
            case NONE:{break;
            }
        }
    }

    private String formatName(String raw) {
        if (raw == null) return "None";
        return Arrays.stream(raw.toLowerCase().split("_"))
                     .map(str -> str.substring(0,1).toUpperCase() + str.substring(1))
                     .collect(Collectors.joining(" "));
    }

    private static <E> int indexOf(E[] arr, E value) {
        for (int i = 0; i < arr.length; i++) {
            if ((arr[i] == null && value == null)
                || (arr[i] != null && arr[i].equals(value))) {
                return i;
            }
        }
        return 0; // fallback
    }
}
