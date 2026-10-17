package net.legoman.animod.client;

import net.legoman.animod.AnyaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class AnyaRenderer extends GeoEntityRenderer<AnyaEntity> {
    public AnyaRenderer(EntityRendererProvider.Context context){
        super(context,new AnyaModel());
    }
}