package com.rudues.mod.network;

import com.rudues.mod.client.CorruptionClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: which players carry the corruption right now (entity ids). Sent regularly; an empty list clears it. */
public class CorruptPacket {
    public final int[] ids;

    public CorruptPacket(int[] ids) {
        this.ids = ids;
    }

    public static void encode(CorruptPacket m, FriendlyByteBuf buf) {
        buf.writeVarIntArray(m.ids);
    }

    public static CorruptPacket decode(FriendlyByteBuf buf) {
        return new CorruptPacket(buf.readVarIntArray(64));
    }

    public static void handle(CorruptPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CorruptionClient.set(msg.ids));
        ctx.get().setPacketHandled(true);
    }
}
