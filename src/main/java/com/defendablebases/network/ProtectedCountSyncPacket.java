package com.defendablebases.network;

import com.defendablebases.FortressCenterBlockEntity;
import com.defendablebases.client.FortressCenterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ProtectedCountSyncPacket {
    public final BlockPos centerPos;
    public final int protectedNonAirCount;
    public final int woodCount;
    public final int ironCount;
    public final int diamondCount;
    public final int centerEnergy;

    public ProtectedCountSyncPacket(BlockPos centerPos, int protectedNonAirCount, int woodCount, int ironCount, int diamondCount, int centerEnergy) {
        this.centerPos = centerPos;
        this.protectedNonAirCount = protectedNonAirCount;
        this.woodCount = woodCount;
        this.ironCount = ironCount;
        this.diamondCount = diamondCount;
        this.centerEnergy = centerEnergy;
    }

    public static void encode(ProtectedCountSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.centerPos);
        buf.writeInt(msg.protectedNonAirCount);
        buf.writeInt(msg.woodCount);
        buf.writeInt(msg.ironCount);
        buf.writeInt(msg.diamondCount);
        buf.writeInt(msg.centerEnergy);
    }

    public static ProtectedCountSyncPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int protectedCount = buf.readInt();
        int wood = buf.readInt();
        int iron = buf.readInt();
        int diamond = buf.readInt();
        int centerEnergy = buf.readInt();
        return new ProtectedCountSyncPacket(pos, protectedCount, wood, iron, diamond, centerEnergy);
    }

    public static void handle(ProtectedCountSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            BlockEntity be = mc.level.getBlockEntity(msg.centerPos);
            if (be instanceof FortressCenterBlockEntity fc) {
                fc.clientSetNetStatsFromPacket(msg.protectedNonAirCount, msg.woodCount, msg.ironCount, msg.diamondCount);
                fc.clientSetCenterEnergyFromPacket(msg.centerEnergy);
            }

            if (mc.screen instanceof FortressCenterScreen) {
                // render reads live; no-op
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
