package com.nazarick.event;

import com.nazarick.NazarickMod;
import com.nazarick.ceremony.ThroneCeremony;
import com.nazarick.entity.NazarickAligned;
import com.nazarick.entity.SeatEntity;
import com.nazarick.item.StaffOfAinzItem;
import com.nazarick.world.NazarickRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = NazarickMod.MODID)
public final class NazarickEvents {
    private NazarickEvents() {}

    /** Nazarick's undead and guardians never take a protected player (ruler, teammates, staff bearer) as a target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof NazarickAligned
                && event.getNewTarget() instanceof Player player
                && !player.level().isClientSide
                && NazarickRules.isProtected(player)) {
            event.setCanceled(true);
        }
    }

    /** Remember when each player last held the staff (for the grace period after swapping items). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (StaffOfAinzItem.isHolding(event.player)) {
            event.player.getPersistentData().putLong(NazarickRules.STAFF_TICK_KEY, event.player.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) ThroneCeremony.tickAll(server);
    }

    /** Controls are locked while the claim ceremony plays. */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof SeatEntity
                && event.getEntityMounting() instanceof Player player && ThroneCeremony.isInCeremony(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ThroneCeremony.onLogout(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ThroneCeremony.clear();
    }
}
