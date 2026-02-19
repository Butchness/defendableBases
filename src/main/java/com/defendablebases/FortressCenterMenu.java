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

public class FortressCenterMenu extends AbstractContainerMenu {
    private final SimpleContainer container;
    private final FortressCenterBlockEntity blockEntity;

    // --- Layout constants (matches your SVG, scaled down by /2) ---
    private static final int FORTRESS_SLOTS_X = 8;
    private static final int FORTRESS_SLOTS_Y = 90;

    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 158;

    private static final int HOTBAR_X = 8;
    private static final int HOTBAR_Y = 216;

    public FortressCenterMenu(int containerId, Inventory playerInv, FortressCenterBlockEntity be) {
        super(ModMenus.FORTRESS_CENTER_MENU.get(), containerId);
        this.blockEntity = be;
        this.container = be.getInventory();

        // 27 container slots (3 rows x 9)
        int index = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(container, index++, FORTRESS_SLOTS_X + col * 18, FORTRESS_SLOTS_Y + row * 18));
            }
        }

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, HOTBAR_X + col * 18, HOTBAR_Y));
        }
    }

    public FortressCenterMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBE(playerInv.player.level(), buf.readBlockPos()));
    }

    public FortressCenterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    private static FortressCenterBlockEntity getBE(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FortressCenterBlockEntity fc) return fc;
        throw new IllegalStateException("FortressCenterBlockEntity not found at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Vanilla-like shift-click:
     * - From center container -> player inventory/hotbar
     * - From player inventory -> center container, else hotbar
     * - From hotbar -> center container, else player inventory
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack empty = ItemStack.EMPTY;

        if (index < 0 || index >= this.slots.size()) return empty;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return empty;

        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();

        final int containerSlots = 27;

        final int playerInvStart = containerSlots;
        final int playerInvEnd = playerInvStart + 27; // exclusive
        final int hotbarStart = playerInvEnd;
        final int hotbarEnd = hotbarStart + 9;        // exclusive

        // Container -> Player
        if (index < containerSlots) {
            if (!this.moveItemStackTo(stackInSlot, playerInvStart, hotbarEnd, true)) {
                return empty;
            }
        }
        // Player inventory -> Container, else Hotbar
        else if (index < playerInvEnd) {
            if (!this.moveItemStackTo(stackInSlot, 0, containerSlots, false)) {
                if (!this.moveItemStackTo(stackInSlot, hotbarStart, hotbarEnd, false)) {
                    return empty;
                }
            }
        }
        // Hotbar -> Container, else Player inventory
        else if (index < hotbarEnd) {
            if (!this.moveItemStackTo(stackInSlot, 0, containerSlots, false)) {
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
