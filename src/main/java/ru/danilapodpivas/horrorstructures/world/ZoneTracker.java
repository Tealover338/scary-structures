package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.network.ModNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HorrorStructures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZoneTracker {
    private static final Map<UUID, String> LAST_ZONE = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_APPARITION = new HashMap<>();

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ZONE.remove(event.getEntity().getUUID());
        NEXT_APPARITION.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        String zone = findZone(player);
        String previous = LAST_ZONE.put(player.getUUID(), zone);
        if (!zone.equals(previous)) ModNetwork.sendZone(player, zone);
        if (zone.equals("abandoned_park")) {
            int remaining = NEXT_APPARITION.getOrDefault(player.getUUID(), 120 + player.getRandom().nextInt(180)) - 10;
            if (remaining <= 0) {
                spawnApparition(player);
                remaining = 240 + player.getRandom().nextInt(480);
            }
            NEXT_APPARITION.put(player.getUUID(), remaining);
        } else {
            NEXT_APPARITION.remove(player.getUUID());
        }
    }

    private static void spawnApparition(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int attempt = 0; attempt < 3; attempt++) {
            double angle = Math.toRadians(player.getYRot()) + (player.getRandom().nextBoolean() ? 0.45 : -0.45);
            int distance = 9 + player.getRandom().nextInt(8);
            int x = (int) Math.floor(player.getX() - Math.sin(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.cos(angle) * distance);
            BlockPos probe = new BlockPos(x, player.blockPosition().getY(), z);
            if (!level.hasChunkAt(probe)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - player.getY()) > 8) continue;
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) continue;
            WhiteFigureEntity figure = WhiteFigureEntity.create(level);
            if (figure == null) return;
            figure.moveTo(x + 0.5, y, z + 0.5, 0, 0);
            if (level.addFreshEntity(figure)) figure.illuminate();
            return;
        }
    }

    private static String findZone(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level != level.getServer().overworld()) return "";
        if (!WorldgenLayout.ready(level)) return "";
        StructureWorldData data = StructureWorldData.get(level);
        for (StructureDefinition definition : StructureDefinition.ALL) {
            if (definition.effect() == StructureDefinition.ZoneEffect.NONE) continue;
            StructureWorldData.Placement placement = data.getPlacement(definition.name());
            if (placement == null || !placement.generated()) continue;
            int minX = placement.centerX() - definition.size().getX() / 2;
            int minZ = placement.centerZ() - definition.size().getZ() / 2;
            if (player.getX() >= minX && player.getX() <= minX + definition.size().getX()
                    && player.getZ() >= minZ && player.getZ() <= minZ + definition.size().getZ()) {
                if (definition.name().equals("labyrinth") && (player.getY() < placement.originY() - 2
                        || player.getY() >= placement.originY() + definition.size().getY() + 8)) continue;
                return definition.effect().networkName();
            }
        }
        return "";
    }

    private ZoneTracker() {}
}
