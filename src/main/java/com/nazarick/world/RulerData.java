package com.nazarick.world;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The single "world has a ruler" record. While no ruler exists the raid path is open;
 * once someone claims the throne it closes until an operator runs /nazarick ruler reset.
 */
public class RulerData extends SavedData {
    private static final String NAME = "nazarick_ruler";
    @Nullable private UUID ruler;
    private String rulerName = "";

    public static RulerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RulerData::load, RulerData::new, NAME);
    }

    private static RulerData load(CompoundTag tag) {
        RulerData data = new RulerData();
        if (tag.hasUUID("Ruler")) {
            data.ruler = tag.getUUID("Ruler");
            data.rulerName = tag.getString("RulerName");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (this.ruler != null) {
            tag.putUUID("Ruler", this.ruler);
            tag.putString("RulerName", this.rulerName);
        }
        return tag;
    }

    public boolean hasRuler() { return this.ruler != null; }
    @Nullable public UUID getRuler() { return this.ruler; }
    public String getRulerName() { return this.rulerName; }

    public void setRuler(UUID id, String name) {
        this.ruler = id;
        this.rulerName = name;
        this.setDirty();
    }

    public void clear() {
        this.ruler = null;
        this.rulerName = "";
        this.setDirty();
    }
}
