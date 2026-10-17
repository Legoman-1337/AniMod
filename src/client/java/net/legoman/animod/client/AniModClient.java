package net.legoman.animod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.legoman.animod.AnyaEntity;

public class AniModClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(AnyaEntity.ANYA, AnyaRenderer::new);
	}
}