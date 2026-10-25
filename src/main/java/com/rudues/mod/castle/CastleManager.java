package com.rudues.mod.castle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.world.CastleBuilder;
import com.rudues.mod.world.CastleLayout;
import com.rudues.mod.world.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Feathered Hold: builds the castle the first time anyone goes there, keeps it from being torn down, and fills its
 * rooms with defenders as players walk in (a room's guard returns some minutes after it was last cut down).
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID)
public final class CastleManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** bump to rebuild the castle in existing worlds */
    public static final int CASTLE_VERSION = 2;
    private static final String RETURN = "rudues_castle_return";
    /** a room's guard comes back this long after it last spawned, once nobody is in it */
    private static final int ROOM_RESPAWN = 20 * 60 * 6;
    private static final String TAG = "rudues_castle_guard";

    private CastleManager() {
    }

    // ---------------------------------------------------------------- saved state
    public static final class CastleData extends SavedData {
        public int version;

        static CastleData load(CompoundTag tag) {
            CastleData d = new CastleData();
            d.version = tag.getInt("Version");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("Version", version);
            return tag;
        }

        static CastleData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(CastleData::load, CastleData::new, "rudues_castle");
        }
    }

    @Nullable
    public static ServerLevel castleLevel(MinecraftServer server) {
        return server.getLevel(ModDimensions.CASTLE);
    }

    /** the castle and the storm crater: neither can be broken, built on or blown up */
    public static boolean isProtected(@Nullable LevelAccessor level) {
        return isCastle(level) || CraterManager.isCrater(level);
    }

    public static boolean isCastle(@Nullable LevelAccessor level) {
        return level instanceof Level l && l.dimension() == ModDimensions.CASTLE;
    }

    /** builds it if this world has not got it (or has an older one); takes some seconds the first time */
    public static boolean ensureBuilt(MinecraftServer server) {
        ServerLevel level = castleLevel(server);
        if (level == null) {
            LOGGER.error("The Feathered Hold dimension is missing; is the mod's data pack loaded?");
            return false;
        }
        CastleData d = CastleData.get(server);
        if (d.version < CASTLE_VERSION) {
            long t0 = System.currentTimeMillis();
            int n = CastleBuilder.build(level);
            d.version = CASTLE_VERSION;
            d.setDirty();
            ROOMS.clear();
            LOGGER.info("Built the Feathered Hold ({} blocks) in {} ms", n, System.currentTimeMillis() - t0);
        }
        CraterManager.ensureBuilt(server);      // where her storm takes you
        return true;
    }

    // ---------------------------------------------------------------- the portals
    private static final java.util.Set<UUID> TRAVELLING = new java.util.HashSet<>();
    private static final java.util.Map<UUID, Long> LAST_TRAVEL = new java.util.HashMap<>();
    private static final List<Runnable> LATER = new ArrayList<>();

    /** the portal surface calls this every tick a player stands in it */
    public static void queueTravel(ServerPlayer p, BlockPos portal) {
        long now = p.level().getGameTime();
        Long last = LAST_TRAVEL.get(p.getUUID());
        if ((last != null && now - last < 60) || !TRAVELLING.add(p.getUUID())) {
            return;
        }
        boolean home = isCastle(p.level());
        if (!home && !com.rudues.mod.ability.PowerData.has(p)) {
            // only the bearer of the power of Rage may cross
            if (now % 40 == 0) {
                p.displayClientMessage(Component.translatable("message.rudues.castle_needs_power"), true);
            }
            return;
        }
        BlockPos at = portal.immutable();
        LATER.add(() -> {
            TRAVELLING.remove(p.getUUID());
            if (!p.isAlive()) {
                return;
            }
            LAST_TRAVEL.put(p.getUUID(), p.level().getGameTime());
            if (home) {
                leave(p);
            } else {
                enter(p, at);
                LAST_TRAVEL.put(p.getUUID(), p.level().getGameTime());
            }
        });
    }

    /** thrown emeralds, watched until they settle in a calcite ring or are picked up */
    private static final List<net.minecraft.world.entity.item.ItemEntity> EMERALDS = new ArrayList<>();

    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                && item.getItem().is(Items.EMERALD) && !isCastle(event.getLevel())) {
            EMERALDS.add(item);
        }
    }

    private static void tickEmeralds() {
        EMERALDS.removeIf(item -> {
            if (item.isRemoved() || item.tickCount > 20 * 60 || !(item.level() instanceof ServerLevel level)) {
                return true;
            }
            if (item.tickCount % 4 != 0 || !item.onGround()) {
                return false;
            }
            if (CastlePortal.tryActivate(level, item.blockPosition())) {
                ItemStack left = item.getItem().copy();
                left.shrink(1);
                if (left.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(left);
                }
                return true;
            }
            return false;
        });
    }

    // ---------------------------------------------------------------- going there and back
    public static void enter(ServerPlayer p) {
        enter(p, null);
    }

    /** portal: the calcite ring they came through (they come home just outside it), or null (a command) */
    public static void enter(ServerPlayer p, @Nullable BlockPos portal) {
        MinecraftServer server = p.getServer();
        if (server == null || !ensureBuilt(server)) {
            return;
        }
        ServerLevel level = castleLevel(server);
        if (p.level() != level) {
            CompoundTag back = new CompoundTag();
            back.putString("Dim", p.level().dimension().location().toString());
            if (portal != null) {
                // come home beside the ring, not in it: three blocks out from its centre, on the side they stepped in from
                BlockPos c = findCentre(p.serverLevel(), portal);
                double ox = p.getX() - (c.getX() + 0.5), oz = p.getZ() - (c.getZ() + 0.5);
                if (Math.abs(ox) < 0.2 && Math.abs(oz) < 0.2) {
                    oz = 1.0;
                }
                double len = Math.sqrt(ox * ox + oz * oz);
                back.putDouble("X", c.getX() + 0.5 + ox / len * 3.6);
                back.putDouble("Y", c.getY() + 1.0);
                back.putDouble("Z", c.getZ() + 0.5 + oz / len * 3.6);
            } else {
                back.putDouble("X", p.getX());
                back.putDouble("Y", p.getY());
                back.putDouble("Z", p.getZ());
            }
            CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            pers.put(RETURN, back);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        }
        p.teleportTo(level, CastleLayout.SPAWN_X + 0.5, CastleLayout.SPAWN_Y, CastleLayout.SPAWN_Z + 0.5, 180.0F, -8.0F);
        level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.PORTAL_TRAVEL, net.minecraft.sounds.SoundSource.PLAYERS, 0.35F, 1.3F);
        p.displayClientMessage(Component.translatable("message.rudues.castle_arrive"), true);
    }

    /** the middle of the 3x3 of portal blocks around one of them */
    private static BlockPos findCentre(ServerLevel level, BlockPos portal) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos c = portal.offset(dx, 0, dz);
                boolean all = true;
                for (int ax = -1; ax <= 1 && all; ax++) {
                    for (int az = -1; az <= 1 && all; az++) {
                        all = level.getBlockState(c.offset(ax, 0, az)).is(com.rudues.mod.registry.ModBlocks.CASTLE_PORTAL.get());
                    }
                }
                if (all) {
                    return c;
                }
            }
        }
        return portal;
    }

    public static void leave(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) {
            return;
        }
        CompoundTag pers = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag back = pers.getCompound(RETURN);
        ServerLevel to = server.overworld();
        double x, y, z;
        if (back.contains("Dim")) {
            for (ServerLevel l : server.getAllLevels()) {
                if (l.dimension().location().toString().equals(back.getString("Dim"))) {
                    to = l;
                }
            }
            x = back.getDouble("X");
            y = back.getDouble("Y");
            z = back.getDouble("Z");
        } else {
            BlockPos s = to.getSharedSpawnPos();
            x = s.getX() + 0.5;
            y = s.getY();
            z = s.getZ() + 0.5;
        }
        pers.remove(RETURN);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, pers);
        p.teleportTo(to, x, y, z, p.getYRot(), p.getXRot());
    }

    // ---------------------------------------------------------------- the guards
    private static final class RoomState {
        final CastleLayout.Room room;
        long lastSpawn = -ROOM_RESPAWN;
        final List<UUID> guards = new ArrayList<>();

        RoomState(CastleLayout.Room room) {
            this.room = room;
        }
    }

    private static final List<RoomState> ROOMS = new ArrayList<>();

    private static List<RoomState> rooms() {
        if (ROOMS.isEmpty()) {
            if (CastleLayout.ROOMS.isEmpty()) {
                CastleLayout.build((x, y, z, b) -> {
                });                                 // (only to learn where the rooms are)
            }
            for (CastleLayout.Room r : CastleLayout.ROOMS) {
                ROOMS.add(new RoomState(r));
            }
        }
        return ROOMS;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!LATER.isEmpty()) {
            List<Runnable> run = new ArrayList<>(LATER);
            LATER.clear();
            run.forEach(Runnable::run);
        }
        if (!EMERALDS.isEmpty()) {
            tickEmeralds();
        }
        if (event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        ServerLevel level = castleLevel(event.getServer());
        if (level == null || level.players().isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (RoomState rs : rooms()) {
            CastleLayout.Room r = rs.room;
            boolean occupied = false;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && !p.isCreative() && p.getX() >= r.x0() && p.getX() <= r.x1() + 1 && p.getY() >= r.y0() - 1 && p.getY() <= r.y1() + 1
                        && p.getZ() >= r.z0() && p.getZ() <= r.z1() + 1) {
                    occupied = true;
                    break;
                }
            }
            rs.guards.removeIf(id -> {
                var e = level.getEntity(id);
                return e == null || !e.isAlive();
            });
            if (occupied && rs.guards.isEmpty() && now - rs.lastSpawn >= ROOM_RESPAWN) {
                rs.lastSpawn = now;
                spawnGuard(level, rs);
            }
        }
    }

    private static void spawnGuard(ServerLevel level, RoomState rs) {
        CastleLayout.Room r = rs.room;
        int n = r.spawns().length / 3;
        for (int i = 0; i < n; i++) {
            String kind = r.mobs()[i % r.mobs().length];
            BlockPos at = new BlockPos(r.spawns()[i * 3], r.spawns()[i * 3 + 1], r.spawns()[i * 3 + 2]);
            // stand it on the floor's first free spot nearby
            BlockPos.MutableBlockPos q = at.mutable();
            boolean ok = false;
            for (int tries = 0; tries < 12 && !ok; tries++) {
                q.set(at.getX() + level.random.nextInt(5) - 2, at.getY(), at.getZ() + level.random.nextInt(5) - 2);
                ok = level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()
                        && !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty();
            }
            if (!ok) {
                continue;
            }
            Mob mob = make(level, kind);
            if (mob == null) {
                continue;
            }
            mob.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(q), MobSpawnType.STRUCTURE, null, null);
            dress(mob, kind);
            if (mob instanceof net.minecraft.world.entity.monster.Zombie z) {
                z.setCanBreakDoors(false);
            }
            mob.setPersistenceRequired();
            mob.addTag(TAG);
            level.addFreshEntity(mob);
            rs.guards.add(mob.getUUID());
        }
    }

    @Nullable
    private static Mob make(ServerLevel level, String kind) {
        EntityType<? extends Mob> type = switch (kind) {
            case "skeleton_archer" -> EntityType.SKELETON;
            case "stray_archer" -> EntityType.STRAY;
            case "vindicator" -> EntityType.VINDICATOR;
            case "witch" -> EntityType.WITCH;
            default -> EntityType.ZOMBIE;
        };
        return type.create(level);
    }

    /** the hold's soldiers: iron and chain, swords, the knight's green on their heads */
    private static void dress(Mob mob, String kind) {
        switch (kind) {
            case "zombie_soldier" -> {
                mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            }
            case "skeleton_archer", "stray_archer" -> mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            default -> {
            }
        }
        for (EquipmentSlot s : EquipmentSlot.values()) {
            mob.setDropChance(s, 0.04F);
        }
    }

    // ---------------------------------------------------------------- the castle cannot be torn down
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isProtected(event.getLevel()) && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (isProtected(event.getLevel()) && !(event.getEntity() instanceof Player p && p.isCreative())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (isProtected(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    /** no flint and steel, no fire charges, no spawn eggs on its stones: items cannot act on the castle's blocks
     * (doors, chests and levers still open) */
    @SubscribeEvent
    public static void onUseOnBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (isProtected(event.getLevel()) && !event.getEntity().isCreative()) {
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** nothing else changes it either: no trampling, no mobs breaking doors */
    @SubscribeEvent
    public static void onGrief(net.minecraftforge.event.entity.EntityMobGriefingEvent event) {
        if (event.getEntity() != null && isProtected(event.getEntity().level())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (isProtected(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBucket(FillBucketEvent event) {
        if (isProtected(event.getLevel()) && !event.getEntity().isCreative()) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- commands (testing, until the way in exists)
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("rudues").requires(src -> src.hasPermission(2))
                .then(Commands.literal("castle")
                        .then(Commands.literal("enter").executes(ctx -> {
                            enter(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("leave").executes(ctx -> {
                            leave(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("roof").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            enter(p);
                            ServerLevel level = castleLevel(p.getServer());
                            p.teleportTo(level, CastleLayout.ARENA_CX + 0.5, CastleLayout.ROOF, CastleLayout.ARENA_CZ + 8.5, 180.0F, 0.0F);
                            return 1;
                        }))
                        .then(Commands.literal("knight").executes(ctx -> {
                            CastleBoss.reset(castleLevel(ctx.getSource().getServer()));
                            ctx.getSource().sendSuccess(() -> Component.literal("The roof is reset; she will be waiting again."), true);
                            return 1;
                        }))
                        .then(Commands.literal("rebuild").executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            CastleData.get(server).version = 0;
                            ensureBuilt(server);
                            ctx.getSource().sendSuccess(() -> Component.literal("Rebuilt the Feathered Hold."), true);
                            return 1;
                        }))));
    }
}
