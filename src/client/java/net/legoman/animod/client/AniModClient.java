package net.legoman.animod.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.legoman.animod.AniMod;
import net.legoman.animod.AnyaEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public class AniModClient implements ClientModInitializer {

    private static int lastSyncAct = -1; // для fade только при реальной смене акта

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AnyaEntity.ANYA, AnyaRenderer::new);
        EntityRendererRegistry.register(AnyaEntity.GREEN_ORB, GreenOrbRenderer::new);

        // Дев-наблюдение: скриншоты из рендера (/cshot, файл-флаг animod_autoshot.txt)
        DevShot.register();

        // Автотест: сам создаёт свежий мир по файлу-флагу (для агентских прогонов)
        AutoTest.register();

        // Акт 4: собственное небо Космоса (звёзды + чёрная дыра)
        KosmosSky.register();

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

        ClientPlayNetworking.registerGlobalReceiver(AniMod.TOXIC_SKY_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean active = buf.readBoolean();
                    client.execute(() -> StoryScreenOverlay.setToxicSkyActive(active));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.SHOP_SYNC_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean open = buf.readBoolean();
                    int coins = buf.readInt();
                    int[] levels = {buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()};
                    int curseId = buf.readInt();
                    int karma = buf.readInt();
                    boolean discount = buf.readBoolean();
                    client.execute(() -> {
                        CoinHud.setCoins(coins);
                        if (open) ShopScreen.open(coins, levels, curseId, karma, discount);
                        else ShopScreen.updateData(coins, levels, curseId, karma, discount);
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.CURSE_SYNC_PACKET,
                (client, handler, buf, responseSender) -> {
                    int curseId = buf.readInt();
                    client.execute(() -> StoryScreenOverlay.setActiveCurse(curseId));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.VISION_PACKET,
                (client, handler, buf, responseSender) -> {
                    float soap = buf.readFloat();
                    client.execute(() -> StoryScreenOverlay.setVisionSoap(soap));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.KOSMOS_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean active = buf.readBoolean();
                    client.execute(() -> StoryScreenOverlay.setKosmosSkyActive(active));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.NONE_SKY_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean active = buf.readBoolean();
                    client.execute(() -> StoryScreenOverlay.setNoneMoonActive(active));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.BLOOD_SKY_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean active = buf.readBoolean();
                    client.execute(() -> StoryScreenOverlay.setBloodMoonActive(active));
                });

        // ПОЛНЫЙ снапшот сюжета: клиент выставляет РОВНО серверное состояние —
        // наслойвшиеся неба/бары/луны самоликвидируются. Fade — только при смене акта.
        ClientPlayNetworking.registerGlobalReceiver(AniMod.STORY_ACT_PACKET,
                (client, handler, buf, responseSender) -> {
                    int act = buf.readInt();
                    boolean fixed = buf.readBoolean();
                    boolean insomnia = buf.readBoolean();
                    boolean toxic = buf.readBoolean();
                    boolean none = buf.readBoolean();
                    boolean kosmos = buf.readBoolean();
                    boolean blood = buf.readBoolean();
                    int bossStage = buf.readInt();
                    int bossSegments = buf.readInt();
                    client.execute(() -> {
                        ClientOsHaunt.setAct(act, fixed);
                        StoryScreenOverlay.setInsomniaSkyActive(insomnia);
                        StoryScreenOverlay.setToxicSkyActive(toxic);
                        StoryScreenOverlay.setNoneMoonActive(none);
                        StoryScreenOverlay.setKosmosSkyActive(kosmos);
                        StoryScreenOverlay.setBloodMoonActive(blood);
                        StoryScreenOverlay.setBossGlobal(bossStage, bossSegments);
                        if (act != lastSyncAct) {
                            lastSyncAct = act;
                            StoryScreenOverlay.resetTransients(); // гасим эффекты прошлого акта
                            StoryScreenOverlay.triggerActFade();
                        }
                    });
                });

        // Кинематограф-субтитры: сюжетный текст на экране (letterbox), не в чате
        ClientPlayNetworking.registerGlobalReceiver(AniMod.CINEMATIC_TEXT_PACKET,
                (client, handler, buf, responseSender) -> {
                    String text = buf.readUtf(512);
                    int ticks = buf.readInt();
                    int voiceId = buf.readInt();
                    client.execute(() -> {
                        StoryScreenOverlay.showCinematic(text, ticks);
                        if (voiceId > 0) VoiceEngine.playVoiceById(voiceId);
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.COIN_POPUP_PACKET,
                (client, handler, buf, responseSender) -> {
                    int amount = buf.readInt();
                    int total = buf.readInt();
                    client.execute(() -> CoinHud.addCoins(amount, total));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.BOSS_GLOBAL_PACKET,
                (client, handler, buf, responseSender) -> {
                    int stage = buf.readInt();
                    int segments = buf.readInt();
                    client.execute(() -> StoryScreenOverlay.setBossGlobal(stage, segments));
                });

        ClientPlayNetworking.registerGlobalReceiver(AniMod.VOICE_TOGGLE_PACKET,
                (client, handler, buf, responseSender) -> {
                    boolean enabled = buf.readBoolean();
                    client.execute(() -> VoiceEngine.setEnabled(enabled));
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            StoryScreenOverlay.clientTick();
            ClientOsHaunt.tick(client);
        });

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            StoryScreenOverlay.render(graphics, tickDelta);
            CoinHud.render(graphics, Minecraft.getInstance());
        });
    }

    static void sendTeleportConfirmation(boolean accepted) {
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        packet.writeBoolean(accepted);
        ClientPlayNetworking.send(AniMod.TELEPORT_CONFIRM_PACKET, packet);
    }
}