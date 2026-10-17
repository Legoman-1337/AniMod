package net.legoman.animod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AiServerClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("animod-ai-client");
    private static final String SERVER_URL = "http://127.0.0.1:8000/chat";

    public static String send(String worldId, String playerName, String message, int karma, JsonObject gameContext, List<String> coreMemory, List<String> chatHistory) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(SERVER_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000); // 5 секунд на подключение
            conn.setReadTimeout(60000);   // 60 секунд на ожидание ответа

            JsonObject json = new JsonObject();
            json.addProperty("world_id", worldId);
            json.addProperty("player_name", playerName);
            json.addProperty("message", message);
            json.addProperty("karma", karma);
            json.add("game_context", gameContext);

            // Передача долговременной памяти
            JsonArray memoryArray = new JsonArray();
            if (coreMemory != null) {
                for (String m : coreMemory) memoryArray.add(m);
            }
            json.add("core_memory", memoryArray);

            // Передача истории диалога
            JsonArray historyArray = new JsonArray();
            if (chatHistory != null) {
                for (String h : chatHistory) historyArray.add(h);
            }
            json.add("chat_history", historyArray);

            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            String body = readStream(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());

            if (status < 200 || status >= 300) {
                LOGGER.error("AI Server HTTP {}: {}", status, body);
                return "";
            }

            JsonObject result = JsonParser.parseString(body).getAsJsonObject();
            if (result.has("reply")) {
                String raw = result.get("reply").getAsString();
                String clean = AnyaAIFilters.cleanText(raw);
                LOGGER.info("Аня (Python) RAW: {}", raw.replace("\n", " \\n "));
                LOGGER.info("Аня (Python) CLEAN: {}", clean);
                return clean;
            }
            return "";
        } catch (Exception e) {
            LOGGER.error("Ошибка связи с AI-сервером: {}", e.getMessage());
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws java.io.IOException {
        if (stream == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) s.append(line).append('\n');
            return s.toString().trim();
        }
    }
}