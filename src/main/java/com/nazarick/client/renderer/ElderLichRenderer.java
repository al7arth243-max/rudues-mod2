package com.nazarick.client.renderer;

import com.nazarick.NazarickMod;
import com.nazarick.client.model.ElderLichModel;
import com.nazarick.entity.ElderLichEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class ElderLichRenderer extends MobRenderer<ElderLichEntity, ElderLichModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(NazarickMod.MODID, "textures/entity/elder_lich.png");

    public ElderLichRenderer(EntityRendererProvider.Context context) {
        super(context, new ElderLichModel(context.bakeLayer(ElderLichModel.LAYER)), 0.5F);
        this.addLayer(new GlowLayer<>(this, new ResourceLocation(NazarickMod.MODID, "textures/entity/elder_lich_glow.png")));
    }

    @Override
    public ResourceLocation getTextureLocation(ElderLichEntity entity) {
        return TEXTURE;
    }
}
