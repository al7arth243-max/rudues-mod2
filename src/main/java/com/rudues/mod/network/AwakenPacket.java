package com.rudues.mod.network;

import com.rudues.mod.client.AwakenClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: one of the Shadow's scenes with a player (the awakening, the last clash): which, its phase and
 * when that phase began, the line it plays on, and its QTE gauge (and how long it has been held above the line).
 */
public class AwakenPacket {
    public final int shadowId, playerId;
    public final boolean active;
    public final int scene, phase;
    public final long start;
    public final double ox, oy, oz;
    public final float yaw, gauge, hold;

    public AwakenPacket(int shadowId, int playerId, boolean active, int scene, int phase, long start, double ox, double oy, double oz,
                        float yaw, float gauge, float hold) {
        this.shadowId = shadowId;
        this.playerId = playerId;
        this.active = active;
        this.scene = scene;
        this.phase = phase;
        this.start = start;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.yaw = yaw;
        this.gauge = gauge;
        this.hold = hold;
    }

    public static void encode(AwakenPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.shadowId);
        buf.writeVarInt(m.playerId);
        buf.writeBoolean(m.active);
        buf.writeByte(m.scene);
        buf.writeByte(m.phase);
        buf.writeLong(m.start);
        buf.writeDouble(m.ox);
        buf.writeDouble(m.oy);
        buf.writeDouble(m.oz);
        buf.writeFloat(m.yaw);
        buf.writeFloat(m.gauge);
        buf.writeFloat(m.hold);
    }

    public static AwakenPacket decode(FriendlyByteBuf buf) {
        return new AwakenPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readLong(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(AwakenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AwakenClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
