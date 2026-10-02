package ru.danilapodpivas.horrorstructures;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import ru.danilapodpivas.horrorstructures.registry.ModItems;
import ru.danilapodpivas.horrorstructures.registry.ModBlocks;
import ru.danilapodpivas.horrorstructures.registry.ModEntities;
import ru.danilapodpivas.horrorstructures.registry.ModFeatures;
import ru.danilapodpivas.horrorstructures.registry.ModBiomeModifiers;
import ru.danilapodpivas.horrorstructures.network.ModNetwork;
import ru.danilapodpivas.horrorstructures.world.WorldgenLayout;
import ru.danilapodpivas.horrorstructures.world.VegetationRepair;
import ru.danilapodpivas.horrorstructures.world.LabyrinthBlackout;
import org.slf4j.Logger;

@Mod(HorrorStructures.MOD_ID)
public final class HorrorStructures {
    public static final String MOD_ID = "horror_structures";
    public static final Logger LOGGER = LogUtils.getLogger();
    public HorrorStructures(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModFeatures.FEATURES.register(modBus);
        ModBiomeModifiers.SERIALIZERS.register(modBus);
        ModNetwork.register();
        modBus.addListener(this::addCreativeTabContents);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(VegetationRepair::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(VegetationRepair::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(VegetationRepair::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(LabyrinthBlackout::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(LabyrinthBlackout::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(LabyrinthBlackout::onBlockPlaced);
    }
    private void onServerStarted(ServerStartedEvent event) {
        WorldgenLayout.warm(event.getServer().overworld());
    }
    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ModItems.STRUCTURE_WAND);
    }
}
