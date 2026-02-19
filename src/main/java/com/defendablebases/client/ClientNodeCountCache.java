package com.defendablebases.client;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientNodeCountCache {
    private ClientNodeCountCache() {}

    private static final Map<Long, Integer> NODE_COUNTS = new ConcurrentHashMap<>();

    public static void set(BlockPos pos, int count) {
        if (pos == null) return;
        NODE_COUNTS.put(pos.asLong(), Math.max(0, count));
    }

    public static int get(BlockPos pos) {
        if (pos == null) return 0;
        return NODE_COUNTS.getOrDefault(pos.asLong(), 0);
    }

    public static void clear(BlockPos pos) {
        if (pos == null) return;
        NODE_COUNTS.remove(pos.asLong());
    }
}
