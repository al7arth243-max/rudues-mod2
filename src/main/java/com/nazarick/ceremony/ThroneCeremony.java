package com.nazarick.ceremony;

import com.nazarick.entity.NazarickNpcEntity;
import com.nazarick.entity.SeatEntity;
import com.nazarick.entity.SebasEntity;
import com.nazarick.item.RingOfAinzOoalGownItem;
import com.nazarick.item.StaffOfAinzItem;
import com.nazarick.registry.ModEntities;
import com.nazarick.registry.ModItems;
import com.nazarick.world.RulerData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * The throne claim (from the design doc):
 *  Right-click the throne with the staff -> confirm "Claim Nazarick as its ruler?"
 *  1. You sit, controls lock, lights dim.
 *  2. Seven gems appear in the staff one by one (sound + particles); the staff takes its full form.
 *  3. Sebas enters, bows and gives a short welcome. (The maids join once they are added.)
 *  4. Controls unlock, the ruler is saved, the world flips; the Ring of Ainz Ooal Gown is given.
 * The claim only completes at the end (logging out cancels it). If enemies are hunting you, the scene waits.
 */
public final class ThroneCeremony {
    private ThroneCeremony() {}

    private static final int GEM_START = 40;
    private static final int GEM_INTERVAL = 25;
    private static final int FULL_FORM = GEM_START + GEM_INTERVAL * StaffOfAinzItem.MAX_GEMS;   // 215
    private static final int SERVANTS_ENTER = FULL_FORM + 20;                                    // 235
    private static final int SERVANTS_BOW = SERVANTS_ENTER + 100;                                // 335
    private static final int FINISH = SERVANTS_BOW + 70;                                         // 405
    private static final long CONFIRM_WINDOW = 600L;

    private record Pending(BlockPos throne, long expiresAt) {}

    private static final class Ceremony {
        final UUID playerId;
        final ServerLevel level;
        final BlockPos throne;
        int tick;
        int pausedNotice;
        SeatEntity seat;
        final List<NazarickNpcEntity> servants = new ArrayList<>();

        Ceremony(UUID playerId, ServerLevel level, BlockPos throne) {
            this.playerId = playerId;
            this.level = level;
            this.throne = throne;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Ceremony> ACTIVE = new HashMap<>();

    public static boolean isInCeremony(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    // ================================================================== throne interaction
    public static void onThroneUsed(ServerPlayer player, BlockPos throne) {
        RulerData ruler = RulerData.get(player.server);
        if (ruler.hasRuler()) {
            if (player.getUUID().equals(ruler.getRuler())) {
                sit(player, throne);
            } else {
                player.displayClientMessage(Component.literal("This throne belongs to " + ruler.getRulerName() + ".")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            return;
        }
        if (!ACTIVE.isEmpty()) {
            player.displayClientMessage(Component.literal("A claim is already under way.").withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        if (!StaffOfAinzItem.isHolding(player)) {
            player.displayClientMessage(Component.literal("The throne does not acknowledge you. Return bearing the Staff of Ainz Ooal Gown.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        PENDING.put(player.getUUID(), new Pending(throne.immutable(), player.level().getGameTime() + CONFIRM_WINDOW));
        Component yes = Component.literal("[Claim]").withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nazarick claim confirm"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Become the ruler of Nazarick"))));
        Component no = Component.literal("[Cancel]").withStyle(style -> style.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nazarick claim cancel")));
        player.sendSystemMessage(Component.literal("Claim Nazarick as its ruler? ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(yes).append(" ").append(no));
    }

    public static void cancelPending(ServerPlayer player) {
        if (PENDING.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.literal("You step away from the throne.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Called by /nazarick claim confirm. */
    public static void confirm(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null || player.level().getGameTime() > pending.expiresAt()) {
            player.displayClientMessage(Component.literal("Approach the throne again to claim it.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (RulerData.get(player.server).hasRuler() || !ACTIVE.isEmpty()) return;
        if (!StaffOfAinzItem.isHolding(player)) {
            player.displayClientMessage(Component.literal("You must hold the Staff of Ainz Ooal Gown.").withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        if (player.blockPosition().distSqr(pending.throne()) > 36.0D) {
            player.displayClientMessage(Component.literal("You have strayed too far from the throne.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Ceremony c = new Ceremony(player.getUUID(), player.serverLevel(), pending.throne());
        ACTIVE.put(player.getUUID(), c);
        StaffOfAinzItem.setGems(StaffOfAinzItem.findStaff(player), 0);
        c.seat = sit(player, c.throne);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, FINISH + 200, 0, false, false));
        c.level.playSound(null, c.throne, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    private static SeatEntity sit(ServerPlayer player, BlockPos throne) {
        SeatEntity seat = new SeatEntity(ModEntities.SEAT.get(), player.level());
        seat.setPos(throne.getX() + 0.5D, throne.getY() + 0.3D, throne.getZ() + 0.5D);
        player.level().addFreshEntity(seat);
        player.startRiding(seat, true);
        return seat;
    }

    // ================================================================== ticking
    public static void tickAll(MinecraftServer server) {
        PENDING.entrySet().removeIf(e -> server.overworld().getGameTime() > e.getValue().expiresAt() + 20L);
        for (Ceremony c : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(c.playerId);
            if (player == null || player.level() != c.level || !player.isAlive()) {
                abort(c, player, "The claim was interrupted.");
                continue;
            }
            tick(c, player);
        }
    }

    private static void tick(Ceremony c, ServerPlayer player) {
        ItemStack staff = StaffOfAinzItem.findStaff(player);
        if (staff.isEmpty()) {
            abort(c, player, "The staff has left your possession. The claim fails.");
            return;
        }
        if (player.getVehicle() == null && c.tick < FINISH) {
            c.seat = sit(player, c.throne);
        }
        // Combat pauses the scene until the hall is clear.
        boolean threatened = !c.level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(24.0D),
                m -> m.getTarget() == player).isEmpty();
        if (threatened) {
            if (c.pausedNotice-- <= 0) {
                player.displayClientMessage(Component.literal("The ceremony waits... the hall is not yet secure.").withStyle(ChatFormatting.RED), true);
                c.pausedNotice = 40;
            }
            return;
        }

        int t = c.tick;
        double x = player.getX(), y = player.getY() + 1.4D, z = player.getZ();
        if (t >= GEM_START && t < FULL_FORM && (t - GEM_START) % GEM_INTERVAL == 0) {
            int gem = (t - GEM_START) / GEM_INTERVAL + 1;
            StaffOfAinzItem.setGems(staff, gem);
            c.level.sendParticles(ParticleTypes.END_ROD, x, y, z, 24, 0.5D, 0.5D, 0.5D, 0.04D);
            c.level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 40, 0.8D, 0.8D, 0.8D, 0.6D);
            c.level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 0.6F + gem * 0.12F);
            player.displayClientMessage(Component.literal("A gem awakens in the staff (" + gem + "/7)").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        if (t == FULL_FORM) {
            c.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 120, 0.6D, 1.0D, 0.6D, 0.4D);
            c.level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
            player.displayClientMessage(Component.literal("The Staff of Ainz Ooal Gown takes its true form.").withStyle(ChatFormatting.GOLD), true);
        }
        if (t == SERVANTS_ENTER) {
            summonServants(c);
            c.level.playSound(null, c.throne, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.5F);
        }
        if (t == SERVANTS_BOW) {
            for (NazarickNpcEntity npc : c.servants) npc.bow(player);
            for (NazarickNpcEntity npc : c.servants) {
                if (npc instanceof SebasEntity sebas) {
                    sebas.say(player, "Welcome, Supreme One. The Great Tomb of Nazarick and all who dwell within it are yours to command.");
                }
            }
        }
        if (t >= FINISH) {
            finish(c, player);
            return;
        }
        c.tick++;
    }

    /** Throne-relative points: the throne sits 8 blocks above the hall floor, the dais steps begin 19 blocks in front. */
    private static BlockPos hall(Ceremony c, int dx, int dz) {
        return c.throne.offset(dx, -8, dz);
    }

    private static void summonServants(Ceremony c) {
        AABB area = new AABB(c.throne).inflate(320.0D, 128.0D, 320.0D);
        List<SebasEntity> sebas = c.level.getEntitiesOfClass(SebasEntity.class, area);
        SebasEntity butler;
        if (sebas.isEmpty()) {   // Sebas is available from the claim on, summoned or not
            butler = ModEntities.SEBAS.get().create(c.level);
            if (butler != null) {
                BlockPos p = hall(c, 0, -20);
                butler.moveTo(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, 180.0F, 0.0F);
                butler.finalizeSpawn(c.level, c.level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null, null);
                c.level.addFreshEntity(butler);
            }
        } else {
            butler = sebas.get(0);
        }
        BlockPos look = c.throne;
        if (butler != null) {
            butler.beginEscort(hall(c, 0, -34), hall(c, 0, -20), look, FINISH - SERVANTS_ENTER + 60);
            c.servants.add(butler);
        }
    }

    private static void finish(Ceremony c, ServerPlayer player) {
        ACTIVE.remove(c.playerId);
        RulerData.get(player.server).setRuler(player.getUUID(), player.getGameProfile().getName());
        StaffOfAinzItem.setGems(StaffOfAinzItem.findStaff(player), StaffOfAinzItem.MAX_GEMS);
        player.removeEffect(MobEffects.DARKNESS);
        player.stopRiding();
        if (c.seat != null) c.seat.discard();
        if (!RingOfAinzOoalGownItem.hasRing(player)) {
            ItemStack ring = new ItemStack(ModItems.RING_OF_AINZ_OOAL_GOWN.get());
            if (!player.getInventory().add(ring)) player.drop(ring, false);
        }
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Ruler of Nazarick").withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("The Great Tomb bows before you").withStyle(ChatFormatting.LIGHT_PURPLE)));
        c.level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.server.getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().getName()
                + " has claimed the Great Tomb of Nazarick.").withStyle(ChatFormatting.GOLD), false);
        // Servants linger a moment, then return to their duties (their escorts time out on their own).
    }

    private static void abort(Ceremony c, ServerPlayer player, String reason) {
        ACTIVE.remove(c.playerId);
        for (NazarickNpcEntity npc : c.servants) {
            if (npc.isAlive()) npc.endEscort();
        }
        if (c.seat != null) c.seat.discard();
        if (player != null) {
            ItemStack staff = StaffOfAinzItem.findStaff(player);
            if (!staff.isEmpty()) StaffOfAinzItem.setGems(staff, 0);
            player.removeEffect(MobEffects.DARKNESS);
            player.stopRiding();
            player.displayClientMessage(Component.literal(reason).withStyle(ChatFormatting.RED), true);
        }
    }

    public static void onLogout(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        Ceremony c = ACTIVE.get(player.getUUID());
        if (c != null) abort(c, player, "The claim was interrupted.");
    }

    public static void clear() {
        PENDING.clear();
        ACTIVE.clear();
    }
}
