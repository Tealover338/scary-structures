package ru.danilapodpivas.horrorstructures.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.danilapodpivas.horrorstructures.HorrorStructures;

import java.util.Optional;

public final class ModNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(HorrorStructures.MOD_ID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    public static void register() {
        CHANNEL.registerMessage(0, ZoneEffectPacket.class, ZoneEffectPacket::encode, ZoneEffectPacket::decode,
                ZoneEffectPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendZone(ServerPlayer player, String zone) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ZoneEffectPacket(zone));
    }

    private ModNetwork() {}
}
