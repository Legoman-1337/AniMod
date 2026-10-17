package net.legoman.animod;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class KoboldClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("animod-kobold");
    // Эндпоинт изменён на стандарт OpenAI-compatible
    private static final String KOBOLD_URL = "http://localhost:5001/v1/chat/completions";

    public static String send(JsonArray messages, float temperature) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(KOBOLD_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("Accept-Encoding", "identity");
            conn.setUseCaches(false);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(120000);

            JsonObject json = new JsonObject();
            json.add("messages", messages);

            // Параметры сохранены строго как было
            json.addProperty("max_tokens", 80); // Заменили max_length на max_tokens
            json.addProperty("temperature", temperature);
            json.addProperty("repetition_penalty", 1.18); // Заменили rep_pen на repetition_penalty
            json.addProperty("rep_pen_range", 512);
            json.addProperty("rep_pen_slope", 0.7);
            json.addProperty("top_p", 0.92);
            json.addProperty("top_k", 100);

            // stop_sequence заменён на стандартный stop, значения сохранены для обратной совместимости
            JsonArray stop = new JsonArray();
            stop.add("\nИгрок:");
            stop.add("Игрок:");
            stop.add("\nАня:");
            stop.add("###");
            stop.add("\nSystem:");
            stop.add("\nUser:");
            stop.add("\nuser:");
            json.add("stop", stop);

            conn.setDoOutput(true);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            String body = readStream(status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream());

            if (status < 200 || status >= 300) {
                LOGGER.error("KoboldAI HTTP {}: {}", status, body);
                return "";
            }

            JsonObject result = JsonParser.parseString(body).getAsJsonObject();
            if (!result.has("choices")) return "";

            JsonArray choices = result.getAsJsonArray("choices");
            if (choices.isEmpty()) return "";

            JsonObject first = choices.get(0).getAsJsonObject();
            if (!first.has("message")) return "";
            JsonObject message = first.getAsJsonObject("message");
            if (!message.has("content")) return "";

            // Извлекаем ответ в формате OpenAI Chat
            String raw = message.get("content").getAsString();
            String clean = AnyaAIFilters.cleanText(raw);

            LOGGER.info("Аня RAW: {}", raw.replace("\n", " \\n "));
            LOGGER.info("Аня CLEAN: {}", clean);

            return clean;
        } catch (Exception e) {
            LOGGER.error("Ошибка связи с KoboldAI: {}", e.getMessage());
            return "";
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) s.append(line).append('\n');
            return s.toString().trim();
        }
    }

    public static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}