package net.legoman.animod;

public class AnyaTextEmotion {

    // Глобальный словарь оскорблений (максимально расширенный)
    private static final String[] INSULT_ROOTS = {
            "идиот", "дебил", "урод", "твар", "мраз", "сук", "шлюх", "стерв",
            "чмо", "лох", "гнид", "даун", "пидор", "гандон", "уеб", "долбае",
            "долбо", "шалав", "шмар", "бляд", "ебану", "хуесос", "нахуй",
            "тупая", "глупая", "тупиц", "бесишь", "заткнись", "завали", "овца",
            "аутист", "бестолков", "мразь", "уебищ", "шлюп", "пиздюк", "хуйн", "хуил",
            "шлюшк", "проститут", "шаболд", "курв", "выбляд", "шляп", "залуп",
            "отстал", "давалк", "шмаровоз", "говноед", "соси", "потаскух",
            // Прямые формы
            " дура ", " дуру ", " дурой ", " дуре ", " дурак ", " придур", " тупой ", " тупорыл"
    };

    // Глобальный словарь комплиментов и признаний
    private static final String[] COMPLIMENT_ROOTS = {
            "мил", "красив", "красот", "умн", "лапоч", "зайк", "солнышк",
            "люб", "обожа", "нрави", "прекрасн", "очароват", "дорог", "класс",
            "лучш", "супер", "шикарн", "богин", "королев", "золот", "милаш",
            "сладк", "ахуен", "охуен", "пиздат", "прелест", "родна", "чудесн",
            "идеальн", "невероятн", "потрясающ", "секс", "горяч", "соск", "лучшая",
            "умница", "умничка", "пупсик", "котик", "краш", "шикарна", "богиня",
            "превосходн", "замечательн", "обалден"
    };

    // Слова-маркеры ситуативной ругани (ругается на обстоятельства/мир)
    private static final String[] SITUATION_MARKERS = {
            " я ", " тут ", " здесь ", " застрял", " упал", " помоги", " сдох",
            " капец", " блин", " пипец", " пиздец", " как ", " выбраться", " черт",
            " ебать", " хули", " где ", " почему ", " моб", " крипер", " зомби", " скелет",
            " паук", " лава", " вода", " умер", " шахт", " алмаз", " ресы"
    };

    // Ласковое поддразнивание и извинения (отменяют штраф кармы)
    private static final String[] TEASING_AND_APOLOGIES = {
            " дурочка ", " дурашка ", " глупенькая ", " балбес ", " вредина ", " злюка ", " глупыш ",
            " прости", " извини", " сорян", " виноват", " случайно", " не хотел", " сори ", " пардон "
    };

    public static boolean isInsult(String text) {
        if (text == null) return false;

        String s = " " + AnyaAIPrompts.normalize(text) + " ";

        // 1. Проверка на извинения и ласковое поддразнивание
        for (String word : TEASING_AND_APOLOGIES) {
            if (s.contains(word)) {
                return false;
            }
        }

        // Проверяем явные местоимения направления
        boolean hasDirectPronoun = s.contains(" ты ") || s.contains(" тебе ") || s.contains(" тебя ") ||
                s.contains(" тобой ") || s.contains(" твой ") || s.contains(" твоя ");
        boolean hasAnyaName = s.contains(" аня ") || s.contains(" ань ") || s.contains(" анечк");

        boolean directedAtAnya = hasDirectPronoun;

        // Третьи лица (если игрок говорит о ком-то/чем-то другом)
        boolean hasThirdPerson = s.contains(" он ") || s.contains(" она ") || s.contains(" они ") ||
                s.contains(" этот ") || s.contains(" эта ");

        // 2. Если есть имя, но нет прямого "ты", проверяем маркеры ситуации
        if (hasAnyaName && !directedAtAnya) {
            boolean situational = false;
            for (String marker : SITUATION_MARKERS) {
                if (s.contains(marker)) {
                    situational = true;
                    break;
                }
            }
            if (!situational) {
                directedAtAnya = true;
            }
        }

        // 3. УМНЫЙ ПЕРЕХВАТ коротких и средних фраз без "ты" (например: "какой шлюхой стала")
        if (!directedAtAnya && !hasThirdPerson) {
            String[] words = s.trim().split("\\s+");
            // Если слов до 6, нет третьего лица и нет ситуативных маркеров — это направлено Ане
            if (words.length <= 6 && words.length > 0) {
                boolean situational = false;
                for (String marker : SITUATION_MARKERS) {
                    if (s.contains(marker)) {
                        situational = true;
                        break;
                    }
                }
                if (!situational) {
                    directedAtAnya = true;
                }
            }
        }

        // Если не направлено на Аню — пропускаем
        if (!directedAtAnya) {
            return false;
        }

        // 4. Ищем корни оскорблений
        for (String root : INSULT_ROOTS) {
            if (s.contains(root)) return true;
        }

        return false;
    }

    public static boolean isCompliment(String text) {
        if (text == null) return false;

        String s = AnyaAIPrompts.normalize(text);

        for (String root : COMPLIMENT_ROOTS) {
            if (s.contains(root)) return true;
        }

        return false;
    }
}