package com.defendablebases.client.screen;

import com.defendablebases.DefendableBases;
import com.defendablebases.FortressBlockEntity;
import com.defendablebases.FortressNodeMenu;
import com.defendablebases.FortressTier;
import com.defendablebases.client.ClientNodeCountCache;
import com.defendablebases.network.FortressNodeCountRequestPacket;
import com.defendablebases.network.ModNetworking;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class FortressNodeScreen extends AbstractContainerScreen<FortressNodeMenu> {

    private static final ResourceLocation TEX_WOOD =
            ResourceLocation.fromNamespaceAndPath(DefendableBases.MODID, "textures/gui/fortress_wood.png");
    private static final ResourceLocation TEX_IRON =
            ResourceLocation.fromNamespaceAndPath(DefendableBases.MODID, "textures/gui/fortress_iron.png");
    private static final ResourceLocation TEX_DIAMOND =
            ResourceLocation.fromNamespaceAndPath(DefendableBases.MODID, "textures/gui/fortress_diamond.png");

    // ---- MUST match your exported PNG size ----
    private static final int GUI_W = 176;
    private static final int GUI_H = 166;

    // Title placement + scale (adjust these)
    private static final int TITLE_X = 12;
    private static final int TITLE_Y = 4;
    private static final float TITLE_SCALE = 0.75f;

    // Blocks text placement + scale (adjust these)
    private static final int BLOCKS_X = 58;
    private static final int BLOCKS_Y = 29;
    private static final float BLOCKS_SCALE = 0.75f;

    // Energy text placement + scale (adjust these)
    private static final int ENERGY_X = 16;
    private static final int ENERGY_Y = 15;
    private static final float ENERGY_SCALE = 0.65f;

    // Progress bar placement/size (adjust these)
    private static final int ENERGY_BAR_X = 66;
    private static final int ENERGY_BAR_Y = 15;
    private static final int ENERGY_BAR_W = 86;
    private static final int ENERGY_BAR_H = 4;

    // Bar colors (ARGB)
    private static final int BAR_BG = 0xFF2A2A2A;
    private static final int BAR_FG = 0xFFC6C6C6;
    private static final int BAR_BORDER = 0xFF8B8B8B;

    // Poll server every 20 ticks while UI is open so “Blocks” updates as you build
    private static final int REQUEST_PERIOD_TICKS = 20;

    // client-side throttling
    private long lastRequestGameTime = Long.MIN_VALUE;

    public FortressNodeScreen(FortressNodeMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);

        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;

        // Disable vanilla label rendering positions (we'll draw labels manually)
        this.titleLabelX = 0;
        this.titleLabelY = 0;
        this.inventoryLabelX = 0;
        this.inventoryLabelY = 0;
    }

    private ResourceLocation getTextureForTier(FortressTier tier) {
        if (tier == FortressTier.DIAMOND) return TEX_DIAMOND;
        if (tier == FortressTier.IRON) return TEX_IRON;
        return TEX_WOOD;
    }

    /** Draw text scaled without affecting any other UI elements. */
    private void drawScaledString(@NotNull GuiGraphics gfx, @NotNull Component text, int x, int y, int color, float scale) {
        gfx.pose().pushPose();
        gfx.pose().scale(scale, scale, 1.0f);
        int sx = (int) (x / scale);
        int sy = (int) (y / scale);
        gfx.drawString(this.font, text, sx, sy, color, false);
        gfx.pose().popPose();
    }

    private void drawEnergyBar(@NotNull GuiGraphics gfx, int x, int y, int current, int max) {
        if (max <= 0) max = 1;
        current = Math.max(0, Math.min(current, max));

        // Border
        gfx.fill(x - 1, y - 1, x + ENERGY_BAR_W + 1, y + ENERGY_BAR_H + 1, BAR_BORDER);

        // Background
        gfx.fill(x, y, x + ENERGY_BAR_W, y + ENERGY_BAR_H, BAR_BG);

        // Foreground fill
        int filled = (int) Math.floor((current / (double) max) * ENERGY_BAR_W);
        if (filled > 0) {
            gfx.fill(x, y, x + filled, y + ENERGY_BAR_H, BAR_FG);
        }
    }

    private void requestNodeCountOnce() {
        FortressBlockEntity be = this.menu.getBlockEntity();
        if (be == null) return;
        ModNetworking.sendToServer(new FortressNodeCountRequestPacket(be.getBlockPos()));
    }

    @Override
    protected void init() {
        super.init();
        lastRequestGameTime = Long.MIN_VALUE;
        requestNodeCountOnce(); // request immediately on open
    }

    @Override
    public void onClose() {
        // Optional: prevent stale cache if node breaks/changes while closed
        FortressBlockEntity be = this.menu.getBlockEntity();
        if (be != null) {
            ClientNodeCountCache.clear(be.getBlockPos());
        }
        super.onClose();
    }

    private void maybePollServerForNodeCount() {
        Minecraft mc = this.minecraft;
        if (mc == null || mc.level == null) return;

        long now = mc.level.getGameTime();
        if (now - lastRequestGameTime >= REQUEST_PERIOD_TICKS) {
            lastRequestGameTime = now;
            requestNodeCountOnce();
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // Poll here (tick() is final in your target)
        maybePollServerForNodeCount();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        FortressBlockEntity be = this.menu.getBlockEntity();
        FortressTier tier = (be != null) ? be.getTier() : FortressTier.WOOD;

        ResourceLocation tex = getTextureForTier(tier);

        gfx.blit(tex, this.leftPos, this.topPos,
                0, 0, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight);

        // Draw bar in screen-space (relative to GUI top-left)
        int hp = (be != null) ? be.getClientHealth() : 0;
        int maxHp = (be != null) ? be.getMaxHealth() : 1;

        drawEnergyBar(
                gfx,
                this.leftPos + ENERGY_BAR_X,
                this.topPos + ENERGY_BAR_Y,
                hp,
                maxHp
        );
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {
        // Title ("Wood Fortress" / "Iron Fortress" / "Diamond Fortress")
        drawScaledString(gfx, this.title, TITLE_X, TITLE_Y, 0x404040, TITLE_SCALE);

        // Node-only protected blocks (server authoritative via packet -> client cache)
        FortressBlockEntity be = this.menu.getBlockEntity();
        int blocks = 0;
        if (be != null) {
            blocks = ClientNodeCountCache.get(be.getBlockPos());
        }

        drawScaledString(gfx,
                Component.literal("Blocks:   " + blocks),
                BLOCKS_X, BLOCKS_Y, 0x404040, BLOCKS_SCALE);

        // Energy text
        int hp = (be != null) ? be.getClientHealth() : 0;
        int maxHp = (be != null) ? be.getMaxHealth() : 0;

        drawScaledString(gfx,
                Component.literal("Energy: " + hp + "/" + maxHp),
                ENERGY_X, ENERGY_Y, 0x8B8B8B, ENERGY_SCALE);
    }
}
