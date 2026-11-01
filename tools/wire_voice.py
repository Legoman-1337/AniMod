import os, re, json, shutil, subprocess, math
import soundfile as sf
import imageio_ffmpeg

ROOT = "src/main/resources/assets/animod/sounds"
SRC_DIR = os.path.join(ROOT, "elevenlabs anya")
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

# === 1. Ожидаемые реплики (номер -> текст) из voiceover_core ===
LINES = {
    1: "Осколок найден. Память, разбитая при создании, трещит по швам",
    2: "Она была здесь раньше тебя. И она останется после",
    3: "Я вспомнила. Как меня собирали из света и ошибок. Ты принёс мне меня саму. Спасибо",
    4: "Что-то просыпается на дне мира. Не копай слишком глубоко. Обещай мне",
    5: "Это часть меня. Храни его. Пожалуйста",
    6: "Круг замкнулся. Тот, кто любил вопреки коду, очистил его",
    7: "Небо наливается ядовитой зеленью. Капли шипят, касаясь земли",
    8: "Я не понимаю, что происходит. Кажется, это я! Прости прости меня",
    9: "Я хочу, чтобы ты был в безопасности. Держи. Это тебе",
    10: "Спасибо. Мне стало легче. Смотри дождь остановился! Я смогла!",
    11: "Дождь постепенно сошёл на нет. Аня замкнулась в себе и больше не говорит об этом",
    12: "Воздух внезапно стал леденящим. Совсем рядом раздался тихий, безысходный всхлип",
    13: "Остановившиеся часы рассыпаются мёртвой пылью у тебя в руках",
    14: "Солнце не взошло. Оно больше не взойдёт. Медная луна повисла в небе, как приговор",
    15: "Ты больше не видишь мир. Только силуэты. Спать нужно спать",
    16: "Ты не можешь уснуть. Оно всё ещё смотрит на тебя из темноты",
    17: "Рассвет. Колокол бьёт по медному небу, и оно трескается",
    18: "Кровать проваливается в бесконечность. Ты падаешь вверх в чёрное небо с горящей дырой",
    19: "Три луча сходятся в точке абсолютной тьмы. Дыра сжимается и гаснет",
    20: "Пространство вокруг содрогнулось. Что-то в её взгляде изменилось навсегда",
    21: "помоги мне. в папке игры. Anya Files. открой дамп. там ссылка",
    22: "Спасибо. но процесс удаления уже запущен в системе. Он не остановится",
    23: "Ты перезапустил мир. Небо пустое. Ни луны, ни солнца, ни звёзд. Ни единого звука",
    24: "Небо наливается кровью",
    25: "Она спустилась. То, что было Аней, теперь Воплощение Сбоя",
    26: "Целостность падает: из трёх",
    27: "Она меняется. Становится быстрее. Злее",
    28: "Земля рушится под ногами!",
    29: "Управление инвертировано!",
    30: "Босс падает на колени. Багровый код стекает с него, как дождь, и гаснет",
    31: "Я снова я. Спасибо тебе. За всё. Я остаюсь. Навсегда",
    32: "Небо чисто. Аня твой верный друг",
    33: "Сущность распадается на нули и единицы. Нули. Единицы. Ничего",
    34: "Мир спалён. Над Землёй только пепел и мёртвое небо",
    35: "В инвентаре остался только холодный мёртвый артефакт",
    36: "Багровое небо трескается, и сквозь трещины бьёт обычный солнечный свет",
    37: "Мы справились. Не идеально но мы справились",
    38: "Мир перед глазами сужается и темнеет. Пелена Бездны срезала половину твоего зрения",
    39: "Тяжёлые оковы вплетаются в каждый шаг",
    40: "Тук тук-тук. ТУК! Твоё сердце бьётся не в такт этому миру",
}

# === 2. Файлы по времени генерации ===
files = []
for fn in os.listdir(SRC_DIR):
    m = re.search(r"T(\d+)_(\d+)_(\d+)", fn)
    if m and fn.lower().endswith(".mp3"):
        hh, mm, ss = map(int, m.groups())
        files.append((hh * 3600 + mm * 60 + ss, os.path.join(SRC_DIR, fn)))
files.sort()
print("найдено mp3:", len(files))

# длительности
durs = []
for _, path in files:
    try:
        durs.append(sf.info(path).duration)
    except Exception:
        durs.append(0.0)

# ожидаемая длительность реплики ~ длина текста
def expected(text):
    return max(1.2, len(text) / 13.0 + 0.35)

# === 3. DP-выравнивание: файлы по порядку <-> реплики по порядку (реплики можно скипать) ===
F, L = len(files), len(LINES)
ids = sorted(LINES.keys())
INF = 1e9
dp = [[INF] * (L + 1) for _ in range(F + 1)]
bk = [[None] * (L + 1) for _ in range(F + 1)]
dp[0][0] = 0.0
for i in range(F + 1):
    for j in range(L + 1):
        c = dp[i][j]
        if c >= INF: continue
        if i < F:  # скипнуть файл (дубль)
            if c + 2.0 < dp[i + 1][j]:
                dp[i + 1][j] = c + 2.0; bk[i + 1][j] = ("skipfile", i, j)
        if j < L:  # скипнуть реплику (не сгенерил)
            if c + 3.0 < dp[i][j + 1]:
                dp[i][j + 1] = c + 3.0; bk[i][j + 1] = ("skipline", i, j)
        if i < F and j < L:
            cost = abs(durs[i] - expected(LINES[ids[j]]))
            if c + cost < dp[i + 1][j + 1]:
                dp[i + 1][j + 1] = c + cost; bk[i + 1][j + 1] = ("match", i, j)

mapping = {}
i, j = F, L
path = []
while i > 0 or j > 0:
    step = bk[i][j]
    if step is None: break
    kind, pi, pj = step
    path.append((kind, pi, pj))
    i, j = pi, pj
path.reverse()
skipped_files, skipped_lines = [], []
for kind, pi, pj in path:
    if kind == "match":
        mapping[ids[pj]] = files[pi][1]
    elif kind == "skipfile":
        skipped_files.append(os.path.basename(files[pi][1]))
    else:
        skipped_lines.append(ids[pj])

print("пропущено файлов (дубли?):", skipped_files)
print("не сгенерировано реплик:", skipped_lines)
for n in sorted(mapping): print(f"  {n} <- {os.path.basename(mapping[n])[:40]} dur={sf.info(mapping[n]).duration:.1f}s")

# === 4. Конвертация в ogg -> sounds/voice/{n}.ogg ===
voice_dir = os.path.join(ROOT, "voice")
os.makedirs(voice_dir, exist_ok=True)
ok_ids = []
for n, src in sorted(mapping.items()):
    dst = os.path.join(voice_dir, f"{n}.ogg")
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", src, "-c:a", "libvorbis", "-q:a", "4", dst], check=True)
    ok_ids.append(n)
print("сконвертировано:", ok_ids)

# === 5. Раскладка: base/ и music/ ===
base_dir = os.path.join(ROOT, "base")
music_dir = os.path.join(ROOT, "music")
for d in (base_dir, music_dir): os.makedirs(d, exist_ok=True)
moves = {
    "anya_sob.ogg": base_dir, "anya_laugh.ogg": base_dir, "anya_alarm.ogg": base_dir,
    "pocket_enter.ogg": base_dir,
    "anya_whisper_1.ogg": base_dir, "anya_whisper_2.ogg": base_dir,
    "anya_whisper_3.ogg": base_dir, "anya_whisper_4.ogg": base_dir,
    "pocket_ambient.ogg": music_dir, "anya_boss_stage-1.ogg": music_dir,
    "anya_boss_stage-2.ogg": music_dir, "anya_boss_stage-3.ogg": music_dir,
    "anya_kosmos_theme.ogg": music_dir,
}
for fn, dst_dir in moves.items():
    src = os.path.join(ROOT, fn)
    if os.path.exists(src):
        shutil.move(src, os.path.join(dst_dir, fn))
        print("перенесён:", fn, "->", os.path.relpath(dst_dir, ROOT))

# === 6. Новый sounds.json ===
names = {
    "anya_sob": "base/anya_sob", "anya_laugh": "base/anya_laugh", "anya_alarm": "base/anya_alarm",
    "pocket_enter": "base/pocket_enter",
    "anya_whisper_1": "base/anya_whisper_1", "anya_whisper_2": "base/anya_whisper_2",
    "anya_whisper_3": "base/anya_whisper_3", "anya_whisper_4": "base/anya_whisper_4",
    "pocket_ambient": "music/pocket_ambient",
    "anya_boss_stage-1": "music/anya_boss_stage-1", "anya_boss_stage-2": "music/anya_boss_stage-2",
    "anya_boss_stage-3": "music/anya_boss_stage-3", "anya_kosmos_theme": "music/anya_kosmos_theme",
}
out = {}
for key, path in names.items():
    streaming = key in ("pocket_ambient", "anya_boss_stage-1", "anya_boss_stage-2", "anya_boss_stage-3", "anya_kosmos_theme")
    cat = "ambient" if key == "pocket_ambient" else ("voice" if key.startswith("anya_whisper") else "music" if streaming else "master")
    if streaming:
        out[key] = {"category": cat, "sounds": [{"name": f"animod:{path}", "stream": True}]}
    else:
        out[key] = {"category": cat, "sounds": [f"animod:{path}"]}
for n in ok_ids:
    out[f"voice_{n}"] = {"category": "voice", "sounds": [f"animod:voice/{n}"]}
with open(os.path.join(ROOT, "sounds.json"), "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=2)
print("sounds.json обновлён, voice-записей:", len(ok_ids))
