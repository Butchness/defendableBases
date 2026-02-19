package com.defendablebases;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ExplosivesData {
    private ExplosivesData() {}

    // Loaded from datapack JSON on reload
    private static final Map<ResourceLocation, Float> BLOCK_DPB = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Float> ENTITY_DPB = new ConcurrentHashMap<>();

    public static void clear() {
        BLOCK_DPB.clear();
        ENTITY_DPB.clear();
    }

    public static void putBlock(ResourceLocation id, float dpb) {
        if (id == null) return;
        BLOCK_DPB.put(id, Math.max(0f, dpb));
    }

    public static void putEntity(ResourceLocation id, float dpb) {
        if (id == null) return;
        ENTITY_DPB.put(id, Math.max(0f, dpb));
    }

    public static Float getBlockDpb(Block b) {
        if (b == null) return null;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        if (id == null) return null;
        return BLOCK_DPB.get(id);
    }

    public static Float getEntityDpb(ResourceLocation entityTypeId) {
        if (entityTypeId == null) return null;
        return ENTITY_DPB.get(entityTypeId);
    }

    public static boolean hasBlockEntry(Block b) {
        return getBlockDpb(b) != null;
    }
}
