package com.rudues.mod.network;

import com.rudues.mod.client.ClashClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the state of an end-fight clash (see ClashScript). Sent on every phase change and with QTE updates. */
public class ClashPacket {
    public final int heroId, playerId;
    public final boolean active;
    public final int stage, phase, result;
    public final long start;
    public final double ox, oy, oz;
    public final float yaw, dHero, dPlayer, balance;

    public ClashPacket(int heroId, int playerId, boolean active, int stage, int phase, int result, long start,
                       double ox, double oy, double oz, float yaw, float dHero, float dPlayer, float balance) {
        this.heroId = heroId;
        this.playerId = playerId;
        this.active = active;
        this.stage = stage;
        this.phase = phase;
        this.result = result;
        this.start = start;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.yaw = yaw;
        this.dHero = dHero;
        this.dPlayer = dPlayer;
        this.balance = balance;
    }

    public static void encode(ClashPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.heroId);
        buf.writeVarInt(m.playerId);
        buf.writeBoolean(m.active);
        buf.writeByte(m.stage);
        buf.writeByte(m.phase);
        buf.writeByte(m.result);
        buf.writeLong(m.start);
        buf.writeDouble(m.ox);
        buf.writeDouble(m.oy);
        buf.writeDouble(m.oz);
        buf.writeFloat(m.yaw);
        buf.writeFloat(m.dHero);
        buf.writeFloat(m.dPlayer);
        buf.writeFloat(m.balance);
    }

    public static ClashPacket decode(FriendlyByteBuf buf) {
        return new ClashPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readByte(),
                buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat());
    }

    public static void handle(ClashPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClashClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
