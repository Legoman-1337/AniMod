package net.legoman.animod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import static net.legoman.animod.AnyaAIPrompts.*;

public class AnyaAIFilters {
    private static final Logger LOGGER = LoggerFactory.getLogger("animod-filters");

    public static boolean isBadResponse(String text,String userText){
        LOGGER.info("[AI FILTER] RAW: {}", text);
        if(text==null||text.isBlank()) {
            LOGGER.warn("[AI FILTER] REJECT: null_or_blank");
            return true;
        }
        String a=text.trim(),x=normalize(a);

        if(a.length()<2) {
            LOGGER.warn("[AI FILTER] REJECT: length={}", a.length());
            return true;
        }

        int letters = 0;
        for (char c : a.toCharArray()) {
            if (Character.isLetter(c)) letters++;
        }
        if (letters == 0) {
            LOGGER.warn("[AI FILTER] REJECT: no_letters");
            return true;
        }

        String[] cutoffs = {",", ":", "-", " и", " а", " но", " с", " к", " в", " на", " от"};
        for(String c : cutoffs) {
            if(a.endsWith(c)) {
                LOGGER.warn("[AI FILTER] REJECT: cutoff='{}'", c);
                return true;
            }
        }

        if(hasMetaContent(x)) {
            LOGGER.warn("[AI FILTER] REJECT: meta_content");
            return true;
        }
        if(hasFakePlayerActions(x)) {
            LOGGER.warn("[AI FILTER] REJECT: fake_player_action");
            return true;
        }
        if(hasAssistantStart(x)) {
            LOGGER.warn("[AI FILTER] REJECT: assistant_start");
            return true;
        }

        return false;
    }

    public static boolean hasMetaContent(String x){
        String[] bad={
                "assistant","user:","system:","instruction:","response:","player:","json",
                "not found","error","[игрок]","[аня]","[игровая ситуация]",
                "задание ани","ответ ани","ответ игрока",
                "я как ии","как искусственный интеллект","моя задача","моя цель",
                "я должна ответить","я должен ответить","обоснование:",
                "объяснение:","почему этот ответ",
                "ответ соответствует","варианты ответа","пример ответа",
                "список вариантов","можно ответить:","можно сказать:",
                "согласно требованиям","в соответствии с требованиями",
                "требования пользователя","анализ сообщения",
                "анализ ответа","рассуждение","рассуждения","план ответа",
                "player said", "user said", "analysis", "the player", "user wrote",
                "игрок сказал", "пользователь сказал", "игрок спрашивает",
                "okay", "alright", "sure", "let's", "i will now", "here is",
                "имя игрока", "player name", "player_name", "[имя]", "<имя>"
        };
        for(String s:bad)if(x.contains(s))return true;

        int lat=0,cyr=0;
        for(char c:x.toCharArray()){
            if(c>='a'&&c<='z')lat++;
            if((c>='а'&&c<='я')||c=='ё')cyr++;
        }
        return lat>10&&lat>cyr;
    }

    private static boolean hasAssistantStart(String x){
        // Убраны разговорные: "конечно", "без проблем", "разумеется", "понял тебя"
        String[] bad={
                "хорошо я понял", "хорошо, я понял", "конечно вот",
                "я помогу", "я могу помочь",
                "давайте разберем", "вернемся к основному заданию",
                "в чате прислали запрос", "основное задание",
                "давай разберемся", "давай разберёмся"
        };
        for(String s:bad)if(x.contains(s))return true;
        return false;
    }

    private static boolean hasFakePlayerActions(String x){
        // Убраны разговорные: "ты сделал", "ты взял", "ты решил", "ты захотел", "ты собираешься"
        String[] bad={
                "ты пошел","ты пошёл","ты пришел","ты пришёл",
                "ты построил","ты сломал","ты добыл",
                "ты копаешь","ты копал"
        };
        for(String s:bad)if(x.contains(s))return true;
        return false;
    }

    public static String cleanText(String text){
        if(text==null)return "";
        String s=text
                .replaceAll("<\\|.*?\\|>"," ")
                .replace("ЗАДАНИЕ АНИ:"," ")
                .replace("ОТВЕТ АНИ:"," ")
                .replace("### Response:"," ")
                .replace("### Instruction:"," ")
                .trim();

        s=s.replaceAll("(?i)^\\s*(\\[?аня\\]?|anya)\\s*[:\\-—]\\s*","");
        s=s.replaceAll("(?i)^\\s*\\*?Аня\\*?\\s*[:\\-—]\\s*","");

        s = s.replaceAll("(?i)[o0о]_+[o0о]", "");
        s = s.replace("-_-", "").replace("T_T", "");
        s = s.replaceFirst("^\\s*\\*[^*]+\\*\\s*", "");

        String[] cuts={
                "###", "[Игрок]", "[ИГРОК]", "Assistant:", "User:", "System:",
                "Игрок:", "ИГРОК:", "Вернёмся к", "Вернемся к", "В чате",
                "Теперь игрок", "Основное задание:", "Okay,", "Okay.", "The player",
                "User said", "Игрок сказал", "Пользователь сказал", "Analysis:",
                "Хорошо, давайте", "Хорошо. Давайте", "Давайте посмотрим", "Давайте разберем",
                "Ответ Ани:", "Рассуждение:", "Объяснение:"
        };

        for(String c:cuts){
            int i = s.toLowerCase(Locale.ROOT).indexOf(c.toLowerCase(Locale.ROOT));
            if(i>=0)s=s.substring(0,i);
        }

        s = s.replaceAll("[\\uAC00-\\uD7A3\\u3131-\\u318E\\u4E00-\\u9FFF]", "");
        s = s.replaceAll("(?i)(?u),?\\s*(\\[имя игрока\\]|<имя игрока>|\\{имя игрока\\}|имя игрока|player name|player)\\b", "");
        s = s.replaceAll("(?i)(?u)^(\\[имя игрока\\]|<имя игрока>|\\{имя игрока\\}|имя игрока|player name|player)\\b,?\\s*", "");

        s = s.replace("<br>", " ").replace("<br/>", " ").replace("<br />", " ");
        s=s.replaceAll("\\s+"," ").trim();
        return s;
    }
}