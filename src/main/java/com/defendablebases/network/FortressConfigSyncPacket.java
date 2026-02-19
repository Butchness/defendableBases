package com.defendablebases.network;

import com.defendablebases.client.ClientConfigCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FortressConfigSyncPacket {
    public final int maxPrivileged;

    public FortressConfigSyncPacket(int maxPrivileged) {
        this.maxPrivileged = maxPrivileged;
    }

    public static void encode(FortressConfigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maxPrivileged);
    }

    public static FortressConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new FortressConfigSyncPacket(buf.readInt());
    }

    public static void handle(FortressConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // client-side cache update
            ClientConfigCache.setMaxPrivileged(msg.maxPrivileged);
        });
        ctx.get().setPacketHandled(true);
    }
}
