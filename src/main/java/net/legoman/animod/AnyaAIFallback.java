package net.legoman.animod;

public class AnyaAIFallback {
    public static String get(int value,String userText,String item,String mobs,String time,String biome,float health,String lastUniqueResponse){
        String u=AnyaAIPrompts.normalize(userText);

        // Отработка простейших приветствий без привлечения ИИ
        if(AnyaAIPrompts.isSimpleSocialMessage(u)){
            String[][] variants={
                    {"Ку... Я здесь.","Привет. Я слушаю.","Ку. Звал меня?","Я здесь. Что случилось?","Ку. Я тебя слышу.","Привет. Говори."},
                    {"Ку. Я тебя слышу.","Привет. Ну, я здесь.","О, привет.","Ку. Что тебе нужно?","Привет. Я слушаю.","Ну, привет."},
                    {"Ку. Чего хотел?","Привет. Говори.","Ку. Я слушаю тебя.","Ну? Я здесь.","Привет. Я тебя слышу.","Ку. Что случилось?"},
                    {"Привет. Рада тебя видеть.","Ку. Я рядом.","Привет. Хорошо, что позвал.","Ку. Рада тебя слышать.","Привет. Я здесь.","О, привет. Я слушаю."},
                    {"Привет. Я с тобой.","Ку. Рада тебя слышать.","О, ты здесь. Привет.","Привет. Я уже слушаю.","Ку. Я рядом.","Ну привет. Я здесь."},
                    {"Ку... Наконец-то.","Привет. Я уже слушаю.","Ку. Не исчезай надолго.","О, ты пришёл. Привет.","Ку. Я ждала.","Привет. Теперь ты здесь."}};

            int group=value<20?0:value<30?1:value<50?2:value<75?3:value<90?4:5;
            String[] pool=variants[group];
            int start=(int)(System.nanoTime()%pool.length);

            for(int i=0;i<pool.length;i++){
                String candidate=pool[(start+i)%pool.length];
                if(!candidate.equalsIgnoreCase(lastUniqueResponse)) return candidate;
            }
            return pool[start];
        }

        // Крайний резервный ответ, если все 3 попытки генерации провалились
        if(value<20)return "Я здесь. Говори.";
        if(value<30)return "Пф... Я слушаю.";
        if(value<50)return "Ну? Я слушаю.";
        if(value<75)return "Я слушаю тебя.";
        if(value<90)return "Я рядом и слушаю тебя.";
        return "Я слушаю тебя. Я никуда не делась.";
    }
}