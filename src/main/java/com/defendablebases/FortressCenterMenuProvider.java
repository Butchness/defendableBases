package com.defendablebases;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class FortressCenterMenuProvider implements MenuProvider {
    private final FortressCenterBlockEntity be;

    public FortressCenterMenuProvider(FortressCenterBlockEntity be) {
        this.be = be;
    }

    @Override
    public Component getDisplayName() {
        return be.getDisplayName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new FortressCenterMenu(containerId, playerInv, be);
    }
}
