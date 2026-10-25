package com.rudues.mod.castle;

import com.mojang.logging.LogUtils;
import com.rudues.mod.RuduesMod;
import com.rudues.mod.world.CastleBuilder;
import com.rudues.mod.world.CraterLayout;
import com.rudues.mod.world.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Eye of the Storm: the crater Benedikta's tornado leaves the world as, where the Hero of Hell fights Garuda.
 * Built the first time it is needed (together with the Feathered Hold). Unbreakable like the castle; fighters are
 * kept inside the bowl.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID)
public final class CraterManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int CRATER_VERSION = 1;

    private CraterManager() {
    }

    public static final class CraterData extends SavedData {
        int version;

        static CraterData load(CompoundTag tag) {
            CraterData d = new CraterData();
            d.version = tag.getInt("Version");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("Version", version);
            return tag;
        }

        static CraterData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(CraterData::load, CraterData::new, "rudues_crater");
        }
    }

    @Nullable
    public static ServerLevel craterLevel(MinecraftServer server) {
        return server.getLevel(ModDimensions.CRATER);
    }

    public static boolean isCrater(@Nullable LevelAccessor level) {
        return level instanceof Level l && l.dimension() == ModDimensions.CRATER;
    }

    public static boolean ensureBuilt(MinecraftServer server) {
        ServerLevel level = craterLevel(server);
        if (level == null) {
            LOGGER.error("The storm crater dimension is missing; is the mod's data pack loaded?");
            return false;
        }
        CraterData d = CraterData.get(server);
        if (d.version < CRATER_VERSION) {
            long t0 = System.currentTimeMillis();
            int n = CastleBuilder.build(level, sink -> CraterLayout.build(sink::set));
            d.version = CRATER_VERSION;
            d.setDirty();
            LOGGER.info("Built the storm crater ({} blocks) in {} ms", n, System.currentTimeMillis() - t0);
        }
        return true;
    }

    /** the floor (first air) at x, z */
    public static double floorAt(double x, double z) {
        return CraterLayout.surface((int) Math.floor(x), (int) Math.floor(z)) + 1;
    }

    public static Vec3 spawn() {
        return new Vec3(CraterLayout.SPAWN_X + 0.5, floorAt(CraterLayout.SPAWN_X, CraterLayout.SPAWN_Z), CraterLayout.SPAWN_Z + 0.5);
    }

    public static BlockPos centre() {
        return new BlockPos(0, (int) floorAt(0, 0), 0);
    }

    /** into the crater, unhurt: set down on the floor, falling and burning forgotten */
    public static void bring(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null || !ensureBuilt(server)) {
            return;
        }
        ServerLevel level = craterLevel(server);
        Vec3 s = spawn();
        p.fallDistance = 0;
        p.clearFire();
        p.setDeltaMovement(Vec3.ZERO);
        p.teleportTo(level, s.x, s.y + 0.05, s.z, 180.0F, 0.0F);
        p.fallDistance = 0;
        p.invulnerableTime = 60;
    }

    /** nobody wanders out of the bowl */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 5 != 0) {
            return;
        }
        ServerLevel level = craterLevel(event.getServer());
        if (level == null) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative()) {
                continue;
            }
            double r = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
            if (r > CraterLayout.BOUNDARY_R) {
                double k = (CraterLayout.BOUNDARY_R - 1.5) / r;
                double x = p.getX() * k, z = p.getZ() * k;
                p.teleportTo(level, x, Math.max(p.getY(), floorAt(x, z)), z, p.getYRot(), p.getXRot());
            }
            if (p.getY() < 40) {
                Vec3 s = spawn();
                p.teleportTo(level, s.x, s.y, s.z, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
            }
        }
    }
}
