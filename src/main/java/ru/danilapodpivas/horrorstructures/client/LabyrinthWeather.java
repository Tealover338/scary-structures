package ru.danilapodpivas.horrorstructures.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.danilapodpivas.horrorstructures.HorrorStructures;

/** Vanilla weather snow quads/texture/lightmap, localized without changing global weather. */
@Mod.EventBusSubscriber(modid = HorrorStructures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LabyrinthWeather extends DimensionSpecialEffects.OverworldEffects {
    private static final ResourceLocation SNOW = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/environment/snow.png");

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"), new LabyrinthWeather());
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return ClientZoneState.labyrinth() > 0.01f;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partial, LightTexture light,
                                     double camX, double camY, double camZ) {
        float strength = ClientZoneState.labyrinth();
        if (strength <= 0.01f) return false;
        int radius = Minecraft.useFancyGraphics() ? 10 : 5;
        int centerX = Mth.floor(camX), centerY = Mth.floor(camY), centerZ = Mth.floor(camZ);
        float time = ticks + partial;
        var buffer = Tesselator.getInstance().getBuilder();
        light.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, SNOW);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RandomSource random = RandomSource.create();
        for (int z = centerZ - radius; z <= centerZ + radius; z++)
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int bottom = Math.max(centerY - radius, ground), top = Math.max(centerY + radius, ground);
                if (bottom == top) continue;
                double dx = x + 0.5 - camX, dz = z + 0.5 - camZ;
                double length = Math.sqrt(dx * dx + dz * dz);
                double sideX = length < 0.001 ? 0.5 : -dz / length * 0.5;
                double sideZ = length < 0.001 ? 0 : dx / length * 0.5;
                random.setSeed((long) x * x * 3121 + x * 45238971L ^ (long) z * z * 418711 + z * 13761L);
                double fall = -((ticks & 511) + partial) / 512.0;
                double u = random.nextDouble() + time * 0.01 * random.nextGaussian();
                double v = random.nextDouble() + time * random.nextGaussian() * 0.001;
                float alpha = (float) Math.max(0, (1 - (length / radius) * (length / radius)) * 0.3 + 0.5) * strength;
                int packed = LevelRenderer.getLightColor(level, pos.set(x, Math.max(centerY, ground), z));
                int block = (LightTexture.block(packed) * 3 + 15) / 4;
                int sky = (LightTexture.sky(packed) * 3 + 15) / 4;
                packed = LightTexture.pack(block, sky);
                buffer.vertex(dx - sideX, top - camY, dz - sideZ).uv((float) u, (float) (top * 0.25 + fall + v)).color(1f, 1f, 1f, alpha).uv2(packed).endVertex();
                buffer.vertex(dx + sideX, top - camY, dz + sideZ).uv((float) (u + 1), (float) (top * 0.25 + fall + v)).color(1f, 1f, 1f, alpha).uv2(packed).endVertex();
                buffer.vertex(dx + sideX, bottom - camY, dz + sideZ).uv((float) (u + 1), (float) (bottom * 0.25 + fall + v)).color(1f, 1f, 1f, alpha).uv2(packed).endVertex();
                buffer.vertex(dx - sideX, bottom - camY, dz - sideZ).uv((float) u, (float) (bottom * 0.25 + fall + v)).color(1f, 1f, 1f, alpha).uv2(packed).endVertex();
            }
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        light.turnOffLightLayer();
        return true;
    }
}
