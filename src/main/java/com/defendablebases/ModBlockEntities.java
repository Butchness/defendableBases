package com.defendablebases;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, DefendableBases.MODID);

    // Center BE ONLY for center block
    public static final RegistryObject<BlockEntityType<FortressCenterBlockEntity>> FORTRESS_CENTER =
            BLOCK_ENTITIES.register("fortress_center",
                    () -> BlockEntityType.Builder.of(
                            FortressCenterBlockEntity::new,
                            ModBlocks.FORTRESS_CENTER.get()
                    ).build(null));

    // Tier BE for wood/iron/diamond ONLY
    public static final RegistryObject<BlockEntityType<FortressBlockEntity>> FORTRESS_BLOCK =
            BLOCK_ENTITIES.register("fortress_block",
                    () -> BlockEntityType.Builder.of(
                            FortressBlockEntity::new,
                            ModBlocks.FORTRESS_WOOD.get(),
                            ModBlocks.FORTRESS_IRON.get(),
                            ModBlocks.FORTRESS_DIAMOND.get()
                    ).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
