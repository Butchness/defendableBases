package com.defendablebases;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.List;

public class FortressBlockEntity extends BlockEntity {
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_HEALTH = "Health";
    private static final String TAG_NODE_PROTECTED_NON_AIR = "NodeProtectedNonAirCount";

    // 10 minutes default (20 ticks/sec). Swap this to a Config value later if desired.
    private static final int DEFAULT_REFILL_COOLDOWN_MINUTES = 1;
    public static final int DEFAULT_REFILL_COOLDOWN_TICKS = 20 * 60 * DEFAULT_REFILL_COOLDOWN_MINUTES;

    private FortressTier tier;
    private SimpleContainer repairInv;

    // ----------------------------
    // Server-authoritative state
    // ----------------------------
    private float health;

    // server value
    private int protectedNonAirCount = 0;

    // Refill cooldown state
    private long lastDamageGameTime = 0L;

    // ----------------------------
    // Client mirrors
    // ----------------------------
    private int clientProtectedNonAirCount = 0;
    private float clientHealth = 0f;

    public FortressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORTRESS_BLOCK.get(), pos, state);
        this.tier = tierFromState(state);
        this.repairInv = makeInvForTier(this.tier);
        this.health = this.tier.maxHealth;
        this.clientHealth = this.health;
    }

    // ----------------------------
    // CRITICAL: keep FortressRegistry correct across chunk load/unload
    // ----------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            FortressRegistry.addFortress(level.dimension(), worldPosition);
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

    public FortressTier getTier() { return tier; }

    public int getMaxHealth() { return tier.maxHealth; }

    /** UI uses this (client mirror when on client). */
    public int getClientHealth() {
        float v = (level != null && level.isClientSide) ? clientHealth : health;
        return Math.max(0, Math.min(getMaxHealth(), (int) Math.floor(v)));
    }

    public float getServerHealthFloat() { return health; }

    public int getCubeHalfSize() { return tier.cubeHalfSize; }

    public SimpleContainer getRepairInv() { return repairInv; }

    public int getClientProtectedNonAirCount() {
        if (level != null && level.isClientSide) return clientProtectedNonAirCount;
        return protectedNonAirCount;
    }

    private static SimpleContainer makeInvForTier(FortressTier tier) {
        return new SimpleContainer(tier.slotCount);
    }

    private static FortressTier tierFromState(BlockState state) {
        if (state.getBlock() == ModBlocks.FORTRESS_WOOD.get()) return FortressTier.WOOD;
        if (state.getBlock() == ModBlocks.FORTRESS_IRON.get()) return FortressTier.IRON;
        if (state.getBlock() == ModBlocks.FORTRESS_DIAMOND.get()) return FortressTier.DIAMOND;
        return FortressTier.WOOD;
    }

    private void playOutOfEnergySoundServer() {
        if (level == null || level.isClientSide) return;
        level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    private void setProtectedNonAirCountServer(int count) {
        if (level == null || level.isClientSide) return;
        this.protectedNonAirCount = Math.max(0, count);
        setChanged();
        syncToClients();
    }

    private void syncToClients() {
        if (level == null || level.isClientSide) return;
        BlockState s = getBlockState();
        level.sendBlockUpdated(worldPosition, s, s, 3);
    }

    // ============================================================
    // DAMAGE / ABSORB LOGIC (called from ForgeEvents)
    // ============================================================

    public boolean applyProtectedBreakDamage(float damage) {
        if (level == null || level.isClientSide) return false;
        if (damage <= 0f) return true;

        long now = level.getGameTime();
        lastDamageGameTime = now;

        float max = (float) getMaxHealth();

        health -= damage;

        if (health > 0f) {
            setChanged();
            syncToClients();
            return true;
        }

        float missingToFull = (-health) + max;

        boolean fullyRestored = consumeRepairPointsToCover(missingToFull);

        if (!fullyRestored) {
            health = 0f;
            setChanged();
            syncToClients();
            playOutOfEnergySoundServer();
            return false;
        }

        health = max;
        setChanged();
        syncToClients();
        return true;
    }

    /**
     * decay uses the same "health as energy" pool.
     */
    public boolean applyDecayDamage(float damage) {
        return applyProtectedBreakDamage(damage);
    }

    private boolean consumeRepairPointsToCover(float needed) {
        if (needed <= 0f) return true;

        List<Item> priority = RepairResources.getPriorityItems();

        for (Item pref : priority) {
            for (int i = 0; i < repairInv.getContainerSize(); i++) {
                if (needed <= 0f) return true;

                ItemStack stack = repairInv.getItem(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() != pref) continue;

                int heal = (int) Math.floor(RepairResources.getPoints(pref));
                if (heal <= 0) continue;

                while (!stack.isEmpty() && needed > 0f) {
                    stack.shrink(1);
                    needed -= (float) heal;
                }

                repairInv.setItem(i, stack);
            }
        }

        return needed <= 0f;
    }

    // ============================================================
    // REFILL AFTER COOLDOWN (ticker)
    // ============================================================

    public static void serverTick(Level level, BlockPos pos, BlockState state, FortressBlockEntity be) {
        if (level == null || level.isClientSide) return;
        if (be == null) return;

        long now = level.getGameTime();
        int cooldown = DEFAULT_REFILL_COOLDOWN_TICKS;

        if ((now - be.lastDamageGameTime) < cooldown) return;

        FortressCenterBlockEntity center = TerritoryRules.getFortressCoveringForBreak(level, pos);
        if (center == null) return;
        if (!center.isClaimed()) return;

        be.refillFromCenter(center);

        be.setChanged();
        be.syncToClients();
    }

    private void refillFromCenter(FortressCenterBlockEntity center) {
        if (center == null) return;

        SimpleContainer src = center.getInventory();
        if (src == null) return;

        List<Item> priority = RepairResources.getPriorityItems();

        for (int nodeSlot = 0; nodeSlot < repairInv.getContainerSize(); nodeSlot++) {
            ItemStack dst = repairInv.getItem(nodeSlot);

            if (!dst.isEmpty() && dst.getCount() < dst.getMaxStackSize()) {
                int space = dst.getMaxStackSize() - dst.getCount();
                int moved = moveMatchingItem(src, repairInv, dst.getItem(), nodeSlot, space);
                if (moved > 0) continue;
            }

            if (dst.isEmpty()) {
                for (Item pref : priority) {
                    int moved = moveAnyOfItem(src, repairInv, pref, nodeSlot);
                    if (moved > 0) break;
                }
            }
        }
    }

    private static int moveMatchingItem(SimpleContainer src, SimpleContainer dst, Item item, int dstSlot, int maxToMove) {
        if (item == null || maxToMove <= 0) return 0;

        ItemStack dstStack = dst.getItem(dstSlot);
        if (!dstStack.isEmpty() && dstStack.getItem() != item) return 0;

        int movedTotal = 0;

        for (int i = 0; i < src.getContainerSize(); i++) {
            if (movedTotal >= maxToMove) break;

            ItemStack s = src.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() != item) continue;

            int canMove = Math.min(maxToMove - movedTotal, s.getCount());

            if (dstStack.isEmpty()) {
                int put = Math.min(canMove, s.getMaxStackSize());
                ItemStack newDst = new ItemStack(item, put);
                dst.setItem(dstSlot, newDst);
                dstStack = newDst;
                s.shrink(put);
                movedTotal += put;
            } else {
                int space = dstStack.getMaxStackSize() - dstStack.getCount();
                if (space <= 0) break;

                int put = Math.min(canMove, space);
                dstStack.grow(put);
                dst.setItem(dstSlot, dstStack);
                s.shrink(put);
                movedTotal += put;
            }

            src.setItem(i, s);
        }

        return movedTotal;
    }

    private static int moveAnyOfItem(SimpleContainer src, SimpleContainer dst, Item item, int dstSlot) {
        if (item == null) return 0;

        for (int i = 0; i < src.getContainerSize(); i++) {
            ItemStack s = src.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() != item) continue;

            int moveCount = Math.min(s.getCount(), s.getMaxStackSize());
            ItemStack moved = new ItemStack(item, moveCount);
            dst.setItem(dstSlot, moved);

            s.shrink(moveCount);
            src.setItem(i, s);

            return moveCount;
        }
        return 0;
    }

    // ----------------------------
    // UI (node menu)
    // ----------------------------
    public void openMenu(Player player) {
        if (level == null || level.isClientSide) return;

        FortressCenterBlockEntity center = TerritoryRules.getFortressCoveringForBreak(level, worldPosition);
        if (center != null) {
            center.ensureClaimedBy(player);
            if (!center.canOpen(player)) {
                player.displayClientMessage(Component.literal("Not privileged."), true);
                return;
            }
        }

        int nodeCount = TerritoryRules.computeNodeProtectedNonAirCount(level, worldPosition);
        setProtectedNonAirCountServer(nodeCount);

        if (center != null) {
            center.markNetProtectedDirty(5);
        }

        if (player instanceof ServerPlayer sp) {
            SimpleMenuProvider provider = new SimpleMenuProvider(
                    (containerId, playerInv, p) -> new FortressNodeMenu(containerId, playerInv, this),
                    Component.literal(getNodeTitle())
            );
            NetworkHooks.openScreen(sp, provider, buf -> buf.writeBlockPos(getBlockPos()));
        }
    }

    private String getNodeTitle() {
        return switch (this.tier) {
            case WOOD -> "Wood Fortress";
            case IRON -> "Iron Fortress";
            case DIAMOND -> "Diamond Fortress";
        };
    }

    // ----------------------------
    // NBT persistence
    // ----------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putFloat(TAG_HEALTH, health);
        tag.putInt(TAG_NODE_PROTECTED_NON_AIR, protectedNonAirCount);

        ListTag list = new ListTag();
        for (int i = 0; i < repairInv.getContainerSize(); i++) {
            ItemStack stack = repairInv.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag t = new CompoundTag();
                t.putInt("Slot", i);
                stack.save(t);
                list.add(t);
            }
        }
        tag.put(TAG_ITEMS, list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        this.tier = tierFromState(getBlockState());
        this.repairInv = makeInvForTier(this.tier);

        this.health = tag.contains(TAG_HEALTH, Tag.TAG_FLOAT)
                ? tag.getFloat(TAG_HEALTH)
                : (float) this.tier.maxHealth;

        this.protectedNonAirCount = tag.contains(TAG_NODE_PROTECTED_NON_AIR, Tag.TAG_INT)
                ? tag.getInt(TAG_NODE_PROTECTED_NON_AIR)
                : 0;

        this.clientHealth = this.health;

        for (int i = 0; i < repairInv.getContainerSize(); i++) {
            repairInv.setItem(i, ItemStack.EMPTY);
        }

        if (tag.contains(TAG_ITEMS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                int slot = t.getInt("Slot");
                if (slot >= 0 && slot < repairInv.getContainerSize()) {
                    repairInv.setItem(slot, ItemStack.of(t));
                }
            }
        }
    }

    // ----------------------------
    // Vanilla BE sync to client
    // ----------------------------

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_NODE_PROTECTED_NON_AIR, protectedNonAirCount);
        tag.putFloat(TAG_HEALTH, health);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        int blocks = tag.contains(TAG_NODE_PROTECTED_NON_AIR, Tag.TAG_INT) ? tag.getInt(TAG_NODE_PROTECTED_NON_AIR) : 0;
        float hp = tag.contains(TAG_HEALTH, Tag.TAG_FLOAT) ? tag.getFloat(TAG_HEALTH) : 0f;

        this.clientProtectedNonAirCount = Math.max(0, blocks);
        this.clientHealth = Math.max(0f, hp);
    }
}
