package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** One multi-source leaf-distance flood per chunk, instead of a search per leaf. */
final class LeafSupport {
    private final ServerLevel level;
    private final int cx, cz;
    private Set<Long> supported;
    LeafSupport(ServerLevel level, int cx, int cz) { this.level = level; this.cx = cx; this.cz = cz; }
    boolean supports(BlockPos pos) {
        if (supported == null) build();
        return supported.contains(pos.asLong());
    }
    private void build() {
        supported = new HashSet<>();
        Set<Long> leaves = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        int minX = (cx << 4) - 6, minZ = (cz << 4) - 6;
        int maxX = (cx << 4) + 21, maxZ = (cz << 4) + 21;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int xChunk = cx-1; xChunk <= cx+1; xChunk++) for (int zChunk = cz-1; zChunk <= cz+1; zChunk++) {
            var chunk = level.getChunkSource().getChunkNow(xChunk, zChunk);
            if (chunk == null) continue;
            for (int sectionIndex = 0; sectionIndex < chunk.getSectionsCount(); sectionIndex++) {
                var section = chunk.getSection(sectionIndex);
                if (!section.maybeHas(s -> s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS))) continue;
                int bottom = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int x = Math.max(minX, xChunk << 4); x <= Math.min(maxX, (xChunk << 4) + 15); x++)
                    for (int z = Math.max(minZ, zChunk << 4); z <= Math.min(maxZ, (zChunk << 4) + 15); z++)
                        for (int y = bottom; y < bottom + 16; y++) {
                            var state = section.getBlockState(x & 15, y & 15, z & 15);
                            pos.set(x, y, z);
                            if (state.is(BlockTags.LOGS)) frontier.add(pos.immutable());
                            else if (state.is(BlockTags.LEAVES)) {
                                leaves.add(pos.asLong());
                                // Do not cut a healthy tree whose trunk is not loaded yet.
                                if (level.getChunkSource().getChunkNow((x-1) >> 4, z >> 4) == null
                                        || level.getChunkSource().getChunkNow((x+1) >> 4, z >> 4) == null
                                        || level.getChunkSource().getChunkNow(x >> 4, (z-1) >> 4) == null
                                        || level.getChunkSource().getChunkNow(x >> 4, (z+1) >> 4) == null) {
                                    supported.add(pos.asLong());
                                    frontier.add(pos.immutable());
                                }
                            }
                        }
            }
        }
        for (int distance = 0; distance < 6 && !frontier.isEmpty(); distance++) {
            int size = frontier.size();
            for (int i = 0; i < size; i++) {
                BlockPos source = frontier.removeFirst();
                for (Direction direction : Direction.values()) {
                    BlockPos next = source.relative(direction);
                    if (leaves.contains(next.asLong()) && supported.add(next.asLong())) frontier.addLast(next);
                }
            }
        }
    }
}
