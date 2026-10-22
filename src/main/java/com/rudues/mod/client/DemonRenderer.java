package com.rudues.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.ability.HeroConstants;
import com.rudues.mod.entity.DemonEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.UUID;

/**
 * Renders the Hero of Hell demon.
 * Assets: geo/entity/infernal_titan.geo.json, textures/entity/infernal_titan.png,
 *         animations/entity/infernal_titan.animation.json
 */
public class DemonRenderer extends GeoEntityRenderer<DemonEntity> {
    public DemonRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(new ResourceLocation(RuduesMod.MODID, "infernal_titan")));
        // the titan model is ~5.4 blocks tall at scale 1; DEMON_SCALE makes it stand 7 blocks tall
        withScale(HeroConstants.DEMON_SCALE);
    }

    @Override
    public void render(DemonEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        UUID ownerId = entity.getOwnerUUID();
        Player owner = (ownerId == null || mc.level == null) ? null : mc.level.getPlayerByUUID(ownerId);

        // in first person the camera would be inside the demon's head, so hide your own demon there
        if (owner != null && owner == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        poseStack.pushPose();
        float yaw;
        if (owner != null) {
            // draw the demon exactly where its player is (no network lag) and facing where they aim
            Vec3 delta = owner.getPosition(partialTick).subtract(entity.getPosition(partialTick));
            poseStack.translate(delta.x, delta.y, delta.z);
            yaw = owner.getViewYRot(partialTick);
        } else {
            yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        }
        // GeckoLib does not turn non-living entities, so apply the turn here (net rotation = 180 - yaw)
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        // the obsidian texture is dark: render it fully lit so it never goes black
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
