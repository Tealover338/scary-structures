package ru.danilapodpivas.horrorstructures.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.danilapodpivas.horrorstructures.client.ClientZoneState;

import java.util.function.Supplier;

public record ZoneEffectPacket(String zone) {
    public static void encode(ZoneEffectPacket packet, FriendlyByteBuf buffer) { buffer.writeUtf(packet.zone, 32); }
    public static ZoneEffectPacket decode(FriendlyByteBuf buffer) { return new ZoneEffectPacket(buffer.readUtf(32)); }
    public static void handle(ZoneEffectPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientZoneState.setZone(packet.zone)));
        context.setPacketHandled(true);
    }
}
