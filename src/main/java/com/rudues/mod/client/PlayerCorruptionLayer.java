package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rudues.mod.RuduesMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Infernal Shadow's growths on an awakened player: the same obsidian over the left arm and both legs, with its
 * molten veins drawn again additively so they burn. The cubes come from the Shadow's corruption bones (CorruptionMesh),
 * already placed in each vanilla limb's frame.
 */
public class PlayerCorruptionLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = new ResourceLocation(RuduesMod.MODID, "textures/entity/infernal_shadow_corruption.png");
    private static final ResourceLocation GLOW = new ResourceLocation(RuduesMod.MODID, "textures/entity/infernal_shadow_corruption_glow.png");
    private static float[] arm, leftLeg, rightLeg;

    public PlayerCorruptionLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static float[] parse(String s) {
        String[] parts = s.trim().split(" ");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i]);
        }
        return out;
    }

    private static void load() {
        if (arm == null) {
            arm = parse(CorruptionMesh.LEFT_ARM);
            leftLeg = parse(CorruptionMesh.LEFT_LEG);
            rightLeg = parse(CorruptionMesh.RIGHT_LEG);
        }
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!CorruptionClient.has(player.getId()) || player.isInvisible()) {
            return;
        }
        load();
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        float g = 0.66F + 0.14F * Mth.sin(ageInTicks * 0.11F);
        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        part(ps, model.leftArm, arm, base, light, 1.0F, false);
        part(ps, model.leftLeg, leftLeg, base, light, 1.0F, false);
        part(ps, model.rightLeg, rightLeg, base, light, 1.0F, false);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        part(ps, model.leftArm, arm, glow, LightTexture.FULL_BRIGHT, g, true);
        part(ps, model.leftLeg, leftLeg, glow, LightTexture.FULL_BRIGHT, g, true);
        part(ps, model.rightLeg, rightLeg, glow, LightTexture.FULL_BRIGHT, g, true);
    }

    /** one limb's growths, in that limb's frame (vanilla pivots and rotations as posed this frame) */
    private static void part(PoseStack ps, ModelPart limb, float[] mesh, VertexConsumer vc, int light, float c, boolean bothSides) {
        if (!limb.visible) {
            return;
        }
        ps.pushPose();
        limb.translateAndRotate(ps);
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int f = 0; f + 23 <= mesh.length; f += 23) {
            float nx = mesh[f + 20], ny = mesh[f + 21], nz = mesh[f + 22];
            for (int k = 0; k < 4; k++) {
                vertex(vc, m, n, mesh, f + k * 5, nx, ny, nz, light, c);
            }
            if (bothSides) {
                for (int k = 3; k >= 0; k--) {
                    vertex(vc, m, n, mesh, f + k * 5, -nx, -ny, -nz, light, c);
                }
            }
        }
        ps.popPose();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] mesh, int i, float nx, float ny, float nz, int light, float c) {
        vc.vertex(m, mesh[i] / 16.0F, mesh[i + 1] / 16.0F, mesh[i + 2] / 16.0F).color(c, c, c, 1.0F)
                .uv(mesh[i + 3] / 64.0F, mesh[i + 4] / 64.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
    }
}
