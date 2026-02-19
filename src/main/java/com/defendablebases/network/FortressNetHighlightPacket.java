package com.defendablebases.network;

import com.defendablebases.client.ClientNetHighlightCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server->Client: send current net highlight data.
 *
 * Territory now sends:
 * - list of net node positions
 * - territory radius (XZ circle radius)
 * - base Y to render the territory outline
 *
 * Break protection still sends:
 * - node list + per-node break cube radius
 *
 * Client builds true union wireframes for both.
 */
public class FortressNetHighlightPacket {

    public static final class NodeEntry {
        public final BlockPos pos;
        public final int radius;

        public NodeEntry(BlockPos pos, int radius) {
            this.pos = pos;
            this.radius = radius;
        }
    }

    public final BlockPos centerPos;

    // Territory params
    public final List<BlockPos> territoryNodes;
    public final int territoryRadius;
    public final int territoryBaseY;

    // Break params
    public final List<NodeEntry> breakNodes;

    public final int durationTicks;

    public FortressNetHighlightPacket(
            BlockPos centerPos,
            List<BlockPos> territoryNodes,
            int territoryRadius,
            int territoryBaseY,
            List<NodeEntry> breakNodes,
            int durationTicks
    ) {
        this.centerPos = centerPos;
        this.territoryNodes = territoryNodes;
        this.territoryRadius = territoryRadius;
        this.territoryBaseY = territoryBaseY;
        this.breakNodes = breakNodes;
        this.durationTicks = durationTicks;
    }

    public static void encode(FortressNetHighlightPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.centerPos);

        // Territory nodes + radius + baseY
        buf.writeVarInt(msg.territoryNodes.size());
        for (BlockPos p : msg.territoryNodes) {
            buf.writeBlockPos(p);
        }
        buf.writeVarInt(msg.territoryRadius);
        buf.writeInt(msg.territoryBaseY);

        // Break nodes
        buf.writeVarInt(msg.breakNodes.size());
        for (NodeEntry e : msg.breakNodes) {
            buf.writeBlockPos(e.pos);
            buf.writeVarInt(e.radius);
        }

        buf.writeVarInt(msg.durationTicks);
    }

    public static FortressNetHighlightPacket decode(FriendlyByteBuf buf) {
        BlockPos center = buf.readBlockPos();

        int tn = buf.readVarInt();
        List<BlockPos> territoryNodes = new ArrayList<>(Math.max(0, tn));
        for (int i = 0; i < tn; i++) {
            territoryNodes.add(buf.readBlockPos());
        }
        int territoryRadius = buf.readVarInt();
        int territoryBaseY = buf.readInt();

        int bn = buf.readVarInt();
        List<NodeEntry> breakNodes = new ArrayList<>(Math.max(0, bn));
        for (int i = 0; i < bn; i++) {
            BlockPos p = buf.readBlockPos();
            int r = buf.readVarInt();
            breakNodes.add(new NodeEntry(p, r));
        }

        int dur = buf.readVarInt();
        return new FortressNetHighlightPacket(center, territoryNodes, territoryRadius, territoryBaseY, breakNodes, dur);
    }

    public static void handle(FortressNetHighlightPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            ClientNetHighlightCache.set(
                    msg.centerPos,
                    msg.territoryNodes,
                    msg.territoryRadius,
                    msg.territoryBaseY,
                    msg.breakNodes,
                    msg.durationTicks
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
