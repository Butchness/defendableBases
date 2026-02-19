package com.defendablebases;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FortressNodeMenu extends AbstractContainerMenu {
    private final FortressBlockEntity blockEntity;
    private final SimpleContainer container;

    // ---- Slot layout knobs (edit these to match your PNG/SVG) ----
    private static final int SLOT_Y = 41;

    private static final int SLOT_1_X = 80;      // wood/iron single slot
    private static final int SLOT_2A_X = 69;     // diamond slot 0
    private static final int SLOT_2B_X = 87;     // diamond slot 1

    // Player inventory default positions (vanilla-ish)
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    public FortressNodeMenu(int containerId, Inventory playerInv, FortressBlockEntity be) {
        super(ModMenus.FORTRESS_NODE_MENU.get(), containerId);

        this.blockEntity = be;
        this.container = be.getRepairInv();

        int slots = container.getContainerSize();

        if (slots == 1) {
            this.addSlot(new Slot(container, 0, SLOT_1_X, SLOT_Y));
        } else if (slots == 2) {
            this.addSlot(new Slot(container, 0, SLOT_2A_X, SLOT_Y));
            this.addSlot(new Slot(container, 1, SLOT_2B_X, SLOT_Y));
        } else {
            for (int i = 0; i < slots; i++) {
                this.addSlot(new Slot(container, i, SLOT_1_X + (i * 18), SLOT_Y));
            }
        }

        // Player inventory (3 rows x 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18,
                        PLAYER_INV_Y + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col,
                    PLAYER_INV_X + col * 18,
                    HOTBAR_Y));
        }
    }

    public FortressNodeMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBE(playerInv.player.level(), buf.readBlockPos()));
    }

    public FortressBlockEntity getBlockEntity() {
        return blockEntity;
    }

    private static FortressBlockEntity getBE(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FortressBlockEntity fb) return fb;
        throw new IllegalStateException("FortressBlockEntity not found at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Vanilla-like shift-click:
     * - From node slots -> player inventory/hotbar
     * - From player inventory -> node slots, else to hotbar
     * - From hotbar -> node slots, else to player inventory
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;

        if (index < 0 || index >= this.slots.size()) return empty;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return empty;

        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();

        int nodeSlots = this.container.getContainerSize();

        int playerInvStart = nodeSlots;
        int playerInvEnd = playerInvStart + 27; // exclusive
        int hotbarStart = playerInvEnd;
        int hotbarEnd = hotbarStart + 9;        // exclusive

        // Node -> Player
        if (index < nodeSlots) {
            if (!this.moveItemStackTo(stackInSlot, playerInvStart, hotbarEnd, true)) {
                return empty;
            }
        }
        // Player inventory -> Node, else Hotbar
        else if (index < playerInvEnd) {
            if (!this.moveItemStackTo(stackInSlot, 0, nodeSlots, false)) {
                if (!this.moveItemStackTo(stackInSlot, hotbarStart, hotbarEnd, false)) {
                    return empty;
                }
            }
        }
        // Hotbar -> Node, else Player inventory
        else if (index < hotbarEnd) {
            if (!this.moveItemStackTo(stackInSlot, 0, nodeSlots, false)) {
                if (!this.moveItemStackTo(stackInSlot, playerInvStart, playerInvEnd, false)) {
                    return empty;
                }
            }
        } else {
            return empty;
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stackInSlot.getCount() == original.getCount()) {
            return empty;
        }

        slot.onTake(player, stackInSlot);
        return original;
    }
}
