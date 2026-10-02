package ru.danilapodpivas.horrorstructures.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import ru.danilapodpivas.horrorstructures.HorrorStructures;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Places only the 16x16 slice belonging to the chunk being decorated. */
public final class ChunkStructureFeature extends Feature<NoneFeatureConfiguration> {
    public ChunkStructureFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!(context.level() instanceof WorldGenRegion region)) return false;
        ServerLevel serverLevel = region.getLevel();
        if (serverLevel != serverLevel.getServer().overworld()) return false;
        // Minecraft chooses the final shared spawn after preparing the initial chunks.
        if (!serverLevel.getServer().isReady()) return false;
        int chunkX = context.origin().getX() >> 4;
        int chunkZ = context.origin().getZ() >> 4;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;

        Map<String, StructureWorldData.Placement> layout = WorldgenLayout.get(serverLevel);
        for (StructureDefinition definition : StructureDefinition.ALL) {
            StructureWorldData.Placement placement = layout.get(definition.name());
            if (placement == null) continue;
            int originX = placement.centerX() - definition.size().getX() / 2;
            int originZ = placement.centerZ() - definition.size().getZ() / 2;
            int maxX = originX + definition.size().getX() - 1;
            int maxZ = originZ + definition.size().getZ() - 1;
            if (minX > maxX || minX + 15 < originX || minZ > maxZ || minZ + 15 < originZ) continue;

            StructureWorldData oldData = StructureWorldData.peek(serverLevel);
            if (oldData != null && oldData.version() < 6) continue;

            clearCapturedVolume(context.level(), serverLevel, definition, placement.originY(), originX, originZ, minX, minZ);
            if (!definition.name().equals("bunker"))
                supportFootprint(context.level(), serverLevel, definition, placement.originY(), originX, originZ, minX, minZ);
            int localChunkX = (minX - originX) >> 4;
            int localChunkZ = (minZ - originZ) >> 4;
            if (localChunkX < 0 || localChunkZ < 0
                    || localChunkX * 16 >= definition.size().getX()
                    || localChunkZ * 16 >= definition.size().getZ()) continue;
            placePart(context.level(), serverLevel, definition, placement.originY(), minX, minZ, localChunkX, localChunkZ);
            fadeParkSoil(context.level(), serverLevel, definition, placement.originY(), originX, originZ, minX, minZ);
        }
        return true;
    }

    private static void fadeParkSoil(WorldGenLevel level, ServerLevel server, StructureDefinition definition,
                                      int originY, int originX, int originZ, int chunkX, int chunkZ) {
        if (!definition.name().equals("abandoned_park")) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkX; x < chunkX + 16; x++) for (int z = chunkZ; z < chunkZ + 16; z++) {
            int inside = EdgeBlend.insideDistance(x, z, originX, originZ,
                    originX + definition.size().getX() - 1, originZ + definition.size().getZ() - 1);
            if (inside < 0 || inside >= 24) continue;
            double t = inside / 24.0;
            double keep = t * t * (3 - 2 * t);
            if ((EdgeBlend.hash(x, z) + 1) * 0.5 < keep) continue;
            int y = originY + SavedTerrain.at(server, definition, x - originX, z - originZ);
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            // Sparse captures have empty border columns. Close only the shallow
            // soil band, never entrances or roofs, so the fade has no rectangular trench.
            if (state.isAir() && y <= originY + 8) {
                level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int fill = y - 1; fill >= originY - 1; fill--) {
                    pos.set(x, fill, z);
                    if (level.getBlockState(pos).isSolidRender(level, pos)) break;
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
                }
            }
            if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
                    || state.is(Blocks.ROOTED_DIRT))
                level.setBlock(pos.set(x, y, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        }
    }

    private static void placePart(WorldGenLevel level, ServerLevel serverLevel, StructureDefinition definition,
                                  int originY, int worldX, int worldZ, int partX, int partZ) {
        ResourceLocation resource = new ResourceLocation(HorrorStructures.MOD_ID,
                "worldgen_parts/" + definition.name() + "/" + partX + "_" + partZ + ".nbt");
        try {
            Resource part = serverLevel.getServer().getResourceManager().getResourceOrThrow(resource);
            try (InputStream input = part.open()) {
                CompoundTag tag = NbtIo.readCompressed(input);
                StructureTemplate template = new StructureTemplate();
                template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
                BlockPos position = new BlockPos(worldX, originY, worldZ);
                template.placeInWorld(level, position, position,
                        new StructurePlaceSettings().setIgnoreEntities(false).setKeepLiquids(false), serverLevel.getRandom(), 2);
            }
        } catch (IOException | RuntimeException exception) {
            HorrorStructures.LOGGER.error("Could not generate {} part {},{}", definition.name(), partX, partZ, exception);
        }
    }

    /** Run after vegetation: remove vanilla hills and trees from the volume saved as air. */
    private static void clearCapturedVolume(WorldGenLevel level, ServerLevel serverLevel,
                                            StructureDefinition definition, int originY,
                                            int originX, int originZ, int chunkMinX, int chunkMinZ) {
        int maxX = originX + definition.size().getX();
        int maxZ = originZ + definition.size().getZ();
        boolean bunker = definition.name().equals("bunker");
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunkMinX; x < chunkMinX + 16; x++) {
            for (int z = chunkMinZ; z < chunkMinZ + 16; z++) {
                if (x < originX || x >= maxX || z < originZ || z >= maxZ) continue;
                int localX = x - originX;
                int localZ = z - originZ;
                int base = bunker ? originY + BunkerSurface.at(serverLevel, localX, localZ) + 1
                        : originY;
                // The bunker entrance is cut into the lower west side of its saved hillside.
                if (bunker && localX >= 94 && localX <= 132 && localZ >= 70 && localZ <= 112)
                    base = Math.min(base, originY + 115);
                // Sparse bunker templates omit exterior air. Replace the surrounding
                // underground terrain with a sealed envelope before carving saved rooms.
                // Otherwise aquifers survive beside the rooms and refill them on ticks.
                if (bunker) {
                    int surface = originY + BunkerSurface.at(serverLevel, localX, localZ);
                    for (int y = Math.max(originY, level.getMinBuildHeight()); y < base; y++) {
                        cursor.set(x, y, z);
                        BlockState existing = level.getBlockState(cursor);
                        if (!existing.isSolidRender(level, cursor) || !existing.getFluidState().isEmpty())
                            level.setBlock(cursor, y < surface - 3
                                    ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
                    }
                    if (base == surface + 1 && !TemplateVegetation.containsSolid(serverLevel,
                            definition, localX, surface - originY, localZ))
                        level.setBlock(cursor.set(x, surface, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                }
                // WORLD_SURFACE_WG can lag behind blocks written by other decoration
                // steps. Scan the whole saved volume so late soil cannot survive in rooms.
                int actualTop = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1,
                        originY + definition.size().getY() + 8);
                actualTop = Math.min(actualTop, level.getMaxBuildHeight() - 1);
                for (int y = base; y <= actualTop; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir())
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    /** The template can contain a bare grass layer over former ocean or a cave. */
    private static void supportFootprint(WorldGenLevel level, ServerLevel server, StructureDefinition definition,
                                         int originY, int originX, int originZ, int chunkMinX, int chunkMinZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunkMinX; x < chunkMinX + 16; x++) for (int z = chunkMinZ; z < chunkMinZ + 16; z++) {
            if (x < originX || x >= originX + definition.size().getX()
                    || z < originZ || z >= originZ + definition.size().getZ()) continue;
            int supportY = originY + Math.min(definition.grassLevel(),
                    SavedTerrain.at(server, definition, x - originX, z - originZ)) - 1;
            cursor.set(x, supportY, z);
            BlockState state = level.getBlockState(cursor);
            if (state.isSolidRender(level, cursor) && state.getFluidState().isEmpty()) continue;
            for (int y = supportY; y >= level.getMinBuildHeight(); y--) {
                cursor.set(x, y, z);
                state = level.getBlockState(cursor);
                if (state.isSolidRender(level, cursor) && state.getFluidState().isEmpty()) break;
                level.setBlock(cursor, y < supportY - 5 ? Blocks.STONE.defaultBlockState()
                        : Blocks.DIRT.defaultBlockState(), 2);
            }
        }
    }
}
