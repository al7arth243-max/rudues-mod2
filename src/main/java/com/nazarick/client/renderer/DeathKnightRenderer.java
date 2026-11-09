package com.nazarick.client.renderer;

import com.nazarick.NazarickMod;
import com.nazarick.client.model.DeathKnightModel;
import com.nazarick.entity.DeathKnightEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class DeathKnightRenderer extends MobRenderer<DeathKnightEntity, DeathKnightModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(NazarickMod.MODID, "textures/entity/death_knight.png");

    public DeathKnightRenderer(EntityRendererProvider.Context context) {
        super(context, new DeathKnightModel(context.bakeLayer(DeathKnightModel.LAYER)), 1.1F);
        this.addLayer(new GlowLayer<>(this, new ResourceLocation(NazarickMod.MODID, "textures/entity/death_knight_glow.png")));
    }

    @Override
    public ResourceLocation getTextureLocation(DeathKnightEntity entity) {
        return TEXTURE;
    }
}
