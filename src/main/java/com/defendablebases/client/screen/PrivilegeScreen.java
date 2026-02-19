package com.defendablebases.client.screen;

import com.defendablebases.FortressCenterBlockEntity;
import com.defendablebases.TerritoryRules;
import com.defendablebases.network.ModNetworking;
import com.defendablebases.network.PrivilegeListRequestPacket;
import com.defendablebases.network.PrivilegeUpdatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.UUID;

public class PrivilegeScreen extends Screen {

    private final Screen parent;
    private final FortressCenterBlockEntity be;

    private PrivilegeList list;
    private UUID selected;

    private Button removeBtn;

    public PrivilegeScreen(Screen parent, FortressCenterBlockEntity be) {
        super(Component.literal("Fortress Privileges"));
        this.parent = parent;
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();

        int listTop = 74;
        int listBottom = this.height - 64;

        this.list = new PrivilegeList(this.minecraft, this.width, this.height, listTop, listBottom, 20);
        this.addRenderableWidget(this.list);

        // Ask server for authoritative list every open
        ModNetworking.sendToServer(new PrivilegeListRequestPacket(be.getBlockPos()));

        // Add Online Player
        this.addRenderableWidget(Button.builder(Component.literal("Add Online Player"), btn -> {
            if (this.minecraft != null) this.minecraft.setScreen(new PlayerPickerScreen(this, be));
        }).bounds(this.width / 2 - 154, this.height - 54, 150, 20).build());

        // Remove Selected
        this.removeBtn = this.addRenderableWidget(Button.builder(Component.literal("Remove Selected"), btn -> {
            if (selected != null) {
                ModNetworking.sendToServer(new PrivilegeUpdatePacket(be.getBlockPos(), selected, false));
                selected = null;
                ModNetworking.sendToServer(new PrivilegeListRequestPacket(be.getBlockPos()));
            }
        }).bounds(this.width / 2 + 4, this.height - 54, 110, 20).build());

        // Refresh
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), btn -> {
            ModNetworking.sendToServer(new PrivilegeListRequestPacket(be.getBlockPos()));
        }).bounds(this.width / 2 + 118, this.height - 54, 72, 20).build());

        // Done
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            if (this.minecraft != null) this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 + 194, this.height - 54, 60, 20).build());

        refreshList();
    }

    public void refreshList() {
        if (this.list == null) return;

        Set<UUID> uuids = be.getPrivilegedCopy();
        this.list.setUUIDs(uuids);

        if (selected != null && !uuids.contains(selected)) {
            selected = null;
            this.list.setSelected(null);
        }

        updateButtonStates(uuids);
    }

    private void updateButtonStates(Set<UUID> uuids) {
        if (removeBtn == null || this.minecraft == null || this.minecraft.player == null) return;

        boolean hasSelection = selected != null;
        boolean selectedIsSelf = hasSelection && selected.equals(this.minecraft.player.getUUID());
        boolean lastOne = uuids.size() <= 1;

        // Current rule: can't remove yourself + can't remove last privileged
        removeBtn.active = hasSelection && !selectedIsSelf && !lastOne;
    }

    private String resolveName(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;

        PlayerInfo info = mc.getConnection().getPlayerInfo(id);
        if (info != null && info.getProfile() != null) {
            return info.getProfile().getName();
        }
        return null;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        gfx.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        Set<UUID> uuids = be.getPrivilegedCopy();

        int count = uuids.size();
        int max = be.getClientMaxPrivileged();
        gfx.drawCenteredString(this.font,
                Component.literal("Privileged players: " + count + " / " + max),
                this.width / 2, 26, 0xA0A0A0);

        // Territory radius (synced)
        int territoryRadius = be.getClientRadiusBlocks();

        // Break radius (fixed by fortress type; not synced)
        int breakRadius = TerritoryRules.getBreakRadiusBlocksForKind(
                TerritoryRules.getKindFromBlock(be.getBlockState().getBlock())
        );

        gfx.drawCenteredString(this.font,
                Component.literal("Territory radius: " + territoryRadius + " blocks"),
                this.width / 2, 42, 0xA0A0A0);

        gfx.drawCenteredString(this.font,
                Component.literal("Break radius: " + breakRadius + " blocks"),
                this.width / 2, 54, 0xA0A0A0);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    // ---------------------------
    // List implementation
    // ---------------------------
    private class PrivilegeList extends ObjectSelectionList<PrivilegeList.Entry> {

        public PrivilegeList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }

        public void setUUIDs(Set<UUID> uuids) {
            this.clearEntries();
            for (UUID id : uuids) {
                this.addEntry(new Entry(id));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 40;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width - 14;
        }

        class Entry extends ObjectSelectionList.Entry<Entry> {
            private final UUID id;

            Entry(UUID id) {
                this.id = id;
            }

            @Override
            public Component getNarration() {
                return Component.literal(id.toString());
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                PrivilegeList.this.setSelected(this);
                selected = id;
                updateButtonStates(be.getPrivilegedCopy());
                return true;
            }

            @Override
            public void render(GuiGraphics gfx, int index, int y, int x, int rowWidth, int rowHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {

                boolean isSelected = (PrivilegeList.this.getSelected() == this);

                String name = resolveName(id);
                String label = (name != null) ? (name + "  (" + id + ")") : id.toString();

                if (minecraft != null && minecraft.player != null && id.equals(minecraft.player.getUUID())) {
                    label = label + "  [You]";
                }

                int color = isSelected ? 0xFFFFA0 : 0xFFFFFF;
                gfx.drawString(Minecraft.getInstance().font, label, x + 4, y + 6, color, false);
            }
        }
    }
}
