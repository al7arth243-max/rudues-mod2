package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.FiendEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The summoned fiends: the flame spirit model (geo/entity/flame_spirit.geo.json). */
public class FiendRenderer extends GeoEntityRenderer<FiendEntity> {
    public FiendRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(new ResourceLocation(RuduesMod.MODID, "flame_spirit")));
        withScale(1.0F);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(FiendEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
    }
}
