package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import ru.danilapodpivas.horrorstructures.registry.ModBlocks;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Gradually swaps lanterns for zero-light equivalents while someone is inside. */
public final class LabyrinthBlackout {
    private static final int RADIUS = 32;
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    public static void onBlockPlaced(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) return;
        State current;
        synchronized (STATES) { current = STATES.get(level); }
        if (current != null && current.active && replacement(event.getPlacedBlock(), true) != null)
            current.enqueue(event.getPos().getX() >> 4, event.getPos().getZ() >> 4, 0);
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()
                || !level.getServer().isReady() || !WorldgenLayout.ready(level)) return;
        StructureWorldData.Placement placement = WorldgenLayout.get(level).get("labyrinth");
        if (placement == null) return;
        StructureDefinition definition = StructureDefinition.byName("labyrinth");
        int minX = placement.centerX() - definition.size().getX() / 2;
        int minZ = placement.centerZ() - definition.size().getZ() / 2;
        int cx = event.getChunk().getPos().x;
        int cz = event.getChunk().getPos().z;
        if ((cx << 4) > minX + definition.size().getX() - 1 + RADIUS || (cx << 4) + 15 < minX - RADIUS
                || (cz << 4) > minZ + definition.size().getZ() - 1 + RADIUS || (cz << 4) + 15 < minZ - RADIUS) return;
        state(level).enqueue(cx, cz, 0);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel level = event.getServer().overworld();
        if (!WorldgenLayout.ready(level)) return;
        StructureWorldData.Placement placement = WorldgenLayout.get(level).get("labyrinth");
        if (placement == null) return;
        StructureDefinition definition = StructureDefinition.byName("labyrinth");
        int minX = placement.centerX() - definition.size().getX() / 2;
        int minZ = placement.centerZ() - definition.size().getZ() / 2;
        boolean occupied = level.players().stream().anyMatch(player ->
                player.getX() >= minX && player.getX() < minX + definition.size().getX()
                        && player.getZ() >= minZ && player.getZ() < minZ + definition.size().getZ()
                        && player.getY() >= placement.originY() - 2
                        && player.getY() < placement.originY() + definition.size().getY() + 8);
        State state = state(level);
        if (!state.initialized || state.active != occupied || level.getGameTime() % 100 == 0) {
            state.initialized = true;
            state.active = occupied;
            for (int cx = (minX - RADIUS) >> 4; cx <= (minX + definition.size().getX() - 1 + RADIUS) >> 4; cx++)
                for (int cz = (minZ - RADIUS) >> 4; cz <= (minZ + definition.size().getZ() - 1 + RADIUS) >> 4; cz++)
                    if (level.getChunkSource().getChunkNow(cx, cz) != null) state.enqueue(cx, cz, 0);
        }
        long deadline = System.nanoTime() + 3_000_000L;
        for (int count = 0; count < 16 && System.nanoTime() < deadline; count++) {
            Pending next = state.poll();
            if (next == null) return;
            if (level.getChunkSource().getChunkNow(next.cx, next.cz) == null) {
                if (next.attempt < 20) state.enqueue(next.cx, next.cz, next.attempt + 1);
                continue;
            }
            applyChunk(level, definition, placement, next.cx, next.cz, state.active);
        }
    }

    public static void applyChunk(ServerLevel level, StructureDefinition definition,
                                   StructureWorldData.Placement placement, int cx, int cz, boolean active) {
        int minX = placement.centerX() - definition.size().getX() / 2;
        int minZ = placement.centerZ() - definition.size().getZ() / 2;
        var chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return;
        boolean[] relevant = new boolean[chunk.getSectionsCount()];
        for (int section = 0; section < relevant.length; section++)
            relevant[section] = chunk.getSection(section).maybeHas(s -> replacement(s, active) != null);
        int fromX = Math.max(minX - RADIUS, cx << 4);
        int toX = Math.min(minX + definition.size().getX() - 1 + RADIUS, (cx << 4) + 15);
        int fromZ = Math.max(minZ - RADIUS, cz << 4);
        int toZ = Math.min(minZ + definition.size().getZ() - 1 + RADIUS, (cz << 4) + 15);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = fromX; x <= toX; x++) for (int z = fromZ; z <= toZ; z++) {
            for (int y = Math.max(level.getMinBuildHeight(), placement.originY() - RADIUS);
                 y < Math.min(level.getMaxBuildHeight(), placement.originY() + definition.size().getY() + RADIUS); y++) {
                if (!relevant[chunk.getSectionIndex(y)]) {
                    y = (y & ~15) + 15;
                    continue;
                }
                cursor.set(x, y, z);
                if (!withinRadius(definition, placement, cursor)) continue;
                BlockState old = chunk.getBlockState(cursor);
                Block replacement = replacement(old, active);
                if (replacement == null) continue;
                BlockState fresh = replacement.defaultBlockState();
                if (old.hasProperty(LanternBlock.HANGING)) fresh = fresh
                        .setValue(LanternBlock.HANGING, old.getValue(LanternBlock.HANGING))
                        .setValue(LanternBlock.WATERLOGGED, old.getValue(LanternBlock.WATERLOGGED));
                if (replacement == Blocks.REDSTONE_LAMP)
                    fresh = fresh.setValue(RedstoneLampBlock.LIT, level.hasNeighborSignal(cursor));
                level.setBlock(cursor, fresh, 3);
            }
        }
    }

    public static boolean withinRadius(StructureDefinition definition, StructureWorldData.Placement placement, BlockPos pos) {
        BlockPos origin = placement.origin(definition);
        int dx = Math.max(origin.getX() - pos.getX(), Math.max(0, pos.getX() - (origin.getX() + definition.size().getX() - 1)));
        int dy = Math.max(origin.getY() - pos.getY(), Math.max(0, pos.getY() - (origin.getY() + definition.size().getY() - 1)));
        int dz = Math.max(origin.getZ() - pos.getZ(), Math.max(0, pos.getZ() - (origin.getZ() + definition.size().getZ() - 1)));
        return dx * dx + dy * dy + dz * dz <= RADIUS * RADIUS;
    }

    private static Block replacement(BlockState state, boolean dark) {
        if (dark) {
            if (state.is(Blocks.REDSTONE_LAMP)) return ModBlocks.DARK_REDSTONE_LAMP.get();
            if (state.is(Blocks.LANTERN)) return ModBlocks.DARK_LANTERN.get();
            if (state.is(Blocks.SOUL_LANTERN)) return ModBlocks.DARK_SOUL_LANTERN.get();
        } else {
            if (state.is(ModBlocks.DARK_REDSTONE_LAMP.get())) return Blocks.REDSTONE_LAMP;
            if (state.is(ModBlocks.DARK_LANTERN.get())) return Blocks.LANTERN;
            if (state.is(ModBlocks.DARK_SOUL_LANTERN.get())) return Blocks.SOUL_LANTERN;
        }
        return null;
    }

    private static State state(ServerLevel level) {
        synchronized (STATES) { return STATES.computeIfAbsent(level, ignored -> new State()); }
    }

    private static final class State {
        private boolean active;
        private boolean initialized;
        private final ArrayDeque<Pending> queue = new ArrayDeque<>();
        private final Set<Long> queued = new HashSet<>();
        private synchronized void enqueue(int cx, int cz, int attempt) {
            long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
            if (queued.add(key)) queue.addLast(new Pending(cx, cz, attempt));
        }
        private synchronized Pending poll() {
            Pending next = queue.pollFirst();
            if (next != null) queued.remove(((long) next.cx << 32) ^ (next.cz & 0xffffffffL));
            return next;
        }
    }

    private record Pending(int cx, int cz, int attempt) {}
    private LabyrinthBlackout() {}
}
