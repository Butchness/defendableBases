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

public class FortressNodeCountRequestPacket {
    public final BlockPos nodePos;

    public FortressNodeCountRequestPacket(BlockPos nodePos) {
        this.nodePos = nodePos;
    }

    public static void encode(FortressNodeCountRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.nodePos);
    }

    public static FortressNodeCountRequestPacket decode(FriendlyByteBuf buf) {
        return new FortressNodeCountRequestPacket(buf.readBlockPos());
    }

    public static void handle(FortressNodeCountRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Level level = sender.level();
            BlockEntity raw = level.getBlockEntity(msg.nodePos);
            if (raw == null) return; // node must exist & be loaded

            // Privilege gate uses governing center
            FortressCenterBlockEntity center = TerritoryRules.getFortressCoveringForBreak(level, msg.nodePos);
            if (center == null) return;
            if (!center.canOpen(sender)) return;

            int nodeCount = TerritoryRules.computeNodeProtectedNonAirCount(level, msg.nodePos);

            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sender),
                    new FortressNodeCountSyncPacket(msg.nodePos, nodeCount)
            );
        });

        ctx.get().setPacketHandled(true);
    }
}
