package com.nazarick.entity;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Base class for Nazarick's servants (Albedo and Sebas).
 * - Never despawns; only creative players or /kill can hurt them (Albedo overrides this for intruders).
 * - Keeps to a "post": the spot it was spawned or generated on.
 * - Right-click: bows / curtsies and says a line.
 * - Can be escorted somewhere for a scene (the throne claim) and then returns to its post.
 */
public abstract class NazarickNpcEntity extends PathfinderMob implements NazarickAligned {
    public static final byte EVENT_GREET = 105;
    protected static final int GREET_TICKS = 40;
    protected static final int POST_RADIUS = 10;

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState greetAnimationState = new AnimationState();
    private int greetClientTicks;
    private int greetServerTicks;
    @Nullable private Entity lookTarget;

    @Nullable private BlockPos post;
    @Nullable private BlockPos escortDestination;
    @Nullable private BlockPos escortLook;
    private int escortTicks;

    protected NazarickNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createNpcAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MoveTowardsRestrictionGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.55D, 0.004F));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Lines spoken when a player right-clicks this servant. */
    protected abstract List<String> greetingLines();

    protected SoundEvent greetSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    // ------------------------------------------------------------------ greeting / bowing
    /** Play the bow / curtsy animation, facing the given entity. Server side. */
    public void bow(@Nullable Entity toward) {
        if (this.level().isClientSide) return;
        this.greetServerTicks = GREET_TICKS;
        this.lookTarget = toward;
        this.getNavigation().stop();
        this.level().broadcastEntityEvent(this, EVENT_GREET);
    }

    public void say(Player player, String line) {
        player.sendSystemMessage(Component.literal("<" + this.getDisplayName().getString() + "> ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide && this.greetServerTicks == 0 && this.getTarget() == null) {
            this.bow(player);
            List<String> lines = this.greetingLines();
            this.say(player, lines.get(this.random.nextInt(lines.size())));
            this.playSound(this.greetSound(), 0.6F, 1.0F);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ------------------------------------------------------------------ escort (scenes)
    /** Teleport to {@code start}, walk to {@code destination}, face {@code look}; return to the post after {@code ticks}. */
    public void beginEscort(BlockPos start, BlockPos destination, BlockPos look, int ticks) {
        if (this.post == null) this.post = this.blockPosition();
        this.clearRestriction();
        this.teleportTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        this.escortDestination = destination;
        this.escortLook = look;
        this.escortTicks = ticks;
    }

    public void endEscort() {
        this.escortTicks = 0;
        this.escortDestination = null;
        this.escortLook = null;
        if (this.post != null) this.restrictTo(this.post, POST_RADIUS);
    }

    public boolean isEscorting() {
        return this.escortTicks > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnimationState.startIfStopped(this.tickCount);
            if (this.greetClientTicks > 0 && --this.greetClientTicks == 0) {
                this.greetAnimationState.stop();
            }
            return;
        }
        if (this.post == null && this.tickCount > 20) {   // generated in a structure: adopt this spot as the post
            this.post = this.blockPosition();
            this.restrictTo(this.post, POST_RADIUS);
        }
        if (this.escortTicks > 0) {
            this.escortTicks--;
            if (this.escortDestination != null) {
                double d = this.distanceToSqr(this.escortDestination.getX() + 0.5D, this.escortDestination.getY(), this.escortDestination.getZ() + 0.5D);
                if (d > 2.0D) {
                    if (this.getNavigation().isDone()) {
                        this.getNavigation().moveTo(this.escortDestination.getX() + 0.5D, this.escortDestination.getY(),
                                this.escortDestination.getZ() + 0.5D, 0.9D);
                    }
                } else if (this.escortLook != null && this.greetServerTicks == 0) {
                    this.getNavigation().stop();
                    this.getLookControl().setLookAt(this.escortLook.getX() + 0.5D, this.escortLook.getY() + 1.0D, this.escortLook.getZ() + 0.5D);
                }
            }
            if (this.escortTicks == 0) this.endEscort();
        }
        if (this.greetServerTicks > 0) {
            this.greetServerTicks--;
            this.getNavigation().stop();
            if (this.lookTarget != null) this.getLookControl().setLookAt(this.lookTarget, 30.0F, 30.0F);
            if (this.greetServerTicks == 0) this.lookTarget = null;
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_GREET) {
            this.greetAnimationState.start(this.tickCount);
            this.greetClientTicks = GREET_TICKS;
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------------ durability & persistence
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) return false;
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        this.post = this.blockPosition();
        this.restrictTo(this.post, POST_RADIUS);
        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.post != null) tag.put("Post", NbtUtils.writeBlockPos(this.post));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Post")) {
            this.post = NbtUtils.readBlockPos(tag.getCompound("Post"));
            this.restrictTo(this.post, POST_RADIUS);
        }
    }
}
