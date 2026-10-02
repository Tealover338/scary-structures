package ru.danilapodpivas.horrorstructures.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.world.WhiteFigureEntity;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HorrorStructures.MOD_ID);
    public static final RegistryObject<EntityType<WhiteFigureEntity>> WHITE_FIGURE = ENTITIES.register("white_figure",
            () -> EntityType.Builder.<WhiteFigureEntity>of(WhiteFigureEntity::new, MobCategory.MISC)
                    .sized(0.6f, 2.0f).clientTrackingRange(64).updateInterval(1)
                    .build(HorrorStructures.MOD_ID + ":white_figure"));
    private ModEntities() {}
}
