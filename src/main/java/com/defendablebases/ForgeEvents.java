package com.defendablebases;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

@Mod.EventBusSubscriber(modid = DefendableBases.MODID)
public final class ForgeEvents {
    private ForgeEvents() {}

    // Prevent recursion when WE destroy a fortress node (no-drop) from inside BreakEvent logic.
    private static final ThreadLocal<Boolean> INTERNAL_NODE_BREAK =
            ThreadLocal.withInitial(() -> false);

    // ----------------------------
    // Helpers
    // ----------------------------

    private static void cancelRightClick(PlayerInteractEvent.RightClickBlock event) {
        event.setUseItem(Event.Result.DENY);
        event.setUseBlock(Event.Result.DENY);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    /**
     * Force-send all player inventory slots to the client.
     * Overwrites client-side predicted decrements immediately.
     */
    private static void forceResyncInventory(ServerPlayer sp) {
        if (sp == null || sp.connection == null) return;

        sp.inventoryMenu.incrementStateId();
        int stateId = sp.inventoryMenu.getStateId();
        int containerId = sp.inventoryMenu.containerId;

        sp.connection.send(new ClientboundContainerSetSlotPacket(
                containerId, stateId, -1, sp.inventoryMenu.getCarried().copy()
        ));

        for (int i = 0; i < sp.inventoryMenu.slots.size(); i++) {
            ItemStack st = sp.inventoryMenu.slots.get(i).getItem();
            sp.connection.send(new ClientboundContainerSetSlotPacket(
                    containerId, stateId, i, st.copy()
            ));
        }
    }

    /** Forces client to correct predicted block state at this position. */
    private static void resyncBlock(ServerPlayer sp, Level level, BlockPos pos) {
        if (sp == null || level == null || pos == null) return;
        if (sp.connection == null) return;
        sp.connection.send(new ClientboundBlockUpdatePacket(level, pos));
    }

    private static boolean isPlacingBlock(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BlockItem);
    }

    private static Block getPlacedBlockFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        return bi.getBlock();
    }

    private static boolean isContainerBlock(BlockState state, BlockEntity be) {
        if (be instanceof net.minecraft.world.MenuProvider) return true;
        if (be instanceof net.minecraft.world.Container) return true;

        Block b = state.getBlock();
        return b instanceof ChestBlock
                || b instanceof BarrelBlock
                || b instanceof ShulkerBoxBlock
                || b instanceof HopperBlock;
    }

    /** Doors + redstone-ish "use" blocks should be privileged-only inside territory. */
    private static boolean isPrivOnlyInteractable(BlockState state) {
        Block b = state.getBlock();

        if (b instanceof DoorBlock) return true;
        if (b instanceof TrapDoorBlock) return true;
        if (b instanceof FenceGateBlock) return true;

        if (b instanceof ButtonBlock) return true;
        if (b instanceof LeverBlock) return true;

        if (b instanceof RepeaterBlock) return true;
        if (b instanceof ComparatorBlock) return true;
        if (b instanceof NoteBlock) return true;

        return false;
    }

    /** True for any of the 4 fortress blocks. */
    private static boolean isFortressBlock(Block b) {
        return b == ModBlocks.FORTRESS_CENTER.get()
                || b == ModBlocks.FORTRESS_WOOD.get()
                || b == ModBlocks.FORTRESS_IRON.get()
                || b == ModBlocks.FORTRESS_DIAMOND.get();
    }

    /** True for tier nodes only (wood/iron/diamond). */
    private static boolean isFortressNodeBlock(Block b) {
        return b == ModBlocks.FORTRESS_WOOD.get()
                || b == ModBlocks.FORTRESS_IRON.get()
                || b == ModBlocks.FORTRESS_DIAMOND.get();
    }

    /** Mark governing center dirty if this position is within any net break coverage. */
    private static void dirtyNetIfCovered(Level level, BlockPos pos, int delayTicks) {
        if (level == null || level.isClientSide) return;
        if (pos == null) return;

        FortressCenterBlockEntity center = TerritoryRules.getFortressCoveringForBreak(level, pos);
        if (center == null) return;

        if (center.getBlockState().getBlock() != ModBlocks.FORTRESS_CENTER.get()) return;

        center.markNetProtectedDirty(delayTicks);
    }

    /** Damage the held tool because the player attempted a protected break. */
    private static void damageToolOnCanceledBreak(ServerPlayer sp, int amount) {
        if (sp == null) return;
        if (sp.isCreative()) return;
        if (amount <= 0) return;

        ItemStack tool = sp.getMainHandItem();
        if (tool.isEmpty()) return;
        if (!tool.isDamageableItem()) return;

        tool.hurtAndBreak(amount, sp, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        forceResyncInventory(sp);
    }

    /** Destroy the fortress NODE only (no drop), safely (no recursion). */
    private static void destroyFortressNodeNoDrop(Level level, BlockPos nodePos) {
        if (level == null || level.isClientSide) return;
        if (nodePos == null) return;

        BlockState st = level.getBlockState(nodePos);
        if (!isFortressNodeBlock(st.getBlock())) return;

        try {
            INTERNAL_NODE_BREAK.set(true);
            level.destroyBlock(nodePos, false);
            FortressRegistry.removeFortress(level.dimension(), nodePos);
        } finally {
            INTERNAL_NODE_BREAK.set(false);
        }

        dirtyNetIfCovered(level, nodePos, 5);
    }

    /** Explosion pressure -> damage the protecting node. Returns true if absorbed. */
    private static boolean applyExplosionDamageToNode(Level level, BlockPos nodePos, float damage) {
        if (level == null || level.isClientSide) return false;
        if (nodePos == null) return false;

        BlockEntity be = level.getBlockEntity(nodePos);
        if (!(be instanceof FortressBlockEntity node)) return false;

        return node.applyProtectedBreakDamage(damage);
    }

    // ----------------------------
    // Right click: placement + interact rules
    // ----------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level == null) return;
        if (event.getEntity() == null) return;

        BlockPos clickedPos = event.getPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        BlockEntity clickedBE = level.getBlockEntity(clickedPos);

        FortressCenterBlockEntity fcUse = TerritoryRules.getFortressCoveringForUse(level, clickedPos);
        if (fcUse == null) return;

        boolean privileged = fcUse.canOpen(event.getEntity());

        // 1) Containers openable by anyone
        if (isContainerBlock(clickedState, clickedBE)) return;

        // 2) Priv-only interactables
        if (isPrivOnlyInteractable(clickedState)) {
            if (!privileged) {
                cancelRightClick(event);
                if (!level.isClientSide && event.getEntity() instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("Not privileged."), true);
                }
            } else {
                event.setUseItem(Event.Result.DENY);
                event.setUseBlock(Event.Result.ALLOW);
            }
            return;
        }

        // 3) Placement rules:
        //    - Normally: privileged-only inside territory
        //    - Exception: explosives (e.g., TNT) are allowed even for non-priv players
        ItemStack stack = event.getItemStack();
        if (isPlacingBlock(stack)) {
            Block placedBlock = getPlacedBlockFromStack(stack);
            boolean isExplosivePlacement = (placedBlock != null && Explosives.isExplosivePlaceable(placedBlock));

            BlockPos placePos = clickedPos.relative(event.getFace());

            FortressCenterBlockEntity fcClicked = TerritoryRules.getFortressCoveringForUse(level, clickedPos);
            FortressCenterBlockEntity fcPlace   = TerritoryRules.getFortressCoveringForUse(level, placePos);

            boolean inClaimed = (fcClicked != null) || (fcPlace != null);
            if (!inClaimed) return;

            // If it's an explosive, allow placement even if not privileged.
            if (isExplosivePlacement) return;

            boolean priv =
                    (fcClicked != null && fcClicked.canOpen(event.getEntity())) ||
                            (fcPlace   != null && fcPlace.canOpen(event.getEntity()));

            if (!priv) {
                cancelRightClick(event);

                if (!level.isClientSide && event.getEntity() instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.literal("Not privileged."), true);
                    forceResyncInventory(sp);
                    resyncBlock(sp, level, placePos);
                    resyncBlock(sp, level, clickedPos);
                }
            }
        }
    }

    // ----------------------------
    // Authoritative placement cancel + dirty counts
    // ----------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        Level level = sp.level();
        if (level.isClientSide) return;

        BlockPos placedPos = event.getPos();
        BlockState placedState = event.getPlacedBlock();

        FortressCenterBlockEntity fc = TerritoryRules.getFortressCoveringForUse(level, placedPos);
        if (fc != null && !fc.canOpen(sp)) {
            // Exception: explosives are allowed inside protected territory.
            if (Explosives.isExplosiveState(placedState)) {
                // still dirty counts since blocks change
                dirtyNetIfCovered(level, placedPos, 20);
                if (isFortressBlock(placedState.getBlock())) {
                    dirtyNetIfCovered(level, placedPos, 5);
                }
                return;
            }

            event.setCanceled(true);
            sp.displayClientMessage(Component.literal("Not privileged."), true);

            forceResyncInventory(sp);
            resyncBlock(sp, level, placedPos);
            return;
        }

        if (placedState.getBlock() == ModBlocks.FORTRESS_CENTER.get()) {
            FortressCenterBlockEntity territoryCover = TerritoryRules.getFortressCoveringForUse(level, placedPos);
            FortressCenterBlockEntity breakCover = TerritoryRules.getFortressCoveringForBreak(level, placedPos);

            FortressCenterBlockEntity cover = (territoryCover != null) ? territoryCover : breakCover;
            if (cover != null && !cover.getBlockPos().equals(placedPos)) {
                event.setCanceled(true);
                sp.displayClientMessage(Component.literal("Cannot place Fortress Center inside another net."), true);
                forceResyncInventory(sp);
                resyncBlock(sp, level, placedPos);
                return;
            }
        }

        if (isFortressBlock(placedState.getBlock())) {
            FortressRegistry.addFortress(level.dimension(), placedPos);

            if (placedState.getBlock() == ModBlocks.FORTRESS_CENTER.get()) {
                TerritoryRules.pruneNewestCenterIfNetHasMultipleCenters(level, placedPos);
            }
        }

        dirtyNetIfCovered(level, placedPos, 20);

        if (isFortressBlock(placedState.getBlock())) {
            dirtyNetIfCovered(level, placedPos, 5);
        }
    }

    // ----------------------------
    // Breaking blocks: protection + node health/damageDebt
    // ----------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null) return;

        Level level = event.getPlayer().level();
        if (level.isClientSide) return;

        if (Boolean.TRUE.equals(INTERNAL_NODE_BREAK.get())) return;

        BlockPos pos = event.getPos();

        FortressCenterBlockEntity fc = TerritoryRules.getFortressCoveringForBreak(level, pos);
        if (fc == null) return;

        boolean privileged = fc.canOpen(event.getPlayer());
        if (privileged) {
            dirtyNetIfCovered(level, pos, 20);

            BlockState state = event.getState();
            if (isFortressBlock(state.getBlock())) {
                FortressRegistry.removeFortress(level.dimension(), pos);
                dirtyNetIfCovered(level, pos, 5);
            }
            return;
        }

        BlockPos protectorPos = TerritoryRules.findBestProtectingNodeForBreak(level, pos);

        if (protectorPos == null) {
            boolean absorbedByCenter = fc.applyCenterProtectionDamage(1.0f);
            if (!absorbedByCenter) {
                return;
            }

            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("Protected."), true);
                damageToolOnCanceledBreak(sp, 1);
            }
            return;
        }

        BlockEntity be = level.getBlockEntity(protectorPos);
        if (!(be instanceof FortressBlockEntity node)) {
            boolean absorbedByCenter = fc.applyCenterProtectionDamage(1.0f);
            if (!absorbedByCenter) {
                return;
            }

            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("Protected."), true);
                damageToolOnCanceledBreak(sp, 1);
            }
            return;
        }

        float damage = 1.0f;

        boolean absorbed = node.applyProtectedBreakDamage(damage);

        if (absorbed) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.literal("Protected."), true);
                damageToolOnCanceledBreak(sp, 1);
            }
            return;
        }

        destroyFortressNodeNoDrop(level, protectorPos);
        dirtyNetIfCovered(level, pos, 5);
    }

    // ----------------------------
    // Explosions: protect blocks inside break protection volumes
    // ----------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level == null || level.isClientSide) return;

        Explosion explosion = event.getExplosion();
        if (explosion == null) return;

        var affected = event.getAffectedBlocks();
        if (affected == null || affected.isEmpty()) return;

        // Vanilla explosions default to 1.0 damage per protected block prevented.
        // Custom explosives can stamp their exploding entity with a higher value.
        final float DAMAGE_PER_BLOCK = Explosives.getDamagePerProtectedBlock(explosion);

        Iterator<BlockPos> it = affected.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();

            FortressCenterBlockEntity fc = TerritoryRules.getFortressCoveringForBreak(level, pos);
            if (fc == null) continue;

            BlockPos protectorPos = TerritoryRules.findBestProtectingNodeForBreak(level, pos);
            if (protectorPos == null) continue; // no node => allow vanilla break

            boolean absorbed = applyExplosionDamageToNode(level, protectorPos, DAMAGE_PER_BLOCK);

            if (absorbed) {
                // prevent block destruction
                it.remove();
                dirtyNetIfCovered(level, pos, 5);
            } else {
                // node failed -> destroy it, and allow this block to be destroyed normally
                destroyFortressNodeNoDrop(level, protectorPos);
                dirtyNetIfCovered(level, protectorPos, 5);
            }
        }
    }
}
