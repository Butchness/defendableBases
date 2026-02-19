package com.defendablebases.client.screen;

import com.defendablebases.FortressCenterBlockEntity;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class PlayerPickerScreen extends Screen {

    private final Screen parent;
    private final FortressCenterBlockEntity be;

    private PlayerList list;
    private UUID selected;

    private Button addBtn;

    public PlayerPickerScreen(Screen parent, FortressCenterBlockEntity be) {
        super(Component.literal("Add Online Player"));
        this.parent = parent;
        this.be = be;
    }

    @Override
    protected void init() {
        super.init();

        int listTop = 40;
        int listBottom = this.height - 64;

        this.list = new PlayerList(this.minecraft, this.width, this.height, listTop, listBottom, 20);
        this.addRenderableWidget(this.list);

        ModNetworking.sendToServer(new PrivilegeListRequestPacket(be.getBlockPos()));

        this.addBtn = this.addRenderableWidget(Button.builder(Component.literal("Add Selected"), btn -> {
            if (selected != null) {
                ModNetworking.sendToServer(new PrivilegeUpdatePacket(be.getBlockPos(), selected, true));
                ModNetworking.sendToServer(new PrivilegeListRequestPacket(be.getBlockPos()));
            }
        }).bounds(this.width / 2 - 154, this.height - 54, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), btn -> {
            ModNetworking.sendToServer(new PrivilegeListRequestPacket(be.getBlockPos()));
            refreshList();
        }).bounds(this.width / 2 + 4, this.height - 54, 72, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            if (this.minecraft != null) this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 + 80, this.height - 54, 74, 20).build());

        refreshList();
    }

    public void refreshList() {
        if (this.list == null || this.minecraft == null || this.minecraft.getConnection() == null) return;

        Collection<PlayerInfo> online = this.minecraft.getConnection().getOnlinePlayers();
        ArrayList<PlayerInfo> players = new ArrayList<>(online);
        this.list.setPlayers(players);

        if (selected != null) {
            boolean stillOnline = players.stream().anyMatch(p -> p.getProfile() != null && selected.equals(p.getProfile().getId()));
            if (!stillOnline) {
                selected = null;
                this.list.setSelected(null);
            }
        }

        updateButtonState();
    }

    private boolean isTeamFull(Set<UUID> priv) {
        return priv.size() >= be.getClientMaxPrivileged();
    }

    private void updateButtonState() {
        if (addBtn == null) return;

        Set<UUID> priv = be.getPrivilegedCopy();
        boolean full = isTeamFull(priv);
        boolean hasSelection = selected != null;
        boolean already = hasSelection && priv.contains(selected);

        addBtn.active = hasSelection && !full && !already;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        gfx.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        Set<UUID> priv = be.getPrivilegedCopy();
        gfx.drawCenteredString(this.font,
                Component.literal("Team size: " + priv.size() + " / " + be.getClientMaxPrivileged()),
                this.width / 2, 26, 0xA0A0A0);

        if (isTeamFull(priv)) {
            gfx.drawCenteredString(this.font, Component.literal("Team full"), this.width / 2, 34, 0xFF4040);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private class PlayerList extends ObjectSelectionList<PlayerList.Entry> {

        public PlayerList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }

        public void setPlayers(ArrayList<PlayerInfo> players) {
            this.clearEntries();
            for (PlayerInfo info : players) {
                if (info == null || info.getProfile() == null) continue;
                UUID id = info.getProfile().getId();
                String name = info.getProfile().getName();
                this.addEntry(new Entry(id, name));
            }
        }

        @Override
        public int getRowWidth() { return this.width - 40; }

        @Override
        protected int getScrollbarPosition() { return this.width - 14; }

        class Entry extends ObjectSelectionList.Entry<Entry> {
            private final UUID id;
            private final String name;

            Entry(UUID id, String name) {
                this.id = id;
                this.name = name;
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                PlayerList.this.setSelected(this);
                selected = id;
                updateButtonState();
                return true;
            }

            @Override
            public void render(GuiGraphics gfx, int index, int y, int x, int rowWidth, int rowHeight,
                               int mouseX, int mouseY, boolean hovered, float partialTick) {

                boolean isSelected = (PlayerList.this.getSelected() == this);
                boolean alreadyPriv = be.getPrivilegedCopy().contains(id);

                String label = name + "  (" + id + ")";
                if (minecraft != null && minecraft.player != null && id.equals(minecraft.player.getUUID())) {
                    label = label + "  [You]";
                }
                if (alreadyPriv) {
                    label = label + "  [Already privileged]";
                }

                int color = isSelected ? 0xFFFFA0 : (alreadyPriv ? 0xA0A0A0 : 0xFFFFFF);
                gfx.drawString(Minecraft.getInstance().font, label, x + 4, y + 6, color, false);
            }
        }
    }
}
