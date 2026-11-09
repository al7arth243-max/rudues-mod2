package com.nazarick.world;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers where Nazarick's rooms are in a dimension (recorded when they generate or are placed). */
public class NazarickLocationData extends SavedData {
    private static final String NAME = "nazarick_location";
    @Nullable private BlockPos entrance;
    @Nullable private BlockPos throneOrigin;
    @Nullable private BlockPos quartersOrigin;

    public static NazarickLocationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(NazarickLocationData::load, NazarickLocationData::new, NAME);
    }

    private static NazarickLocationData load(CompoundTag tag) {
        NazarickLocationData data = new NazarickLocationData();
        if (tag.contains("Entrance")) data.entrance = NbtUtils.readBlockPos(tag.getCompound("Entrance"));
        if (tag.contains("Throne")) data.throneOrigin = NbtUtils.readBlockPos(tag.getCompound("Throne"));
        if (tag.contains("Quarters")) data.quartersOrigin = NbtUtils.readBlockPos(tag.getCompound("Quarters"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (this.entrance != null) tag.put("Entrance", NbtUtils.writeBlockPos(this.entrance));
        if (this.throneOrigin != null) tag.put("Throne", NbtUtils.writeBlockPos(this.throneOrigin));
        if (this.quartersOrigin != null) tag.put("Quarters", NbtUtils.writeBlockPos(this.quartersOrigin));
        return tag;
    }

    @Nullable public BlockPos getEntrance() { return this.entrance; }
    @Nullable public BlockPos getThroneOrigin() { return this.throneOrigin; }
    @Nullable public BlockPos getQuartersOrigin() { return this.quartersOrigin; }
    public boolean hasEntrance() { return this.entrance != null; }

    public void setEntrance(BlockPos pos) {
        if (!pos.equals(this.entrance)) { this.entrance = pos.immutable(); this.setDirty(); }
    }

    public void setThroneOrigin(BlockPos pos) {
        if (!pos.equals(this.throneOrigin)) { this.throneOrigin = pos.immutable(); this.setDirty(); }
    }

    public void setQuartersOrigin(BlockPos pos) {
        if (!pos.equals(this.quartersOrigin)) { this.quartersOrigin = pos.immutable(); this.setDirty(); }
    }
}
