package com.extantape.onevoid.meter;

import java.util.Locale;
import java.util.Map;

/**
 * What one item is worth to the void, from its material alone. Gear is worth its tier's material times the
 * number of pieces it takes to make, so a diamond chestplate (8 diamonds) is worth 56 and a stone sword
 * almost nothing. Anything not listed falls back to its rarity, which keeps common blocks near zero.
 * Pure logic, without a server, so it can be unit-tested.
 */
public final class ValueTable {
    private ValueTable() {}

    /** Material units a piece of gear takes to make. */
    private static final Map<String, Double> PIECES = Map.ofEntries(
            Map.entry("SWORD", 2.0), Map.entry("PICKAXE", 3.0), Map.entry("AXE", 3.0),
            Map.entry("SHOVEL", 1.0), Map.entry("HOE", 2.0), Map.entry("SPEAR", 1.0),
            Map.entry("HELMET", 5.0), Map.entry("CHESTPLATE", 8.0), Map.entry("LEGGINGS", 7.0),
            Map.entry("BOOTS", 4.0), Map.entry("HORSE_ARMOR", 6.0), Map.entry("NAUTILUS_ARMOR", 6.0));

    /** What one unit of each tier's material is worth. Netherite is diamond plus an ingot, handled below. */
    private static final Map<String, Double> TIERS = Map.ofEntries(
            Map.entry("WOODEN", 0.1), Map.entry("STONE", 0.1), Map.entry("LEATHER", 0.5),
            Map.entry("COPPER", 0.5), Map.entry("GOLDEN", 1.5), Map.entry("CHAINMAIL", 1.5),
            Map.entry("IRON", 2.0), Map.entry("DIAMOND", 7.0));

    private static final double NETHERITE_INGOT = 40.0;

    private static final Map<String, Double> SPECIAL = Map.ofEntries(
            Map.entry("DIAMOND", 7.0), Map.entry("DIAMOND_BLOCK", 63.0),
            Map.entry("EMERALD", 3.0), Map.entry("EMERALD_BLOCK", 27.0),
            Map.entry("NETHERITE_INGOT", NETHERITE_INGOT), Map.entry("NETHERITE_BLOCK", NETHERITE_INGOT * 9),
            Map.entry("NETHERITE_SCRAP", 10.0), Map.entry("ANCIENT_DEBRIS", 10.0),
            Map.entry("IRON_INGOT", 2.0), Map.entry("IRON_BLOCK", 18.0), Map.entry("RAW_IRON", 1.5),
            Map.entry("RAW_IRON_BLOCK", 13.5), Map.entry("GOLD_INGOT", 1.5), Map.entry("GOLD_BLOCK", 13.5),
            Map.entry("RAW_GOLD", 1.0), Map.entry("RAW_GOLD_BLOCK", 9.0),
            Map.entry("COPPER_INGOT", 0.3), Map.entry("LAPIS_LAZULI", 0.3), Map.entry("AMETHYST_SHARD", 0.3),
            Map.entry("ENDER_PEARL", 1.0), Map.entry("BLAZE_ROD", 1.0), Map.entry("EXPERIENCE_BOTTLE", 1.0),
            Map.entry("ECHO_SHARD", 5.0), Map.entry("SHULKER_SHELL", 6.0), Map.entry("SHULKER_BOX", 12.0),
            Map.entry("GOLDEN_APPLE", 6.0), Map.entry("ENCHANTED_GOLDEN_APPLE", 40.0),
            Map.entry("TOTEM_OF_UNDYING", 30.0), Map.entry("ELYTRA", 60.0), Map.entry("TRIDENT", 30.0),
            Map.entry("MACE", 40.0), Map.entry("HEAVY_CORE", 30.0), Map.entry("NETHER_STAR", 50.0),
            Map.entry("BEACON", 50.0), Map.entry("DRAGON_EGG", 80.0), Map.entry("TURTLE_HELMET", 10.0),
            Map.entry("BOW", 2.0), Map.entry("CROSSBOW", 3.0), Map.entry("SHIELD", 3.0),
            Map.entry("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 20.0),
            // An enchanted book is worth its enchantments only.
            Map.entry("ENCHANTED_BOOK", 0.0), Map.entry("BOOK", 0.05));

    private static final Map<String, Double> RARITY = Map.of(
            "COMMON", 0.02, "UNCOMMON", 1.0, "RARE", 5.0, "EPIC", 15.0);

    /**
     * Worth of one item of this material before enchantments, durability and repeats.
     * @param material the material's name, such as {@code DIAMOND_SWORD}
     * @param rarity   the item's rarity name, used only when the material isn't known here
     */
    public static double base(String material, String rarity) {
        String m = material.toUpperCase(Locale.ROOT);
        Double special = SPECIAL.get(m);
        if (special != null) return special;
        if (m.endsWith("_SHULKER_BOX")) return SPECIAL.get("SHULKER_BOX");
        if (m.startsWith("MUSIC_DISC_")) return 3.0;
        if (m.endsWith("_SMITHING_TEMPLATE")) return 5.0;
        double gear = gear(m);
        if (gear >= 0) return gear;
        return RARITY.getOrDefault(rarity == null ? "COMMON" : rarity.toUpperCase(Locale.ROOT), 0.02);
    }

    /** True for weapons and armour, the items "the void gives back" can return. */
    public static boolean isGear(String material) {
        String m = material.toUpperCase(Locale.ROOT);
        return gear(m) >= 0 || m.equals("TRIDENT") || m.equals("MACE") || m.equals("BOW")
                || m.equals("CROSSBOW") || m.equals("SHIELD") || m.equals("TURTLE_HELMET") || m.equals("ELYTRA");
    }

    private static double gear(String m) {
        int split = m.indexOf('_');
        if (split < 0) return -1;
        String tier = m.substring(0, split);
        Double pieces = PIECES.get(m.substring(split + 1));
        if (pieces == null) return -1;
        if (tier.equals("NETHERITE")) return pieces * TIERS.get("DIAMOND") + NETHERITE_INGOT;
        Double unit = TIERS.get(tier);
        return unit == null ? -1 : pieces * unit;
    }

    /** Worth of the enchantments on an item: 2 a level, 4 for treasure ones like Mending, nothing for curses. */
    public static double enchantment(int level, boolean treasure, boolean curse) {
        if (curse) return 0;
        return level * (treasure ? 4.0 : 2.0);
    }

    /** How much of an item's worth is left after wear. */
    public static double durabilityFactor(int damage, int maxDamage, double floor) {
        if (maxDamage <= 0) return 1.0;
        double left = 1.0 - Math.min(damage, maxDamage) / (double) maxDamage;
        return Math.max(floor, left);
    }

    /**
     * Worth of {@code count} items, each worth {@code unit}, when {@code alreadyToday} of the same type were
     * given earlier today. Each repeat is worth {@code factor} of the one before.
     */
    public static double repeated(double unit, int count, int alreadyToday, double factor) {
        if (count <= 0 || unit <= 0) return 0;
        if (factor >= 1.0) return unit * count;
        double first = Math.pow(factor, alreadyToday);
        return unit * first * (1 - Math.pow(factor, count)) / (1 - factor);
    }
}
