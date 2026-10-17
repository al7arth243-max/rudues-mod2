package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.network.CastAbilityPacket;
import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModEffects;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import com.rudues.mod.ability.HeroConstants;
import net.minecraft.client.Camera;
import java.lang.reflect.Method;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {
    private static boolean wheelWasDown = false;
    private static boolean wasDemon = false;
    private static Method cameraMove;
    private static boolean cameraLookedUp = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            wasDemon = false;
            return;
        }

        if (ClientPowerState.heroTicks > 0) {
            ClientPowerState.heroTicks--;
        }
        if (ClientPowerState.hasPowers && mc.player.isOnFire()) {
            mc.player.clearFire();
        }

        // when the local player turns into (or back from) the demon: resize hitbox, show them the demon
        boolean demon = mc.player.hasEffect(ModEffects.HERO_OF_HELL.get());
        if (demon != wasDemon) {
            wasDemon = demon;
            mc.player.refreshDimensions();
            if (demon) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }

        // hold R -> skill wheel (rising edge only, so it does not reopen while R is still held)
        boolean rDown = ClientKeys.isHeld(ClientKeys.OPEN_WHEEL);
        if (rDown && !wheelWasDown && mc.screen == null && ClientPowerState.hasPowers) {
            mc.setScreen(new SkillWheelScreen());
        }
        wheelWasDown = rDown;
        while (ClientKeys.OPEN_WHEEL.consumeClick()) {
            // drain
        }

        // universal cast button
        while (ClientKeys.CAST_SELECTED.consumeClick()) {
            if (mc.screen == null && ClientPowerState.hasPowers) {
                Network.CHANNEL.sendToServer(new CastAbilityPacket(ClientPowerState.getSelected().ordinal()));
            }
        }
    }

    /** The player is drawn as the GeckoLib demon instead of the normal player model. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().hasEffect(ModEffects.HERO_OF_HELL.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.hasEffect(ModEffects.HERO_OF_HELL.get())) {
            event.setCanceled(true);
        }
    }

    /** No burning-screen overlay for anyone carrying the power. */
    @SubscribeEvent
    public static void onScreenEffect(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.FIRE && ClientPowerState.hasPowers) {
            event.setCanceled(true);
        }
    }

    /** The demon is huge: pull the third-person camera back and up so you can actually see. */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.getCameraType().isFirstPerson()
                || !mc.player.hasEffect(ModEffects.HERO_OF_HELL.get())) {
            return;
        }
        Method move = findCameraMove();
        if (move == null) {
            return;
        }
        try {
            move.invoke(event.getCamera(), -HeroConstants.CAMERA_EXTRA, 1.5D, 0.0D);
        } catch (Exception ignored) {
            // leave the camera alone if anything goes wrong
        }
    }

    private static Method findCameraMove() {
        if (!cameraLookedUp) {
            cameraLookedUp = true;
            // runtime (obfuscated) name first, then the dev-environment name
            for (String name : new String[]{"m_90568_", "move"}) {
                try {
                    Method m = Camera.class.getDeclaredMethod(name, double.class, double.class, double.class);
                    m.setAccessible(true);
                    cameraMove = m;
                    break;
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return cameraMove;
    }
}
