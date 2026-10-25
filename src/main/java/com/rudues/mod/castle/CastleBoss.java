package com.rudues.mod.castle;

import com.rudues.mod.RuduesMod;
import com.rudues.mod.ability.PowerData;
import com.rudues.mod.entity.FeatheredKnightEntity;
import com.rudues.mod.entity.KnightIntro;
import com.rudues.mod.network.IntroPacket;
import com.rudues.mod.network.Network;
import com.rudues.mod.registry.ModEntities;
import com.rudues.mod.world.CastleLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The fight on the keep roof. A player with the infernal powers who steps out onto the roof starts it: the screen
 * fades in on them walking out to the middle while the Feathered Knight, sat on the south parapet, turns to look and
 * drops down to face them ({@link KnightIntro}). Then the fight; if the player dies, leaves or wanders off the roof
 * for long, she goes and the roof is reset. To meet her again after a fight, go back down and come up again.
 */
@Mod.EventBusSubscriber(modid = RuduesMod.MODID)
public final class CastleBoss {
    private CastleBoss() {
    }

    private static final class Fight {
        final UUID player;
        UUID knight;
        final long start;
        int away;

        Fight(UUID player, long start) {
            this.player = player;
            this.start = start;
        }
    }

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
    /** players who have been down off the roof since their last fight (so the roof can start one) */
    private static final Set<UUID> SPENT = new HashSet<>();
    private static final int AWAY_LIMIT = 20 * 12;

    public static boolean onRoof(Entity e) {
        return e.getY() >= CastleLayout.ROOF - 0.6 && e.getY() < CastleLayout.ROOF + 8
                && e.getX() >= CastleLayout.KX0 && e.getX() < CastleLayout.KX1 + 1
                && e.getZ() >= CastleLayout.KZ0 && e.getZ() < CastleLayout.KZ1 + 1;
    }

    /** ticks into this player's entrance scene, or -1 */
    public static int introTick(Player p) {
        Fight f = FIGHTS.get(p.getUUID());
        if (f == null) {
            return -1;
        }
        long t = p.level().getGameTime() - f.start;
        return t <= KnightIntro.RELEASE ? (int) t : -1;
    }

    public static boolean inIntro(Player p) {
        return introTick(p) >= 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerLevel level = CastleManager.castleLevel(event.getServer());
        if (level == null) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (FIGHTS.containsKey(p.getUUID()) || p.isSpectator() || p.isCreative()) {
                continue;
            }
            boolean roof = onRoof(p);
            if (!roof) {
                SPENT.remove(p.getUUID());
            } else if (!SPENT.contains(p.getUUID()) && PowerData.has(p) && p.onGround() && FIGHTS.isEmpty()) {
                begin(level, p);
            }
        }
        Iterator<Map.Entry<UUID, Fight>> it = FIGHTS.entrySet().iterator();
        while (it.hasNext()) {
            Fight f = it.next().getValue();
            if (!tick(level, f)) {
                it.remove();
            }
        }
    }

    private static void begin(ServerLevel level, ServerPlayer p) {
        // anything left over from a fight the server never finished
        for (FeatheredKnightEntity old : level.getEntitiesOfClass(FeatheredKnightEntity.class, arenaBox())) {
            old.discard();
        }
        FeatheredKnightEntity k = ModEntities.FEATHERED_KNIGHT.get().create(level);
        if (k == null) {
            return;
        }
        k.moveTo(KnightIntro.LAND_X, KnightIntro.Y, KnightIntro.LAND_Z, KnightIntro.KNIGHT_YAW, 0.0F);
        k.setYBodyRot(KnightIntro.KNIGHT_YAW);
        k.setYHeadRot(KnightIntro.KNIGHT_YAW);
        level.addFreshEntity(k);
        k.beginIntro(p);
        Fight f = new Fight(p.getUUID(), level.getGameTime());
        f.knight = k.getUUID();
        FIGHTS.put(p.getUUID(), f);
        SPENT.add(p.getUUID());
        p.setDeltaMovement(Vec3.ZERO);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new IntroPacket(k.getId(), f.start, true));
    }

    private static AABB arenaBox() {
        return new AABB(CastleLayout.KX0 - 4, CastleLayout.ROOF - 4, CastleLayout.KZ0 - 4,
                CastleLayout.KX1 + 5, CastleLayout.ROOF + 24, CastleLayout.KZ1 + 5);
    }

    /** false once the fight is over */
    private static boolean tick(ServerLevel level, Fight f) {
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(f.player);
        Entity ke = level.getEntity(f.knight);
        FeatheredKnightEntity k = ke instanceof FeatheredKnightEntity fk ? fk : null;
        boolean here = p != null && p.isAlive() && p.level() == level;
        if (k == null || !k.isAlive()) {
            if (here) {
                stopIntro(p);
                if (k != null && k.isDeadOrDying()) {
                    p.displayClientMessage(Component.translatable("message.rudues.knight_defeated"), true);
                }
            }
            return false;
        }
        if (!here) {
            k.discard();
            if (p != null) {
                stopIntro(p);
            }
            return false;
        }
        long t = level.getGameTime() - f.start;
        if (t <= KnightIntro.RELEASE) {
            intro(p, (int) t);
            return true;
        }
        k.setTarget(p);
        // wandered off: she waits a while, then the roof resets
        if (!onRoof(p) && p.getY() < CastleLayout.ROOF - 6) {
            if (++f.away > AWAY_LIMIT) {
                k.discard();
                return false;
            }
        } else {
            f.away = 0;
        }
        return true;
    }

    /** the player's side of the entrance: set down, walked to the middle (by their own client), held there */
    private static void intro(ServerPlayer p, int t) {
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        if (t == KnightIntro.PLACE) {
            p.connection.teleport(KnightIntro.WALK_X, KnightIntro.Y, KnightIntro.WALK_Z0, KnightIntro.PLAYER_YAW, 0.0F);
        }
        if (t == KnightIntro.WALK_START) {
            Network.sendPuppet(p, "castle_walk", KnightIntro.WALK_END - KnightIntro.WALK_START + 2, KnightIntro.PLAYER_YAW, false, false);
        }
        if (t >= KnightIntro.WALK_END) {
            double z = KnightIntro.WALK_Z1;
            if (Math.abs(p.getX() - KnightIntro.WALK_X) > 0.3 || Math.abs(p.getZ() - z) > 0.3 || Math.abs(p.getY() - KnightIntro.Y) > 0.6) {
                p.connection.teleport(KnightIntro.WALK_X, KnightIntro.Y, z, KnightIntro.PLAYER_YAW, 0.0F);
            }
        }
        if (t == KnightIntro.RELEASE) {
            p.displayClientMessage(Component.translatable("message.rudues.knight_arrives"), true);
        }
    }

    private static void stopIntro(ServerPlayer p) {
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new IntroPacket(-1, 0L, false));
    }

    /** the scene can't be interrupted: the player can't be hurt or hurt anything until it's over */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide && inIntro(p)) {
            event.setCanceled(true);
        } else if (event.getSource().getEntity() instanceof Player p && !p.level().isClientSide && inIntro(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Fight f = FIGHTS.remove(p.getUUID());
            if (f != null && p.level() instanceof ServerLevel sl && sl.getEntity(f.knight) instanceof FeatheredKnightEntity k) {
                k.discard();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SPENT.remove(event.getEntity().getUUID());
    }

    /** /rudues castle knight: ends any fight on the roof */
    public static void reset(ServerLevel level) {
        for (FeatheredKnightEntity old : level.getEntitiesOfClass(FeatheredKnightEntity.class, arenaBox())) {
            old.discard();
        }
        FIGHTS.clear();
        SPENT.clear();
    }

    @Nullable
    public static UUID knightOf(Player p) {
        Fight f = FIGHTS.get(p.getUUID());
        return f == null ? null : f.knight;
    }
}
