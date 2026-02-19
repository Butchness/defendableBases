package com.defendablebases.client;

import com.defendablebases.DefendableBases;
import com.defendablebases.ModMenus;
import com.defendablebases.client.screen.FortressNodeScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = DefendableBases.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            //register our menus
            MenuScreens.register(ModMenus.FORTRESS_CENTER_MENU.get(), FortressCenterScreen::new);
            MenuScreens.register(ModMenus.FORTRESS_NODE_MENU.get(), com.defendablebases.client.screen.FortressNodeScreen::new);
        });
    }
}
