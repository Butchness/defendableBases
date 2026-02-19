package com.defendablebases.client;

import com.defendablebases.network.FortressNetHighlightPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ClientNetHighlightCache {
    private ClientNetHighlightCache() {}

    private static BlockPos centerPos = null;

    // Territory data (client builds union outline)
    private static List<BlockPos> territoryNodes = null;
    private static int territoryRadius = 0;
    private static int territoryBaseY = 0; // kept for packet compatibility (no longer required for geometry)
    private static Set<Edge> territoryEdges = null;

    // Break data (client builds union outline)
    private static List<FortressNetHighlightPacket.NodeEntry> breakNodes = null;
    private static Set<Edge> breakEdges = null;

    private static long expireGameTime = 0L;

    // Used to detect world reload / dimension change
    private static Level lastLevelRef = null;

    // Visual height for territory volume (debug visualization only)
    private static final int TERRITORY_Y_HALF_HEIGHT = 6; // total height = 13 blocks

    public static void clear() {
        centerPos = null;

        territoryNodes = null;
        territoryRadius = 0;
        territoryBaseY = 0;
        territoryEdges = null;

        breakNodes = null;
        breakEdges = null;

        expireGameTime = 0L;
        lastLevelRef = null;
    }

    /**
     * Set highlight data.
     *
     * Behavior:
     * - If highlight is already active, we update geometry but DO NOT extend expiration.
     * - If highlight is not active (or expired), we start a new timer using durationTicks.
     * - If the world/level changed, we clear stale data immediately.
     */
    public static void set(
            BlockPos center,
            List<BlockPos> territoryNodesIn,
            int territoryRadiusIn,
            int territoryBaseYIn,
            List<FortressNetHighlightPacket.NodeEntry> breakNodesIn,
            int durationTicks
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        Level lvl = mc.level;
        if (lvl == null) return;

        // If the world changed since last set(), wipe old cached geometry
        if (lastLevelRef != null && lastLevelRef != lvl) {
            clear();
        }
        lastLevelRef = lvl;

        long now = lvl.getGameTime();
        long newExpire = now + Math.max(1, durationTicks);

        boolean currentlyActive =
                (territoryEdges != null && breakEdges != null && now <= expireGameTime);

        centerPos = center;

        territoryNodes = territoryNodesIn;
        territoryRadius = Math.max(0, territoryRadiusIn);
        territoryBaseY = territoryBaseYIn;

        breakNodes = breakNodesIn;

        // Rebuild edges so render stays cheap
        // NOTE: territoryBaseY is no longer used for geometry; we use each node's Y instead.
        territoryEdges = buildUnionTerritoryEdges3D(territoryNodes, territoryRadius);
        breakEdges = buildUnionBreakEdges(breakNodes);

        // Do NOT extend expiration if we are receiving refresh updates while active
        if (!currentlyActive) {
            expireGameTime = newExpire;
        }
    }

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        Level lvl = mc.level;
        if (lvl == null) {
            clear();
            return false;
        }

        // If the world changed (reload / disconnect / dimension swap), kill stale highlight
        if (lastLevelRef != null && lastLevelRef != lvl) {
            clear();
            return false;
        }
        lastLevelRef = lvl;

        if (territoryEdges == null || breakEdges == null) return false;

        long now = lvl.getGameTime();
        if (now > expireGameTime) {
            clear();
            return false;
        }

        return true;
    }

    public static BlockPos getCenterPos() { return centerPos; }
    public static Set<Edge> getTerritoryEdges() { return territoryEdges; }
    public static Set<Edge> getBreakEdges() { return breakEdges; }

    // ----------------------------
    // Geometry
    // ----------------------------

    public record Edge(int x1, int y1, int z1, int x2, int y2, int z2) { }

    private static void addEdge(Set<Edge> edges, int ax, int ay, int az, int bx, int by, int bz) {
        // canonical ordering so duplicates collapse
        if (ax < bx || (ax == bx && (ay < by || (ay == by && az <= bz)))) {
            edges.add(new Edge(ax, ay, az, bx, by, bz));
        } else {
            edges.add(new Edge(bx, by, bz, ax, ay, az));
        }
    }

    private enum Dir { POS_X, NEG_X, POS_Y, NEG_Y, POS_Z, NEG_Z }

    private static long packVoxel(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    // ----------------------------
    // Territory union surface wireframe (3D):
    // - Fill voxels for each node’s territory circle in XZ
    // - Extrude in Y around EACH NODE's own Y (so it "dome"/tracks elevation like break)
    // - Then compute union surface edges exactly like break
    // ----------------------------

    private static Set<Edge> buildUnionTerritoryEdges3D(List<BlockPos> nodes, int rT) {
        HashSet<Long> filled = new HashSet<>(16384);
        if (nodes == null || nodes.isEmpty() || rT <= 0) {
            return new HashSet<>();
        }

        final int rr = rT * rT;

        // Fill voxel volume: (XZ circle footprint) x (per-node Y band)
        for (BlockPos c : nodes) {
            int cx = c.getX();
            int cy = c.getY();
            int cz = c.getZ();

            int minY = cy - TERRITORY_Y_HALF_HEIGHT;
            int maxY = cy + TERRITORY_Y_HALF_HEIGHT;

            for (int dx = -rT; dx <= rT; dx++) {
                int dx2 = dx * dx;
                for (int dz = -rT; dz <= rT; dz++) {
                    if (dx2 + (dz * dz) > rr) continue;

                    int x = cx + dx;
                    int z = cz + dz;

                    for (int y = minY; y <= maxY; y++) {
                        filled.add(packVoxel(x, y, z));
                    }
                }
            }
        }

        // Build union surface edges by checking missing neighbors (same method as break)
        HashSet<Edge> edges = new HashSet<>(32768);

        for (Long key : filled) {
            BlockPos p = BlockPos.of(key);
            int x = p.getX();
            int y = p.getY();
            int z = p.getZ();

            if (!filled.contains(packVoxel(x + 1, y, z))) addFaceEdges(edges, x, y, z, Dir.POS_X);
            if (!filled.contains(packVoxel(x - 1, y, z))) addFaceEdges(edges, x, y, z, Dir.NEG_X);

            if (!filled.contains(packVoxel(x, y + 1, z))) addFaceEdges(edges, x, y, z, Dir.POS_Y);
            if (!filled.contains(packVoxel(x, y - 1, z))) addFaceEdges(edges, x, y, z, Dir.NEG_Y);

            if (!filled.contains(packVoxel(x, y, z + 1))) addFaceEdges(edges, x, y, z, Dir.POS_Z);
            if (!filled.contains(packVoxel(x, y, z - 1))) addFaceEdges(edges, x, y, z, Dir.NEG_Z);
        }

        return edges;
    }

    // ----------------------------
    // Break union surface wireframe for cubes (full 3D)
    // ----------------------------

    private static Set<Edge> buildUnionBreakEdges(List<FortressNetHighlightPacket.NodeEntry> nodes) {
        HashSet<Long> filled = new HashSet<>(16384);
        if (nodes == null || nodes.isEmpty()) return new HashSet<>();

        for (FortressNetHighlightPacket.NodeEntry e : nodes) {
            BlockPos c = e.pos;
            int r = Math.max(0, e.radius);

            int cx = c.getX();
            int cy = c.getY();
            int cz = c.getZ();

            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        filled.add(packVoxel(cx + dx, cy + dy, cz + dz));
                    }
                }
            }
        }

        HashSet<Edge> edges = new HashSet<>(32768);

        for (Long key : filled) {
            BlockPos p = BlockPos.of(key);
            int x = p.getX();
            int y = p.getY();
            int z = p.getZ();

            if (!filled.contains(packVoxel(x + 1, y, z))) addFaceEdges(edges, x, y, z, Dir.POS_X);
            if (!filled.contains(packVoxel(x - 1, y, z))) addFaceEdges(edges, x, y, z, Dir.NEG_X);

            if (!filled.contains(packVoxel(x, y + 1, z))) addFaceEdges(edges, x, y, z, Dir.POS_Y);
            if (!filled.contains(packVoxel(x, y - 1, z))) addFaceEdges(edges, x, y, z, Dir.NEG_Y);

            if (!filled.contains(packVoxel(x, y, z + 1))) addFaceEdges(edges, x, y, z, Dir.POS_Z);
            if (!filled.contains(packVoxel(x, y, z - 1))) addFaceEdges(edges, x, y, z, Dir.NEG_Z);
        }

        return edges;
    }

    private static void addFaceEdges(Set<Edge> edges, int x, int y, int z, Dir dir) {
        switch (dir) {
            case POS_X -> {
                int fx = x + 1;
                addEdge(edges, fx, y,     z,     fx, y + 1, z);
                addEdge(edges, fx, y + 1, z,     fx, y + 1, z + 1);
                addEdge(edges, fx, y + 1, z + 1, fx, y,     z + 1);
                addEdge(edges, fx, y,     z + 1, fx, y,     z);
            }
            case NEG_X -> {
                int fx = x;
                addEdge(edges, fx, y,     z,     fx, y + 1, z);
                addEdge(edges, fx, y + 1, z,     fx, y + 1, z + 1);
                addEdge(edges, fx, y + 1, z + 1, fx, y,     z + 1);
                addEdge(edges, fx, y,     z + 1, fx, y,     z);
            }
            case POS_Y -> {
                int fy = y + 1;
                addEdge(edges, x,     fy, z,     x + 1, fy, z);
                addEdge(edges, x + 1, fy, z,     x + 1, fy, z + 1);
                addEdge(edges, x + 1, fy, z + 1, x,     fy, z + 1);
                addEdge(edges, x,     fy, z + 1, x,     fy, z);
            }
            case NEG_Y -> {
                int fy = y;
                addEdge(edges, x,     fy, z,     x + 1, fy, z);
                addEdge(edges, x + 1, fy, z,     x + 1, fy, z + 1);
                addEdge(edges, x + 1, fy, z + 1, x,     fy, z + 1);
                addEdge(edges, x,     fy, z + 1, x,     fy, z);
            }
            case POS_Z -> {
                int fz = z + 1;
                addEdge(edges, x,     y,     fz, x + 1, y,     fz);
                addEdge(edges, x + 1, y,     fz, x + 1, y + 1, fz);
                addEdge(edges, x + 1, y + 1, fz, x,     y + 1, fz);
                addEdge(edges, x,     y + 1, fz, x,     y,     fz);
            }
            case NEG_Z -> {
                int fz = z;
                addEdge(edges, x,     y,     fz, x + 1, y,     fz);
                addEdge(edges, x + 1, y,     fz, x + 1, y + 1, fz);
                addEdge(edges, x + 1, y + 1, fz, x,     y + 1, fz);
                addEdge(edges, x,     y + 1, fz, x,     y,     fz);
            }
        }
    }
}
