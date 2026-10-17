package com.rudues.mod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

/** One class for all four Rudues particles (glowing, full-bright, animated sprites). */
public class PurpleParticle extends TextureSheetParticle {
    public enum Kind { FLAME, FLAME_BIG, SPARK, EMBER }

    private final SpriteSet sprites;
    private final Kind kind;
    private float baseSize;

    protected PurpleParticle(ClientLevel level, double x, double y, double z, double xs, double ys, double zs,
                             SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.kind = kind;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = xs;
        this.yd = ys;
        this.zd = zs;
        switch (kind) {
            case FLAME, FLAME_BIG -> {
                this.lifetime = 14 + this.random.nextInt(10);
                this.baseSize = (kind == Kind.FLAME ? 0.34F : 0.95F) * (0.8F + this.random.nextFloat() * 0.4F);
                this.friction = 0.9F;
                this.yd += 0.03 + this.random.nextFloat() * 0.03;
            }
            case SPARK -> {
                this.lifetime = 4 + this.random.nextInt(4);
                this.baseSize = 0.5F + this.random.nextFloat() * 0.5F;
                this.friction = 0.8F;
                this.roll = this.random.nextFloat() * (float) Math.PI * 2.0F;
                this.oRoll = this.roll;
            }
            default -> {
                this.lifetime = 22 + this.random.nextInt(20);
                this.baseSize = 0.07F + this.random.nextFloat() * 0.06F;
                this.friction = 0.96F;
                this.yd += 0.02;
            }
        }
        this.quadSize = this.baseSize;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        this.setSpriteFromAge(this.sprites);
        float life = (float) this.age / (float) this.lifetime;
        switch (kind) {
            case FLAME, FLAME_BIG -> {
                this.xd += (this.random.nextFloat() - 0.5F) * 0.01F;
                this.zd += (this.random.nextFloat() - 0.5F) * 0.01F;
                this.alpha = life < 0.6F ? 1.0F : 1.0F - (life - 0.6F) / 0.4F;
            }
            case SPARK -> this.alpha = this.random.nextFloat() < 0.3F ? 0.5F : 1.0F;
            default -> {
                this.xd += (this.random.nextFloat() - 0.5F) * 0.006F;
                this.zd += (this.random.nextFloat() - 0.5F) * 0.006F;
                float flicker = 0.7F + 0.3F * (float) Math.sin(this.age * 0.9F);
                this.alpha = life < 0.7F ? flicker : flicker * (1.0F - (life - 0.7F) / 0.3F);
            }
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float life = ((float) this.age + partialTick) / (float) this.lifetime;
        return switch (kind) {
            case FLAME, FLAME_BIG -> this.baseSize * (1.0F - 0.65F * life * life);
            case SPARK -> this.baseSize * (1.0F - 0.4F * life);
            default -> this.baseSize * (1.0F - 0.5F * life);
        };
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // always full bright: they glow
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xs, double ys, double zs) {
            return new PurpleParticle(level, x, y, z, xs, ys, zs, sprites, kind);
        }
    }
}
