package com.rudues.mod.trial;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.ability.HeroForm;
import com.rudues.mod.ability.PowerData;
import com.rudues.mod.entity.InfernalShadowEntity;
import com.rudues.mod.registry.ModEntities;
import com.rudues.mod.world.ArenaBuilder;
import com.rudues.mod.world.ArenaLayout;
import com.rudues.mod.world.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Infernal Trial: who is chosen, who is helping, how it is lost and won, and the rules of the arena.
 *
 *  - The first player to enter (while nobody in the world holds the power) is the chosen one; anyone entering
 *    afterwards joins as a helper.
 *  - The chosen one dying or logging out ends the trial and sends every participant back to the overworld.
 *  - Helpers who die respawn in the overworld and may come back while the chosen one survives.
 *  - Nothing is lost by dying inside: inventory and experience come back on respawn.
 *  - Only one player per world can hold the power. Once it is taken, the arena stays quiet and the sword is the
 *    way out.
 *  - The arena cannot be broken, built on or blown up, and nobody can leave the ring except by the sword.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID)
public final class TrialManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** bump this when the arena layout changes so existing worlds rebuild it */
    public static final int ARENA_VERSION = 1;
    private static final String KEPT_INVENTORY = "rudues_trial_inventory";
    private static final double WALL_R = ArenaLayout.FIRE_R + 1.0;

    private static final List<Runnable> PENDING = new ArrayList<>();
    private static final Set<UUID> ENTERING = new HashSet<>();

    private TrialManager() {
    }

    public static boolean inTrial(Entity e) {
        return e.level().dimension() == ModDimensions.TRIAL;
    }

    private static boolean isTrialLevel(Object level) {
        return level instanceof Level l && l.dimension() == ModDimensions.TRIAL;
    }

    private static ServerLevel trialLevel(MinecraftServer server) {
        return server.getLevel(ModDimensions.TRIAL);
    }

    /** Teleports and other world changes are done at the end of the server tick, never mid-collision or mid-death. */
    private static void later(Runnable r) {
        synchronized (PENDING) {
            PENDING.add(r);
        }
    }

    // ------------------------------------------------------------------ getting in and out
    /** The portal surface calls this every tick a player is inside it. */
    public static void queueEnter(ServerPlayer player) {
        if (ENTERING.add(player.getUUID())) {
            later(() -> {
                ENTERING.remove(player.getUUID());
                ServerLevel trial = trialLevel(player.getServer());
                if (trial == null || !player.isAlive() || inTrial(player)) {
                    return;
                }
                player.teleportTo(trial, ArenaLayout.SPAWN_X + 0.5, ArenaLayout.FLOOR + 1, ArenaLayout.SPAWN_Z + 0.5, 180.0F, 0.0F);
                trial.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4F, 0.6F);
            });
        }
    }

    /** The sword at the centre: home you go. */
    public static void leaveViaSword(ServerPlayer player) {
        later(() -> sendHome(player));
    }

    /** Back to the player's bed or anchor if it still works, otherwise to the world spawn. */
    public static void sendHome(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel dest = server.getLevel(player.getRespawnDimension());
        BlockPos respawn = player.getRespawnPosition();
        Optional<Vec3> at = Optional.empty();
        if (dest != null && dest.dimension() != ModDimensions.TRIAL && respawn != null) {
            at = Player.findRespawnPositionAndUseSpawnBlock(dest, respawn, player.getRespawnAngle(), player.isRespawnForced(), true);
        }
        if (at.isEmpty()) {
            dest = server.overworld();
            BlockPos top = dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dest.getSharedSpawnPos());
            at = Optional.of(Vec3.atBottomCenterOf(top));
        }
        Vec3 v = at.get();
        player.teleportTo(dest, v.x, v.y, v.z, player.getYRot(), player.getXRot());
    }

    // ------------------------------------------------------------------ the trial itself
    private static void onEnter(ServerPlayer player) {
        TrialData d = TrialData.get(player.getServer());
        if (d.holder != null) {
            player.displayClientMessage(Component.translatable("message.rudues.trial_quiet").withStyle(ChatFormatting.DARK_PURPLE), false);
            return;
        }
        if (!d.active) {
            begin(player, d);
        } else if (d.participants.add(player.getUUID())) {
            d.setDirty();
            ServerPlayer chosen = d.chosen == null ? null : player.getServer().getPlayerList().getPlayer(d.chosen);
            String name = chosen == null ? "?" : chosen.getGameProfile().getName();
            player.displayClientMessage(Component.translatable("message.rudues.trial_join", name).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    private static void begin(ServerPlayer chosen, TrialData d) {
        d.begin(chosen.getUUID());
        ServerLevel trial = trialLevel(chosen.getServer());
        ArenaBuilder.placeSword(trial, false);
        for (ServerPlayer p : trial.players()) {
            title(p, Component.translatable("title.rudues.trial").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable(p == chosen ? "title.rudues.trial_chosen_you" : "title.rudues.trial_chosen", chosen.getGameProfile().getName())
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        trial.playSound(null, chosen.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 0.6F);
        // The Infernal Shadow is summoned here once the boss is in place (next step).
    }

    /** The chosen one fell or fled: everyone is sent back and the arena resets. */
    public static void fail(MinecraftServer server, Component reason) {
        TrialData d = TrialData.get(server);
        if (!d.active) {
            return;
        }
        d.end();
        ServerLevel trial = trialLevel(server);
        for (ServerPlayer p : new ArrayList<>(trial.players())) {
            p.displayClientMessage(reason, false);
            if (p.isAlive()) {
                sendHome(p);      // anyone on the death screen respawns in the overworld anyway
            }
        }
        ArenaBuilder.placeSword(trial, true);
        LOGGER.info("Infernal Trial failed");
    }

    /** Both bosses beaten: the chosen one earns the power, the sword appears. */
    public static void complete(MinecraftServer server) {
        TrialData d = TrialData.get(server);
        if (!d.active || d.chosen == null) {
            return;
        }
        UUID chosenId = d.chosen;
        ServerPlayer chosen = server.getPlayerList().getPlayer(chosenId);
        d.end();
        d.holder = chosenId;
        d.setDirty();
        if (chosen != null) {
            PowerData.grant(chosen);
            PowerData.applyEffects(chosen);
            HeroForm.sync(chosen);
        }
        ServerLevel trial = trialLevel(server);
        ArenaBuilder.placeSword(trial, true);
        String name = chosen == null ? "?" : chosen.getGameProfile().getName();
        for (ServerPlayer p : trial.players()) {
            title(p, Component.translatable("title.rudues.trial_won").withStyle(ChatFormatting.GOLD),
                    Component.translatable("title.rudues.trial_won_sub", name).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        trial.playSound(null, ArenaBuilder.SWORD_POS, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ------------------------------------------------------------------ server lifecycle
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel trial = trialLevel(server);
        if (trial == null) {
            LOGGER.error("Infernal Trial dimension is missing; is the mod's data pack loaded?");
            return;
        }
        TrialData d = TrialData.get(server);
        if (d.arenaVersion < ARENA_VERSION) {
            long t0 = System.currentTimeMillis();
            int n = ArenaBuilder.build(trial);
            d.arenaVersion = ARENA_VERSION;
            d.setDirty();
            LOGGER.info("Built the Infernal Trial arena ({} blocks) in {} ms", n, System.currentTimeMillis() - t0);
        }
        if (d.active) {
            d.end();       // a restart ends any trial that was in progress
        }
        ArenaBuilder.placeSword(trial, true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        List<Runnable> run;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            run = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Runnable r : run) {
            try {
                r.run();
            } catch (RuntimeException e) {
                LOGGER.error("Infernal Trial task failed", e);
            }
        }
    }

    // ------------------------------------------------------------------ players coming and going
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TrialData d = TrialData.get(player.getServer());
        if (event.getTo() == ModDimensions.TRIAL) {
            onEnter(player);
        } else if (event.getFrom() == ModDimensions.TRIAL && d.active) {
            if (player.getUUID().equals(d.chosen)) {
                later(() -> fail(player.getServer(), Component.translatable("message.rudues.trial_fled", player.getGameProfile().getName())
                        .withStyle(ChatFormatting.DARK_RED)));
            } else if (d.participants.remove(player.getUUID())) {
                d.setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TrialData d = TrialData.get(player.getServer());
        if (d.active && player.getUUID().equals(d.chosen)) {
            MinecraftServer server = player.getServer();
            Component reason = Component.translatable("message.rudues.trial_left", player.getGameProfile().getName()).withStyle(ChatFormatting.DARK_RED);
            later(() -> fail(server, reason));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inTrial(player)) {
            return;
        }
        TrialData d = TrialData.get(player.getServer());
        if (d.active && !player.getUUID().equals(d.chosen)) {
            d.participants.add(player.getUUID());       // a helper coming back into a trial still being fought
            d.setDirty();
        } else if (d.holder == null) {
            later(() -> sendHome(player));               // the trial they were in has ended
        }
    }

    // ------------------------------------------------------------------ death inside the trial
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inTrial(player)) {
            return;
        }
        // keep everything: stash the inventory, give it back on respawn
        CompoundTag persisted = persisted(player);
        persisted.put(KEPT_INVENTORY, player.getInventory().save(new ListTag()));
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.getInventory().clearContent();

        TrialData d = TrialData.get(player.getServer());
        if (d.active && player.getUUID().equals(d.chosen)) {
            MinecraftServer server = player.getServer();
            Component reason = Component.translatable("message.rudues.trial_lost", player.getGameProfile().getName()).withStyle(ChatFormatting.DARK_RED);
            later(() -> fail(server, reason));
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Player player && inTrial(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        Player original = event.getOriginal();
        CompoundTag old = original.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!old.contains(KEPT_INVENTORY, Tag.TAG_LIST)) {
            return;
        }
        Player now = event.getEntity();
        now.getInventory().load(old.getList(KEPT_INVENTORY, Tag.TAG_COMPOUND));
        now.experienceLevel = original.experienceLevel;
        now.experienceProgress = original.experienceProgress;
        now.totalExperience = original.totalExperience;
        // remove the stash from both copies so it can never be restored twice
        old.remove(KEPT_INVENTORY);
        CompoundTag fresh = now.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        fresh.remove(KEPT_INVENTORY);
        now.getPersistentData().put(Player.PERSISTED_NBT_TAG, fresh);
    }

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ------------------------------------------------------------------ the arena cannot be changed
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isTrialLevel(event.getLevel()) && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (isTrialLevel(event.getLevel()) && !(event.getEntity() instanceof Player p && p.isCreative())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (isTrialLevel(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    @SubscribeEvent
    public static void onBucket(FillBucketEvent event) {
        if (isTrialLevel(event.getLevel()) && !event.getEntity().isCreative()) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ the invisible wall beyond the fire ring
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 5 != 0 || !inTrial(player) || player.isCreative() || player.isSpectator()) {
            return;
        }
        double r = Math.hypot(player.getX(), player.getZ());
        if (player.getY() < ArenaLayout.FLOOR - 20) {
            player.teleportTo(ArenaLayout.SPAWN_X + 0.5, ArenaLayout.FLOOR + 1, ArenaLayout.SPAWN_Z + 0.5);
        } else if (r > WALL_R) {
            double k = (ArenaLayout.FLOOR_R - 4.0) / r;
            player.teleportTo(player.getX() * k, ArenaLayout.FLOOR + 1, player.getZ() * k);
            player.displayClientMessage(Component.translatable("message.rudues.trial_wall").withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ------------------------------------------------------------------ commands (operators): testing and recovery
    /** Test summon: an Infernal Shadow 8 blocks in front of the caller, copying a player's skin (or the caller's). */
    private static int summonShadow(CommandSourceStack src, @Nullable ServerPlayer copy) {
        ServerLevel level = src.getLevel();
        InfernalShadowEntity shadow = ModEntities.INFERNAL_SHADOW.get().create(level);
        if (shadow == null) {
            return 0;
        }
        Vec3 at = src.getPosition();
        float yaw = src.getRotation().y;
        Vec3 look = Vec3.directionFromRotation(0, yaw);
        Vec3 pos = at.add(look.scale(8.0));
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(pos));
        double y = Math.abs(ground.getY() - at.y) < 6 ? ground.getY() : at.y;
        shadow.moveTo(pos.x, y, pos.z, yaw + 180.0F, 0.0F);
        shadow.setYHeadRot(yaw + 180.0F);
        shadow.setYBodyRot(yaw + 180.0F);
        Player skin = copy != null ? copy : (src.getEntity() instanceof Player p ? p : null);
        shadow.setCopyOf(skin);
        level.addFreshEntity(shadow);
        src.sendSuccess(() -> Component.literal("Summoned an Infernal Shadow" + (skin != null ? " of " + skin.getGameProfile().getName() : "") + "."), true);
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("rudues").requires(src -> src.hasPermission(2))
                .then(Commands.literal("trial")
                        .then(Commands.literal("complete").executes(ctx -> {
                            complete(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Infernal Trial completed."), true);
                            return 1;
                        }))
                        .then(Commands.literal("fail").executes(ctx -> {
                            fail(ctx.getSource().getServer(), Component.literal("The trial was ended by an operator."));
                            ctx.getSource().sendSuccess(() -> Component.literal("Infernal Trial ended."), true);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            TrialData d = TrialData.get(ctx.getSource().getServer());
                            String s = "active=" + d.active + " chosen=" + d.chosen + " helpers=" + (d.participants.size() - (d.chosen == null ? 0 : 1))
                                    + " holder=" + d.holder + " arena=v" + d.arenaVersion;
                            ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                            return 1;
                        })))
                .then(Commands.literal("arena").then(Commands.literal("rebuild").executes(ctx -> {
                    ServerLevel trial = trialLevel(ctx.getSource().getServer());
                    int n = ArenaBuilder.build(trial);
                    ctx.getSource().sendSuccess(() -> Component.literal("Rebuilt the arena (" + n + " blocks)."), true);
                    return 1;
                })))
                .then(Commands.literal("shadow").then(Commands.literal("summon")
                        .executes(ctx -> summonShadow(ctx.getSource(), null))
                        .then(Commands.argument("copy", EntityArgument.player())
                                .executes(ctx -> summonShadow(ctx.getSource(), EntityArgument.getPlayer(ctx, "copy"))))))
                .then(Commands.literal("powers").then(Commands.literal("revoke")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            PowerData.revoke(target);
                            HeroForm.sync(target);
                            TrialData d = TrialData.get(ctx.getSource().getServer());
                            if (target.getUUID().equals(d.holder)) {
                                d.holder = null;
                                d.setDirty();
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Removed the Fire Fiend's power from " + target.getGameProfile().getName() + "."), true);
                            return 1;
                        })))));
    }
}
