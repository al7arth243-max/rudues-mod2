package com.rudues.mod.network;

import com.rudues.mod.client.AwakenClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the Shadow's awakening cutscene with a player started (or ended): when, and the line it plays on. */
public class AwakenPacket {
    public final int shadowId, playerId;
    public final boolean active;
    public final long start;
    public final double ox, oy, oz;
    public final float yaw;

    public AwakenPacket(int shadowId, int playerId, boolean active, long start, double ox, double oy, double oz, float yaw) {
        this.shadowId = shadowId;
        this.playerId = playerId;
        this.active = active;
        this.start = start;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.yaw = yaw;
    }

    public static void encode(AwakenPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.shadowId);
        buf.writeVarInt(m.playerId);
        buf.writeBoolean(m.active);
        buf.writeLong(m.start);
        buf.writeDouble(m.ox);
        buf.writeDouble(m.oy);
        buf.writeDouble(m.oz);
        buf.writeFloat(m.yaw);
    }

    public static AwakenPacket decode(FriendlyByteBuf buf) {
        return new AwakenPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readLong(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat());
    }

    public static void handle(AwakenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AwakenClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
