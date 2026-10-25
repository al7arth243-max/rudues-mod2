package com.rudues.mod.network;

import com.rudues.mod.entity.InfernalHeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: presses of the attack button during a clash. */
public class ClashInputPacket {
    private final int heroId;
    private final int presses;

    public ClashInputPacket(int heroId, int presses) {
        this.heroId = heroId;
        this.presses = presses;
    }

    public static void encode(ClashInputPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.heroId);
        buf.writeVarInt(m.presses);
    }

    public static ClashInputPacket decode(FriendlyByteBuf buf) {
        return new ClashInputPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ClashInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null && p.level().getEntity(msg.heroId) instanceof InfernalHeroEntity hero) {
            hero.clashInput(p, msg.presses);
        } else if (p != null && p.level().getEntity(msg.heroId) instanceof com.rudues.mod.entity.FeatheredKnightEntity knight) {
            knight.clashInput(p, msg.presses);
        } else if (p != null && p.level().getEntity(msg.heroId) instanceof com.rudues.mod.entity.InfernalShadowEntity shadow) {
            shadow.sceneInput(p, msg.presses);
        }
        ctx.get().setPacketHandled(true);
    }
}
