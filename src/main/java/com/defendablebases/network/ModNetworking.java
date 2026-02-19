package com.defendablebases.network;

import com.defendablebases.DefendableBases;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private ModNetworking() {}

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(DefendableBases.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int id = 0;

    public static void register() {
        CHANNEL.messageBuilder(PrivilegeListRequestPacket.class, id++)
                .encoder(PrivilegeListRequestPacket::encode)
                .decoder(PrivilegeListRequestPacket::decode)
                .consumerMainThread(PrivilegeListRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(PrivilegeListSyncPacket.class, id++)
                .encoder(PrivilegeListSyncPacket::encode)
                .decoder(PrivilegeListSyncPacket::decode)
                .consumerMainThread(PrivilegeListSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(PrivilegeUpdatePacket.class, id++)
                .encoder(PrivilegeUpdatePacket::encode)
                .decoder(PrivilegeUpdatePacket::decode)
                .consumerMainThread(PrivilegeUpdatePacket::handle)
                .add();

        CHANNEL.messageBuilder(ProtectedCountRequestPacket.class, id++)
                .encoder(ProtectedCountRequestPacket::encode)
                .decoder(ProtectedCountRequestPacket::decode)
                .consumerMainThread(ProtectedCountRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(ProtectedCountSyncPacket.class, id++)
                .encoder(ProtectedCountSyncPacket::encode)
                .decoder(ProtectedCountSyncPacket::decode)
                .consumerMainThread(ProtectedCountSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(FortressNodeCountRequestPacket.class, id++)
                .encoder(FortressNodeCountRequestPacket::encode)
                .decoder(FortressNodeCountRequestPacket::decode)
                .consumerMainThread(FortressNodeCountRequestPacket::handle)
                .add();

        CHANNEL.messageBuilder(FortressNodeCountSyncPacket.class, id++)
                .encoder(FortressNodeCountSyncPacket::encode)
                .decoder(FortressNodeCountSyncPacket::decode)
                .consumerMainThread(FortressNodeCountSyncPacket::handle)
                .add();

        // NEW: client->server refresh request while highlight active
        CHANNEL.messageBuilder(FortressNetHighlightRequestPacket.class, id++)
                .encoder(FortressNetHighlightRequestPacket::encode)
                .decoder(FortressNetHighlightRequestPacket::decode)
                .consumerMainThread(FortressNetHighlightRequestPacket::handle)
                .add();

        // UPDATED: now sends node list + radii (not just AABB)
        CHANNEL.messageBuilder(FortressNetHighlightPacket.class, id++)
                .encoder(FortressNetHighlightPacket::encode)
                .decoder(FortressNetHighlightPacket::decode)
                .consumerMainThread(FortressNetHighlightPacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
