package com.rudues.mod.events;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.ability.AbilityHandler;
import com.rudues.mod.ability.HeroConstants;
import com.rudues.mod.ability.HeroForm;
import com.rudues.mod.ability.PowerData;
import com.rudues.mod.registry.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RuduesMod.MODID)
public class CommonEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)) {
            return;
        }
        com.rudues.mod.ability.Pummel.tick(sp);
        if (!PowerData.has(sp)) {
            if (HeroForm.empowered(sp)) {
                HeroForm.tick(sp);          // the Hero of Hell form borrowed for the trial's last fight
            }
            return;
        }
        if (sp.isOnFire()) {
            sp.clearFire(); // the fiend's power makes you immune to catching fire
        }
        if (sp.tickCount % 20 == 0) {
            PowerData.applyEffects(sp);
        }
        HeroForm.tick(sp);
        com.rudues.mod.ability.SemiPrime.tick(sp);
    }

    /** While transformed the player's hitbox becomes demon-sized (both client and server run this). */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        try {
            if (event.getEntity() instanceof Player player
                    && ModEffects.HERO_OF_HELL.isPresent()
                    && (player.hasEffect(ModEffects.HERO_OF_HELL.get())
                        || player.level().isClientSide && com.rudues.mod.entity.DemonEntity.CLIENT_OWNERS.contains(player.getUUID()))
                    && (event.getPose() == Pose.STANDING || event.getPose() == Pose.CROUCHING)) {
                event.setNewSize(EntityDimensions.scalable(HeroConstants.DEMON_WIDTH, HeroConstants.DEMON_HEIGHT), false);
                // Player.getEyeHeight ignores the hitbox, so the eye height must be set explicitly
                event.setNewEyeHeight(event.getPose() == Pose.CROUCHING ? HeroConstants.DEMON_EYE - 0.4F : HeroConstants.DEMON_EYE);
            }
        } catch (RuntimeException ignored) {
            // this event can fire while the player object is still being constructed (effects not ready yet)
        }
    }

    /** No fire / lava damage for anyone carrying the power. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && PowerData.has(player)
                && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
        // nothing touches a player while the Shadow's awakening plays out
        if (event.getEntity() instanceof Player player && (com.rudues.mod.entity.InfernalShadowEntity.AWAKENING.contains(player.getUUID())
                    || com.rudues.mod.ability.SemiPrime.transforming(player))
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** a 7-block demon with a huge jump should not hurt himself landing */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && ModEffects.HERO_OF_HELL.isPresent()
                && player.hasEffect(ModEffects.HERO_OF_HELL.get())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(Player.PERSISTED_NBT_TAG, 10)) {
            event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG,
                    old.getCompound(Player.PERSISTED_NBT_TAG).copy());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            HeroForm.cleanup(sp);
            com.rudues.mod.ability.SemiPrime.cleanup(sp);
            com.rudues.mod.ability.Pummel.cancel(sp);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // the marker effect is saved with the player; make sure nobody logs in stuck as a demon
            sp.removeEffect(ModEffects.HERO_OF_HELL.get());
            sp.refreshDimensions();
            HeroForm.sync(sp);
            if (PowerData.has(sp)) {
                com.rudues.mod.registry.ModAdvancements.abilitiesGained(sp);      // bearers from before the advancement existed
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sp.removeEffect(ModEffects.HERO_OF_HELL.get());
            sp.refreshDimensions();
            if (PowerData.has(sp)) {
                PowerData.applyEffects(sp);
            }
            HeroForm.sync(sp);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            HeroForm.sync(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AbilityHandler.clear(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer sp) {
            HeroForm.cleanup(sp);
            com.rudues.mod.ability.SemiPrime.cleanup(sp);
            com.rudues.mod.ability.Pummel.cancel(sp);
        }
    }
}
