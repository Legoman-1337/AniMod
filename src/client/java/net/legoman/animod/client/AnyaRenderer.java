package net.legoman.animod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.legoman.animod.AnyaEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class AnyaRenderer extends GeoEntityRenderer<AnyaEntity> {
    public AnyaRenderer(EntityRendererProvider.Context context){
        super(context,new AnyaModel());
    }

    @Override
    public void render(AnyaEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Акт 9: босс «Воплощение Сбоя» — гигантская хоррор-модель Ани
        if (entity.isBossForm()) {
            poseStack.scale(7.0F, 7.0F, 7.0F);
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
