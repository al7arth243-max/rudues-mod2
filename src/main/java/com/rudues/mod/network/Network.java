package com.rudues.mod.network;

import com.rudues.mod.RuduesMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class Network {
    private static final String PROTOCOL = "11";
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
        CHANNEL.messageBuilder(PuppetPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PuppetPacket::encode).decoder(PuppetPacket::decode)
                .consumerMainThread(PuppetPacket::handle).add();
        CHANNEL.messageBuilder(PummelPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PummelPacket::encode).decoder(PummelPacket::decode)
                .consumerMainThread(PummelPacket::handle).add();
        CHANNEL.messageBuilder(AwakenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AwakenPacket::encode).decoder(AwakenPacket::decode)
                .consumerMainThread(AwakenPacket::handle).add();
        CHANNEL.messageBuilder(CorruptPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CorruptPacket::encode).decoder(CorruptPacket::decode)
                .consumerMainThread(CorruptPacket::handle).add();
        CHANNEL.messageBuilder(BoomPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BoomPacket::encode).decoder(BoomPacket::decode)
                .consumerMainThread(BoomPacket::handle).add();
        CHANNEL.messageBuilder(IntroPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(IntroPacket::encode).decoder(IntroPacket::decode)
                .consumerMainThread(IntroPacket::handle).add();
    }

    /** a rendered explosion, for everyone near enough to see it */
    public static void sendBoom(net.minecraft.server.level.ServerLevel level, double x, double y, double z, double ground, float size) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, 160.0, level.dimension())),
                new BoomPacket(x, y, z, ground, size));
    }

    /** a player drawn as a stand-in playing a clip, for everyone who can see them (and themselves) */
    public static void sendPuppet(ServerPlayer player, String clip, int ticks, float yaw, boolean sword, boolean corrupt) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PuppetPacket(player.getId(), clip, ticks, yaw, sword, corrupt));
    }

    public static void sendState(ServerPlayer player, boolean hasPowers, int gauge, int heroTicks, int semiTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncStatePacket(hasPowers, gauge, heroTicks, semiTicks));
    }
}
