package com.defendablebases;

import net.minecraft.world.level.block.Block;

public final class FortressRadii {
    private FortressRadii() {}

    // Territory/use/build radius (XZ)
    public static final int TERRITORY_WOOD    = 16;
    public static final int TERRITORY_CENTER  = 16; // uses Config.fortressRadius today; set this if you want it fixed too
    public static final int TERRITORY_IRON    = 16;
    public static final int TERRITORY_DIAMOND = 16;

    // Break-protection radius (XZ) - your fixed values
    public static final int BREAK_WOOD    = 1;
    public static final int BREAK_CENTER  = 3;
    public static final int BREAK_IRON    = 3;
    public static final int BREAK_DIAMOND = 6;

    /** Fixed break radius by block type. */
    public static int breakRadiusFor(Block block) {
        if (block == ModBlocks.FORTRESS_WOOD.get())    return BREAK_WOOD;
        if (block == ModBlocks.FORTRESS_CENTER.get())  return BREAK_CENTER;
        if (block == ModBlocks.FORTRESS_IRON.get())    return BREAK_IRON;
        if (block == ModBlocks.FORTRESS_DIAMOND.get()) return BREAK_DIAMOND;
        return 0;
    }

    /** Fixed territory radius by block type (optional if you want fixed territory too). */
    public static int territoryRadiusFor(Block block) {
        if (block == ModBlocks.FORTRESS_WOOD.get())    return TERRITORY_WOOD;
        if (block == ModBlocks.FORTRESS_CENTER.get())  return TERRITORY_CENTER;
        if (block == ModBlocks.FORTRESS_IRON.get())    return TERRITORY_IRON;
        if (block == ModBlocks.FORTRESS_DIAMOND.get()) return TERRITORY_DIAMOND;
        return 0;
    }
}
