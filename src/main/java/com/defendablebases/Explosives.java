package com.defendablebases;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Explosives {
    private Explosives() {}

    /** NBT key stored on the explosion's source entity (e.g., your primed explosive entity). */
    public static final String NBT_DAMAGE_PER_BLOCK = "defendablebases_fortress_dpb";

    /** Optional datapack tag for "explosives" blocks (placement exception). */
    public static final TagKey<Block> EXPLOSIVES_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(DefendableBases.MODID, "explosives"));

    /**
     * Code-registered defaults for YOUR blocks (handy if you want code defaults + datapack overrides).
     * Datapack can still override behavior by stamping entities or via entity mappings.
     */
    private static final Map<Block, Float> CODE_DEFAULT_DPB = new ConcurrentHashMap<>();

    public static void register(Block block, float damagePerBlock) {
        if (block == null) return;
        CODE_DEFAULT_DPB.put(block, Math.max(0f, damagePerBlock));
    }

    /**
     * Placement exception: allow TNT + anything in explosives tag + anything listed in datapack (blocks)
     * + anything code-registered.
     */
    public static boolean isExplosivePlaceable(Block block) {
        if (block == null) return false;
        if (block == Blocks.TNT) return true;
        if (CODE_DEFAULT_DPB.containsKey(block)) return true;
        if (ExplosivesData.hasBlockEntry(block)) return true;
        // Tag membership is via BlockState, so handled in isExplosiveState()
        return false;
    }

    public static boolean isExplosiveState(BlockState state) {
        if (state == null) return false;
        Block b = state.getBlock();
        if (b == Blocks.TNT) return true;
        if (CODE_DEFAULT_DPB.containsKey(b)) return true;
        if (ExplosivesData.hasBlockEntry(b)) return true;
        return state.is(EXPLOSIVES_TAG);
    }

    /** Stamp the exploding entity with a DPB value. */
    public static void stampExploderEntity(Entity exploder, float damagePerBlock) {
        if (exploder == null) return;
        exploder.getPersistentData().putFloat(NBT_DAMAGE_PER_BLOCK, Math.max(0f, damagePerBlock));
    }

    /**
     * Determine DPB for an explosion:
     * 1) If exploder entity has stamped NBT -> use it
     * 2) Else if datapack provides entity-type mapping -> use it
     * 3) Else default to 1.0 (vanilla)
     */
    public static float getDamagePerProtectedBlock(Explosion explosion) {
        if (explosion == null) return 1f;

        Entity e = explosion.getExploder();
        if (e != null) {
            if (e.getPersistentData().contains(NBT_DAMAGE_PER_BLOCK)) {
                float v = e.getPersistentData().getFloat(NBT_DAMAGE_PER_BLOCK);
                return (v > 0f) ? v : 0f;
            }

            // entity-type fallback from datapack
            ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            Float mapped = ExplosivesData.getEntityDpb(typeId);
            if (mapped != null) return mapped;
        }

        return 1f;
    }

    /**
     * For your own priming code, if you know a source block:
     * datapack block mapping wins, then code mapping, else 1.0.
     */
    public static float getDefaultDamagePerBlockFor(Block sourceBlock) {
        if (sourceBlock == null) return 1f;

        Float pack = ExplosivesData.getBlockDpb(sourceBlock);
        if (pack != null) return pack;

        Float code = CODE_DEFAULT_DPB.get(sourceBlock);
        if (code != null) return code;

        return 1f;
    }
}
