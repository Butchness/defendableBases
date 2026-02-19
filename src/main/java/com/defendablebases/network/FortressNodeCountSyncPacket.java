package com.defendablebases.network;

import com.defendablebases.client.ClientNodeCountCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FortressNodeCountSyncPacket {
    public final BlockPos nodePos;
    public final int nodeProtectedNonAir;

    public FortressNodeCountSyncPacket(BlockPos nodePos, int nodeProtectedNonAir) {
        this.nodePos = nodePos;
        this.nodeProtectedNonAir = nodeProtectedNonAir;
    }

    public static void encode(FortressNodeCountSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.nodePos);
        buf.writeInt(msg.nodeProtectedNonAir);
    }

    public static FortressNodeCountSyncPacket decode(FriendlyByteBuf buf) {
        return new FortressNodeCountSyncPacket(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(FortressNodeCountSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // store in client cache; screen reads from cache
            ClientNodeCountCache.set(msg.nodePos, msg.nodeProtectedNonAir);

            // optional: if you later want to force a redraw, you could poke the screen here,
            // but it's not required because render will read cache each frame.
            Minecraft.getInstance();
        });
        ctx.get().setPacketHandled(true);
    }
}
