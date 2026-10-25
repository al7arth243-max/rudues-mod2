package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.GarudaEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Garuda at the Hero of Hell's size: her lime markings glow in a second additive pass. After the hellfire she is
 * charred (embers smouldering in the char) and her arms are gone.
 */
public class GarudaRenderer extends GeoEntityRenderer<GarudaEntity> {
    private static final ResourceLocation NORMAL = new ResourceLocation(RuduesMod.MODID, "textures/entity/garuda.png");
    private static final ResourceLocation BURNT = new ResourceLocation(RuduesMod.MODID, "textures/entity/garuda_burnt.png");
    private static final ResourceLocation GLOW = new ResourceLocation(RuduesMod.MODID, "textures/entity/garuda_glow.png");
    private static final ResourceLocation BURNT_GLOW = new ResourceLocation(RuduesMod.MODID, "textures/entity/garuda_burnt_glow.png");
    private static final String[] ARMS = {"right_upper_arm", "left_upper_arm"};

    public GarudaRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(new ResourceLocation(RuduesMod.MODID, "garuda")));
        withScale(GarudaEntity.SCALE);
        this.shadowRadius = 2.4F;
        addRenderLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(GarudaEntity e) {
        return e.burnt() ? BURNT : NORMAL;
    }

    @Override
    public boolean shouldRender(GarudaEntity e, Frustum frustum, double x, double y, double z) {
        return !e.hidden() && (GarudaSceneClient.active() || super.shouldRender(e, frustum, x, y, z));
    }

    @Override
    public void render(GarudaEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        if (e.hidden()) {
            return;
        }
        super.render(e, yaw, pt, ps, buffers, light);
    }

    @Override
    public void preRender(PoseStack poseStack, GarudaEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        boolean armless = animatable.burnt();
        for (String n : ARMS) {
            model.getBone(n).ifPresent(b -> show(b, !armless));
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    private static void show(GeoBone b, boolean on) {
        b.setHidden(!on);
        b.setChildrenHidden(!on);
    }

    @Override
    protected float getDeathMaxRotation(GarudaEntity animatable) {
        return 0.0F;
    }

    private static final class GlowLayer extends GeoRenderLayer<GarudaEntity> {
        GlowLayer(GarudaRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, GarudaEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            float k = 0.75F + 0.25F * Mth.sin((animatable.tickCount + partialTick) * 0.1F);
            RenderType glow = RenderType.eyes(animatable.burnt() ? BURNT_GLOW : GLOW);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, k, k, k, 1.0F);
            bufferSource.getBuffer(renderType);
        }
    }
}
