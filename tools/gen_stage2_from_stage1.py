import os
import subprocess

import numpy as np
import soundfile as sf
import imageio_ffmpeg

SRC = "src/main/resources/assets/animod/sounds/anya_boss_stage-1.ogg"
DST = "src/main/resources/assets/animod/sounds/anya_boss_stage-2.ogg"
TMP_WAV = "tools/tmp_stage2.wav"

data, sr = sf.read(SRC, always_2d=True)
mono = data.mean(axis=1)

# 1) Замедление + понижение тона (тема «тонет»)
factor = 0.78
n_new = int(len(mono) / factor)
slow = np.interp(np.linspace(0, len(mono) - 1, n_new), np.arange(len(mono)), mono)

# 2) Глушим верха: жёсткий lowpass (тёмное, давящее)
k = 40
slow = np.convolve(slow, np.ones(k) / k, mode="same")

# 3) Гул-подложка: детюненные суб-басы (биения)
t = np.arange(n_new) / sr
drone = 0.5 * np.sin(2 * np.pi * 41.0 * t) + 0.5 * np.sin(2 * np.pi * 41.7 * t)
drone *= 0.6 + 0.4 * np.sin(2 * np.pi * 0.05 * t)

# 4) Редкие глубокие удары — сердцебиение в материале трека
thump = np.zeros(n_new)
for start in range(int(4 * sr), n_new - int(2 * sr), int(5.8 * sr)):
    d = int(0.8 * sr)
    tt = np.arange(d) / sr
    thump[start:start + d] += 0.8 * np.sin(2 * np.pi * 33 * tt) * np.exp(-tt * 6.0)

# 5) Фейды + нормализация под громкость этапа 1
fade = np.ones(n_new)
head = int(3 * sr)
fade[:head] = np.linspace(0, 1, head)
tail = int(5 * sr)
fade[-tail:] = np.linspace(1, 0, tail)

mix = (slow * 1.15 + drone * 0.45 + thump) * fade
peak_src = np.abs(mono).max()
mix = mix / (np.abs(mix).max() + 1e-9) * peak_src * 0.95

# WAV (libsndfile пишет его стабильно), затем ffmpeg -> ogg
sf.write(TMP_WAV, mix.astype(np.float32), sr, format="WAV", subtype="FLOAT")
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", TMP_WAV,
                "-c:a", "libvorbis", "-q:a", "5", DST], check=True)
os.remove(TMP_WAV)

info = sf.info(DST)
print("OK:", round(info.duration, 1), "сек,", os.path.getsize(DST) // 1024, "КБ")
