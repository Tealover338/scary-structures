package ru.danilapodpivas.horrorstructures.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.registry.ModItems;
import ru.danilapodpivas.horrorstructures.structure.SelectionManager;

@Mod.EventBusSubscriber(modid = HorrorStructures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WandEvents {
    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getMainHandItem().is(ModItems.STRUCTURE_WAND.get())) return;
        event.setCanceled(true);
        SelectionManager.setFirst(player, event.getPos());
    }
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getItemInHand(event.getHand()).is(ModItems.STRUCTURE_WAND.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        SelectionManager.setSecond(player, event.getPos());
    }
    private WandEvents() {}
}
