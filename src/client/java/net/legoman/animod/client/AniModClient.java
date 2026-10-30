package net.legoman.animod.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.legoman.animod.AniMod;
import net.legoman.animod.AnyaEntity;
import net.minecraft.network.FriendlyByteBuf;

public class AniModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AnyaEntity.ANYA, AnyaRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(AniMod.TELEPORT_PROMPT_PACKET,
                (client, handler, buf, responseSender) -> {
                    double distance = buf.readDouble();
                    client.execute(() -> client.setScreen(new AnyaTeleportConfirmScreen(distance)));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.STORY_CUTSCENE_PACKET,
                (client, handler, buf, responseSender) -> {
                    int stateId = buf.readInt();
                    int durationTicks = buf.readInt();
                    client.execute(() -> StoryScreenOverlay.startCutscene(stateId, durationTicks));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.INSOMNIA_SKY_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean active = buf.readBoolean();
                    client.execute(() -> StoryScreenOverlay.setInsomniaSkyActive(active));
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> StoryScreenOverlay.clientTick());

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> StoryScreenOverlay.render(graphics, tickDelta));
    }

    static void sendTeleportConfirmation(boolean accepted) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBoolean(accepted);
        ClientPlayNetworking.send(AniMod.TELEPORT_CONFIRM_PACKET, packet);
    }
}