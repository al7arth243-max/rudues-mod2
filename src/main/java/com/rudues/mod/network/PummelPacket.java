package com.rudues.mod.network;

import com.rudues.mod.client.PummelClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: a player has grabbed a victim for Pummel (from game time start, facing yaw), or let go of it. */
public class PummelPacket {
    public final int playerId, victimId;
    public final long start;
    public final float yaw;
    public final boolean active;

    public PummelPacket(int playerId, int victimId, long start, float yaw, boolean active) {
        this.playerId = playerId;
        this.victimId = victimId;
        this.start = start;
        this.yaw = yaw;
        this.active = active;
    }

    public static void encode(PummelPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.playerId);
        buf.writeVarInt(m.victimId);
        buf.writeLong(m.start);
        buf.writeFloat(m.yaw);
        buf.writeBoolean(m.active);
    }

    public static PummelPacket decode(FriendlyByteBuf buf) {
        return new PummelPacket(buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(PummelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PummelClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
