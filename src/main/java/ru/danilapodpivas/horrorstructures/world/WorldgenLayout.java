package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Arrays;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/** Five deterministic sites in a ring around spawn; terrain is adapted instead of searching indefinitely. */
public final class WorldgenLayout {
    private static final Map<ServerLevel, CompletableFuture<Map<String, StructureWorldData.Placement>>> CACHE = new WeakHashMap<>();
    private static final int ANGLES = 24;
    private static final int[] RADII = {560, 680, 800, 920};

    public static Map<String, StructureWorldData.Placement> get(ServerLevel level) {
        CompletableFuture<Map<String, StructureWorldData.Placement>> future;
        synchronized (CACHE) {
            future = CACHE.get(level);
            if (future == null) {
                Map<String, StructureWorldData.Placement> saved = saved(level);
                future = CompletableFuture.completedFuture(saved != null ? saved : create(level));
                CACHE.put(level, future);
            }
        }
        return future.join();
    }

    public static void warm(ServerLevel level) {
        synchronized (CACHE) {
            if (CACHE.containsKey(level)) return;
            Map<String, StructureWorldData.Placement> saved = saved(level);
            CACHE.put(level, saved != null ? CompletableFuture.completedFuture(saved)
                    : CompletableFuture.supplyAsync(() -> create(level)));
        }
    }

    public static boolean ready(ServerLevel level) {
        synchronized (CACHE) {
            CompletableFuture<Map<String, StructureWorldData.Placement>> future = CACHE.get(level);
            return future == null || future.isDone();
        }
    }

    private static Map<String, StructureWorldData.Placement> saved(ServerLevel level) {
        StructureWorldData data = StructureWorldData.peek(level);
        if (data == null) return null;
        Map<String, StructureWorldData.Placement> saved = new LinkedHashMap<>();
        data.entries().forEach(entry -> saved.put(entry.getKey(), entry.getValue()));
        return saved.isEmpty() ? null : Map.copyOf(saved);
    }

    private static Map<String, StructureWorldData.Placement> create(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        double seedRotation = new Random(level.getSeed() ^ 0x484F52524F52L).nextDouble() * Math.PI * 2.0;
        int count = ANGLES * RADII.length;
        Candidate[][] candidates = new Candidate[StructureDefinition.ALL.size()][count];
        for (int i = 0; i < candidates.length; i++) {
            StructureDefinition definition = StructureDefinition.ALL.get(i);
            for (int angle = 0; angle < ANGLES; angle++) for (int r = 0; r < RADII.length; r++) {
                int radius = RADII[r];
                double direction = seedRotation + angle * Math.PI * 2.0 / ANGLES;
                int x = spawn.getX() + Mth.floor(Math.cos(direction) * radius);
                int z = spawn.getZ() + Mth.floor(Math.sin(direction) * radius);
                x = Math.floorDiv(x - definition.size().getX() / 2, 16) * 16 + definition.size().getX() / 2;
                z = Math.floorDiv(z - definition.size().getZ() / 2, 16) * 16 + definition.size().getZ() / 2;
                Terrain terrain = sampleSparse(level, x, z, definition);
                long score = (long) terrain.wet * 100000L + terrain.range * 70L + (radius - 560) * 2L;
                candidates[i][angle * RADII.length + r] = new Candidate(x, z, terrain.median,
                        terrain.wet, terrain.range, score);
            }
        }

        Candidate[] chosen = new Candidate[candidates.length];
        // The bunker must be on dry ground: an open lake beside its saved
        // underground rooms can continuously flood the interior.
        int[] priority = {4, 0, 3, 2, 1};
        for (int i : priority) {
            final int structureIndex = i;
            Integer[] order = new Integer[count];
            for (int j = 0; j < count; j++) order[j] = j;
            Arrays.sort(order, java.util.Comparator.comparingLong(j -> candidates[structureIndex][j].score));
            Candidate best = null;
            long bestLocalScore = Long.MAX_VALUE;
            int checked = 0;
            for (int j : order) {
                Candidate candidate = candidates[i][j];
                if (!separated(candidate, chosen, i)) continue;
                Terrain terrain = sample(level, candidate.x, candidate.z,
                        StructureDefinition.ALL.get(i), i == 4 ? 16 : 32);
                long score = (long) terrain.wet * 100000L + terrain.range * 70L
                        + (Math.round(Math.hypot(candidate.x - spawn.getX(), candidate.z - spawn.getZ())) - 560) * 2L;
                if (score < bestLocalScore) { bestLocalScore = score; best = candidate; }
                checked++;
                if (i == 4 ? terrain.wet == 0 && checked >= 3 || checked >= 20
                        : checked >= 8 || terrain.wet == 0 && checked >= 3) break;
            }
            if (best == null) throw new IllegalStateException("Could not separate horror structure sites");
            chosen[i] = best;
        }

        Map<String, StructureWorldData.Placement> result = new LinkedHashMap<>();
        for (int i = 0; i < StructureDefinition.ALL.size(); i++) {
            StructureDefinition definition = StructureDefinition.ALL.get(i);
            Candidate site = chosen[i];
            Terrain detailed = sample(level, site.x, site.z, definition, 16);
            int groundY = detailed.median;
            if (detailed.wet > 0) groundY = Math.max(groundY, level.getSeaLevel() + 4);
            int minimumGround = level.getMinBuildHeight() + 16 + definition.grassLevel();
            groundY = Math.max(groundY, minimumGround);
            int originY = Math.min(groundY - definition.grassLevel(),
                    level.getMaxBuildHeight() - definition.size().getY() - 4);
            result.put(definition.name(), new StructureWorldData.Placement(site.x, site.z, originY, true));
            ru.danilapodpivas.horrorstructures.HorrorStructures.LOGGER.info(
                    "Site for {} at {},{}: range {}, water samples {}",
                    definition.name(), site.x, site.z, detailed.range, detailed.wet);
        }
        return Map.copyOf(result);
    }

    private static boolean separated(Candidate candidate, Candidate[] chosen, int index) {
        for (int i = 0; i < chosen.length; i++) {
            Candidate other = chosen[i];
            if (other == null) continue;
            int dx = Math.abs(candidate.x - other.x);
            int dz = Math.abs(candidate.z - other.z);
            if (Math.hypot(dx, dz) < 500) return false;
            StructureDefinition a = StructureDefinition.ALL.get(index);
            StructureDefinition b = StructureDefinition.ALL.get(i);
            if (dx < (a.size().getX() + b.size().getX()) / 2 + 16
                    && dz < (a.size().getZ() + b.size().getZ()) / 2 + 16) return false;
        }
        return true;
    }


    private static Terrain sampleSparse(ServerLevel level, int centerX, int centerZ,
                                        StructureDefinition definition) {
        int halfX = definition.size().getX() / 2 + 8;
        int halfZ = definition.size().getZ() / 2 + 8;
        int[] heights = new int[5];
        int wet = 0;
        int index = 0;
        for (int dx = -halfX; dx <= halfX; dx += halfX) for (int dz = -halfZ; dz <= halfZ; dz += halfZ) {
            if (dx != 0 && dz == 0 || dx == 0 && dz != 0) continue;
            int x = centerX + dx;
            int z = centerZ + dz;
            int surface = level.getChunkSource().getGenerator().getBaseHeight(x, z,
                    Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState()) - 1;
            heights[index++] = surface;
            if (surface <= level.getSeaLevel() + 1) {
                int floor = level.getChunkSource().getGenerator().getBaseHeight(x, z,
                        Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState()) - 1;
                if (surface > floor) wet++;
            }
        }
        Arrays.sort(heights);
        return new Terrain(heights[2], heights[4] - heights[0], wet);
    }

    /** A few cheap probes for ring selection, a 16-block grid for the selected sites. */
    private static Terrain sample(ServerLevel level, int centerX, int centerZ,
                                  StructureDefinition definition, int spacing) {
        int halfX = definition.size().getX() / 2 + 8;
        int halfZ = definition.size().getZ() / 2 + 8;
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        int[] heights = new int[spacing == 3 ? 9 :
                ((2 * halfX) / spacing + 1) * ((2 * halfZ) / spacing + 1)];
        int count = 0;
        int wet = 0;
        int incrementX = spacing == 3 ? halfX : spacing;
        int incrementZ = spacing == 3 ? halfZ : spacing;
        for (int dx = -halfX; dx <= halfX; dx += incrementX) {
            for (int dz = -halfZ; dz <= halfZ; dz += incrementZ) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int surface = level.getChunkSource().getGenerator().getBaseHeight(x, z,
                        Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState()) - 1;
                if (count == heights.length) heights = java.util.Arrays.copyOf(heights, heights.length + 32);
                heights[count++] = surface;
                minimum = Math.min(minimum, surface);
                maximum = Math.max(maximum, surface);
                if (surface <= level.getSeaLevel() + 1) {
                    int floor = level.getChunkSource().getGenerator().getBaseHeight(x, z,
                            Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState()) - 1;
                    if (surface - floor > 0) wet++;
                }
            }
        }
        java.util.Arrays.sort(heights, 0, count);
        return new Terrain(heights[count / 2], maximum - minimum, wet);
    }

    private record Candidate(int x, int z, int groundY, int wet, int range, long score) {}
    private record Terrain(int median, int range, int wet) {}
    private WorldgenLayout() {}
}
