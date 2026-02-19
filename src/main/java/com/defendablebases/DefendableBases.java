package com.defendablebases;

import com.defendablebases.network.ModNetworking;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(DefendableBases.MODID)
@SuppressWarnings("removal")
public class DefendableBases {
    public static final String MODID = "defendablebases";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DefendableBases() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register ALL registries here (order doesn't matter much, but do it early)
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
        ModNetworking.register();


        // Optional: keep your config
        FMLJavaModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("DefendableBases common setup");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.FORTRESS_WOOD_ITEM.get());
            event.accept(ModBlocks.FORTRESS_IRON_ITEM.get());
            event.accept(ModBlocks.FORTRESS_DIAMOND_ITEM.get());
            event.accept(ModBlocks.FORTRESS_CENTER_ITEM.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("DefendableBases server starting");
    }
}
