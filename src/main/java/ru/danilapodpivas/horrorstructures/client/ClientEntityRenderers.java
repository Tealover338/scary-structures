package ru.danilapodpivas.horrorstructures.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.registry.ModEntities;

@Mod.EventBusSubscriber(modid = HorrorStructures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEntityRenderers {
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WHITE_FIGURE.get(), WhiteFigureRenderer::new);
    }
    private ClientEntityRenderers() {}
}
