package ru.danilapodpivas.horrorstructures.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Map;

/** Shapes the land before trees and flowers are generated. The template is placed later. */
public final class TerrainPreparationFeature extends Feature<NoneFeatureConfiguration> {
    static final int BLEND_RADIUS = 144;

    public TerrainPreparationFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!(context.level() instanceof WorldGenRegion region)) return false;
        ServerLevel server = region.getLevel();
        if (server != server.getServer().overworld() || !server.getServer().isReady()) return false;
        StructureWorldData saved = StructureWorldData.peek(server);
        if (saved != null && saved.version() < 6) return false;
        int chunkX = context.origin().getX() >> 4;
        int chunkZ = context.origin().getZ() >> 4;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        Map<String, StructureWorldData.Placement> layout = WorldgenLayout.get(server);
        for (StructureDefinition definition : StructureDefinition.ALL) {
            StructureWorldData.Placement placement = layout.get(definition.name());
            if (placement == null) continue;
            int originX = placement.centerX() - definition.size().getX() / 2;
            int originZ = placement.centerZ() - definition.size().getZ() / 2;
            int maxX = originX + definition.size().getX() - 1;
            int maxZ = originZ + definition.size().getZ() - 1;
            if (minX > maxX + BLEND_RADIUS || minX + 15 < originX - BLEND_RADIUS
                    || minZ > maxZ + BLEND_RADIUS || minZ + 15 < originZ - BLEND_RADIUS) continue;
            shapeChunk(context.level(), server, definition, placement.originY(),
                    originX, originZ, maxX, maxZ, minX, minZ);
        }
        return true;
    }

    private static void shapeChunk(WorldGenLevel level, ServerLevel server, StructureDefinition definition,
                                   int originY, int minX, int minZ, int maxX, int maxZ, int chunkX, int chunkZ) {
        boolean bunker = definition.name().equals("bunker");
        int seaLevel = server.getSeaLevel();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunkX; x < chunkX + 16; x++) {
            for (int z = chunkZ; z < chunkZ + 16; z++) {
                int dx = Math.max(minX - x, Math.max(0, x - maxX));
                int dz = Math.max(minZ - z, Math.max(0, z - maxZ));
                double distance = EdgeBlend.distance(x, z, minX, minZ, maxX, maxZ);
                if (distance >= BLEND_RADIUS) continue;
                if (bunker && dx == 0 && dz == 0) continue;
                int actualTop = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int currentY = actualTop;
                BlockState surface;
                while (currentY >= level.getMinBuildHeight()) {
                    surface = level.getBlockState(cursor.set(x, currentY, z));
                    if (surface.isSolidRender(level, cursor) && !surface.is(BlockTags.LOGS)
                            && !surface.is(BlockTags.LEAVES)) break;
                    currentY--;
                }
                if (currentY < level.getMinBuildHeight()) continue;
                surface = level.getBlockState(cursor.set(x, currentY, z));
                if (surface.isAir() || !surface.getFluidState().isEmpty()) continue;
                boolean ocean = !level.getFluidState(cursor.set(x, currentY + 1, z)).isEmpty();
                BlockState filler = surface.is(Blocks.SAND) || surface.is(Blocks.RED_SAND) ? surface
                        : surface.is(Blocks.GRAVEL) ? Blocks.GRAVEL.defaultBlockState()
                        : Blocks.DIRT.defaultBlockState();
                double weight = 1.0 - distance / BLEND_RADIUS;
                weight = weight * weight * (3.0 - 2.0 * weight);
                int targetY = originY + SavedTerrain.at(server, definition, x - minX, z - minZ);
                // Soil saved on roofs must not become a temporary mountain that
                // the later template pass would have to excavate again.
                if (dx == 0 && dz == 0 && !bunker)
                    targetY = Math.min(targetY, originY + definition.grassLevel());
                // Small coherent variation breaks the ruler-straight embankment without
                // changing the level of the structure itself.
                double variation = EdgeBlend.noise(x, z, 19) * 3.0 * Math.min(1, distance / 18.0);
                int desiredY = Mth.floor(Mth.lerp(weight, currentY, targetY + variation) + 0.5);
                // Raise a continuous bank against water instead of skipping wet
                // columns and leaving an exposed vertical template wall.
                if (ocean) desiredY = Math.max(currentY, desiredY);
                if (desiredY < currentY) {
                    for (int y = actualTop; y > desiredY; y--)
                        level.setBlock(cursor.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                } else if (desiredY > currentY) {
                    for (int y = currentY + 1; y <= desiredY; y++) {
                        BlockState material = ocean ? y < seaLevel - 3 ? Blocks.STONE.defaultBlockState()
                                : y <= seaLevel + 2 ? Blocks.SAND.defaultBlockState()
                                : Blocks.DIRT.defaultBlockState() : filler;
                        level.setBlock(cursor.set(x, y, z), material, 2);
                    }
                }
                BlockState top = ocean ? desiredY <= seaLevel + 2 ? Blocks.SAND.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState() : surface;
                // Blend the exposed edge into the local soil instead of stamping a
                // continuous grass stripe around an otherwise rocky or sandy site.
                // Keep local topsoil. A fixed-width coarse-dirt stripe made the
                // rectangular perimeter more conspicuous instead of hiding it.
                level.setBlock(cursor.set(x, desiredY, z), top, 2);
            }
        }
    }
}
