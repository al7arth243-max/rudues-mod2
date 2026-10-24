package com.rudues.mod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rudues.mod.registry.ModParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The mod's fire, all from one class:
 *  FLAME / FLAME_BIG  licking tongues of violet fire that rise faster as they go, curl in the draught, swell then
 *                     burn down from white-hot through pink to deep violet - and throw off embers, the odd spark and
 *                     (the big ones) a curl of smoke as they burn;
 *  SPARK              a white-hot streak drawn along its flight, falling, skipping off the ground, cooling as it goes;
 *  EMBER              a glowing mote that drifts up on the heat, swaying and flickering out;
 *  SMOKE              dark violet smoke that billows up and spreads.
 * The player's fire is drawn additively (it glows, and brightens where it piles up); the Infernal Hero's darker fire
 * is drawn translucent, so it reads as dark flame rather than light.
 */
public class PurpleParticle extends TextureSheetParticle {
    public enum Kind { FLAME, FLAME_BIG, SPARK, EMBER, SMOKE }

    /** glowing fire: added onto what is behind it */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "RUDUES_ADDITIVE";
        }
    };

    /** child embers/sparks/smoke spawned by burning flames, per client tick (so a big fire can't flood the screen) */
    private static final int CHILD_BUDGET = 90;
    private static long budgetTick = Long.MIN_VALUE;
    private static int budget;

    private final SpriteSet sprites;
    private final Kind kind;
    private final boolean dark;
    private float baseSize;
    private final float seed;
    private float spin;
    /** colour at birth and at death (the flame cools as it burns) */
    private float r0, g0, b0, r1, g1, b1;

    protected PurpleParticle(ClientLevel level, double x, double y, double z, double xs, double ys, double zs,
                             SpriteSet sprites, Kind kind) {
        this(level, x, y, z, xs, ys, zs, sprites, kind, false);
    }

    /** dark = the Infernal Hero's fire: deeper, darker violet, drawn as dark flame */
    protected PurpleParticle(ClientLevel level, double x, double y, double z, double xs, double ys, double zs,
                             SpriteSet sprites, Kind kind, boolean dark) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.kind = kind;
        this.dark = dark;
        this.seed = this.random.nextFloat() * 100.0F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = xs;
        this.yd = ys;
        this.zd = zs;
        float jitter = 0.85F + this.random.nextFloat() * 0.3F;
        switch (kind) {
            case FLAME, FLAME_BIG -> {
                boolean big = kind == Kind.FLAME_BIG;
                this.lifetime = (big ? 18 : 14) + this.random.nextInt(big ? 14 : 10);
                this.baseSize = (big ? 0.95F : 0.34F) * jitter;
                this.friction = 0.91F;
                this.yd += 0.02 + this.random.nextFloat() * 0.03;
                this.roll = (this.random.nextFloat() - 0.5F) * 0.5F;
                this.oRoll = this.roll;
                this.spin = (this.random.nextFloat() - 0.5F) * 0.06F;
                colours(1.0F, 0.93F, 1.0F, 0.42F, 0.13F, 0.72F);
            }
            case SPARK -> {
                this.lifetime = 9 + this.random.nextInt(12);
                this.baseSize = 0.05F + this.random.nextFloat() * 0.035F;
                this.friction = 0.95F;
                this.gravity = 0.9F;
                this.hasPhysics = true;
                // a little extra fling so they fly off rather than hang
                this.xd *= 1.25;
                this.zd *= 1.25;
                this.yd = this.yd * 1.25 + 0.05;
                colours(1.0F, 0.97F, 1.0F, 0.75F, 0.32F, 1.0F);
            }
            case SMOKE -> {
                this.lifetime = 34 + this.random.nextInt(26);
                this.baseSize = 0.55F * jitter;
                this.friction = 0.94F;
                this.yd += 0.025;
                this.roll = this.random.nextFloat() * Mth.TWO_PI;
                this.oRoll = this.roll;
                this.spin = (this.random.nextFloat() - 0.5F) * 0.04F;
                colours(0.30F, 0.18F, 0.38F, 0.10F, 0.06F, 0.13F);
            }
            default -> {
                this.lifetime = 30 + this.random.nextInt(30);
                this.baseSize = (0.045F + this.random.nextFloat() * 0.05F);
                this.friction = 0.96F;
                this.yd += 0.015 + this.random.nextFloat() * 0.02;
                colours(1.0F, 0.88F, 1.0F, 0.62F, 0.22F, 0.95F);
            }
        }
        if (dark && kind != Kind.SMOKE) {
            // the Hero's fire: the same flames, a deeper, duskier violet
            r0 *= 0.62F; g0 *= 0.36F; b0 *= 0.82F;
            r1 *= 0.45F; g1 *= 0.25F; b1 *= 0.62F;
        }
        setColor(r0, g0, b0);
        this.quadSize = this.baseSize;
        this.setSpriteFromAge(sprites);
    }

    private void colours(float ra, float ga, float ba, float rb, float gb, float bb) {
        r0 = ra; g0 = ga; b0 = ba; r1 = rb; g1 = gb; b1 = bb;
    }

    private static boolean takeBudget(ClientLevel level) {
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            budget = CHILD_BUDGET;
        }
        if (budget <= 0) {
            return false;
        }
        budget--;
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        this.setSpriteFromAge(this.sprites);
        float life = (float) this.age / (float) this.lifetime;
        this.oRoll = this.roll;
        this.roll += this.spin;
        float cool = kind == Kind.SPARK ? life : life * life;
        setColor(Mth.lerp(cool, r0, r1), Mth.lerp(cool, g0, g1), Mth.lerp(cool, b0, b1));
        switch (kind) {
            case FLAME, FLAME_BIG -> {
                // heat: the tongue speeds up as it rises, and the draught curls it
                this.yd += 0.0035;
                float t = this.age * 0.45F + this.seed;
                this.xd += Mth.sin(t) * 0.0045F + (this.random.nextFloat() - 0.5F) * 0.006F;
                this.zd += Mth.cos(t * 1.3F) * 0.0045F + (this.random.nextFloat() - 0.5F) * 0.006F;
                // (the big ones a touch softer, so a bonfire of them doesn't burn out to white)
                this.alpha = (life < 0.55F ? 1.0F : 1.0F - (life - 0.55F) / 0.45F) * (kind == Kind.FLAME_BIG ? 0.8F : 1.0F);
                throwOff(kind == Kind.FLAME_BIG, life);
            }
            case SPARK -> {
                // skips off the ground, losing most of its speed
                if (this.onGround) {
                    this.yd = Math.abs(this.yd) * 0.35 + 0.03 * this.random.nextFloat();
                    this.xd *= 0.55;
                    this.zd *= 0.55;
                }
                this.alpha = life < 0.6F ? 1.0F : 1.0F - (life - 0.6F) / 0.4F;
                if (this.random.nextFloat() < 0.08F) {
                    this.alpha *= 0.5F;          // a little flicker
                }
            }
            case SMOKE -> {
                this.xd += (this.random.nextFloat() - 0.5F) * 0.004F;
                this.zd += (this.random.nextFloat() - 0.5F) * 0.004F;
                this.yd += 0.001;
                this.alpha = (life < 0.15F ? life / 0.15F : 1.0F - (life - 0.15F) / 0.85F) * (dark ? 0.85F : 0.65F);
            }
            default -> {
                float t = this.age * 0.25F + this.seed;
                this.xd += Mth.sin(t) * 0.0025F + (this.random.nextFloat() - 0.5F) * 0.004F;
                this.zd += Mth.cos(t * 0.8F) * 0.0025F + (this.random.nextFloat() - 0.5F) * 0.004F;
                this.yd += 0.0008;
                float flicker = 0.65F + 0.35F * Mth.sin(this.age * 0.8F + this.seed);
                this.alpha = life < 0.7F ? flicker : flicker * (1.0F - (life - 0.7F) / 0.3F);
            }
        }
    }

    /** a burning flame sheds embers from its tip, now and then a spark, and the big ones a curl of smoke */
    private void throwOff(boolean big, float life) {
        float tip = (float) (this.quadSize * 0.6);
        if (this.random.nextFloat() < (big ? 0.11F : 0.035F) && life > 0.15F && takeBudget(this.level)) {
            this.level.addParticle(dark ? ModParticles.DARK_EMBER.get() : ModParticles.PURPLE_EMBER.get(),
                    this.x + (this.random.nextFloat() - 0.5F) * tip, this.y + tip, this.z + (this.random.nextFloat() - 0.5F) * tip,
                    this.xd * 0.5 + (this.random.nextFloat() - 0.5F) * 0.05, this.yd * 0.6 + 0.04, this.zd * 0.5 + (this.random.nextFloat() - 0.5F) * 0.05);
        }
        if (this.random.nextFloat() < (big ? 0.03F : 0.008F) && takeBudget(this.level)) {
            double a = this.random.nextFloat() * Math.PI * 2;
            double s = 0.12 + this.random.nextFloat() * 0.12;
            this.level.addParticle(dark ? ModParticles.DARK_SPARK.get() : ModParticles.PURPLE_SPARK.get(), this.x, this.y + tip * 0.5, this.z,
                    Math.cos(a) * s, 0.12 + this.random.nextFloat() * 0.1, Math.sin(a) * s);
        }
        if (big && life > 0.5F && this.random.nextFloat() < 0.05F && takeBudget(this.level)) {
            this.level.addParticle(ModParticles.FIRE_SMOKE.get(), this.x, this.y + tip, this.z, this.xd * 0.3, 0.02, this.zd * 0.3);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float life = Mth.clamp(((float) this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        return switch (kind) {
            // flares up, then burns down to a thin wisp
            case FLAME, FLAME_BIG -> this.baseSize * (life < 0.2F ? 0.65F + 1.75F * life : 1.0F - 0.75F * (life - 0.2F) / 0.8F);
            case SMOKE -> this.baseSize * (0.6F + 1.4F * life);
            case SPARK -> this.baseSize * (1.0F - 0.5F * life);
            default -> this.baseSize * (1.0F - 0.55F * life);
        };
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind == Kind.SMOKE ? super.getLightColor(partialTick) : 0xF000F0;    // fire glows; smoke is lit
    }

    @Override
    public ParticleRenderType getRenderType() {
        if (kind == Kind.SMOKE || (dark && (kind == Kind.FLAME || kind == Kind.FLAME_BIG))) {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
        return ADDITIVE;
    }

    /** sparks are drawn as streaks along their flight; everything else as normal camera-facing sprites */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (kind != Kind.SPARK) {
            super.render(buffer, camera, partialTick);
            return;
        }
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x);
        float py = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y);
        float pz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z);
        Vector3f vel = new Vector3f((float) this.xd, (float) this.yd, (float) this.zd);
        float speed = vel.length();
        if (speed < 1.0E-4F) {
            vel.set(0, -1, 0);
            speed = 0.02F;
        }
        Vector3f dir = new Vector3f(vel).normalize();
        float len = Mth.clamp(speed * 2.6F, 0.08F, 0.9F);
        Vector3f head = new Vector3f(px, py, pz);
        Vector3f tail = new Vector3f(head).sub(new Vector3f(dir).mul(len));
        Vector3f side = new Vector3f(dir).cross(head);           // across the streak, as seen from the camera
        if (side.lengthSquared() < 1.0E-8F) {
            side.set(1, 0, 0);
        }
        side.normalize().mul(getQuadSize(partialTick));
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTick);
        // tail at the sprite's left (dim), head at its right (bright)
        buffer.vertex(tail.x - side.x * 0.4F, tail.y - side.y * 0.4F, tail.z - side.z * 0.4F).uv(u0, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        buffer.vertex(head.x - side.x, head.y - side.y, head.z - side.z).uv(u1, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        buffer.vertex(head.x + side.x, head.y + side.y, head.z + side.z).uv(u1, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        buffer.vertex(tail.x + side.x * 0.4F, tail.y + side.y * 0.4F, tail.z + side.z * 0.4F).uv(u0, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        // and the other face, so it shows whichever way round it is seen
        buffer.vertex(tail.x + side.x * 0.4F, tail.y + side.y * 0.4F, tail.z + side.z * 0.4F).uv(u0, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        buffer.vertex(head.x + side.x, head.y + side.y, head.z + side.z).uv(u1, v0).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        buffer.vertex(head.x - side.x, head.y - side.y, head.z - side.z).uv(u1, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        buffer.vertex(tail.x - side.x * 0.4F, tail.y - side.y * 0.4F, tail.z - side.z * 0.4F).uv(u0, v1).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;
        private final boolean dark;

        public Provider(SpriteSet sprites, Kind kind) {
            this(sprites, kind, false);
        }

        public Provider(SpriteSet sprites, Kind kind, boolean dark) {
            this.sprites = sprites;
            this.kind = kind;
            this.dark = dark;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xs, double ys, double zs) {
            return new PurpleParticle(level, x, y, z, xs, ys, zs, sprites, kind, dark);
        }
    }
}
