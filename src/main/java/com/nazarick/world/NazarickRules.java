package com.nazarick.world;

import com.nazarick.item.StaffOfAinzItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

/** Who Nazarick treats as a master and who it treats as an intruder. Server side only. */
public final class NazarickRules {
    private NazarickRules() {}

    public static final String STAFF_TICK_KEY = "nazarick_staff_tick";
    /** Grace after putting the staff away: 3 seconds. */
    public static final long STAFF_GRACE_TICKS = 60L;

    public static boolean hasRuler(MinecraftServer server) {
        return RulerData.get(server).hasRuler();
    }

    public static boolean isRuler(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        RulerData data = RulerData.get(server);
        return data.hasRuler() && player.getUUID().equals(data.getRuler());
    }

    /** The ruler, or a player on the ruler's scoreboard team. */
    public static boolean isRulerOrTeammate(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        RulerData data = RulerData.get(server);
        if (!data.hasRuler()) return false;
        if (player.getUUID().equals(data.getRuler())) return true;
        PlayerTeam rulerTeam = server.getScoreboard().getPlayersTeam(data.getRulerName());
        return rulerTeam != null && rulerTeam == player.getTeam();
    }

    public static boolean recentlyHeldStaff(Player player) {
        if (StaffOfAinzItem.isHolding(player)) return true;
        long last = player.getPersistentData().getLong(STAFF_TICK_KEY);
        return last > 0L && player.level().getGameTime() - last <= STAFF_GRACE_TICKS;
    }

    /**
     * Undead and guardians leave protected players alone.
     * Before anyone rules: whoever carries the Staff (plus a short grace). After the claim: the ruler and teammates only.
     */
    public static boolean isProtected(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        if (RulerData.get(server).hasRuler()) return isRulerOrTeammate(player);
        return recentlyHeldStaff(player);
    }
}
