package com.defendablebases;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class TerritoryRules {
    private TerritoryRules() {}

    public static final int BREAK_RADIUS_WOOD    = 1; // 3x3x3
    public static final int BREAK_RADIUS_IRON    = 2; // 5x5x5
    public static final int BREAK_RADIUS_DIAMOND = 4; // 7x7x7
    public static final int BREAK_RADIUS_CENTER  = 2; // 5x5x5

    public enum FortressKind { WOOD, CENTER, IRON, DIAMOND }

    public static FortressKind getKindFromBlock(Block b) {
        if (b == ModBlocks.FORTRESS_WOOD.get()) return FortressKind.WOOD;
        if (b == ModBlocks.FORTRESS_CENTER.get()) return FortressKind.CENTER;
        if (b == ModBlocks.FORTRESS_IRON.get()) return FortressKind.IRON;
        if (b == ModBlocks.FORTRESS_DIAMOND.get()) return FortressKind.DIAMOND;
        return null;
    }

    /** Fixed protection/break cube half-size by fortress type (NOT configurable). */
    @SuppressWarnings("unused")
    public static int getBreakRadiusBlocksFor(FortressCenterBlockEntity fc) {
        if (fc == null) return 0;
        FortressKind k = getKindFromBlock(fc.getBlockState().getBlock());
        return getBreakRadiusBlocksForKind(k);
    }

    /** Same radii, but for a tier node BE. */
    public static int getBreakRadiusBlocksForNode(FortressBlockEntity node) {
        if (node == null) return 0;
        FortressKind k = getKindFromBlock(node.getBlockState().getBlock());
        return getBreakRadiusBlocksForKind(k);
    }

    /** Convenience method (does not change counting logic). */
    public static int getBreakRadiusBlocksForKind(FortressKind k) {
        if (k == null) return 0;
        return switch (k) {
            case WOOD -> BREAK_RADIUS_WOOD;
            case IRON -> BREAK_RADIUS_IRON;
            case DIAMOND -> BREAK_RADIUS_DIAMOND;
            case CENTER -> BREAK_RADIUS_CENTER;
        };
    }

    private static boolean isCenterBlock(FortressCenterBlockEntity fc) {
        return fc != null && fc.getBlockState().getBlock() == ModBlocks.FORTRESS_CENTER.get();
    }

    // ============================================================
    // Chunk safety helpers (avoid deprecated hasChunkAt)
    // ============================================================

    /** Returns true if the chunk containing this block position is already loaded (does NOT force-load). */
    private static boolean isChunkLoaded(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    // ============================================================
    // Fortress-kind + radius helpers that DO NOT depend on BE type
    // ============================================================

    /** Returns the fortress kind at a position by looking at the BLOCK STATE, not the BE type. */
    private static FortressKind getKindAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;

        if (!isChunkLoaded(level, pos)) return null;

        BlockState state = level.getBlockState(pos);
        return getKindFromBlock(state.getBlock());
    }

    /** True if this position is one of our fortress blocks (center or node), based on block state. */
    private static boolean isFortressPos(Level level, BlockPos pos) {
        return getKindAt(level, pos) != null;
    }

    /** Break radius for whatever fortress block is at this pos (0 if none / unloaded). */
    private static int getBreakRadiusAt(Level level, BlockPos pos) {
        return getBreakRadiusBlocksForKind(getKindAt(level, pos));
    }

    // ============================================================
    // NEW: Public helpers (used by net highlight/debug)
    // ============================================================

    /** Public: fortress kind at pos, returns null if chunk not loaded or not a fortress. */
    public static FortressKind getKindAtLoaded(Level level, BlockPos pos) {
        return getKindAt(level, pos);
    }

    /** Public: break radius at pos, returns 0 if chunk not loaded or not a fortress. */
    public static int getBreakRadiusAtLoaded(Level level, BlockPos pos) {
        return getBreakRadiusAt(level, pos);
    }

    /** Public: true if chunk loaded and block at pos is a fortress block. */
    public static boolean isFortressPosLoaded(Level level, BlockPos pos) {
        return isFortressPos(level, pos);
    }

    // ============================
    // Public API
    // ============================

    public static FortressCenterBlockEntity getFortressCoveringForUse(Level level, BlockPos targetPos) {
        return findCenterCovering(level, targetPos, CoverageMode.TERRITORY);
    }

    public static FortressCenterBlockEntity getFortressCoveringForBreak(Level level, BlockPos targetPos) {
        return findCenterCovering(level, targetPos, CoverageMode.BREAK);
    }

    /**
     * Return the full linked-net positions for the claimed center at centerPos.
     * Includes the center itself + all linked nodes. Only considers loaded chunks.
     */
    public static Set<BlockPos> getNetForCenter(Level level, BlockPos centerPos) {
        if (level == null || centerPos == null) return Set.of();
        if (!isChunkLoaded(level, centerPos)) return Set.of();

        BlockEntity be = level.getBlockEntity(centerPos);
        if (!(be instanceof FortressCenterBlockEntity fc)) return Set.of();
        if (!isCenterBlock(fc)) return Set.of();
        if (!fc.isClaimed()) return Set.of();

        ResourceKey<Level> dim = level.dimension();
        return floodFillNetByProtectionOverlap(level, dim, centerPos);
    }

    /**
     * Find the "best" protecting NODE for a break at targetPos.
     */
    public static BlockPos findBestProtectingNodeForBreak(Level level, BlockPos targetPos) {
        if (level == null || targetPos == null) return null;

        FortressCenterBlockEntity center = getFortressCoveringForBreak(level, targetPos);
        if (center == null) return null;

        ResourceKey<Level> dim = level.dimension();
        Set<BlockPos> net = floodFillNetByProtectionOverlap(level, dim, center.getBlockPos());
        if (net.isEmpty()) return null;

        BlockPos best = null;
        long bestD2 = Long.MAX_VALUE;

        for (BlockPos nodePos : net) {
            FortressKind k = getKindAt(level, nodePos);
            if (k == null || k == FortressKind.CENTER) continue;

            int r = getBreakRadiusBlocksForKind(k);
            if (r <= 0) continue;

            if (!isWithinCube(nodePos, targetPos, r)) continue;

            long d2 = dist2XYZ(nodePos, targetPos);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = nodePos.immutable();
            }
        }

        return best;
    }

    // ============================
    // Node-only protected count
    // ============================

    public static int computeNodeProtectedNonAirCount(Level level, BlockPos nodePos) {
        if (level == null || nodePos == null) return 0;

        BlockEntity be = level.getBlockEntity(nodePos);
        if (!(be instanceof FortressBlockEntity node)) return 0;

        int r = getBreakRadiusBlocksForNode(node);
        if (r <= 0) return 0;

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cx = nodePos.getX();
        int cy = nodePos.getY();
        int cz = nodePos.getZ();

        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(cx + dx, cy + dy, cz + dz);

                    if (!isChunkLoaded(level, m)) continue;

                    BlockState st = level.getBlockState(m);
                    if (st.isAir()) continue;

                    count++;
                }
            }
        }
        return count;
    }

    // ============================
    // Net stats (union blocks + node counts)
    // ============================

    public static final class NetStats {
        public final int protectedNonAir;
        public final int woodCount;
        public final int ironCount;
        public final int diamondCount;

        public NetStats(int protectedNonAir, int woodCount, int ironCount, int diamondCount) {
            this.protectedNonAir = protectedNonAir;
            this.woodCount = woodCount;
            this.ironCount = ironCount;
            this.diamondCount = diamondCount;
        }
    }

    public static NetStats computeNetStats(Level level, BlockPos anyNodeOrCenterPos) {
        if (level == null || anyNodeOrCenterPos == null) return new NetStats(0, 0, 0, 0);

        FortressCenterBlockEntity center = getFortressCoveringForBreak(level, anyNodeOrCenterPos);
        if (center == null) return new NetStats(0, 0, 0, 0);

        ResourceKey<Level> dim = level.dimension();
        Set<BlockPos> net = floodFillNetByProtectionOverlap(level, dim, center.getBlockPos());
        if (net.isEmpty()) return new NetStats(0, 0, 0, 0);

        int wood = 0, iron = 0, diamond = 0;

        int approxVolume = 0;
        for (BlockPos nodePos : net) {
            int rr = getBreakRadiusAt(level, nodePos);
            if (rr <= 0) continue;
            int side = (2 * rr + 1);
            approxVolume += side * side * side;
        }

        LongOpenHashSet uniquePositions = new LongOpenHashSet(Math.max(1024, approxVolume));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (BlockPos nodePos : net) {
            FortressKind k = getKindAt(level, nodePos);
            if (k == null) continue;

            if (k == FortressKind.WOOD) wood++;
            else if (k == FortressKind.IRON) iron++;
            else if (k == FortressKind.DIAMOND) diamond++;

            int r = getBreakRadiusBlocksForKind(k);
            if (r <= 0) continue;

            int cx = nodePos.getX();
            int cy = nodePos.getY();
            int cz = nodePos.getZ();

            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        m.set(cx + dx, cy + dy, cz + dz);

                        if (!isChunkLoaded(level, m)) continue;

                        BlockState state = level.getBlockState(m);
                        if (state.isAir()) continue;

                        uniquePositions.add(m.asLong());
                    }
                }
            }
        }

        return new NetStats(uniquePositions.size(), wood, iron, diamond);
    }

    // ============================
    // Net linking + coverage
    // ============================

    private enum CoverageMode { TERRITORY, BREAK }

    private static FortressCenterBlockEntity findCenterCovering(Level level, BlockPos targetPos, CoverageMode mode) {
        if (level == null) return null;

        ResourceKey<Level> dim = level.dimension();

        FortressCenterBlockEntity bestCenter = null;
        long bestCenterDist2 = Long.MAX_VALUE;

        for (BlockPos p : FortressRegistry.getFortresses(dim)) {
            if (!isChunkLoaded(level, p)) continue;

            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof FortressCenterBlockEntity maybeCenter)) continue;
            if (!isCenterBlock(maybeCenter)) continue;
            if (!maybeCenter.isClaimed()) continue;

            Set<BlockPos> net = floodFillNetByProtectionOverlap(level, dim, p);

            boolean covers = (mode == CoverageMode.TERRITORY)
                    ? netCoversTerritory(level, net, targetPos)
                    : netCoversBreak(level, net, targetPos);

            if (!covers) continue;

            long d2 = dist2XZ(p, targetPos);
            if (d2 < bestCenterDist2) {
                bestCenterDist2 = d2;
                bestCenter = maybeCenter;
            }
        }

        return bestCenter;
    }

    private static Set<BlockPos> floodFillNetByProtectionOverlap(Level level, ResourceKey<Level> dim, BlockPos startCenterPos) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();

        visited.add(startCenterPos);
        q.add(startCenterPos);

        Set<BlockPos> all = new HashSet<>(FortressRegistry.getFortresses(dim));

        while (!q.isEmpty()) {
            BlockPos aPos = q.removeFirst();

            if (!isFortressPos(level, aPos)) continue;

            int aR = getBreakRadiusAt(level, aPos);
            if (aR <= 0) continue;

            for (BlockPos bPos : all) {
                if (visited.contains(bPos)) continue;
                if (!isFortressPos(level, bPos)) continue;

                int bR = getBreakRadiusAt(level, bPos);
                if (bR <= 0) continue;

                if (cubesOverlap(aPos, aR, bPos, bR)) {
                    visited.add(bPos);
                    q.addLast(bPos);
                }
            }
        }

        return visited;
    }

    private static boolean netCoversTerritory(Level level, Set<BlockPos> net, BlockPos targetPos) {
        int r = Config.fortressTerritoryRadius;
        if (r <= 0) return false;

        for (BlockPos nodePos : net) {
            if (!isFortressPos(level, nodePos)) continue;
            if (isWithinRadiusXZ(nodePos, targetPos, r)) return true;
        }
        return false;
    }

    private static boolean netCoversBreak(Level level, Set<BlockPos> net, BlockPos targetPos) {
        for (BlockPos nodePos : net) {
            if (!isFortressPos(level, nodePos)) continue;

            int r = getBreakRadiusAt(level, nodePos);
            if (r <= 0) continue;

            if (isWithinCube(nodePos, targetPos, r)) return true;
        }
        return false;
    }

    // ============================
    // Geometry
    // ============================

    private static boolean isWithinCube(BlockPos center, BlockPos target, int r) {
        int dx = Math.abs(center.getX() - target.getX());
        int dy = Math.abs(center.getY() - target.getY());
        int dz = Math.abs(center.getZ() - target.getZ());
        return Math.max(dx, Math.max(dy, dz)) <= r;
    }

    private static boolean cubesOverlap(BlockPos a, int rA, BlockPos b, int rB) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return Math.max(dx, Math.max(dy, dz)) <= (rA + rB);
    }

    private static boolean isWithinRadiusXZ(BlockPos center, BlockPos target, int radiusBlocks) {
        long dx = (long) center.getX() - (long) target.getX();
        long dz = (long) center.getZ() - (long) target.getZ();
        long rr = (long) radiusBlocks;
        return (dx * dx + dz * dz) <= (rr * rr);
    }

    private static long dist2XZ(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - (long) b.getX();
        long dz = (long) a.getZ() - (long) b.getZ();
        return dx * dx + dz * dz;
    }

    private static long dist2XYZ(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - (long) b.getX();
        long dy = (long) a.getY() - (long) b.getY();
        long dz = (long) a.getZ() - (long) b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
