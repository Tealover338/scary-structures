package ru.danilapodpivas.horrorstructures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import ru.danilapodpivas.horrorstructures.HorrorStructures;
import ru.danilapodpivas.horrorstructures.world.WhiteFigureEntity;

public final class WhiteFigureRenderer extends EntityRenderer<WhiteFigureEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(HorrorStructures.MOD_ID, "textures/gui/white_figure.png");

    public WhiteFigureRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(WhiteFigureEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.translate(0, 1.05, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(-1.0f, -1.0f, 1.0f);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        Matrix4f position = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        vertex(consumer, position, normal, -0.68f, -1.0f, 0, 1);
        vertex(consumer, position, normal, 0.68f, -1.0f, 1, 1);
        vertex(consumer, position, normal, 0.68f, 1.0f, 1, 0);
        vertex(consumer, position, normal, -0.68f, 1.0f, 0, 0);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, LightTexture.FULL_BRIGHT);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f position, Matrix3f normal,
                               float x, float y, float u, float v) {
        consumer.vertex(position, x, y, 0).color(255, 255, 255, 220).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(normal, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(WhiteFigureEntity entity) { return TEXTURE; }
}
