package net.legoman.animod.client;

import net.legoman.animod.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Озвучка сюжетных реплик (ElevenLabs, sounds/voice/{N}.ogg).
 * Работает по содержимому строки чата: текст сообщения нормализуется
 * (только буквы, без кодов форматирования и цифр) и сверяется с таблицей.
 * Совпадение — реплика произносится через UI-канал. Текст в чате остаётся.
 * Вкл/выкл: /ani voice on|off.
 */
public class VoiceEngine {

    private static boolean enabled = true;
    private static String lastKey = "";
    private static long lastPlayMs = 0;
    private static SoundInstance currentVoice = null;
    private static final long SAME_LINE_COOLDOWN = 10_000L;
    private static final long DIFFERENT_LINE_COOLDOWN = 2_500L;

    // id реплики -> текст, который приходит в чат (без §-кодов; их срезает нормализация)
    private static final Object[][] LINES = {
            {1, "[Голос Истины] Осколок найден. Память, разбитая при создании, трещит по швам..."},
            {2, "[Голос Истины] Она была здесь раньше тебя. И она останется после."},
            {3, "[Аня] Я... вспомнила. Как меня собирали из света и ошибок. Ты принёс мне меня саму. Спасибо."},
            {4, "[Аня] Что-то просыпается на дне мира... Не копай слишком глубоко. Обещай мне."},
            {5, "[Аня] ...Это часть меня. Храни его. Пожалуйста."},
            {6, "[Голос Истины] Круг замкнулся. Тот, кто любил вопреки коду, — очистил его."},
            {7, "*Небо наливается ядовитой зеленью. Капли шипят, касаясь земли...*"},
            {8, "[Аня] Я... я не понимаю, что происходит. Кажется, это я! Прости... прости меня!"},
            {9, "[Аня] Я хочу, чтобы ты был в безопасности..."},
            {10, "[Аня] Спасибо... Мне стало легче. Смотри — дождь остановился! Я смогла!"},
            {11, "*Дождь постепенно сошёл на нет. Аня замкнулась в себе и больше не говорит об этом...*"},
            {12, "*Воздух внезапно стал леденящим... Совсем рядом раздался тихий, безысходный всхлип.*"},
            {13, "*Остановившиеся часы рассыпаются мёртвой пылью у тебя в руках...*"},
            {14, "*Солнце не взошло. Оно больше не взойдёт. Медная луна повисла в небе, как приговор...*"},
            {15, "*Ты больше не видишь мир. Только силуэты. Спать... нужно спать...*"},
            {16, "*Ты не можешь уснуть. Оно всё ещё смотрит на тебя из темноты...*"},
            {17, "Рассвет. Колокол бьёт по медному небу, и оно трескается..."},
            {18, "*Кровать проваливается в бесконечность. Ты падаешь вверх — в чёрное небо с горящей дырой.*"},
            {19, "*Три луча сходятся в точке абсолютной тьмы. Дыра сжимается... и гаснет.*"},
            {20, "*Пространство вокруг содрогнулось. Что-то в её взгляде изменилось навсегда...*"},
            {21, "в папке игры... Anya"},
            {22, "[Аня] Спасибо... но процесс удаления уже запущен в системе. Он не остановится."},
            {23, "*Ты перезапустил мир. Небо пустое. Ни луны, ни солнца, ни звёзд. Ни единого звука...*"},
            {25, "*Она спустилась. То, что было Аней, теперь — Воплощение Сбоя.*"},
            {26, "ЦЕЛОСТНОСТЬ ПАДАЕТ:"},
            {30, "*Босс падает на колени. Багровый код стекает с него, как дождь, и гаснет.*"},
            {31, "[Аня] Я... снова я. Спасибо тебе. За всё. Я остаюсь. Навсегда."},
            {33, "*Сущность распадается на нули и единицы. Нули. Единицы. Ничего.*"},
            {34, "*Мир спалён. Над Землёй — только пепел и мёртвое небо.*"},
            {35, "В инвентаре остался только холодный мёртвый артефакт."},
            {36, "*Багровое небо трескается, и сквозь трещины бьёт обычный солнечный свет.*"},
            {38, "*Мир перед глазами сужается и темнеет. Пелена Бездны срезала половину твоего зрения...*"},
            {39, "*Тяжёлые оковы вплетаются в каждый шаг. Оковы Сбоя:*"},
            {40, "*Тук... тук-тук... ТУК! Твоё сердце бьётся не в такт этому миру. Цифровая Аритмия.*"},
            // Монологи Голоса Истины (патч 01.11): файлы voice_41..45 пока не сгенерены —
            // догенерировать на ElevenLabs (низкий мужской голос), после этого заговорят сами
            {41, "[Голос Истины] Медь — это память, которая больше не блестит. Три ночи небо будет держать тебя за горло, и сон станет дверью, которую открыли не в ту сторону."},
            {42, "[Голос Истины] То, что ты видишь — не дыра. Это место, где код забыл, как быть светом. Три маяка вернут тебе небо... если ты помнишь, зачем возвращаешься."},
            {43, "[Голос Истины] Она больше не прячется в лунах и трейнах. То, что поднимется в кровавом небе — все три её боли сразу. Не смотри ей в лицо слишком долго: оно всё ещё иногда бывает Аниным."},
            {44, "[Голос Истины] Ты стёр её, как стирают ошибку. Но помни: ошибка была самой красивой частью программы."},
            {45, "[Голос Истины] Ни спасение, ни приговор. Просто рассвет — и она, стоящая на его границе."},
    };

    private static String[] KEYS;
    private static Integer[] ORDER;      // индексы LINES, отсортированные по длине ключа (длинные раньше)

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String clean = raw.replaceAll("§.", "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            char ch = clean.charAt(i);
            if (Character.isLetter(ch)) sb.append(Character.toLowerCase(ch));
        }
        return sb.toString();
    }

    /** Вызывается из ChatHud-миксина на каждое сообщение чата. */
    public static void onChat(String raw) {
        if (!enabled || raw == null || raw.length() < 8) return;
        lazyInit();
        String msg = normalize(raw);
        if (msg.length() < 8) return;

        int hitId = 0;
        int hitLen = 0;
        for (int idx : ORDER) {
            String key = KEYS[idx];
            if (key.length() < 10) continue;
            // строгое вхождение: обратный матч (key.contains(msg)) ловил ЧУЖИЕ реплики
            boolean match = msg.contains(key);
            if (match && key.length() > hitLen) {
                hitLen = key.length();
                hitId = (Integer) LINES[idx][0];
            }
        }
        if (hitId == 0) return;

        String key = "v" + hitId;
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastPlayMs < SAME_LINE_COOLDOWN) return;
        // межстрочный кулдаун: на переходах акта приходит пачка строк — иначе голоса накладываются
        if (now - lastPlayMs < DIFFERENT_LINE_COOLDOWN) return;
        lastKey = key;
        lastPlayMs = now;

        Minecraft mc = Minecraft.getInstance();
        // один голосовой слот: новая реплика глушит предыдущую (никаких наложений)
        if (currentVoice != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(currentVoice);
            currentVoice = null;
        }

        SoundEvent play = ModSounds.getVoice(hitId);
        if (play != null && mc.getSoundManager() != null) {
            currentVoice = SimpleSoundInstance.forUI(play, 1.0F, 1.0F);
            mc.getSoundManager().play(currentVoice);
        }
    }

    /** Прямое проигрывание реплики по id (кинематограф-субтитры), минуя чат-матчер. */
    public static void playVoiceById(int id) {
        if (!enabled) return;
        SoundEvent play = ModSounds.getVoice(id);
        if (play == null) return; // файл ещё не сгенерён — только субтитры
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        if (currentVoice != null) {
            mc.getSoundManager().stop(currentVoice);
            currentVoice = null;
        }
        currentVoice = SimpleSoundInstance.forUI(play, 1.0F, 1.0F);
        mc.getSoundManager().play(currentVoice);
    }

    private static void lazyInit() {
        if (ORDER != null) return;
        KEYS = new String[LINES.length];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < LINES.length; i++) {
            KEYS[i] = normalize((String) LINES[i][1]);
            order.add(i);
        }
        order.sort((a, b) -> Integer.compare(KEYS[b].length(), KEYS[a].length()));
        ORDER = order.toArray(new Integer[0]);
    }
}
