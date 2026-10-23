package com.rudues.mod.trial;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** World-wide trial state, saved with the overworld. */
public class TrialData extends SavedData {
    private static final String NAME = "rudues_trial";

    /** which version of the arena has been built into the dimension (0 = not built) */
    public int arenaVersion;
    /** a trial is being fought */
    public boolean active;
    /** the first player to enter: the one the trial is about, and the one who earns the power */
    @Nullable
    public UUID chosen;
    /** everyone who has entered this trial to help */
    public final Set<UUID> participants = new HashSet<>();
    /** the one player in this world who carries the Fire Fiend's power */
    @Nullable
    public UUID holder;

    public static TrialData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TrialData::load, TrialData::new, NAME);
    }

    public static TrialData load(CompoundTag tag) {
        TrialData d = new TrialData();
        d.arenaVersion = tag.getInt("ArenaVersion");
        d.active = tag.getBoolean("Active");
        d.chosen = tag.hasUUID("Chosen") ? tag.getUUID("Chosen") : null;
        d.holder = tag.hasUUID("Holder") ? tag.getUUID("Holder") : null;
        for (Tag t : tag.getList("Participants", Tag.TAG_INT_ARRAY)) {
            d.participants.add(NbtUtils.loadUUID(t));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("ArenaVersion", arenaVersion);
        tag.putBoolean("Active", active);
        if (chosen != null) {
            tag.putUUID("Chosen", chosen);
        }
        if (holder != null) {
            tag.putUUID("Holder", holder);
        }
        ListTag list = new ListTag();
        for (UUID id : participants) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("Participants", list);
        return tag;
    }

    public void begin(UUID chosenPlayer) {
        active = true;
        chosen = chosenPlayer;
        participants.clear();
        participants.add(chosenPlayer);
        setDirty();
    }

    public void end() {
        active = false;
        chosen = null;
        participants.clear();
        setDirty();
    }
}
