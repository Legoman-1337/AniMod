package net.legoman.animod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.legoman.animod.GreenOrbEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Рендер зелёного файрбола: billboard-квад с текстурой зелёного пламени. */
public class GreenOrbRenderer extends EntityRenderer<GreenOrbEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("animod", "textures/entity/green_orb.png");

    public GreenOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(GreenOrbEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(GreenOrbEntity entity, float yaw, float partialTick,
                       PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        // billboard: квадрат всегда лицом к камере
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
        float half = 0.45F;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        var pose = ps.last();
        var normal = pose.normal();
        quad(vc, pose, -half, -half, half, half, 0, 0, 1, 1, light);
        quad(vc, pose, -half, -half, half, half, 0, 1, 1, 0, light); // задняя грань — видно с обеих сторон
        ps.popPose();
        super.render(entity, yaw, partialTick, ps, buffers, light);
    }

    private void quad(VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                      float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1, int light) {
        var matrix = pose.pose();
        var n = pose.normal();
        vc.vertex(matrix, x0, y0, 0).color(255, 255, 255, 255).uv(u0, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(matrix, x1, y0, 0).color(255, 255, 255, 255).uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(matrix, x1, y1, 0).color(255, 255, 255, 255).uv(u1, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(matrix, x0, y1, 0).color(255, 255, 255, 255).uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }
}
