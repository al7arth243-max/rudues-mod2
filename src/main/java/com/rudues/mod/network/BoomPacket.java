package com.rudues.mod.network;

import com.rudues.mod.client.ExplosionFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: draw a rendered explosion (fireball, shock rings, flash) at this spot. */
public class BoomPacket {
    private final double x, y, z, ground;
    private final float size;

    public BoomPacket(double x, double y, double z, double ground, float size) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.ground = ground;
        this.size = size;
    }

    public static void encode(BoomPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeDouble(msg.ground);
        buf.writeFloat(msg.size);
    }

    public static BoomPacket decode(FriendlyByteBuf buf) {
        return new BoomPacket(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
    }

    public static void handle(BoomPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ExplosionFx.spawn(msg.x, msg.y, msg.z, msg.ground, msg.size));
        ctx.get().setPacketHandled(true);
    }
}
