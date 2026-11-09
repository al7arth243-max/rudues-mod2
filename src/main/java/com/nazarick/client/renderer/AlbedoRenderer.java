package com.nazarick.client.renderer;

import com.nazarick.NazarickMod;
import com.nazarick.client.model.AlbedoModel;
import com.nazarick.entity.AlbedoEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AlbedoRenderer extends MobRenderer<AlbedoEntity, AlbedoModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(NazarickMod.MODID, "textures/entity/albedo.png");

    public AlbedoRenderer(EntityRendererProvider.Context context) {
        super(context, new AlbedoModel(context.bakeLayer(AlbedoModel.LAYER)), 0.5F);

    }

    @Override
    public ResourceLocation getTextureLocation(AlbedoEntity entity) {
        return TEXTURE;
    }
}
