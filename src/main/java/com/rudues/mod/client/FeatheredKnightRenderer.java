package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.FeatheredKnightEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The Feathered Knight on her own bendable rig, rapier and all. Her wings only exist in Semi Prime (pale skin,
 * platinum hair, the green cracks and eyes glowing in a second additive pass); the talons only while she has them out.
 */
public class FeatheredKnightRenderer extends GeoEntityRenderer<FeatheredKnightEntity> {
    private static final ResourceLocation NORMAL = new ResourceLocation(RuduesMod.MODID, "textures/entity/feathered_knight.png");
    private static final ResourceLocation SEMI = new ResourceLocation(RuduesMod.MODID, "textures/entity/feathered_knight_semi.png");
    private static final ResourceLocation GLOW = new ResourceLocation(RuduesMod.MODID, "textures/entity/feathered_knight_glow.png");
    private static final String[] WINGS = {"wing_right", "wing_left"};
    private static final String[] TALONS = {"talon_right", "talon_left"};

    public FeatheredKnightRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(new ResourceLocation(RuduesMod.MODID, "feathered_knight")));
        this.shadowRadius = 0.45F;
        addRenderLayer(new GlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(FeatheredKnightEntity animatable) {
        return animatable.isSemi() ? SEMI : NORMAL;
    }

    @Override
    public void preRender(PoseStack poseStack, FeatheredKnightEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        boolean semi = animatable.isSemi(), talons = animatable.talonsOut();
        for (String n : WINGS) {
            model.getBone(n).ifPresent(b -> show(b, semi));
        }
        for (String n : TALONS) {
            model.getBone(n).ifPresent(b -> show(b, talons));
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    private static void show(GeoBone b, boolean on) {
        b.setHidden(!on);
        b.setChildrenHidden(!on);
    }

    /** Semi Prime: the cracks and eyes, full bright and breathing */
    private static final class GlowLayer extends GeoRenderLayer<FeatheredKnightEntity> {
        GlowLayer(FeatheredKnightRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, FeatheredKnightEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!animatable.isSemi()) {
                return;
            }
            float k = 0.8F + 0.2F * Mth.sin((animatable.tickCount + partialTick) * 0.11F);
            RenderType glow = RenderType.eyes(GLOW);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, k, k, k, 1.0F);
            bufferSource.getBuffer(renderType);
        }
    }
}
