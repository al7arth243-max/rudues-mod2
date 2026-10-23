package com.rudues.mod.network;

import com.rudues.mod.client.ClientShadowFire;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: an entity started or stopped burning with the Shadow's purple fire. */
public class PurpleFirePacket {
    private final int entityId;
    private final boolean burning;

    public PurpleFirePacket(int entityId, boolean burning) {
        this.entityId = entityId;
        this.burning = burning;
    }

    public static void encode(PurpleFirePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.burning);
    }

    public static PurpleFirePacket decode(FriendlyByteBuf buf) {
        return new PurpleFirePacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PurpleFirePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientShadowFire.set(msg.entityId, msg.burning));
        ctx.get().setPacketHandled(true);
    }
}
