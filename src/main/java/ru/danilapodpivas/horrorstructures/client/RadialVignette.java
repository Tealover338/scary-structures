package ru.danilapodpivas.horrorstructures.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** A true radial alpha gradient, not the former screen-sized tinted rectangles. */
final class RadialVignette {
    private static final int SEGMENTS = 48;
    private static final int RINGS = 8;

    static void draw(GuiGraphics gui, int width, int height, int red, int green, int blue, float maxAlpha) {
        if (maxAlpha < 0.5f) return;
        gui.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = gui.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float cx = width / 2.0f, cy = height / 2.0f;
        float outer = (float) Math.hypot(cx, cy) + 2;
        float inner = Math.min(width, height) * 0.28f;
        for (int ring = 0; ring < RINGS; ring++) {
            float t0 = ring / (float) RINGS, t1 = (ring + 1) / (float) RINGS;
            float r0 = inner + (outer - inner) * t0;
            float r1 = inner + (outer - inner) * t1;
            int a0 = (int) (maxAlpha * t0 * t0);
            int a1 = (int) (maxAlpha * t1 * t1);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double angle0 = segment * Math.PI * 2 / SEGMENTS;
                double angle1 = (segment + 1) * Math.PI * 2 / SEGMENTS;
                float x00 = cx + r0 * (float) Math.cos(angle0), y00 = cy + r0 * (float) Math.sin(angle0);
                float x01 = cx + r0 * (float) Math.cos(angle1), y01 = cy + r0 * (float) Math.sin(angle1);
                float x10 = cx + r1 * (float) Math.cos(angle0), y10 = cy + r1 * (float) Math.sin(angle0);
                float x11 = cx + r1 * (float) Math.cos(angle1), y11 = cy + r1 * (float) Math.sin(angle1);
                vertex(buffer, matrix, x00, y00, red, green, blue, a0);
                vertex(buffer, matrix, x10, y10, red, green, blue, a1);
                vertex(buffer, matrix, x11, y11, red, green, blue, a1);
                vertex(buffer, matrix, x00, y00, red, green, blue, a0);
                vertex(buffer, matrix, x11, y11, red, green, blue, a1);
                vertex(buffer, matrix, x01, y01, red, green, blue, a0);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y,
                               int red, int green, int blue, int alpha) {
        buffer.vertex(matrix, x, y, 0).color(red, green, blue, alpha).endVertex();
    }

    private RadialVignette() {}
}
