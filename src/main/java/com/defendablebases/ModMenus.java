package com.defendablebases;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;


public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, DefendableBases.MODID);

    public static final RegistryObject<MenuType<FortressCenterMenu>> FORTRESS_CENTER_MENU =
            MENUS.register("fortress_center_menu",
                    () -> IForgeMenuType.create(FortressCenterMenu::new));

    public static final RegistryObject<MenuType<FortressNodeMenu>> FORTRESS_NODE_MENU =
            MENUS.register("fortress_node_menu",
                    () -> IForgeMenuType.create(FortressNodeMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
