package ru.danilapodpivas.horrorstructures.registry;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.world.ChunkStructureFeature;
import ru.danilapodpivas.horrorstructures.world.TerrainPreparationFeature;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, HorrorStructures.MOD_ID);
    public static final RegistryObject<Feature<?>> CHUNK_STRUCTURE = FEATURES.register("chunk_structure",
            () -> new ChunkStructureFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<Feature<?>> TERRAIN_PREPARATION = FEATURES.register("terrain_preparation",
            () -> new TerrainPreparationFeature(NoneFeatureConfiguration.CODEC));
    private ModFeatures() {}
}
