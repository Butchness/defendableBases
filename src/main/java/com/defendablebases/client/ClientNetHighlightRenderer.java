package com.defendablebases.client;

import com.defendablebases.DefendableBases;
import com.defendablebases.network.FortressNetHighlightRequestPacket;
import com.defendablebases.network.ModNetworking;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Set;

@Mod.EventBusSubscriber(modid = DefendableBases.MODID, value = Dist.CLIENT)
public final class ClientNetHighlightRenderer {
    private ClientNetHighlightRenderer() {}

    // Refresh net highlight while active so it updates after node breaks/placements
    private static final int REFRESH_PERIOD_TICKS = 20; // 1 second
    private static int refreshTimer = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!ClientNetHighlightCache.isActive()) {
            refreshTimer = 0;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        refreshTimer++;
        if (refreshTimer >= REFRESH_PERIOD_TICKS) {
            refreshTimer = 0;

            if (ClientNetHighlightCache.getCenterPos() != null) {
                ModNetworking.sendToServer(new FortressNetHighlightRequestPacket(ClientNetHighlightCache.getCenterPos()));
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!ClientNetHighlightCache.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.gameRenderer == null) return;

        Set<ClientNetHighlightCache.Edge> territoryEdges = ClientNetHighlightCache.getTerritoryEdges();
        Set<ClientNetHighlightCache.Edge> breakEdges = ClientNetHighlightCache.getBreakEdges();
        if (territoryEdges == null || breakEdges == null) return;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        // Territory union outline (WHITE)
        drawEdges(pose, buffers, territoryEdges, cam, 1f, 1f, 1f, 1f);

        // Break union outline (YELLOW)
        drawEdges(pose, buffers, breakEdges, cam, 1f, 1f, 0f, 1f);

        buffers.endBatch(RenderType.lines());
    }

    private static void drawEdges(PoseStack pose, MultiBufferSource buffers,
                                  Set<ClientNetHighlightCache.Edge> edges, Vec3 cam,
                                  float r, float g, float b, float a) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        Matrix4f mat = pose.last().pose();

        final double eps = 0.002;

        for (ClientNetHighlightCache.Edge e : edges) {
            float x1 = (float) (e.x1() - cam.x + (e.x1() < e.x2() ? -eps : eps));
            float y1 = (float) (e.y1() - cam.y);
            float z1 = (float) (e.z1() - cam.z + (e.z1() < e.z2() ? -eps : eps));

            float x2 = (float) (e.x2() - cam.x + (e.x2() < e.x1() ? -eps : eps));
            float y2 = (float) (e.y2() - cam.y);
            float z2 = (float) (e.z2() - cam.z + (e.z2() < e.z1() ? -eps : eps));

            vc.vertex(mat, x1, y1, z1).color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
            vc.vertex(mat, x2, y2, z2).color(r, g, b, a).normal(0f, 1f, 0f).endVertex();
        }
    }
}
