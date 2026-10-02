package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Final pass after FULL chunk promotion, including trees grown by adjacent chunks. */
public final class VegetationRepair {
    private static final int TREE_REACH = 25;
    private static final int CLEANUP_REACH = TerrainPreparationFeature.BLEND_RADIUS + 12;
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static final Set<Pending> QUEUED = new HashSet<>();

    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        synchronized (PENDING) { PENDING.clear(); QUEUED.clear(); }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        // Forge fires this before FULL promotion: never touch the level here.
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)
                || level != level.getServer().overworld() || !level.getServer().isReady()
                || !WorldgenLayout.ready(level)) return;
        int cx = event.getChunk().getPos().x;
        int cz = event.getChunk().getPos().z;
        Map<String, StructureWorldData.Placement> layout = WorldgenLayout.get(level);
        for (StructureDefinition definition : StructureDefinition.ALL) {
            StructureWorldData.Placement placement = layout.get(definition.name());
            if (placement == null) continue;
            int halfX = definition.size().getX() / 2 + CLEANUP_REACH + 16;
            int halfZ = definition.size().getZ() / 2 + CLEANUP_REACH + 16;
            if (Math.abs((cx << 4) + 8 - placement.centerX()) <= halfX
                    && Math.abs((cz << 4) + 8 - placement.centerZ()) <= halfZ) {
                synchronized (PENDING) {
                    Pending pending = new Pending(level, cx, cz, 0);
                    if (QUEUED.add(pending)) PENDING.addLast(pending);
                }
                break;
            }
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long deadline = System.nanoTime() + 4_000_000L;
        for (int i = 0; i < 12 && System.nanoTime() < deadline; i++) {
            Pending pending;
            synchronized (PENDING) {
                pending = PENDING.pollFirst();
                if (pending != null) QUEUED.remove(new Pending(pending.level, pending.cx, pending.cz, 0));
            }
            if (pending == null) return;
            ServerLevel level = pending.level;
            if (level.getServer().overworld() != level) continue;
            if (level.getChunkSource().getChunkNow(pending.cx, pending.cz) == null) {
                if (pending.attempt < 20) synchronized (PENDING) {
                    QUEUED.add(new Pending(level, pending.cx, pending.cz, 0));
                    PENDING.addLast(new Pending(level, pending.cx, pending.cz, pending.attempt + 1));
                }
                continue;
            }
            Map<String, StructureWorldData.Placement> layout = WorldgenLayout.get(level);
            // FULL promotion follows neighboring FEATURES. Each new chunk owns
            // one final pass; rescanning its eight neighbors multiplied work by nine.
            repairChunk(level, pending.cx, pending.cz, layout);
        }
    }

    private static void repairChunk(ServerLevel level, int cx, int cz,
                                    Map<String, StructureWorldData.Placement> layout) {
        int chunkX = cx << 4;
        int chunkZ = cz << 4;
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return;
        LeafSupport leaves = new LeafSupport(level, cx, cz);
        for (StructureDefinition definition : StructureDefinition.ALL) {
            StructureWorldData.Placement placement = layout.get(definition.name());
            if (placement == null) continue;
            int minX = placement.centerX() - definition.size().getX() / 2;
            int minZ = placement.centerZ() - definition.size().getZ() / 2;
            int maxX = minX + definition.size().getX() - 1;
            int maxZ = minZ + definition.size().getZ() - 1;
            if (chunkX > maxX + CLEANUP_REACH || chunkX + 15 < minX - CLEANUP_REACH
                    || chunkZ > maxZ + CLEANUP_REACH || chunkZ + 15 < minZ - CLEANUP_REACH) continue;
            List<BlockPos> intruders = new ArrayList<>();
            TemplateVegetation.Masks mask = TemplateVegetation.slice(level, definition, chunkX - minX, chunkZ - minZ);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = chunkX; x < chunkX + 16; x++) for (int z = chunkZ; z < chunkZ + 16; z++) {
                int dx = Math.max(minX - x, Math.max(0, x - maxX));
                int dz = Math.max(minZ - z, Math.max(0, z - maxZ));
                if (EdgeBlend.distance(x, z, minX, minZ, maxX, maxZ)
                        >= TerrainPreparationFeature.BLEND_RADIUS) continue;
                boolean inside = dx == 0 && dz == 0;
                int ground = placement.originY() + (definition.name().equals("bunker")
                        ? BunkerSurface.at(level, x - minX, z - minZ) : definition.grassLevel());
                if (inside && definition.name().equals("abandoned_park")
                        && EdgeBlend.insideDistance(x, z, minX, minZ, maxX, maxZ) < 24)
                    ground = placement.originY() + Math.min(8, SavedTerrain.at(level, definition, x - minX, z - minZ));
                int top = Math.min(level.getMaxBuildHeight() - 1, Math.max(
                        chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1,
                        placement.originY() + definition.size().getY() + 8));
                int start = Math.max(level.getMinBuildHeight(), inside
                        ? placement.originY() : ground - 12);
                for (int y = start; y <= top; y++) {
                    if (chunk.getSection(chunk.getSectionIndex(y)).hasOnlyAir()) {
                        y = Math.min(top, (y & ~15) + 15);
                        continue;
                    }
                    cursor.set(x, y, z);
                    BlockState state = chunk.getBlockState(cursor);
                    if (state.isAir()) continue;
                    if (inside) {
                        int localY = y - placement.originY();
                        int localX = x - minX;
                        int localZ = z - minZ;
                        int index = (localY << 8) | ((localZ & 15) << 4) | (localX & 15);
                        boolean savedSolid = mask != null && localY >= 0 && mask.solid().get(index);
                        boolean savedAir = mask != null && localY >= 0 && mask.air().get(index);
                        boolean remove = definition.name().equals("bunker")
                                ? savedAir || y > ground && !savedSolid
                                : y > ground && !savedSolid;
                        if (remove) intruders.add(cursor.immutable());
                    } else if (state.is(BlockTags.LEAVES) && !leaves.supports(cursor)) {
                        intruders.add(cursor.immutable());
                    } else if (isFloatingSoil(state)
                            && unsupported(level, cursor)) {
                        intruders.add(cursor.immutable());
                    } else if (isVegetation(state)
                            && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                            && (!state.canSurvive(level, cursor) || unsupported(level, cursor)
                            || isFloatingSoil(loadedState(level, cursor.below()))
                            && unsupported(level, cursor.below()))) {
                        intruders.add(cursor.immutable());
                    }
                }
            }
            Set<Long> removed = new HashSet<>();
            for (BlockPos pos : intruders) {
                boolean log = loadedState(level, pos).is(BlockTags.LOGS);
                if (log) removeTree(level, definition, placement, minX, minZ, maxX, maxZ, pos, removed);
                if (removed.add(pos.asLong())) setAirIfLoaded(level, pos);
            }
        }
    }

    private static void removeTree(ServerLevel level, StructureDefinition definition,
                                   StructureWorldData.Placement placement,
                                   int minX, int minZ, int maxX, int maxZ,
                                   BlockPos root, Set<Long> removed) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        queue.add(root);
        while (!queue.isEmpty() && seen.size() < 1024) {
            BlockPos pos = queue.pollFirst();
            if (!seen.add(pos.asLong()) || pos.getX() < minX - TREE_REACH || pos.getX() > maxX + TREE_REACH
                    || pos.getZ() < minZ - TREE_REACH || pos.getZ() > maxZ + TREE_REACH
                    || Math.abs(pos.getY() - root.getY()) > 45) continue;
            if (!loadedState(level, pos).is(BlockTags.LOGS)
                    || authored(level, definition, placement, pos)) continue;
            logs.add(pos);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                if (x != 0 || y != 0 || z != 0) queue.add(pos.offset(x, y, z));
            }
        }
        for (BlockPos log : logs) {
            if (removed.add(log.asLong())) setAirIfLoaded(level, log);
            for (int x = -4; x <= 4; x++) for (int y = -4; y <= 4; y++) for (int z = -4; z <= 4; z++) {
                if (x * x + y * y + z * z > 25) continue;
                BlockPos leaf = log.offset(x, y, z);
                if (leaf.getX() < minX - TREE_REACH || leaf.getX() > maxX + TREE_REACH
                        || leaf.getZ() < minZ - TREE_REACH || leaf.getZ() > maxZ + TREE_REACH
                        || authored(level, definition, placement, leaf)) continue;
                if (loadedState(level, leaf).is(BlockTags.LEAVES) && removed.add(leaf.asLong()))
                    setAirIfLoaded(level, leaf);
            }
        }
    }

    private static boolean authored(ServerLevel level, StructureDefinition definition,
                                    StructureWorldData.Placement placement, BlockPos pos) {
        int x = pos.getX() - (placement.centerX() - definition.size().getX() / 2);
        int z = pos.getZ() - (placement.centerZ() - definition.size().getZ() / 2);
        return TemplateVegetation.contains(level, definition, x, pos.getY() - placement.originY(), z);
    }

    private static BlockState loadedState(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }

    private static void setAirIfLoaded(ServerLevel level, BlockPos pos) {
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null)
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }

    private static boolean isVegetation(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || state.is(Blocks.VINE) || state.getBlock() instanceof BushBlock
                || state.is(Blocks.SNOW);
    }

    private static boolean isFloatingSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK);
    }

    private static boolean unsupported(ServerLevel level, BlockPos pos) {
        for (int y = 1; y <= 4; y++) {
            BlockState below = loadedState(level, pos.below(y));
            if (below.isSolidRender(level, pos.below(y))) return false;
        }
        return true;
    }

    private record Pending(ServerLevel level, int cx, int cz, int attempt) {}
    private VegetationRepair() {}
}
