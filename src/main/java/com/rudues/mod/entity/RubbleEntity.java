package com.rudues.mod.entity;

import com.rudues.mod.registry.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A chunk of the keep roof, torn up in Benedikta's great talons and carried into the sky, then driven down onto the
 * player. It goes wherever she holds it (both sides work it out from her pose, so it never lags her), and is drawn as
 * a rough clump of real stone blocks.
 */
public class RubbleEntity extends Entity {
    private static final EntityDataAccessor<Integer> HOLDER = SynchedEntityData.defineId(RubbleEntity.class, EntityDataSerializers.INT);
    public static final float SIZE = 2.6F;

    public RubbleEntity(EntityType<? extends RubbleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public RubbleEntity(Level level, FeatheredKnightEntity holder) {
        this(ModEntities.RUBBLE.get(), level);
        this.entityData.set(HOLDER, holder.getId());
        place(holder.rubbleCentre(1.0F));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(HOLDER, -1);
    }

    public FeatheredKnightEntity holder() {
        return level().getEntity(this.entityData.get(HOLDER)) instanceof FeatheredKnightEntity k ? k : null;
    }

    /** the entity stands at the clump's foot */
    public void place(Vec3 centre) {
        setPos(centre.x, centre.y - SIZE / 2, centre.z);
    }

    public Vec3 centre() {
        return position().add(0, SIZE / 2, 0);
    }

    @Override
    public void tick() {
        super.tick();
        FeatheredKnightEntity k = holder();
        if (k != null && k.isAlive()) {
            Vec3 c = k.rubbleCentre(1.0F);
            if (c != null) {
                place(c);
            }
        } else if (!level().isClientSide && tickCount > 4) {
            discard();
        }
        if (level().isClientSide && random.nextInt(3) == 0) {
            Vec3 c = centre();
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE_BRICKS.defaultBlockState()),
                    c.x + (random.nextDouble() - 0.5) * SIZE, c.y - SIZE * 0.4, c.z + (random.nextDouble() - 0.5) * SIZE, 0, 0, 0);
        }
    }

    /** bursts into stone: fragments, dust and a crack */
    public void shatter() {
        if (level() instanceof ServerLevel sl) {
            Vec3 c = centre();
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), c.x, c.y, c.z, 90, 1.0, 1.0, 1.0, 0.4);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRACKED_STONE_BRICKS.defaultBlockState()), c.x, c.y, c.z, 50, 1.0, 1.0, 1.0, 0.4);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, 16, 0.8, 0.8, 0.8, 0.03);
            sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 0.6, 0.6, 0.6, 0.0);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4F, 0.8F);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.5F);
        }
        discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
