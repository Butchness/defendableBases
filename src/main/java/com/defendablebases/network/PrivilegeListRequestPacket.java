package com.defendablebases.network;

import com.defendablebases.FortressCenterBlockEntity;
import com.defendablebases.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class PrivilegeListRequestPacket {
    public final BlockPos pos;

    public PrivilegeListRequestPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PrivilegeListRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PrivilegeListRequestPacket decode(FriendlyByteBuf buf) {
        return new PrivilegeListRequestPacket(buf.readBlockPos());
    }

    public static void handle(PrivilegeListRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            BlockEntity be = sender.level().getBlockEntity(msg.pos);
            if (!(be instanceof FortressCenterBlockEntity fc)) return;

            // ✅ SERVER ENFORCEMENT: only the CENTER block can manage privileges
            if (fc.getBlockState().getBlock() != ModBlocks.FORTRESS_CENTER.get()) return;

            fc.ensureClaimedBy(sender);

            if (!fc.canModify(sender)) return;

            Set<UUID> list = fc.getPrivilegedCopy();
            int max = fc.getServerMaxPrivileged();
            int territoryRadius = fc.getServerRadiusBlocks();

            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sender),
                    new PrivilegeListSyncPacket(msg.pos, fc.isClaimed(), list, max, territoryRadius)
            );
        });

        ctx.get().setPacketHandled(true);
    }
}
