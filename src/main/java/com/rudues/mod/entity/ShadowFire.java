package com.rudues.mod.entity;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.network.Network;
import com.rudues.mod.network.PurpleFirePacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Shadow's purple fire. It replaces vanilla burning for everything the Infernal Shadow sets alight: it is drawn as
 * purple flames, and it hurts even those whom ordinary fire can't touch (the power's fire immunity, Fire Resistance),
 * because its damage type is not a fire type. Water, rain and powder snow still put it out.
 * <p>
 * The burn time is kept in the entity's persistent data; the client is told when an entity starts or stops burning.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID)
public final class ShadowFire {
    public static final ResourceKey<DamageType> BURN =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(RuduesMod.MODID, "shadow_burn"));
    private static final String KEY = "rudues_shadow_fire";
    /** damage every second while it burns */
    public static final float DAMAGE = 1.0F;

    private ShadowFire() {
    }

    /** sets an entity burning for at least this long (server side; anything but a living entity is ignored) */
    public static void ignite(Entity entity, int seconds) {
        if (entity.level().isClientSide || !(entity instanceof LivingEntity living) || !living.isAlive()
                || entity instanceof InfernalShadowEntity || entity.isInWaterRainOrBubble()) {
            return;
        }
        int was = entity.getPersistentData().getInt(KEY);
        entity.getPersistentData().putInt(KEY, Math.max(was, seconds * 20));
        if (was <= 0) {
            sync(entity, true);
        }
    }

    public static boolean isBurning(Entity entity) {
        return entity.getPersistentData().getInt(KEY) > 0;
    }

    public static void extinguish(Entity entity) {
        if (isBurning(entity)) {
            entity.getPersistentData().remove(KEY);
            sync(entity, false);
        }
    }

    private static DamageSource source(Entity entity) {
        return new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BURN));
    }

    private static void sync(Entity entity, boolean burning) {
        Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), new PurpleFirePacket(entity.getId(), burning));
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) {
            return;
        }
        int t = e.getPersistentData().getInt(KEY);
        if (t <= 0) {
            return;
        }
        if (!e.isAlive() || e.isInWaterRainOrBubble() || e.isInPowderSnow) {
            extinguish(e);
            return;
        }
        t--;
        if (t % 20 == 0) {
            e.hurt(source(e), DAMAGE);
        }
        if (t <= 0) {
            extinguish(e);
        } else {
            e.getPersistentData().putInt(KEY, t);
        }
    }

    /** someone who starts seeing a burning entity is told it is burning */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && isBurning(event.getTarget())) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PurpleFirePacket(event.getTarget().getId(), true));
        }
    }

    /** the player's own flames come back with them after logging in */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isBurning(player)) {
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PurpleFirePacket(player.getId(), true));
        }
    }
}
