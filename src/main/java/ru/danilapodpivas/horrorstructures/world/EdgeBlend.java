package ru.danilapodpivas.horrorstructures.world;

/** Stable, continuous noise shared by the terrain pass and the final edge treatment. */
final class EdgeBlend {
    static double distance(int x, int z, int minX, int minZ, int maxX, int maxZ) {
        int dx = Math.max(minX - x, Math.max(0, x - maxX));
        int dz = Math.max(minZ - z, Math.max(0, z - maxZ));
        double raw = Math.hypot(dx, dz);
        // Keep the actual template boundary exact; bend the transition beyond it.
        return Math.max(0, raw + noise(x, z, 28) * 11 * Math.min(1, raw / 12));
    }

    static int insideDistance(int x, int z, int minX, int minZ, int maxX, int maxZ) {
        return Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
    }

    static double noise(int x, int z, int scale) {
        int cellX = Math.floorDiv(x, scale);
        int cellZ = Math.floorDiv(z, scale);
        double fx = Math.floorMod(x, scale) / (double) scale;
        double fz = Math.floorMod(z, scale) / (double) scale;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = mix(hash(cellX, cellZ), hash(cellX + 1, cellZ), fx);
        double b = mix(hash(cellX, cellZ + 1), hash(cellX + 1, cellZ + 1), fx);
        return mix(a, b, fz);
    }

    static double hash(int x, int z) {
        long value = (long) x * 0x632BE59BD9B4E019L ^ (long) z * 0x9E3779B97F4A7C15L;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        return ((value >>> 40) / (double) (1L << 24)) * 2 - 1;
    }

    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
    private EdgeBlend() {}
}
