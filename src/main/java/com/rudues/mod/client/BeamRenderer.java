package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.ability.HeroConstants;
import com.rudues.mod.entity.BeamEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.UUID;

/** Draws the beam as three crossed glowing, scrolling planes (outer glow) plus a hot white-purple core. */
public class BeamRenderer extends EntityRenderer<BeamEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(RuduesMod.MODID, "textures/entity/purple_beam.png");

    public BeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(BeamEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true; // a long thin entity: never cull it by its tiny hitbox
    }

    @Override
    public ResourceLocation getTextureLocation(BeamEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(BeamEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        float length = entity.getLength();
        if (length < 0.2F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        UUID ownerId = entity.getOwnerUUID();
        Player owner = (ownerId == null || mc.level == null) ? null : mc.level.getPlayerByUUID(ownerId);

        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poseStack.pushPose();
        if (owner != null) {
            // follow the owner's live aim so the beam never lags behind the crosshair
            Vec3 look = owner.getViewVector(partialTick);
            Vec3 origin = owner.getPosition(partialTick).add(0, HeroConstants.BEAM_Y, 0).add(look.scale(HeroConstants.BEAM_FORWARD));
            Vec3 delta = origin.subtract(entity.getPosition(partialTick));
            poseStack.translate(delta.x, delta.y, delta.z);
            yaw = owner.getViewYRot(partialTick);
            pitch = owner.getViewXRot(partialTick);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        float time = entity.tickCount + partialTick;
        float pulse = 1.0F + 0.12F * Mth.sin(time * 1.9F);
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(TEXTURE));
        drawPlanes(poseStack, vc, length, 1.6F * pulse, -time * 0.30F, 255, 255, 255, 190);   // wide glow
        drawPlanes(poseStack, vc, length, 0.9F * pulse, -time * 0.55F, 235, 190, 255, 255);  // mid
        drawPlanes(poseStack, vc, length, 0.38F * pulse, -time * 0.90F, 255, 245, 255, 255);  // white-hot core
        poseStack.popPose();
    }

    private static void drawPlanes(PoseStack ps, VertexConsumer vc, float length, float halfWidth, float scroll,
                                   int r, int g, int b, int a) {
        float v1 = scroll + length * 0.5F;
        for (int k = 0; k < 3; k++) {
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(k * 60.0F));
            PoseStack.Pose pose = ps.last();
            Matrix4f m = pose.pose();
            Matrix3f n = pose.normal();
            // both windings so it shows from either side regardless of culling
            quad(vc, m, n, -halfWidth, 0, 0, 0, scroll, halfWidth, 0, 0, 1, scroll, halfWidth, 0, length, 1, v1, -halfWidth, 0, length, 0, v1, r, g, b, a);
            quad(vc, m, n, -halfWidth, 0, length, 0, v1, halfWidth, 0, length, 1, v1, halfWidth, 0, 0, 1, scroll, -halfWidth, 0, 0, 0, scroll, r, g, b, a);
            ps.popPose();
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4,
                             int r, int g, int b, int a) {
        vertex(vc, m, n, x1, y1, z1, u1, v1, r, g, b, a);
        vertex(vc, m, n, x2, y2, z2, u2, v2, r, g, b, a);
        vertex(vc, m, n, x3, y3, z3, u3, v3, r, g, b, a);
        vertex(vc, m, n, x4, y4, z4, u4, v4, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
