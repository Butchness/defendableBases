package com.defendablebases;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

public enum FortressTier {

    // Tier Definitions (enum constants MUST come first)
    WOOD(Health.MAX_HEALTH_WOOD, 1, 1),       // cubeHalf=1 (3x3x3), slots=1
    IRON(Health.MAX_HEALTH_IRON, 2, 1),       // cubeHalf=2 (5x5x5), slots=1
    DIAMOND(Health.MAX_HEALTH_DIAMOND, 3, 2); // cubeHalf=3 (7x7x7), slots=2

    // ============================
    // EASY TUNABLE MAX HEALTH VALUES
    // (kept "at the top" via nested class)
    // ============================
    public static final class Health {
        private Health() {}

        public static final int MAX_HEALTH_WOOD    = 16;
        public static final int MAX_HEALTH_IRON    = 32;
        public static final int MAX_HEALTH_DIAMOND = 64;
    }

    public final int maxHealth;
    public final int cubeHalfSize;
    public final int slotCount;

    FortressTier(int maxHealth, int cubeHalfSize, int slotCount) {
        this.maxHealth = maxHealth;
        this.cubeHalfSize = cubeHalfSize;
        this.slotCount = slotCount;
    }

    // Cheap-to-expensive preference
    public static final List<Item> REPAIR_PRIORITY = List.of(
            Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS,
            Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS, Items.CRIMSON_PLANKS, Items.WARPED_PLANKS,

            Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG,
            Items.MANGROVE_LOG, Items.CHERRY_LOG,
            Items.CRIMSON_STEM, Items.WARPED_STEM,

            Items.IRON_INGOT,
            Items.DIAMOND,
            Items.EMERALD,
            Items.NETHERITE_INGOT
    );

    public static int healValue(Item item) {
        if (item == null) return 0;

        // planks
        if (item == Items.OAK_PLANKS || item == Items.SPRUCE_PLANKS || item == Items.BIRCH_PLANKS ||
                item == Items.JUNGLE_PLANKS || item == Items.ACACIA_PLANKS || item == Items.DARK_OAK_PLANKS ||
                item == Items.MANGROVE_PLANKS || item == Items.CHERRY_PLANKS || item == Items.BAMBOO_PLANKS ||
                item == Items.CRIMSON_PLANKS || item == Items.WARPED_PLANKS) return 1;

        // logs/stems
        if (item == Items.OAK_LOG || item == Items.SPRUCE_LOG || item == Items.BIRCH_LOG ||
                item == Items.JUNGLE_LOG || item == Items.ACACIA_LOG || item == Items.DARK_OAK_LOG ||
                item == Items.MANGROVE_LOG || item == Items.CHERRY_LOG ||
                item == Items.CRIMSON_STEM || item == Items.WARPED_STEM) return 2;

        if (item == Items.IRON_INGOT) return 5;
        if (item == Items.DIAMOND) return 9;
        if (item == Items.EMERALD) return 12;
        if (item == Items.NETHERITE_INGOT) return 15;

        return 0;
    }
}
