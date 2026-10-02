package ru.danilapodpivas.horrorstructures.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.world.StructureDefinition;
import ru.danilapodpivas.horrorstructures.world.StructureWorldData;
import ru.danilapodpivas.horrorstructures.world.WorldgenLayout;
import ru.danilapodpivas.horrorstructures.world.WhiteFigureEntity;
import ru.danilapodpivas.horrorstructures.world.VegetationRepair;
import ru.danilapodpivas.horrorstructures.registry.ModBlocks;
import net.minecraftforge.event.level.ChunkEvent;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@GameTestHolder(HorrorStructures.MOD_ID)
public final class StructureGenerationTest {
    @GameTest(batch = "b_cleanup", templateNamespace = HorrorStructures.MOD_ID, template = "test_empty", timeoutTicks = 100)
    public static void templateDoesNotInheritAmbientWater(GameTestHelper helper) throws Exception {
        StructureDefinition definition = StructureDefinition.byName("planes");
        BlockPos origin = WorldgenLayout.get(helper.getLevel()).get("planes").origin(definition);
        BlockPos slab = origin.offset(120, 0, 114);
        helper.getLevel().getChunk(slab.getX() >> 4, slab.getZ() >> 4);
        helper.getLevel().setBlock(slab, Blocks.WATER.defaultBlockState(), 2);
        var place = ru.danilapodpivas.horrorstructures.world.ChunkStructureFeature.class.getDeclaredMethod(
                "placePart", net.minecraft.world.level.WorldGenLevel.class,
                net.minecraft.server.level.ServerLevel.class, StructureDefinition.class,
                int.class, int.class, int.class, int.class, int.class);
        place.setAccessible(true);
        place.invoke(null, helper.getLevel(), helper.getLevel(), definition,
                origin.getY(), origin.getX() + 112, origin.getZ() + 112, 7, 7);
        if (!helper.getLevel().getBlockState(slab).is(Blocks.POLISHED_ANDESITE_SLAB)
                || !helper.getLevel().getFluidState(slab).isEmpty())
            helper.fail("Template slab inherited water from the surrounding terrain");
        else helper.succeed();
    }

    @GameTest(batch = "b_cleanup", templateNamespace = HorrorStructures.MOD_ID, template = "test_empty", timeoutTicks = 300)
    public static void lateTreesAreRemovedWithoutCuttingTheBuild(GameTestHelper helper) {
        List<BlockPos> anchors = new ArrayList<>();
        for (StructureDefinition definition : StructureDefinition.ALL) {
            BlockPos origin = WorldgenLayout.get(helper.getLevel()).get(definition.name()).origin(definition);
            BlockPos log = origin.offset(24, definition.size().getY() + 18, 24);
            helper.getLevel().setChunkForced(log.getX() >> 4, log.getZ() >> 4, true);
            anchors.add(log);
        }
        helper.runAfterDelay(20, () -> {
            List<BlockPos> injected = new ArrayList<>();
            for (BlockPos log : anchors) {
                helper.getLevel().getChunk(log.getX() >> 4, log.getZ() >> 4);
                helper.getLevel().setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 2);
                helper.getLevel().setBlock(log.above(), Blocks.OAK_LEAVES.defaultBlockState(), 2);
                BlockPos grass = log.east(2);
                helper.getLevel().setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                helper.getLevel().setBlock(grass.above(), Blocks.VINE.defaultBlockState(), 2);
                injected.add(log);
                injected.add(log.above());
                injected.add(grass);
                injected.add(grass.above());
                VegetationRepair.onChunkLoad(new ChunkEvent.Load(
                        helper.getLevel().getChunk(log.getX() >> 4, log.getZ() >> 4), true));
            }
            BlockPos planesOrigin = WorldgenLayout.get(helper.getLevel()).get("planes")
                    .origin(StructureDefinition.byName("planes"));
            BlockPos crown = planesOrigin.offset(-83, 90, 24);
            helper.getLevel().setChunkForced(crown.getX() >> 4, crown.getZ() >> 4, true);
            helper.getLevel().getChunk(crown.getX() >> 4, crown.getZ() >> 4);
            helper.getLevel().setBlock(crown, Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
            injected.add(crown);
            VegetationRepair.onChunkLoad(new ChunkEvent.Load(
                    helper.getLevel().getChunk(crown.getX() >> 4, crown.getZ() >> 4), true));
            BlockPos bunkerOrigin = WorldgenLayout.get(helper.getLevel()).get("bunker")
                    .origin(StructureDefinition.byName("bunker"));
            // These saved rooms are more than twelve blocks below the hillside.
            for (int y : new int[]{91, 110}) {
                BlockPos room = bunkerOrigin.offset(100, y, 88);
                helper.getLevel().setChunkForced(room.getX() >> 4, room.getZ() >> 4, true);
                helper.getLevel().getChunk(room.getX() >> 4, room.getZ() >> 4);
                helper.getLevel().setBlock(room, y == 91 ? Blocks.WATER.defaultBlockState()
                        : Blocks.DIRT.defaultBlockState(), 2);
                injected.add(room);
                VegetationRepair.onChunkLoad(new ChunkEvent.Load(
                        helper.getLevel().getChunk(room.getX() >> 4, room.getZ() >> 4), true));
            }
            helper.runAfterDelay(120, () -> {
                for (BlockPos pos : injected) if (!helper.getLevel().getBlockState(pos).isAir()) {
                    helper.fail("Late vegetation or floating soil remained at " + pos);
                    return;
                }
                for (BlockPos pos : anchors)
                    helper.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
                helper.getLevel().setChunkForced((bunkerOrigin.getX() + 100) >> 4,
                        (bunkerOrigin.getZ() + 88) >> 4, false);
                helper.getLevel().setChunkForced(crown.getX() >> 4, crown.getZ() >> 4, false);
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = HorrorStructures.MOD_ID, template = "test_empty", timeoutTicks = 100)
    public static void whiteFigureLightsAndCleansItsPosition(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(0, 2, 0));
        WhiteFigureEntity figure = WhiteFigureEntity.create(helper.getLevel());
        if (figure == null) helper.fail("White figure entity is not registered");
        figure.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        helper.getLevel().addFreshEntity(figure);
        figure.illuminate();
        BlockPos light = position.above();
        if (!helper.getLevel().getBlockState(light).is(Blocks.LIGHT)) helper.fail("Figure did not emit light");
        helper.runAfterDelay(10, () -> {
            if (!helper.getLevel().getBlockState(light).isAir()) helper.fail("Figure left a light block behind");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = HorrorStructures.MOD_ID, template = "test_empty", timeoutTicks = 100)
    public static void darkLanternEmitsNoLight(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(0, 2, 0));
        helper.getLevel().setBlock(pos, ModBlocks.DARK_LANTERN.get().defaultBlockState(), 2);
        if (!helper.getLevel().getBlockState(pos).is(ModBlocks.DARK_LANTERN.get())
                || helper.getLevel().getBlockState(pos).getLightEmission(helper.getLevel(), pos) != 0)
            helper.fail("Extinguished lantern still emits light");
        else helper.succeed();
    }

    @GameTest(batch = "a_generation", templateNamespace = HorrorStructures.MOD_ID, template = "test_empty", timeoutTicks = 600)
    public static void allFiveStructuresGenerateInsideTheirChunks(GameTestHelper helper) {
        Map<String, StructureWorldData.Placement> layout = WorldgenLayout.get(helper.getLevel());
        if (layout.size() != 5) helper.fail("Expected exactly five structure positions");
        BlockPos spawn = helper.getLevel().getSharedSpawnPos();
        for (StructureDefinition placed : StructureDefinition.ALL) {
            StructureWorldData.Placement candidate = layout.get(placed.name());
            double distance = Math.hypot(candidate.centerX() - spawn.getX(), candidate.centerZ() - spawn.getZ());
            if (distance < 400 || distance > 950) helper.fail(placed.name() + " is too far from spawn: " + distance);
            if (candidate.originY() < helper.getLevel().getMinBuildHeight()) helper.fail(placed.name() + " starts below the world");
            for (StructureDefinition other : StructureDefinition.ALL) {
                if (placed.name().compareTo(other.name()) >= 0) continue;
                StructureWorldData.Placement neighbor = layout.get(other.name());
                int dx = Math.abs(candidate.centerX() - neighbor.centerX());
                int dz = Math.abs(candidate.centerZ() - neighbor.centerZ());
                if (Math.hypot(dx, dz) < 500) helper.fail(placed.name() + " is too close to " + other.name());
                if (dx < (placed.size().getX() + other.size().getX()) / 2
                        && dz < (placed.size().getZ() + other.size().getZ()) / 2)
                    helper.fail(placed.name() + " overlaps " + other.name());
            }
        }
        List<Sample> samples = List.of(
                new Sample("abandoned_park", 7, 6, 15, 19, 0, Blocks.WHITE_WOOL),
                new Sample("bunker", 7, 5, 0, 31, 0, Blocks.GREEN_STAINED_GLASS),
                new Sample("circus", 3, 3, 0, 0, 0, Blocks.PACKED_MUD),
                new Sample("labyrinth", 3, 3, 1, 0, 5, Blocks.DARK_PRISMARINE),
                new Sample("planes", 7, 7, 8, 0, 2, Blocks.POLISHED_ANDESITE_SLAB));
        for (Sample sample : samples) {
            StructureDefinition definition = StructureDefinition.byName(sample.name);
            StructureWorldData.Placement placement = layout.get(sample.name);
            BlockPos origin = placement.origin(definition);
            helper.getLevel().getChunk((origin.getX() >> 4) + sample.chunkX, (origin.getZ() >> 4) + sample.chunkZ);
        }
        StructureWorldData.Placement bunker = layout.get("bunker");
        BlockPos bunkerOrigin = bunker.origin(StructureDefinition.byName("bunker"));
        helper.getLevel().getChunk(bunkerOrigin.getX() >> 4, bunkerOrigin.getZ() >> 4);
        StructureWorldData.Placement planes = layout.get("planes");
        BlockPos planesOrigin = planes.origin(StructureDefinition.byName("planes"));
        helper.getLevel().getChunk(planesOrigin.getX() >> 4, planesOrigin.getZ() >> 4);
        StructureWorldData.Placement circus = layout.get("circus");
        BlockPos circusOrigin = circus.origin(StructureDefinition.byName("circus"));
        helper.getLevel().getChunk((circusOrigin.getX() + 40) >> 4, (circusOrigin.getZ() + 60) >> 4);
        helper.getLevel().getChunk((bunkerOrigin.getX() + 100) >> 4, (bunkerOrigin.getZ() + 88) >> 4);
        List<BlockPos> wetSupports = new ArrayList<>();
        for (StructureDefinition definition : StructureDefinition.ALL) {
            if (definition.name().equals("bunker")) continue;
            BlockPos origin = layout.get(definition.name()).origin(definition);
            boolean found = false;
            for (int x = 8; x < definition.size().getX() - 8 && !found; x += 32) {
                for (int z = 8; z < definition.size().getZ() - 8; z += 32) {
                    int worldX = origin.getX() + x;
                    int worldZ = origin.getZ() + z;
                    int surface = helper.getLevel().getChunkSource().getGenerator().getBaseHeight(worldX, worldZ,
                            Heightmap.Types.WORLD_SURFACE_WG, helper.getLevel(),
                            helper.getLevel().getChunkSource().randomState()) - 1;
                    int floor = helper.getLevel().getChunkSource().getGenerator().getBaseHeight(worldX, worldZ,
                            Heightmap.Types.OCEAN_FLOOR_WG, helper.getLevel(),
                            helper.getLevel().getChunkSource().randomState()) - 1;
                    if (surface <= helper.getLevel().getSeaLevel() + 1 && surface > floor) {
                        helper.getLevel().getChunk(worldX >> 4, worldZ >> 4);
                        // Border soil is not necessarily at the most common grass
                        // level (the park has several layers). Check its foundation.
                        wetSupports.add(new BlockPos(worldX, origin.getY() - 1, worldZ));
                        found = true;
                        break;
                    }
                }
            }
        }
        helper.succeedIf(() -> {
            if (helper.getLevel().getBlockState(bunkerOrigin.offset(100, 115, 88)).isAir())
                throw new AssertionError("Bunker entrance lost its intended floor");
            if (!helper.getLevel().getBlockState(planesOrigin.below()).isSolidRender(helper.getLevel(), planesOrigin.below()))
                throw new AssertionError("Planes terrain is floating without support");
            if (!helper.getLevel().getBlockState(circusOrigin.offset(40, 6, 60)).isAir())
                throw new AssertionError("Natural soil or vegetation intruded into the circus interior");
            if (!helper.getLevel().getBlockState(bunkerOrigin.offset(100, 125, 88)).isAir())
                throw new AssertionError("The bunker entrance is blocked by natural terrain");
            if (!helper.getLevel().getBlockState(bunkerOrigin.offset(100, 110, 88)).isAir()
                    || !helper.getLevel().getBlockState(bunkerOrigin.offset(100, 91, 88)).isAir())
                throw new AssertionError("Saved underground bunker air was filled by terrain or water");
            for (BlockPos support : wetSupports) {
                if (!helper.getLevel().getBlockState(support).isSolidRender(helper.getLevel(), support))
                    throw new AssertionError("A structure has a floating surface above water at " + support);
            }
            for (Sample sample : samples) {
                StructureDefinition definition = StructureDefinition.byName(sample.name);
                StructureWorldData.Placement placement = layout.get(sample.name);
                BlockPos origin = placement.origin(definition);
                BlockPos expected = origin.offset(sample.chunkX * 16 + sample.localX,
                        sample.localY, sample.chunkZ * 16 + sample.localZ);
                if (!helper.getLevel().getBlockState(expected).is(sample.block)) {
                    throw new AssertionError(sample.name + " was not placed during chunk generation at " + expected);
                }
            }
        });
    }

    private record Sample(String name, int chunkX, int chunkZ, int localX, int localY, int localZ,
                          net.minecraft.world.level.block.Block block) {}

    private StructureGenerationTest() {}
}
