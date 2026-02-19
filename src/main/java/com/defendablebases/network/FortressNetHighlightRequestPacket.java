package com.defendablebases.network;

import com.defendablebases.FortressNetHighlight;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client->Server: ask to refresh the current net highlight for a center position.
 * Used to keep the outline correct when nodes are placed/broken while highlighting.
 */
public class FortressNetHighlightRequestPacket {
    public final BlockPos centerPos;

    public FortressNetHighlightRequestPacket(BlockPos centerPos) {
        this.centerPos = centerPos;
    }

    public static void encode(FortressNetHighlightRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.centerPos);
    }

    public static FortressNetHighlightRequestPacket decode(FriendlyByteBuf buf) {
        return new FortressNetHighlightRequestPacket(buf.readBlockPos());
    }

    public static void handle(FortressNetHighlightRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;

            Level level = sender.level();
            if (level == null || level.isClientSide) return;

            // Reuse the same server-side builder logic
            FortressNetHighlight.trySendHighlight(sender, level, msg.centerPos);
        });

        ctx.get().setPacketHandled(true);
    }
}
