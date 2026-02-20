package com.defendablebases;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class FortressCenterBlockEntity extends BlockEntity {
    public static final int CENTER_MAX_ENERGY = 16;
    private static final String TAG_ITEMS   = "Items";
    private static final String TAG_PRIV    = "Privileged";
    private static final String TAG_CLAIMED = "Claimed";
    private static final String TAG_MAX     = "MaxPrivileged";
    private static final String TAG_RADIUS  = "RadiusBlocks";

    private static final String TAG_PROTECTED_NON_AIR = "ProtectedNonAirCount";
    private static final String TAG_NET_WOOD    = "NetWoodCount";
    private static final String TAG_NET_IRON    = "NetIronCount";
    private static final String TAG_NET_DIAMOND = "NetDiamondCount";

    // decay scheduling
    private static final String TAG_NEXT_DECAY_TIME = "NextDecayGameTime";
    private static final String TAG_PLACED_TIME = "PlacedGameTime";
    private static final String TAG_ENERGY = "CenterEnergy";

    private final SimpleContainer inventory = new SimpleContainer(27) {
        @Override
        public void setChanged() {
            super.setChanged();
            FortressCenterBlockEntity.this.setChanged();
            FortressCenterBlockEntity.this.syncToClients();
        }
    };

    private final Set<UUID> privileged = new HashSet<>();
    private boolean claimed = false;

    private int protectedNonAirCount = 0;
    private int netWoodCount = 0;
    private int netIronCount = 0;
    private int netDiamondCount = 0;

    private boolean netDirty = false;
    private long nextRecomputeGameTime = 0L;

    // decay scheduling
    private long nextDecayGameTime = 0L;
    private long placedGameTime = 0L;
    private float centerEnergy = CENTER_MAX_ENERGY;

    private final Set<UUID> clientPrivileged = new HashSet<>();
    private boolean clientClaimed = false;
    private int clientMaxPrivileged = 16;
    private int clientRadiusBlocks = 32;

    private int clientProtectedNonAirCount = 0;
    private int clientNetWoodCount = 0;
    private int clientNetIronCount = 0;
    private int clientNetDiamondCount = 0;
    private float clientCenterEnergy = CENTER_MAX_ENERGY;

    public FortressCenterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORTRESS_CENTER.get(), pos, state);
    }

    // ----------------------------
    // REGISTRY AUTO-REPAIR (CRITICAL)
    // ----------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            FortressRegistry.addFortress(level.dimension(), worldPosition);

            if (placedGameTime <= 0L) {
                placedGameTime = level.getGameTime();
                setChanged();
            }

            TerritoryRules.pruneNewestCenterIfNetHasMultipleCenters(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        Level lvl = this.level;
        BlockPos pos = this.worldPosition;
        super.setRemoved();
        if (lvl != null && !lvl.isClientSide) {
            ResourceKey<Level> dim = lvl.dimension();
            FortressRegistry.removeFortress(dim, pos);
        }
    }

    public SimpleContainer getInventory() { return inventory; }

    public Component getDisplayName() { return Component.literal("Fortress Center"); }

    public boolean isClaimed() {
        if (level != null && level.isClientSide) return clientClaimed;
        return claimed;
    }

    public int getServerMaxPrivileged() { return Config.maxPrivileged; }
    public int getServerRadiusBlocks() { return Config.fortressTerritoryRadius; }

    public int getClientMaxPrivileged() {
        if (level != null && level.isClientSide) return clientMaxPrivileged;
        return Config.maxPrivileged;
    }

    public int getClientRadiusBlocks() {
        if (level != null && level.isClientSide) return clientRadiusBlocks;
        return Config.fortressTerritoryRadius;
    }

    public int getClientProtectedNonAirCount() {
        if (level != null && level.isClientSide) return clientProtectedNonAirCount;
        return protectedNonAirCount;
    }

    public int getClientNetWoodCount() {
        if (level != null && level.isClientSide) return clientNetWoodCount;
        return netWoodCount;
    }

    public int getClientNetIronCount() {
        if (level != null && level.isClientSide) return clientNetIronCount;
        return netIronCount;
    }

    public int getClientNetDiamondCount() {
        if (level != null && level.isClientSide) return clientNetDiamondCount;
        return netDiamondCount;
    }

    public long getPlacedGameTimeForOrdering() {
        return Math.max(0L, placedGameTime);
    }

    public int getCenterMaxEnergy() {
        return CENTER_MAX_ENERGY;
    }

    public int getClientCenterEnergy() {
        float v = (level != null && level.isClientSide) ? clientCenterEnergy : centerEnergy;
        return Math.max(0, Math.min(getCenterMaxEnergy(), (int) Math.floor(v)));
    }

    public void setProtectedNonAirCountServer(int count) {
        if (level == null || level.isClientSide) return;
        this.protectedNonAirCount = Math.max(0, count);
        setChanged();
        syncToClients();
    }

    public void setNetStatsServer(int protectedNonAir, int wood, int iron, int diamond) {
        if (level == null || level.isClientSide) return;

        this.protectedNonAirCount = Math.max(0, protectedNonAir);
        this.netWoodCount = Math.max(0, wood);
        this.netIronCount = Math.max(0, iron);
        this.netDiamondCount = Math.max(0, diamond);

        setChanged();
        syncToClients();
    }

    public void clientSetNetStatsFromPacket(int protectedNonAir, int wood, int iron, int diamond) {
        if (level == null || !level.isClientSide) return;
        clientSetNetStats(protectedNonAir, wood, iron, diamond);
    }

    private void clientSetNetStats(int protectedNonAir, int wood, int iron, int diamond) {
        this.clientProtectedNonAirCount = Math.max(0, protectedNonAir);
        this.clientNetWoodCount = Math.max(0, wood);
        this.clientNetIronCount = Math.max(0, iron);
        this.clientNetDiamondCount = Math.max(0, diamond);
    }

    public void markNetProtectedDirty(int delayTicks) {
        if (level == null || level.isClientSide) return;

        long now = level.getGameTime();
        long scheduled = now + Math.max(0, delayTicks);

        if (!netDirty || scheduled < nextRecomputeGameTime) {
            nextRecomputeGameTime = scheduled;
        }

        netDirty = true;
        setChanged();
    }

    // ============================================================
    // DECAY MATH
    // ============================================================

    private static long decayIntervalTicks() {
        return 20L * 60L * Math.max(1, Config.decayIntervalMinutes);
    }

    private static int decayCostForBlocks(int blocks) {
        if (blocks <= 0) return 0;
        double coeff = Config.decayCoeff;
        double y = Math.exp(blocks * coeff);
        int cost = (int) Math.ceil(y);
        return Math.max(0, cost);
    }

    /** Sum repair points in the CENTER inventory. Client-safe because inventory is synced via BE update tag. */
    public int getClientCenterRepairPoints() {
        int points = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack st = inventory.getItem(i);
            if (st.isEmpty()) continue;

            int heal = (int) Math.floor(RepairResources.getPoints(st.getItem()));
            if (heal <= 0) continue;

            points += heal * st.getCount();
        }
        return Math.max(0, points);
    }

    /** Cost per decay interval (in repair points) for the current protected block union. */
    public int getClientDecayCostPerIntervalPoints() {
        int blocks = getClientProtectedNonAirCount();
        return decayCostForBlocks(blocks);
    }

    /** Hours remaining until center runs out, assuming blocks + inventory unchanged. */
    public float getClientDecayHoursRemaining() {
        if (!Config.decayEnabled) return -1f;
        if (!isClaimed()) return -1f;

        int cost = getClientDecayCostPerIntervalPoints();
        if (cost <= 0) return Float.POSITIVE_INFINITY;

        int fuel = getClientCenterRepairPoints();
        if (fuel <= 0) return 0f;

        float intervalHours = Math.max(1, Config.decayIntervalMinutes) / 60.0f;
        return (fuel / (float) cost) * intervalHours;
    }

    /** UI string: "Decay (H): X" */
    public String getClientDecayHoursRemainingString() {
        if (!Config.decayEnabled) return "Decay(H):    OFF";
        if (!isClaimed()) return "Decay(H):    N/A";

        float h = getClientDecayHoursRemaining();
        if (Float.isInfinite(h)) return "Decay(H):    \u221E";
        if (h < 0f) return "Decay(H):    N/A";

        return "Decay(H):    " + String.format(Locale.US, "%.1f", Math.max(0f, h));
    }

    // ============================================================
    // DECAY MECHANICS (server)
    // ============================================================

    /** Apply protection damage and always attempt to refill center energy toward full from inventory resources. */
    public boolean applyCenterProtectionDamage(float damage) {
        if (level == null || level.isClientSide) return false;
        if (damage <= 0f) return true;

        float max = (float) getCenterMaxEnergy();

        centerEnergy -= damage;

        // Only trigger resource-based repair when energy is depleted.
        // Repair attempts to refill from zero to full, then applies any overkill debt.
        if (centerEnergy <= 0f) {
            float debt = -centerEnergy;
            centerEnergy = 0f;

            int needed = (int) Math.ceil(max);
            int remaining = consumeCenterRepairPoints(needed);
            int paid = Math.max(0, needed - remaining);

            centerEnergy += paid;
            centerEnergy -= debt;
        }

        centerEnergy = Math.max(0f, Math.min(max, centerEnergy));

        setChanged();
        syncToClients();
        return centerEnergy > 0f;
    }

    private int consumeCenterRepairPoints(int neededPoints) {
        if (neededPoints <= 0) return 0;

        for (Item pref : RepairResources.getPriorityItems()) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (neededPoints <= 0) return 0;

                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() != pref) continue;

                int heal = (int) Math.floor(RepairResources.getPoints(pref));
                if (heal <= 0) continue;

                while (!stack.isEmpty() && neededPoints > 0) {
                    stack.shrink(1);
                    neededPoints -= heal;
                }

                inventory.setItem(i, stack);
            }
        }

        return Math.max(0, neededPoints);
    }

    /** Notify privileged ONLINE players (in same level) that center is out of decay resources. */
    private void notifyPrivilegedCenterOutOfResources(int required, int remaining) {
        if (level == null || level.isClientSide) return;
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;

        Component msg = Component.literal(
                "Fortress Center is out of decay resources (" + (required - remaining) + "/" + required +
                        " paid). Nodes are now consuming energy!"
        );

        for (ServerPlayer sp : sl.players()) {
            if (sp == null) continue;
            if (privileged.contains(sp.getUUID())) {
                sp.displayClientMessage(msg, true);
            }
        }
    }

    /** Destroy a fortress NODE safely (no drops), keep registry + net topology consistent. */
    private void destroyFortressNodeNoDropServer(Level level, BlockPos nodePos) {
        if (level == null || level.isClientSide) return;
        if (nodePos == null) return;

        BlockState st = level.getBlockState(nodePos);
        boolean isNode =
                st.getBlock() == ModBlocks.FORTRESS_WOOD.get()
                        || st.getBlock() == ModBlocks.FORTRESS_IRON.get()
                        || st.getBlock() == ModBlocks.FORTRESS_DIAMOND.get();

        if (!isNode) return;

        level.destroyBlock(nodePos, false); // no drops
        FortressRegistry.removeFortress(level.dimension(), nodePos);

        // topology changed -> recompute soon
        markNetProtectedDirty(5);
    }

    /** Apply remaining decay cost to nodes in the center's net (excluding center). */
    private void pushDecayToNodes(Level level, BlockPos centerPos, int remainingCost) {
        if (remainingCost <= 0) return;

        Set<BlockPos> net = TerritoryRules.getNetForCenter(level, centerPos);
        if (net.isEmpty()) return;

        // Build weights based on each node's own block count => exp(nodeBlocks*coeff)
        int totalWeight = 0;
        class NodeInfo {
            final BlockPos pos;
            final int weight;
            NodeInfo(BlockPos pos, int weight) { this.pos = pos; this.weight = weight; }
        }
        java.util.ArrayList<NodeInfo> nodes = new java.util.ArrayList<>();

        for (BlockPos p : net) {
            if (p.equals(centerPos)) continue;

            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof FortressBlockEntity)) continue;

            int nodeBlocks = TerritoryRules.computeNodeProtectedNonAirCount(level, p);
            int w = decayCostForBlocks(nodeBlocks);
            if (w <= 0) continue;

            nodes.add(new NodeInfo(p.immutable(), w));
            totalWeight += w;
        }

        if (nodes.isEmpty() || totalWeight <= 0) return;

        int allocated = 0;

        for (int idx = 0; idx < nodes.size(); idx++) {
            NodeInfo n = nodes.get(idx);

            int dmg;
            if (idx == nodes.size() - 1) {
                dmg = Math.max(0, remainingCost - allocated);
            } else {
                double frac = n.weight / (double) totalWeight;
                dmg = (int) Math.floor(remainingCost * frac);
            }

            if (dmg <= 0) continue;
            allocated += dmg;

            BlockEntity be = level.getBlockEntity(n.pos);
            if (!(be instanceof FortressBlockEntity node)) continue;

            boolean ok = node.applyDecayDamage((float) dmg);
            if (!ok) {
                destroyFortressNodeNoDropServer(level, n.pos);
            }
        }
    }

    private void doDecayTick(Level level, BlockPos centerPos) {
        // Recompute net union blocks before charging
        TerritoryRules.NetStats stats = TerritoryRules.computeNetStats(level, centerPos);
        setNetStatsServer(stats.protectedNonAir, stats.woodCount, stats.ironCount, stats.diamondCount);

        int blocks = stats.protectedNonAir;
        int required = decayCostForBlocks(blocks);
        if (required <= 0) return;

        int remaining = consumeCenterRepairPoints(required);

        // Persist + update clients if we consumed anything
        setChanged();
        syncToClients();

        // If center couldn't fully pay, notify privileged players, then push remaining to nodes
        if (remaining > 0) {
            notifyPrivilegedCenterOutOfResources(required, remaining);
            pushDecayToNodes(level, centerPos, remaining);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FortressCenterBlockEntity be) {
        if (level == null || level.isClientSide) return;
        if (be == null) return;

        long now = level.getGameTime();

        // ---- Existing: net recompute if dirty ----
        if (be.netDirty) {
            if (now >= be.nextRecomputeGameTime) {
                TerritoryRules.NetStats stats = TerritoryRules.computeNetStats(level, pos);
                be.setNetStatsServer(stats.protectedNonAir, stats.woodCount, stats.ironCount, stats.diamondCount);
                be.netDirty = false;
            }
        }

        // ---- Decay tick ----
        if (!Config.decayEnabled) return;
        if (!be.isClaimed()) return;

        long interval = decayIntervalTicks();

        // initialize if needed
        if (be.nextDecayGameTime <= 0L) {
            be.nextDecayGameTime = now + interval;
            be.setChanged();
            return;
        }

        if (now < be.nextDecayGameTime) return;

        int maxIters = Math.max(0, Config.decayCatchUpMaxIterations);
        int iters = 0;

        while (now >= be.nextDecayGameTime) {
            be.doDecayTick(level, pos);

            be.nextDecayGameTime += interval;
            iters++;

            if (maxIters > 0 && iters >= maxIters) {
                be.nextDecayGameTime = now + interval;
                break;
            }
        }

        be.setChanged();
    }

    // ============================================================
    // PRIVILEGES
    // ============================================================

    public Set<UUID> getPrivilegedCopy() {
        if (level != null && level.isClientSide) return new HashSet<>(clientPrivileged);
        return new HashSet<>(privileged);
    }

    public void clientSetPrivileged(Set<UUID> list, boolean claimed, int maxPrivileged, int territoryRadiusBlocks) {
        clientPrivileged.clear();
        clientPrivileged.addAll(list);
        clientClaimed = claimed;
        clientMaxPrivileged = maxPrivileged;
        clientRadiusBlocks = territoryRadiusBlocks;
    }

    public void ensureClaimedBy(Player player) {
        if (level == null || level.isClientSide) return;
        if (claimed) return;

        claimed = true;
        privileged.clear();
        privileged.add(player.getUUID());

        // Start decay schedule on claim
        long now = level.getGameTime();
        nextDecayGameTime = now + decayIntervalTicks();

        setChanged();
        syncToClients();
    }

    public boolean canOpen(Player player) {
        if (!isClaimed()) return true;

        UUID id = player.getUUID();
        if (level != null && level.isClientSide) return clientPrivileged.contains(id);
        return privileged.contains(id);
    }

    public boolean canModify(Player actor) {
        if (!isClaimed()) return true;

        UUID id = actor.getUUID();
        if (level != null && level.isClientSide) return clientPrivileged.contains(id);
        return privileged.contains(id);
    }

    public boolean tryAddPrivileged(Player actor, UUID idToAdd) {
        if (level == null || level.isClientSide) return false;

        ensureClaimedBy(actor);
        if (!canModify(actor)) return false;

        int max = getServerMaxPrivileged();
        if (privileged.size() >= max) {
            actor.displayClientMessage(Component.literal("Fortress team is full (max " + max + ")."), true);
            return false;
        }

        boolean changed = privileged.add(idToAdd);
        if (changed) {
            setChanged();
            syncToClients();
        }
        return changed;
    }

    public boolean tryRemovePrivileged(Player actor, UUID idToRemove) {
        if (level == null || level.isClientSide) return false;

        ensureClaimedBy(actor);
        if (!canModify(actor)) return false;

        if (actor.getUUID().equals(idToRemove)) return false;
        if (privileged.size() <= 1) return false;

        boolean changed = privileged.remove(idToRemove);
        if (changed) {
            setChanged();
            syncToClients();
        }
        return changed;
    }

    private void syncToClients() {
        if (level == null || level.isClientSide) return;
        BlockState s = getBlockState();
        level.sendBlockUpdated(worldPosition, s, s, 3);
    }

    // ----------------------------
    // Inventory NBT helpers
    // ----------------------------

    private void writeInventoryToTag(CompoundTag tag) {
        ListTag list = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stack.save(itemTag);
                list.add(itemTag);
            }
        }
        tag.put(TAG_ITEMS, list);
    }

    private void readInventoryFromTag(CompoundTag tag) {
        for (int i = 0; i < inventory.getContainerSize(); i++) inventory.setItem(i, ItemStack.EMPTY);

        if (tag.contains(TAG_ITEMS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getInt("Slot");
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, ItemStack.of(itemTag));
                }
            }
        }
    }

    // ----------------------------
    // Persistence (disk)
    // ----------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        writeInventoryToTag(tag);

        tag.putBoolean(TAG_CLAIMED, claimed);

        ListTag privList = new ListTag();
        for (UUID id : privileged) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            privList.add(t);
        }
        tag.put(TAG_PRIV, privList);

        tag.putInt(TAG_PROTECTED_NON_AIR, protectedNonAirCount);
        tag.putInt(TAG_NET_WOOD, netWoodCount);
        tag.putInt(TAG_NET_IRON, netIronCount);
        tag.putInt(TAG_NET_DIAMOND, netDiamondCount);

        tag.putLong(TAG_NEXT_DECAY_TIME, nextDecayGameTime);
        tag.putLong(TAG_PLACED_TIME, placedGameTime);
        tag.putFloat(TAG_ENERGY, centerEnergy);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        readInventoryFromTag(tag);

        claimed = tag.getBoolean(TAG_CLAIMED);

        privileged.clear();
        if (tag.contains(TAG_PRIV, Tag.TAG_LIST)) {
            ListTag privList = tag.getList(TAG_PRIV, Tag.TAG_COMPOUND);
            for (int i = 0; i < privList.size(); i++) {
                CompoundTag t = privList.getCompound(i);
                if (t.hasUUID("Id")) privileged.add(t.getUUID("Id"));
            }
        }

        protectedNonAirCount = tag.contains(TAG_PROTECTED_NON_AIR, Tag.TAG_INT) ? tag.getInt(TAG_PROTECTED_NON_AIR) : 0;
        netWoodCount = tag.contains(TAG_NET_WOOD, Tag.TAG_INT) ? tag.getInt(TAG_NET_WOOD) : 0;
        netIronCount = tag.contains(TAG_NET_IRON, Tag.TAG_INT) ? tag.getInt(TAG_NET_IRON) : 0;
        netDiamondCount = tag.contains(TAG_NET_DIAMOND, Tag.TAG_INT) ? tag.getInt(TAG_NET_DIAMOND) : 0;

        nextDecayGameTime = tag.contains(TAG_NEXT_DECAY_TIME, Tag.TAG_LONG) ? tag.getLong(TAG_NEXT_DECAY_TIME) : 0L;
        placedGameTime = tag.contains(TAG_PLACED_TIME, Tag.TAG_LONG) ? tag.getLong(TAG_PLACED_TIME) : 0L;
        centerEnergy = tag.contains(TAG_ENERGY, Tag.TAG_FLOAT) ? tag.getFloat(TAG_ENERGY) : (float) CENTER_MAX_ENERGY;
        centerEnergy = Math.max(0f, Math.min((float) CENTER_MAX_ENERGY, centerEnergy));
    }

    // ----------------------------
    // Vanilla BE sync (client)
    // ----------------------------

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();

        writeInventoryToTag(tag);

        tag.putBoolean(TAG_CLAIMED, claimed);
        tag.putInt(TAG_MAX, getServerMaxPrivileged());
        tag.putInt(TAG_RADIUS, getServerRadiusBlocks());

        tag.putInt(TAG_PROTECTED_NON_AIR, protectedNonAirCount);
        tag.putInt(TAG_NET_WOOD, netWoodCount);
        tag.putInt(TAG_NET_IRON, netIronCount);
        tag.putInt(TAG_NET_DIAMOND, netDiamondCount);
        tag.putFloat(TAG_ENERGY, centerEnergy);

        ListTag privList = new ListTag();
        for (UUID id : privileged) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            privList.add(t);
        }
        tag.put(TAG_PRIV, privList);

        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (level != null && level.isClientSide) {
            readInventoryFromTag(tag);
        }

        boolean c = tag.getBoolean(TAG_CLAIMED);
        int max = tag.contains(TAG_MAX, Tag.TAG_INT) ? tag.getInt(TAG_MAX) : Config.maxPrivileged;
        int radius = tag.contains(TAG_RADIUS, Tag.TAG_INT) ? tag.getInt(TAG_RADIUS) : Config.fortressTerritoryRadius;

        Set<UUID> set = new HashSet<>();
        if (tag.contains(TAG_PRIV, Tag.TAG_LIST)) {
            ListTag privList = tag.getList(TAG_PRIV, Tag.TAG_COMPOUND);
            for (int i = 0; i < privList.size(); i++) {
                CompoundTag t = privList.getCompound(i);
                if (t.hasUUID("Id")) set.add(t.getUUID("Id"));
            }
        }

        clientSetPrivileged(set, c, max, radius);

        int protectedCount = tag.contains(TAG_PROTECTED_NON_AIR, Tag.TAG_INT) ? tag.getInt(TAG_PROTECTED_NON_AIR) : 0;
        float energy = tag.contains(TAG_ENERGY, Tag.TAG_FLOAT) ? tag.getFloat(TAG_ENERGY) : (float) CENTER_MAX_ENERGY;
        int wood = tag.contains(TAG_NET_WOOD, Tag.TAG_INT) ? tag.getInt(TAG_NET_WOOD) : 0;
        int iron = tag.contains(TAG_NET_IRON, Tag.TAG_INT) ? tag.getInt(TAG_NET_IRON) : 0;
        int diamond = tag.contains(TAG_NET_DIAMOND, Tag.TAG_INT) ? tag.getInt(TAG_NET_DIAMOND) : 0;

        clientSetNetStats(protectedCount, wood, iron, diamond);
        clientCenterEnergy = Math.max(0f, Math.min((float) CENTER_MAX_ENERGY, energy));
    }

    public void openMenu(Player player) {
        if (level == null || level.isClientSide) return;
        if (getBlockState().getBlock() != ModBlocks.FORTRESS_CENTER.get()) return;

        ensureClaimedBy(player);

        if (!canOpen(player)) {
            player.displayClientMessage(Component.literal("Not privileged."), true);
            return;
        }

        markNetProtectedDirty(5);

        if (player instanceof ServerPlayer sp) {
            SimpleMenuProvider provider = new SimpleMenuProvider(
                    (containerId, playerInv, p) -> new FortressCenterMenu(containerId, playerInv, this),
                    getDisplayName()
            );
            NetworkHooks.openScreen(sp, provider, buf -> buf.writeBlockPos(getBlockPos()));
        }
    }
}
