package com.rudues.mod.client;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.entity.InfernalHeroEntity;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Each trial boss has its theme: it starts when the boss is near and alive, loops for as long as the fight lasts,
 * fades into the next boss's theme, and fades out when the fight is over. Vanilla music is held off meanwhile.
 * Played on the Music volume slider.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID, value = Dist.CLIENT)
public final class BossMusic {
    private static final double RANGE = 96.0;
    @Nullable
    private static Track playing;

    private BossMusic() {
    }

    /** a looping, non-positional track that can fade in and out */
    private static final class Track extends AbstractTickableSoundInstance {
        final SoundEvent event;
        float level;
        boolean fadingOut;

        Track(SoundEvent event) {
            super(event, SoundSource.MUSIC, RandomSource.create());
            this.event = event;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.01F;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        @Override
        public void tick() {
            level = fadingOut ? level - 0.025F : Math.min(1.0F, level + 0.05F);     // out over 2 s, in over 1 s
            this.volume = Math.max(0.0F, level);
            if (fadingOut && level <= 0.0F) {
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        SoundEvent want = null;
        if (mc.level != null && mc.player != null) {
            AABB box = mc.player.getBoundingBox().inflate(RANGE);
            if (!mc.level.getEntitiesOfClass(InfernalHeroEntity.class, box, LivingEntity::isAlive).isEmpty()) {
                want = ModSounds.HERO_THEME.get();
            } else if (!mc.level.getEntitiesOfClass(InfernalShadowEntity.class, box, LivingEntity::isAlive).isEmpty()) {
                want = ModSounds.SHADOW_THEME.get();
            } else if (!mc.level.getEntitiesOfClass(com.rudues.mod.entity.FeatheredKnightEntity.class, box, LivingEntity::isAlive).isEmpty()
                    || !mc.level.getEntitiesOfClass(com.rudues.mod.entity.GarudaEntity.class, box.inflate(32), LivingEntity::isAlive).isEmpty()) {
                want = ModSounds.BENEDIKTA_THEME.get();
            }
        }
        if (playing != null && playing.isStopped()) {
            playing = null;                    // faded out (or stopped by something else: it starts again)
        }
        if (playing != null && playing.event != want) {
            playing.fadingOut = true;          // the fight is over, or the next boss has risen
            if (playing.level <= 0.0F) {
                playing = null;
            }
        }
        if (want != null && (playing == null || playing.fadingOut && playing.event != want && playing.level < 0.35F)) {
            if (playing != null) {
                mc.getSoundManager().stop(playing);
            }
            playing = new Track(want);
            mc.getSoundManager().play(playing);
        }
        if (playing != null && !playing.fadingOut) {
            mc.getMusicManager().stopPlaying();      // no overworld/Nether music over the fight
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }
}
