package ru.danilapodpivas.horrorstructures.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.danilapodpivas.horrorstructures.HorrorStructures;

/** Visually identical lanterns with no light emission, used only during the labyrinth event. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HorrorStructures.MOD_ID);
    public static final RegistryObject<Block> DARK_LANTERN = BLOCKS.register("dark_lantern",
            () -> new LanternBlock(BlockBehaviour.Properties.copy(Blocks.LANTERN).lightLevel(state -> 0)));
    public static final RegistryObject<Block> DARK_SOUL_LANTERN = BLOCKS.register("dark_soul_lantern",
            () -> new LanternBlock(BlockBehaviour.Properties.copy(Blocks.SOUL_LANTERN).lightLevel(state -> 0)));
    public static final RegistryObject<Block> DARK_REDSTONE_LAMP = BLOCKS.register("dark_redstone_lamp",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.REDSTONE_LAMP)
                    .lightLevel(state -> 0).dropsLike(Blocks.REDSTONE_LAMP)));
    private ModBlocks() {}
}
