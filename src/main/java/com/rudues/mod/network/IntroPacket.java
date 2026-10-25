package com.rudues.mod.network;

import com.rudues.mod.client.CastleIntroClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the Feathered Knight's entrance has begun (at this game time), or is over. */
public class IntroPacket {
    public final int knightId;
    public final long start;
    public final boolean on;

    public IntroPacket(int knightId, long start, boolean on) {
        this.knightId = knightId;
        this.start = start;
        this.on = on;
    }

    public static void encode(IntroPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.knightId);
        buf.writeLong(m.start);
        buf.writeBoolean(m.on);
    }

    public static IntroPacket decode(FriendlyByteBuf buf) {
        return new IntroPacket(buf.readVarInt(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(IntroPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CastleIntroClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
