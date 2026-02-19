package com.defendablebases;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, DefendableBases.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, DefendableBases.MODID);

    // Tier fortress blocks use FortressBlock (NOT FortressCenterBlock)
    public static final RegistryObject<Block> FORTRESS_WOOD =
            BLOCKS.register("fortress_wood",
                    () -> new FortressBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.25f)));

    public static final RegistryObject<Block> FORTRESS_IRON =
            BLOCKS.register("fortress_iron",
                    () -> new FortressBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.25f)));

    public static final RegistryObject<Block> FORTRESS_DIAMOND =
            BLOCKS.register("fortress_diamond",
                    () -> new FortressBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).strength(0.25f)));

    // Center remains FortressCenterBlock (opens UI)
    public static final RegistryObject<Block> FORTRESS_CENTER =
            BLOCKS.register("fortress_center",
                    () -> new FortressCenterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(6.0f)));

    // BlockItems
    public static final RegistryObject<Item> FORTRESS_WOOD_ITEM =
            ITEMS.register("fortress_wood", () -> new BlockItem(FORTRESS_WOOD.get(), new Item.Properties()));

    public static final RegistryObject<Item> FORTRESS_IRON_ITEM =
            ITEMS.register("fortress_iron", () -> new BlockItem(FORTRESS_IRON.get(), new Item.Properties()));

    public static final RegistryObject<Item> FORTRESS_DIAMOND_ITEM =
            ITEMS.register("fortress_diamond", () -> new BlockItem(FORTRESS_DIAMOND.get(), new Item.Properties()));

    public static final RegistryObject<Item> FORTRESS_CENTER_ITEM =
            ITEMS.register("fortress_center", () -> new BlockItem(FORTRESS_CENTER.get(), new Item.Properties()));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
