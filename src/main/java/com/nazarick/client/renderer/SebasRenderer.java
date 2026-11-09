package com.nazarick.client.renderer;

import com.nazarick.NazarickMod;
import com.nazarick.client.model.SebasModel;
import com.nazarick.entity.SebasEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SebasRenderer extends MobRenderer<SebasEntity, SebasModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(NazarickMod.MODID, "textures/entity/sebas.png");

    public SebasRenderer(EntityRendererProvider.Context context) {
        super(context, new SebasModel(context.bakeLayer(SebasModel.LAYER)), 0.5F);

    }

    @Override
    public ResourceLocation getTextureLocation(SebasEntity entity) {
        return TEXTURE;
    }
}
