package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.world.ModDimensions;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Feathered Hold lies in mist: the fog closes in, thinner up on the battlements and the keep's roof. */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class CastleClient {
    private CastleClient() {
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != ModDimensions.CASTLE || event.getMode() != net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        double y = event.getCamera().getPosition().y;
        // in the forest the mist is thick; it thins as you climb
        float k = (float) Math.max(0.0, Math.min(1.0, (y - 70.0) / 100.0));
        float far = Math.min(event.getFarPlaneDistance(), 70.0F + 150.0F * k);
        event.setNearPlaneDistance(far * 0.05F);
        event.setFarPlaneDistance(far);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }
}
