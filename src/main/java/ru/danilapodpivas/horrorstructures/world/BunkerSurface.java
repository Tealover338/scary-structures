package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import ru.danilapodpivas.horrorstructures.HorrorStructures;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Height of the landscape that was saved with the bunker, one byte per X/Z column. */
final class BunkerSurface {
    static final int WIDTH = 249;
    static final int DEPTH = 182;
    private static volatile byte[] heights;

    static int at(ServerLevel level, int x, int z) {
        x = Math.max(0, Math.min(WIDTH - 1, x));
        z = Math.max(0, Math.min(DEPTH - 1, z));
        byte[] current = heights;
        if (current == null) {
            synchronized (BunkerSurface.class) {
                current = heights;
                if (current == null) heights = current = load(level);
            }
        }
        return Byte.toUnsignedInt(current[z * WIDTH + x]);
    }

    private static byte[] load(ServerLevel level) {
        byte[] fallback = new byte[WIDTH * DEPTH];
        Arrays.fill(fallback, (byte) 125);
        try (InputStream input = level.getServer().getResourceManager().getResourceOrThrow(
                ResourceLocation.fromNamespaceAndPath(HorrorStructures.MOD_ID, "surface_profiles/bunker.bin")).open()) {
            byte[] data = input.readAllBytes();
            if (data.length != fallback.length) throw new IOException("Invalid bunker surface profile length: " + data.length);
            return data;
        } catch (IOException exception) {
            HorrorStructures.LOGGER.error("Could not load bunker surface profile", exception);
            return fallback;
        }
    }

    private BunkerSurface() {}
}
