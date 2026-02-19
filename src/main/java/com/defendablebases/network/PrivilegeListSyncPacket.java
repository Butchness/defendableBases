package com.defendablebases.network;

import com.defendablebases.FortressCenterBlockEntity;
import com.defendablebases.client.screen.PlayerPickerScreen;
import com.defendablebases.client.screen.PrivilegeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> Client privilege list sync.
 * NOTE: No "owner" concept is included here.
 */
public class PrivilegeListSyncPacket {
    public final BlockPos pos;
    public final boolean claimed;
    public final Set<UUID> privileged;
    public final int maxPrivileged;
    public final int territoryRadiusBlocks;

    public PrivilegeListSyncPacket(BlockPos pos, boolean claimed, Set<UUID> privileged, int maxPrivileged, int territoryRadiusBlocks) {
        this.pos = pos;
        this.claimed = claimed;
        this.privileged = privileged;
        this.maxPrivileged = maxPrivileged;
        this.territoryRadiusBlocks = territoryRadiusBlocks;
    }

    public static void encode(PrivilegeListSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.claimed);
        buf.writeInt(msg.maxPrivileged);
        buf.writeInt(msg.territoryRadiusBlocks);

        buf.writeInt(msg.privileged.size());
        for (UUID id : msg.privileged) {
            buf.writeUUID(id);
        }
    }

    public static PrivilegeListSyncPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean claimed = buf.readBoolean();
        int max = buf.readInt();
        int radius = buf.readInt();

        int n = buf.readInt();
        Set<UUID> set = new HashSet<>();
        for (int i = 0; i < n; i++) {
            set.add(buf.readUUID());
        }

        return new PrivilegeListSyncPacket(pos, claimed, set, max, radius);
    }

    public static void handle(PrivilegeListSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            BlockEntity be = mc.level.getBlockEntity(msg.pos);
            if (be instanceof FortressCenterBlockEntity fc) {
                // Expected BE client mirror API: (list, claimed, max, radius)
                fc.clientSetPrivileged(msg.privileged, msg.claimed, msg.maxPrivileged, msg.territoryRadiusBlocks);
            }

            if (mc.screen instanceof PrivilegeScreen ps) ps.refreshList();
            if (mc.screen instanceof PlayerPickerScreen pps) pps.refreshList();
        });

        ctx.get().setPacketHandled(true);
    }
}
