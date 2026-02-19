package com.defendablebases;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks ALL fortress block positions (center + wood + iron + diamond) per-dimension.
 *
 * IMPORTANT:
 * - We track positions only.
 * - TerritoryRules will resolve BlockEntity + type by reading the world at that pos.
 */
public final class FortressRegistry {
    private FortressRegistry() {}

    private static final ConcurrentHashMap<ResourceKey<Level>, Set<BlockPos>> FORTRESSES = new ConcurrentHashMap<>();

    private static Set<BlockPos> setFor(ResourceKey<Level> dim) {
        return FORTRESSES.computeIfAbsent(dim, d -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
    }

    public static void addFortress(ResourceKey<Level> dim, BlockPos pos) {
        if (dim == null || pos == null) return;
        setFor(dim).add(pos.immutable());
    }

    public static void removeFortress(ResourceKey<Level> dim, BlockPos pos) {
        if (dim == null || pos == null) return;
        Set<BlockPos> s = FORTRESSES.get(dim);
        if (s != null) s.remove(pos);
    }

    public static Set<BlockPos> getFortresses(ResourceKey<Level> dim) {
        if (dim == null) return Collections.emptySet();
        Set<BlockPos> s = FORTRESSES.get(dim);
        return (s != null) ? s : Collections.emptySet();
    }
}
