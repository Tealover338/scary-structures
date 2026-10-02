package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;

/** Heights measured from saved soil, not the most common grass layer. */
final class SavedTerrain {
    private static volatile byte[] park;

    static int at(ServerLevel level, StructureDefinition definition, int x, int z) {
        if (definition.name().equals("bunker")) return BunkerSurface.at(level, x, z);
        if (!definition.name().equals("abandoned_park")) return definition.grassLevel();
        byte[] data = park;
        if (data == null) synchronized (SavedTerrain.class) {
            data = park;
            if (data == null) {
                try (var input = level.getServer().getResourceManager().getResourceOrThrow(
                        ResourceLocation.fromNamespaceAndPath("horror_structures", "surface_profiles/abandoned_park.bin")).open()) {
                    data = input.readAllBytes();
                    if (data.length != 230 * 220) throw new IOException("Invalid park height profile");
                    park = data;
                } catch (IOException e) { throw new IllegalStateException("Cannot load park terrain", e); }
            }
        }
        return Byte.toUnsignedInt(data[Math.max(0, Math.min(219, z)) * 230 + Math.max(0, Math.min(229, x))]);
    }

    private SavedTerrain() {}
}
