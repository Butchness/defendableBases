package com.defendablebases;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = DefendableBases.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue MAX_PRIVILEGED = BUILDER
            .comment("Maximum number of players that may be privileged on a single Fortress Center")
            .defineInRange("maxPrivileged", 8, 1, 256);

    // Territory (use/build/interaction) radius (XZ distance)
    private static final ForgeConfigSpec.IntValue FORTRESS_TERRITORY_RADIUS = BUILDER
            .comment("Territory radius in blocks around each claimed fortress (affects placement + interactables). XZ distance.")
            .defineInRange("fortressRadius", 32, 0, 512);

    private static final ForgeConfigSpec.BooleanValue OWNER_ONLY_MODIFY = BUILDER
            .comment("If true, only the owner may add/remove privileged players (future-proof toggle).")
            .define("ownerOnlyModify", false);

    // ============================
    // Decay mechanic
    // ============================

    private static final ForgeConfigSpec.BooleanValue DECAY_ENABLED = BUILDER
            .comment("If true, claimed fortress centers apply hourly decay costs (center pays first; if insufficient, nodes pay).")
            .define("decayEnabled", true);

    private static final ForgeConfigSpec.IntValue DECAY_INTERVAL_MINUTES = BUILDER
            .comment("How often decay runs, in minutes (default 60 = 1 hour).")
            .defineInRange("decayIntervalMinutes", 60, 1, 1440);

    private static final ForgeConfigSpec.DoubleValue DECAY_COEFF = BUILDER
            .comment("Decay cost coefficient. Cost = ceil(exp(protectedBlocks * coeff)). Default matches your spec (0.0021).")
            .defineInRange("decayCoeff", 0.0021D, 0.0000001D, 0.1D);

    private static final ForgeConfigSpec.IntValue DECAY_CATCHUP_MAX_TICKS = BUILDER
            .comment("Max number of decay 'catch-up' iterations if the server fell behind (prevents long spikes).")
            .defineInRange("decayCatchUpMaxIterations", 6, 0, 256);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // Loaded values
    public static int maxPrivileged = 8;
    public static int fortressTerritoryRadius = 32;
    public static boolean ownerOnlyModify = false;

    public static boolean decayEnabled = true;
    public static int decayIntervalMinutes = 60;
    public static double decayCoeff = 0.0021D;
    public static int decayCatchUpMaxIterations = 6;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        maxPrivileged = MAX_PRIVILEGED.get();
        fortressTerritoryRadius = FORTRESS_TERRITORY_RADIUS.get();
        ownerOnlyModify = OWNER_ONLY_MODIFY.get();

        decayEnabled = DECAY_ENABLED.get();
        decayIntervalMinutes = DECAY_INTERVAL_MINUTES.get();
        decayCoeff = DECAY_COEFF.get();
        decayCatchUpMaxIterations = DECAY_CATCHUP_MAX_TICKS.get();
    }
}
