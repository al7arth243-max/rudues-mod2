package com.rudues.mod.network;

import com.rudues.mod.client.PuppetClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: draw a player as a stand-in on the Shadow's rig (their skin, armour and sword) playing a clip for
 * a number of ticks, facing a fixed yaw. An empty clip stops it.
 */
public class PuppetPacket {
    public final int playerId;
    public final String clip;
    public final int ticks;
    public final float yaw;
    public final boolean sword;
    public final boolean corrupt;

    public PuppetPacket(int playerId, String clip, int ticks, float yaw, boolean sword, boolean corrupt) {
        this.playerId = playerId;
        this.clip = clip;
        this.ticks = ticks;
        this.yaw = yaw;
        this.sword = sword;
        this.corrupt = corrupt;
    }

    public static void encode(PuppetPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.playerId);
        buf.writeUtf(m.clip, 64);
        buf.writeVarInt(m.ticks);
        buf.writeFloat(m.yaw);
        buf.writeBoolean(m.sword);
        buf.writeBoolean(m.corrupt);
    }

    public static PuppetPacket decode(FriendlyByteBuf buf) {
        return new PuppetPacket(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(PuppetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PuppetClient.receive(msg));
        ctx.get().setPacketHandled(true);
    }
}
