package com.defendablebases.client;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PrivilegeClientCache {
    private PrivilegeClientCache() {}

    private static final Map<BlockPos, Set<UUID>> CACHE = new HashMap<>();

    public static void set(BlockPos pos, Set<UUID> uuids) {
        CACHE.put(pos, new HashSet<>(uuids));
    }

    public static Set<UUID> get(BlockPos pos) {
        return CACHE.getOrDefault(pos, Collections.emptySet());
    }
}
