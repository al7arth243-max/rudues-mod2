package com.rudues.mod.network;

import com.rudues.mod.ability.AbilityHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CastAbilityPacket {
    private final int ability;

    public CastAbilityPacket(int ability) {
        this.ability = ability;
    }

    public static void encode(CastAbilityPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.ability);
    }

    public static CastAbilityPacket decode(FriendlyByteBuf buf) {
        return new CastAbilityPacket(buf.readVarInt());
    }

    public static void handle(CastAbilityPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            AbilityHandler.tryCast(player, msg.ability);
        }
        ctx.get().setPacketHandled(true);
    }
}
