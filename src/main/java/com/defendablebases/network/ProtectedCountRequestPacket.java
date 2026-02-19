package com.defendablebases.network;

import com.defendablebases.FortressCenterBlockEntity;
import com.defendablebases.TerritoryRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class ProtectedCountRequestPacket {
    public final BlockPos pos;

    public ProtectedCountRequestPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(ProtectedCountRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static ProtectedCountRequestPacket decode(FriendlyByteBuf buf) {
        return new ProtectedCountRequestPacket(buf.readBlockPos());
    }

    public static void handle(ProtectedCountRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Level level = sender.level();
            BlockEntity raw = level.getBlockEntity(msg.pos);

            // Must be one of our fortresses; for now you’re calling from the center menu
            if (!(raw instanceof FortressCenterBlockEntity)) return;

            FortressCenterBlockEntity center = TerritoryRules.getFortressCoveringForBreak(level, msg.pos);
            if (center == null) return;

            if (!center.canOpen(sender)) return;

            TerritoryRules.NetStats stats = TerritoryRules.computeNetStats(level, center.getBlockPos());

            // Store on server + update tag sync (nice to keep world consistent)
            center.setNetStatsServer(stats.protectedNonAir, stats.woodCount, stats.ironCount, stats.diamondCount);

            // ALSO send explicit packet so the UI updates immediately even if tag sync is delayed
            ProtectedCountSyncPacket pkt = new ProtectedCountSyncPacket(
                    center.getBlockPos(),
                    stats.protectedNonAir,
                    stats.woodCount,
                    stats.ironCount,
                    stats.diamondCount
            );

            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), pkt);

            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> sender.serverLevel().getChunkAt(center.getBlockPos())),
                    pkt
            );
        });

        ctx.get().setPacketHandled(true);
    }
}
