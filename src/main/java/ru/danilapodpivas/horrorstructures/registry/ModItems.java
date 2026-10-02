package ru.danilapodpivas.horrorstructures.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.danilapodpivas.horrorstructures.HorrorStructures;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HorrorStructures.MOD_ID);
    public static final RegistryObject<Item> STRUCTURE_WAND = ITEMS.register("structure_wand", () -> new Item(new Item.Properties().stacksTo(1)));
    private ModItems() {}
}
