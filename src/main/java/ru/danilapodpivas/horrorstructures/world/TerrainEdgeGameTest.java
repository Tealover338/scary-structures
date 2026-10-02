package ru.danilapodpivas.horrorstructures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(HorrorStructures.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class TerrainEdgeGameTest {
    @GameTest(batch = "a_edges", templateNamespace = HorrorStructures.MOD_ID, template = "structuregenerationtest.test_empty", timeoutTicks = 300)
    public static void savedTerrainMeetsOutsideSlope(GameTestHelper helper) {
        List<BlockPos[]> pairs = new ArrayList<>();
        for (String name : new String[]{"bunker", "abandoned_park"}) {
            var definition = StructureDefinition.byName(name);
            var origin = WorldgenLayout.get(helper.getLevel()).get(name).origin(definition);
            int width = definition.size().getX(), depth = definition.size().getZ();
            for (int fraction = 1; fraction <= 3; fraction++) {
                int x = width * fraction / 4, z = depth * fraction / 4;
                pairs.add(new BlockPos[]{origin.offset(x, 0, -1), origin.offset(x, 0, 1)});
                pairs.add(new BlockPos[]{origin.offset(x, 0, depth), origin.offset(x, 0, depth-2)});
                pairs.add(new BlockPos[]{origin.offset(-1, 0, z), origin.offset(1, 0, z)});
                pairs.add(new BlockPos[]{origin.offset(width, 0, z), origin.offset(width-2, 0, z)});
            }
        }
        for (BlockPos[] pair : pairs) for (BlockPos pos : pair) {
            helper.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            helper.getLevel().getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }
        helper.runAfterDelay(20, () -> {
            String failure = null;
            for (BlockPos[] pair : pairs) {
                int outside = soilHeight(helper, pair[0]), inside = soilHeight(helper, pair[1]);
                if (Math.abs(outside - inside) > 6) {
                    failure = "Terrain seam at " + pair[0] + ": outside=" + outside + ", inside=" + inside;
                    break;
                }
            }
            for (BlockPos[] pair : pairs) for (BlockPos pos : pair)
                helper.getLevel().setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
            if (failure == null) helper.succeed(); else helper.fail(failure);
        });
    }

    private static int soilHeight(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        for (int y = top; y >= level.getMinBuildHeight(); y--) {
            BlockPos at = new BlockPos(pos.getX(), y, pos.getZ());
            var state = level.getBlockState(at);
            if (state.isSolidRender(level, at) && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) return y;
        }
        return level.getMinBuildHeight();
    }

    @GameTest(batch = "c_blackout", templateNamespace = HorrorStructures.MOD_ID, template = "structuregenerationtest.test_empty", timeoutTicks = 100)
    public static void poweredLampsStayDarkAndRestore(GameTestHelper helper) {
        var definition = StructureDefinition.byName("labyrinth");
        var placement = WorldgenLayout.get(helper.getLevel()).get("labyrinth");
        BlockPos origin = placement.origin(definition);
        for (int distance : new int[]{0, 32, 33}) {
            BlockPos lamp = origin.offset(-distance, 5, 40);
            var level = helper.getLevel();
            level.getChunk(lamp.getX() >> 4, lamp.getZ() >> 4);
            level.setBlock(lamp.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            level.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true), 3);
            LabyrinthBlackout.applyChunk(level, definition, placement, lamp.getX() >> 4, lamp.getZ() >> 4, true);
            if (distance <= 32) {
                if (!level.getBlockState(lamp).is(ModBlocks.DARK_REDSTONE_LAMP.get())
                        || level.getBlockState(lamp).getLightEmission(level, lamp) != 0) {
                    helper.fail("Powered lamp did not go dark at distance " + distance); return;
                }
                level.neighborChanged(lamp, Blocks.REDSTONE_BLOCK, lamp.below());
                if (!level.getBlockState(lamp).is(ModBlocks.DARK_REDSTONE_LAMP.get())) {
                    helper.fail("Power update relit a dark lamp"); return;
                }
                LabyrinthBlackout.applyChunk(level, definition, placement, lamp.getX() >> 4, lamp.getZ() >> 4, false);
                if (!level.getBlockState(lamp).is(Blocks.REDSTONE_LAMP)
                        || !level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT)) {
                    helper.fail("Lamp did not restore its current power state"); return;
                }
            } else if (!level.getBlockState(lamp).is(Blocks.REDSTONE_LAMP)) {
                helper.fail("Lamp beyond 32 blocks was changed"); return;
            }
        }
        helper.succeed();
    }
}
