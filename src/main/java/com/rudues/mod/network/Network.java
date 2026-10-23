package com.rudues.mod.network;

import com.rudues.mod.RuduesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class Network {
    private static final String PROTOCOL = "5";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RuduesMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(CastAbilityPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CastAbilityPacket::encode).decoder(CastAbilityPacket::decode)
                .consumerMainThread(CastAbilityPacket::handle).add();
        CHANNEL.messageBuilder(SyncStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncStatePacket::encode).decoder(SyncStatePacket::decode)
                .consumerMainThread(SyncStatePacket::handle).add();
        CHANNEL.messageBuilder(PurpleFirePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PurpleFirePacket::encode).decoder(PurpleFirePacket::decode)
                .consumerMainThread(PurpleFirePacket::handle).add();
        CHANNEL.messageBuilder(ClashPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClashPacket::encode).decoder(ClashPacket::decode)
                .consumerMainThread(ClashPacket::handle).add();
        CHANNEL.messageBuilder(ClashInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClashInputPacket::encode).decoder(ClashInputPacket::decode)
                .consumerMainThread(ClashInputPacket::handle).add();
    }

    public static void sendState(ServerPlayer player, boolean hasPowers, int gauge, int heroTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncStatePacket(hasPowers, gauge, heroTicks));
    }
}
