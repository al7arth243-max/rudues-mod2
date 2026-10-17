package com.rudues.mod.network;

import com.rudues.mod.client.ClientPowerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: has powers, Hero of Hell gauge (0-100) and remaining hero ticks. */
public class SyncStatePacket {
    private final boolean hasPowers;
    private final int gauge;
    private final int heroTicks;

    public SyncStatePacket(boolean hasPowers, int gauge, int heroTicks) {
        this.hasPowers = hasPowers;
        this.gauge = gauge;
        this.heroTicks = heroTicks;
    }

    public static void encode(SyncStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasPowers);
        buf.writeVarInt(msg.gauge);
        buf.writeVarInt(msg.heroTicks);
    }

    public static SyncStatePacket decode(FriendlyByteBuf buf) {
        return new SyncStatePacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SyncStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        // plain static fields, no client-only classes touched -> safe on any side
        ClientPowerState.hasPowers = msg.hasPowers;
        ClientPowerState.gauge = msg.gauge;
        ClientPowerState.heroTicks = msg.heroTicks;
        ctx.get().setPacketHandled(true);
    }
}
