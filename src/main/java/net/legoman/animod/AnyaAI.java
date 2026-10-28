package net.legoman.animod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnyaAI {
    private static final Logger LOGGER = LoggerFactory.getLogger("animod-ai");

    // ПЕРЕКЛЮЧАТЕЛЬ МАРШРУТА АНИ
    private static final boolean USE_AI_SERVER = true;

    private static String lastUniqueResponse = "";
    private static int lastResponseBand = -1;

    private static final ConcurrentLinkedDeque<String> chatHistory = new ConcurrentLinkedDeque<>();
    private static final ConcurrentLinkedDeque<String> coreMemory = new ConcurrentLinkedDeque<>();

    private static final AtomicBoolean generating = new AtomicBoolean(false);

    private static volatile String cachedBiome = "неизвестно";
    private static volatile String cachedMobs = "никого нет";
    private static volatile String cachedItem = "пустая рука";
    private static volatile float cachedHealth = 20f;
    private static volatile String cachedTime = "день";
    private static volatile long lastContextUpdate = 0L;

    private static final long CONTEXT_CACHE_MS = 1000L;

    public static void loadMemory(List<String> savedMemory) {
        coreMemory.clear();
        coreMemory.addAll(savedMemory);
    }

    public static void addMemoryFact(String eventDescription, String exactPlayerQuote) {
        String structuredFact = "[ФАКТ]: " + eventDescription;
        if (exactPlayerQuote != null && !exactPlayerQuote.isBlank()) {
            structuredFact += " [СЛОВА ИГРОКА]: \"" + exactPlayerQuote + "\"";
        }

        coreMemory.remove(structuredFact);
        coreMemory.addLast(structuredFact);

        while (coreMemory.size() > 5) {
            coreMemory.pollFirst();
        }

        if (AniMod.currentServer != null) {
            AniSaveData data = AniSaveData.getServerState(AniMod.currentServer.overworld());
            data.memory.clear();
            data.memory.addAll(coreMemory);
            data.setDirty();
        }
    }

    public static int cleanMessages(int count) {
        if (count <= 0) return 0;
        int removed = 0;
        while (!chatHistory.isEmpty() && removed < count) {
            chatHistory.pollLast();
            removed++;
        }
        if (!chatHistory.isEmpty() && chatHistory.peekLast().startsWith("Игрок: ")) {
            chatHistory.pollLast();
            removed++;
        }
        return removed;
    }

    public static void updateContextCache(ServerPlayer player){ updateContextCache(player,false); }

    static void updateContextCache(ServerPlayer player, boolean force){
        long now = System.currentTimeMillis();
        cachedHealth = player.getHealth();
        long time = player.serverLevel().getDayTime() % 24000;
        cachedTime = time < 13000 ? "день" : "ночь";
        ItemStack held = player.getMainHandItem();
        cachedItem = held.isEmpty() ? "пустая рука" : BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
        if(!force && now - lastContextUpdate < CONTEXT_CACHE_MS){
            return;
        }
        lastContextUpdate = now;
        try{
            cachedBiome = player.serverLevel().getBiome(player.blockPosition()).unwrapKey().map(k->k.location().getPath()).orElse("неизвестно");
        }catch(Exception ignored){
            cachedBiome = "неизвестно";
        }
        cachedMobs = getNearbyMobs(player);
    }

    public static void processAndSend(ServerPlayer player, String userText){
        if(!generating.compareAndSet(false,true)){return;}
        final String finalUserText = userText == null ? "" : userText.trim();
        final int finalKarma = Math.max(0, Math.min(100, KarmaManager.get()));
        final int finalBand = AnyaAIPrompts.getKarmaBand(finalKarma);
        final String finalInstruction = AnyaAIPrompts.getInstruction(finalKarma);
        final String finalNameColorName = AniMod.getTraitColorName();

        updateContextCache(player);
        final float finalHealth = cachedHealth;
        final String finalTimeText = cachedTime;
        final String finalBiomeName = cachedBiome;
        final String finalMobsInfo = cachedMobs;
        final String finalItemName = cachedItem;

        // Извлечение данных в основном потоке для предотвращения крашей асинхронного таска
        final String finalPlayerName = player.getName().getString();
        final String finalWorldId = AniSaveData.getServerState(player.serverLevel()).worldUuid;

        final List<String> currentHistory = new ArrayList<>(chatHistory);
        final List<String> currentMemory = new ArrayList<>(coreMemory);

        player.serverLevel().getServer().execute(()->{
            if(player.isRemoved()) return;
            sendAnyaMessage(player, finalNameColorName, "gray", "\u0414\u0443\u043C\u0430\u0435\u0442...");
        });

        CompletableFuture.runAsync(()->{
            try{
                String response = generateCandidate(finalInstruction, finalTimeText, finalBiomeName, finalHealth, finalItemName, finalMobsInfo, finalUserText, currentMemory, currentHistory, finalPlayerName, finalWorldId, finalKarma);
                LOGGER.info("[DIAGNOSTIC] CLEAN response recieved in AnyaAI: '{}'", response);

                boolean invalid = isInvalid(response, finalUserText, finalBand);

                if(invalid){
                    boolean isDup = isDuplicate(response, finalBand);
                    boolean isBad = AnyaAIFilters.isBadResponse(response, finalUserText);
                    LOGGER.warn("[DIAGNOSTIC] Valid response REJECTED! isBadResponse: {}, isDuplicate: {}. RAW response: '{}'", isBad, isDup, response);

                    response = AnyaAIFallback.get(finalKarma, finalUserText, finalItemName, finalMobsInfo, finalTimeText, finalBiomeName, finalHealth, lastUniqueResponse);
                    LOGGER.info("[DIAGNOSTIC] Fallback triggered. New response: '{}'", response);
                }

                if(response == null || response.isBlank()){
                    response = "Я тебя слышу.";
                    LOGGER.info("[DIAGNOSTIC] Response was completely empty. Replaced with default.");
                }

                lastUniqueResponse = response;
                lastResponseBand = finalBand;

                chatHistory.addLast("Игрок: " + finalUserText);
                chatHistory.addLast("Аня: " + response);

                while(chatHistory.size() > 6) {
                    chatHistory.pollFirst();
                }

        final String safe = response;

                player.serverLevel().getServer().execute(()->{
                    if(player.isRemoved()) return;
                    sendAnyaMessage(player, finalNameColorName, "white", safe);
                });
            }catch(Exception e){
                LOGGER.error("[DIAGNOSTIC] Exception in async thread: {}", e.getMessage(), e);
                player.serverLevel().getServer().execute(()->{
                    if(player.isRemoved()) return;
                    String safe = KoboldClient.escapeJson(AnyaAIFallback.get(finalKarma,finalUserText,finalItemName,finalMobsInfo,finalTimeText,finalBiomeName,finalHealth,lastUniqueResponse));
                    sendAnyaMessage(player, finalNameColorName, "white", AnyaAIFallback.get(finalKarma,finalUserText,finalItemName,finalMobsInfo,finalTimeText,finalBiomeName,finalHealth,lastUniqueResponse));
                });
            }finally{
                generating.set(false);
            }
        });
    }

    private static String generateCandidate(String instruction, String time, String biome, float health, String item, String mobs, String userText, List<String> memory, List<String> history, String playerName, String worldId, int karma){

        if (USE_AI_SERVER) {
            LOGGER.info("[DIAGNOSTIC] Routing request to external Python AI Server...");
            JsonObject gameContext = new JsonObject();
            gameContext.addProperty("time", time);
            gameContext.addProperty("biome", biome);
            gameContext.addProperty("health", health);
            gameContext.addProperty("item", item);
            gameContext.addProperty("mobs", mobs);

            // Теперь мы передаем memory и history в Python-сервер
            return AiServerClient.send(worldId, playerName, userText, karma, gameContext, memory, history);
        } else {
            LOGGER.info("[DIAGNOSTIC] Routing request to local KoboldCpp...");
            String systemMessage = AnyaAIPrompts.buildSystemMessage(instruction, time, biome, health, item, mobs, lastUniqueResponse, lastResponseBand, memory);

            JsonArray messages = new JsonArray();

            JsonObject sysObj = new JsonObject();
            sysObj.addProperty("role", "system");
            sysObj.addProperty("content", systemMessage);
            messages.add(sysObj);

            int histLen = 0;
            if(history != null && !history.isEmpty()){
                for(String line : history){
                    histLen += line.length();
                    JsonObject msgObj = new JsonObject();
                    if(line.startsWith("Игрок: ")){
                        msgObj.addProperty("role", "user");
                        msgObj.addProperty("content", line.substring(7).trim());
                        messages.add(msgObj);
                    } else if (line.startsWith("Аня: ")){
                        msgObj.addProperty("role", "assistant");
                        msgObj.addProperty("content", line.substring(5).trim());
                        messages.add(msgObj);
                    }
                }
            }

            JsonObject currentUser = new JsonObject();
            currentUser.addProperty("role", "user");
            currentUser.addProperty("content", userText);
            messages.add(currentUser);

            int sysLen = systemMessage.length();
            int usrLen = userText.length();
            int estTokens = (sysLen + histLen + usrLen) / 4;
            LOGGER.info("[DIAGNOSTIC] Запрос сформирован. Примерно токенов: ~{}. (System: {} симв, History: {} симв, User: {} симв)", estTokens, sysLen, histLen, usrLen);

            float temperature = 0.56f;
            return KoboldClient.send(messages, temperature);
        }
    }

    private static boolean isInvalid(String response, String userText, int band) {
        return AnyaAIFilters.isBadResponse(response, userText) || isDuplicate(response, band);
    }

    private static boolean isDuplicate(String response, int band){
        return response != null && !lastUniqueResponse.isBlank() && band == lastResponseBand && AnyaAIPrompts.normalize(response).equals(AnyaAIPrompts.normalize(lastUniqueResponse));
    }

    private static String getNearbyMobs(ServerPlayer player){
        AABB area = player.getBoundingBox().inflate(20.0, 5.0, 20.0);
        List<Mob> mobs = player.serverLevel().getEntitiesOfClass(Mob.class, area);
        Map<String,Integer> counts = new HashMap<>();
        for(Mob mob : mobs){
            if(mob.isNoAi()) continue;
            String name = mob.getType().getDescription().getString();
            if(isIgnoredMob(name)) continue;
            counts.put(name, counts.getOrDefault(name, 0) + 1);
        }
        if(counts.isEmpty()) return "никого нет";
        List<String> result = new ArrayList<>();
        for(Map.Entry<String,Integer> e : counts.entrySet()) result.add(e.getKey() + " (x" + e.getValue() + ")");
        return String.join(", ", result);
    }

    private static boolean isIgnoredMob(String name){
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals("треска") || n.equals("спрут") || n.equals("лосось") || n.equals("тропическая рыба") || n.equals("иглобрюх");
    }

    private static void sendAnyaMessage(ServerPlayer player, String nameColorName, String textColorName, String text) {
        if (player.isRemoved()) return;
        player.sendSystemMessage(
            Component.literal("[\u0410\u043D\u044F] ").withStyle(getChatColor(nameColorName))
                .append(Component.literal(text).withStyle(getChatColor(textColorName)))
        );
    }

    private static ChatFormatting getChatColor(String name) {
        switch (name) {
            case "dark_red": return ChatFormatting.DARK_RED;
            case "light_purple": return ChatFormatting.LIGHT_PURPLE;
            case "gold": return ChatFormatting.GOLD;
            case "green": return ChatFormatting.GREEN;
            case "red": return ChatFormatting.RED;
            case "gray": return ChatFormatting.GRAY;
            default: return ChatFormatting.WHITE;
        }
    }
}
