package ru.danilapodpivas.horrorstructures.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import ru.danilapodpivas.horrorstructures.registry.ModBiomeModifiers;

public record EveryBiomeModifier(Holder<PlacedFeature> feature, Holder<PlacedFeature> terrainFeature) implements BiomeModifier {
    public static final Codec<EveryBiomeModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PlacedFeature.CODEC.fieldOf("feature").forGetter(EveryBiomeModifier::feature),
            PlacedFeature.CODEC.fieldOf("terrain_feature").forGetter(EveryBiomeModifier::terrainFeature)
    ).apply(instance, EveryBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD) {
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, terrainFeature);
            // The template is placed after vegetation so vanilla trees, flowers and snow
            // cannot grow through rooms or remain on top of the saved buildings.
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, feature);
        }
    }

    @Override
    public Codec<? extends BiomeModifier> codec() { return ModBiomeModifiers.EVERY_BIOME.get(); }
}
