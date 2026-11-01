package net.legoman.animod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * АКТ 4: небо Космоса — собственный рендер (заменяет ванильный).
 * Звёздная бездна + ЧЁРНАЯ ДЫРА: горизонт событий (чёрный диск), фотонное кольцо,
 * оранжевый аккреционный диск и холодный ореол линзирования (текстура сгенерирована
 * процедурно). Дыра висит в фиксированной точке неба и всегда повёрнута лицом
 * к камере (билборд), как на небе Интерстеллара.
 */
public class KosmosSky {

    public static final ResourceLocation BLACK_HOLE_TEX =
            new ResourceLocation("animod", "textures/environment/black_hole.png");

    private static final int STAR_COUNT = 220;
    private static final float[] STAR_DIRS = new float[STAR_COUNT * 3];
    private static final float[] STAR_SIZE = new float[STAR_COUNT];
    private static boolean starsInit = false;

    /** Вызывается из AniModClient: регистрирует небо Космоса. */
    public static void register() {
        net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry.registerSkyRenderer(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation("animod", "kosmos")),
                KosmosSky::render);
    }

    private static void initStars() {
        if (starsInit) return;
        starsInit = true;
        RandomSource rnd = RandomSource.create(404L);
        for (int i = 0; i < STAR_COUNT; i++) {
            double u = rnd.nextDouble() * 2 - 1;
            double phi = rnd.nextDouble() * Math.PI * 2;
            double sq = Math.sqrt(1 - u * u);
            STAR_DIRS[i * 3] = (float) (sq * Math.cos(phi));
            STAR_DIRS[i * 3 + 1] = (float) u;
            STAR_DIRS[i * 3 + 2] = (float) (sq * Math.sin(phi));
            STAR_SIZE[i] = 0.6F + rnd.nextFloat() * 1.8F;
        }
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
        PoseStack poseStack = ctx.matrixStack();
        Camera camera = ctx.camera();
        float tickDelta = ctx.tickDelta();
        initStars();
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level == null ? 0 : mc.level.getGameTime() + tickDelta) / 20.0F;

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // ===== ЗВЁЗДНАЯ БЕЗДНА (фиксированные точки на небесной сфере) =====
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f mat = poseStack.last().pose();
        for (int i = 0; i < STAR_COUNT; i++) {
            float dx = STAR_DIRS[i * 3], dy = STAR_DIRS[i * 3 + 1], dz = STAR_DIRS[i * 3 + 2];
            float size = STAR_SIZE[i];
            // правый/верхний векторы перпендикуляра направления звезды
            float rx = -dz, ry = 0, rz = dx;
            float len = (float) Math.sqrt(rx * rx + rz * rz);
            if (len < 0.01F) { rx = 1; ry = 0; rz = 0; len = 1; }
            rx /= len; rz /= len;
            float ux = dy * rz - dz * ry;
            float uy = dz * rx - dx * rz;
            float uz = dx * ry - dy * rx;
            float s = 300.0F, half = size * 2.5F;
            float b = 0.55F + 0.45F * (float) Math.abs(Math.sin(time * 1.5F + i));
            int alpha = (int) (b * 255);
            quad(buf, mat,
                    dx * s + rx * half + ux * half, dy * s + ry * half + uy * half, dz * s + rz * half + uz * half,
                    dx * s - rx * half + ux * half, dy * s - ry * half + uy * half, dz * s - rz * half + uz * half,
                    dx * s - rx * half - ux * half, dy * s - ry * half - uy * half, dz * s - rz * half - uz * half,
                    dx * s + rx * half - ux * half, dy * s + ry * half - uy * half, dz * s + rz * half - uz * half,
                    alpha, alpha, alpha, alpha);
        }
        tesselator.end();

        // ===== ЧЁРНАЯ ДЫРА (билборд в фиксированной точке неба) =====
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BLACK_HOLE_TEX);
        RenderSystem.enableCull();
        poseStack.pushPose();
        // ориентируем локальную систему по камере: после этого -Z = взгляд
        poseStack.mulPose(camera.rotation());
        // фиксированная точка неба: азимут 40°, подъём 30°
        poseStack.mulPose(Axis.YP.rotationDegrees(40.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(30.0F));
        poseStack.translate(0.0, 0.0, -400.0);
        // медленное вращение аккреционного диска
        poseStack.mulPose(Axis.ZP.rotationDegrees(time * 4.0F));
        float size = 340.0F;
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f hm = poseStack.last().pose();
        vertex(buf, hm, -size, -size, 0.0F, 0.0F, 0.0F);
        vertex(buf, hm, size, -size, 0.0F, 1.0F, 0.0F);
        vertex(buf, hm, size, size, 0.0F, 1.0F, 1.0F);
        vertex(buf, hm, -size, size, 0.0F, 0.0F, 1.0F);
        tesselator.end();
        poseStack.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void quad(BufferBuilder buf, Matrix4f mat,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz,
                             int r, int g, int b, int a) {
        buf.vertex(mat, ax, ay, az).color(r, g, b, a).endVertex();
        buf.vertex(mat, bx, by, bz).color(r, g, b, a).endVertex();
        buf.vertex(mat, cx, cy, cz).color(r, g, b, a).endVertex();
        buf.vertex(mat, dx, dy, dz).color(r, g, b, a).endVertex();
    }

    private static void vertex(BufferBuilder buf, Matrix4f mat,
                               float x, float y, float z, float u, float v) {
        buf.vertex(mat, x, y, z).uv(u, v).endVertex();
    }
}
