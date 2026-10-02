package ru.danilapodpivas.horrorstructures.registry;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.world.EveryBiomeModifier;

public final class ModBiomeModifiers {
    public static final DeferredRegister<Codec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, HorrorStructures.MOD_ID);
    public static final RegistryObject<Codec<? extends BiomeModifier>> EVERY_BIOME =
            SERIALIZERS.register("every_biome", () -> EveryBiomeModifier.CODEC);
    private ModBiomeModifiers() {}
}
