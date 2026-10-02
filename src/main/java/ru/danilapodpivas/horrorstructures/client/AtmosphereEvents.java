package ru.danilapodpivas.horrorstructures.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.danilapodpivas.horrorstructures.HorrorStructures;

@Mod.EventBusSubscriber(modid = HorrorStructures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AtmosphereEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            if (Minecraft.getInstance().player == null) ClientZoneState.setZone("");
            ClientZoneState.tick();
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float red = event.getRed(), green = event.getGreen(), blue = event.getBlue();
        float gray = (red + green + blue) / 3.0f;
        red = lerp(ClientZoneState.park(), red, gray * 0.42f);
        green = lerp(ClientZoneState.park(), green, gray * 0.44f);
        blue = lerp(ClientZoneState.park(), blue, gray * 0.48f);
        red = lerp(ClientZoneState.labyrinth(), red, red * 0.32f);
        green = lerp(ClientZoneState.labyrinth(), green, green * 0.43f);
        blue = lerp(ClientZoneState.labyrinth(), blue, Math.min(1.0f, blue * 0.58f + 0.025f));
        red = lerp(ClientZoneState.circus(), red, Math.min(1.0f, red * 0.94f + 0.07f));
        green = lerp(ClientZoneState.circus(), green, green * 0.88f);
        blue = lerp(ClientZoneState.circus(), blue, blue * 0.86f);
        event.setRed(red);
        event.setGreen(green);
        event.setBlue(blue);
    }

    @SubscribeEvent
    public static void onFogDistance(ViewportEvent.RenderFog event) {
        float strength = Math.max(ClientZoneState.park() * 0.60f, ClientZoneState.labyrinth() * 0.63f);
        if (strength <= 0.001f) return;
        float far = event.getFarPlaneDistance() * (1.0f - strength);
        far = lerp(ClientZoneState.labyrinth(), far, Math.min(far, 14.0f));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.18f));
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().options.hideGui || Minecraft.getInstance().screen != null) return;
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        RadialVignette.draw(event.getGuiGraphics(), width, height, 27, 30, 33, 55 * ClientZoneState.park());
        RadialVignette.draw(event.getGuiGraphics(), width, height, 13, 25, 38, 62 * ClientZoneState.labyrinth());
        RadialVignette.draw(event.getGuiGraphics(), width, height, 96, 19, 19, 17 * ClientZoneState.circus());
    }

    private static float lerp(float amount, float from, float to) { return from + amount * (to - from); }

    private AtmosphereEvents() {}
}
