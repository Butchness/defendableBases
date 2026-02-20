package com.defendablebases.client;

import com.defendablebases.DefendableBases;
import com.defendablebases.FortressCenterBlockEntity;
import com.defendablebases.FortressCenterMenu;
import com.defendablebases.client.screen.PrivilegeScreen;
import com.defendablebases.network.ModNetworking;
import com.defendablebases.network.ProtectedCountRequestPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class FortressCenterScreen extends AbstractContainerScreen<FortressCenterMenu> {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(DefendableBases.MODID, "textures/gui/fortress_center.png");

    private static final int GUI_W = 176;
    private static final int GUI_H = 240;

    private Button privilegeButton;
    private Button refreshButton;

    private static final int ENERGY_TEXT_X = 98;
    private static final int ENERGY_TEXT_Y = 6;
    private static final float ENERGY_TEXT_SCALE = 0.65f;

    private static final int ENERGY_BAR_X = 98;
    private static final int ENERGY_BAR_Y = 12;
    private static final int ENERGY_BAR_W = 64;
    private static final int ENERGY_BAR_H = 4;

    private static final int BAR_BG = 0xFF2A2A2A;
    private static final int BAR_FG = 0xFFC6C6C6;
    private static final int BAR_BORDER = 0xFF8B8B8B;

    private static final int REQUEST_PERIOD_TICKS = 20;
    private long lastRequestGameTime = Long.MIN_VALUE;

    public FortressCenterScreen(FortressCenterMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);

        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;

        this.titleLabelX = 0;
        this.titleLabelY = 0;
        this.inventoryLabelX = 0;
        this.inventoryLabelY = 0;
    }

    @Override
    protected void init() {
        super.init();

        lastRequestGameTime = Long.MIN_VALUE;

        int btnX = this.leftPos + 100;

        this.privilegeButton = Button.builder(Component.literal("Privileges"), btn -> {
                    if (this.minecraft == null) return;
                    this.minecraft.setScreen(new PrivilegeScreen(this, this.menu.getBlockEntity()));
                })
                .bounds(btnX, this.topPos + 26, 64, 16)
                .build();
        this.addRenderableWidget(this.privilegeButton);

        this.refreshButton = Button.builder(Component.literal("Refresh"), btn -> {
                    FortressCenterBlockEntity be = this.menu.getBlockEntity();
                    if (be == null) return;
                    ModNetworking.sendToServer(new ProtectedCountRequestPacket(be.getBlockPos()));
                })
                .bounds(btnX, this.topPos + 44, 64, 16)
                .build();
        this.addRenderableWidget(this.refreshButton);

        // request privilege list + protected count on open
        FortressCenterBlockEntity be = this.menu.getBlockEntity();
        if (be != null) {
            ModNetworking.sendToServer(new com.defendablebases.network.PrivilegeListRequestPacket(be.getBlockPos()));
            requestCenterStatsOnce();
        }
    }

    private void requestCenterStatsOnce() {
        FortressCenterBlockEntity be = this.menu.getBlockEntity();
        if (be == null) return;
        ModNetworking.sendToServer(new ProtectedCountRequestPacket(be.getBlockPos()));
    }

    private void maybePollServerForCenterStats() {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.level == null) return;

        long now = mc.level.getGameTime();
        if (now - lastRequestGameTime >= REQUEST_PERIOD_TICKS) {
            lastRequestGameTime = now;
            requestCenterStatsOnce();
        }
    }

    /** Draw text scaled without affecting any other UI elements. */
    private void drawScaledString(GuiGraphics gfx, Component text, int x, int y, int color, float scale) {
        gfx.pose().pushPose();
        gfx.pose().scale(scale, scale, 1.0f);
        int sx = (int) (x / scale);
        int sy = (int) (y / scale);
        gfx.drawString(this.font, text, sx, sy, color, false);
        gfx.pose().popPose();
    }


    private void drawEnergyBar(GuiGraphics gfx, int x, int y, int current, int max) {
        if (max <= 0) max = 1;
        current = Math.max(0, Math.min(current, max));

        gfx.fill(x - 1, y - 1, x + ENERGY_BAR_W + 1, y + ENERGY_BAR_H + 1, BAR_BORDER);
        gfx.fill(x, y, x + ENERGY_BAR_W, y + ENERGY_BAR_H, BAR_BG);

        int filled = (int) Math.floor((current / (double) max) * ENERGY_BAR_W);
        if (filled > 0) {
            gfx.fill(x, y, x + filled, y + ENERGY_BAR_H, BAR_FG);
        }
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        maybePollServerForCenterStats();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        gfx.blit(TEX, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight);

        gfx.drawString(this.font, Component.literal("Fortress Inventory"),
                this.leftPos + 8, this.topPos + 79, 0x404040, false);

        gfx.drawString(this.font, Component.literal("Player Inventory"),
                this.leftPos + 8, this.topPos + 147, 0x404040, false);

        FortressCenterBlockEntity be = this.menu.getBlockEntity();

        String netId = (be != null) ? be.getBlockPos().toShortString() : "?";

        drawScaledString(
                gfx,
                Component.literal(netId),
                this.leftPos + 54,
                this.topPos + 68,
                0xFFFFFF,
                0.4f
        );

        if (be != null) {
            int count = be.getPrivilegedCopy().size();
            int max = be.getClientMaxPrivileged();

            int energy = be.getClientCenterEnergy();
            int maxEnergy = be.getCenterMaxEnergy();

            drawScaledString(gfx,
                    Component.literal("Energy: " + energy + "/" + maxEnergy),
                    this.leftPos + ENERGY_TEXT_X, this.topPos + ENERGY_TEXT_Y, 0x8B8B8B, ENERGY_TEXT_SCALE);

            drawEnergyBar(gfx,
                    this.leftPos + ENERGY_BAR_X,
                    this.topPos + ENERGY_BAR_Y,
                    energy,
                    maxEnergy);

            // Scaled "Privileged: #/#"
            drawScaledString(gfx,
                    Component.literal("Privileged: " + count + "/" + max),
                    this.leftPos + 98, this.topPos + 19, 0x404040, 0.85f);

            // Blocks count (net union)
            int blocks = be.getClientProtectedNonAirCount();
            drawScaledString(gfx,
                    Component.literal("Blocks:      " + blocks),
                    this.leftPos + 6, this.topPos + 45, 0x404040, 0.75f);

            // Decay hours remaining (fuel vs current block cost)
            String decayLine = be.getClientDecayHoursRemainingString();
            drawScaledString(gfx,
                    Component.literal(decayLine),
                    this.leftPos + 6, this.topPos + 55, 0x404040, 0.75f);

            // Net composition counts
            int wood = be.getClientNetWoodCount();
            int iron = be.getClientNetIronCount();
            int diamond = be.getClientNetDiamondCount();

            float s = 0.60f;

            drawScaledString(gfx, Component.literal(Integer.toString(wood)),
                    this.leftPos + 18, this.topPos + 25, 0x404040, s);

            drawScaledString(gfx, Component.literal(Integer.toString(iron)),
                    this.leftPos + 42, this.topPos + 25, 0x404040, s);

            drawScaledString(gfx, Component.literal(Integer.toString(diamond)),
                    this.leftPos + 66, this.topPos + 25, 0x404040, s);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // Intentionally empty: we draw labels manually.
    }
}
