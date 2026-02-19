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

public class PrivilegeUpdatePacket {
    public final BlockPos pos;
    public final UUID target;
    public final boolean add;

    public PrivilegeUpdatePacket(BlockPos pos, UUID target, boolean add) {
        this.pos = pos;
        this.target = target;
        this.add = add;
    }

    public static void encode(PrivilegeUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUUID(msg.target);
        buf.writeBoolean(msg.add);
    }

    public static PrivilegeUpdatePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID target = buf.readUUID();
        boolean add = buf.readBoolean();
        return new PrivilegeUpdatePacket(pos, target, add);
    }

    public static void handle(PrivilegeUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            BlockEntity be = sender.level().getBlockEntity(msg.pos);
            if (!(be instanceof FortressCenterBlockEntity fc)) return;

            // ✅ SERVER ENFORCEMENT: only the CENTER block can manage privileges
            if (fc.getBlockState().getBlock() != ModBlocks.FORTRESS_CENTER.get()) return;

            fc.ensureClaimedBy(sender);

            if (!fc.canModify(sender)) return;

            boolean changed = msg.add
                    ? fc.tryAddPrivileged(sender, msg.target)
                    : fc.tryRemovePrivileged(sender, msg.target);

            Set<UUID> list = fc.getPrivilegedCopy();
            int max = fc.getServerMaxPrivileged();
            int territoryRadius = fc.getServerRadiusBlocks();

            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sender),
                    new PrivilegeListSyncPacket(msg.pos, fc.isClaimed(), list, max, territoryRadius)
            );

            if (!changed) return;

            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> sender.serverLevel().getChunkAt(msg.pos)),
                    new PrivilegeListSyncPacket(msg.pos, fc.isClaimed(), list, max, territoryRadius)
            );
        });

        ctx.get().setPacketHandled(true);
    }
}
