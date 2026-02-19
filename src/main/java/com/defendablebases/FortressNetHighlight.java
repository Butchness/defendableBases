package com.defendablebases;

import com.defendablebases.network.FortressNetHighlightPacket;
import com.defendablebases.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FortressNetHighlight {
    private FortressNetHighlight() {}

    private static final int DEFAULT_DURATION_TICKS = 20 * 6; // 6 seconds

    public static void trySendHighlight(Player player, Level level, BlockPos clickedPos) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (level == null || level.isClientSide) return;
        if (clickedPos == null) return;

        FortressCenterBlockEntity center = TerritoryRules.getFortressCoveringForBreak(level, clickedPos);
        if (center == null) {
            sp.displayClientMessage(Component.literal("No fortress net here."), true);
            return;
        }

        if (!center.canOpen(sp)) {
            sp.displayClientMessage(Component.literal("Not privileged."), true);
            return;
        }

        BlockPos centerPos = center.getBlockPos();
        Set<BlockPos> net = TerritoryRules.getNetForCenter(level, centerPos);
        if (net.isEmpty()) {
            sp.displayClientMessage(Component.literal("Net is empty."), true);
            return;
        }

        // Territory: send net node list + radius + a base Y for display.
        // Client will build a true union outline (dynamic, irregular shapes).
        int rT = Math.max(0, Config.fortressTerritoryRadius);
        int territoryBaseY = clickedPos.getY(); // you can change this later if you prefer centerPos.getY()

        List<BlockPos> territoryNodes = new ArrayList<>();
        for (BlockPos p : net) {
            // Only include loaded fortress positions (matches other net computations)
            if (!TerritoryRules.isFortressPosLoaded(level, p)) continue;
            territoryNodes.add(p.immutable());
        }
        if (territoryNodes.isEmpty()) {
            territoryNodes.add(centerPos.immutable());
        }

        // Break protection: send node list + radii (client will build true union surface wireframe)
        List<FortressNetHighlightPacket.NodeEntry> breakNodes = new ArrayList<>();

        for (BlockPos p : net) {
            if (!TerritoryRules.isFortressPosLoaded(level, p)) continue;

            int rB = TerritoryRules.getBreakRadiusAtLoaded(level, p);
            if (rB <= 0) continue;

            breakNodes.add(new FortressNetHighlightPacket.NodeEntry(p.immutable(), rB));
        }

        if (breakNodes.isEmpty()) {
            int r = TerritoryRules.getBreakRadiusAtLoaded(level, centerPos);
            if (r <= 0) r = 1;
            breakNodes.add(new FortressNetHighlightPacket.NodeEntry(centerPos.immutable(), r));
        }

        ModNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> sp),
                new FortressNetHighlightPacket(centerPos, territoryNodes, rT, territoryBaseY, breakNodes, DEFAULT_DURATION_TICKS)
        );
    }
}
