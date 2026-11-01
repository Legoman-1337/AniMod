import numpy as np
import soundfile as sf

sr = 22050
dur = 70.0
n = int(sr * dur)
t = np.arange(n) / sr

# 1) Суб-бас с биениями (два детюненных синуса — «дыхание» гула)
sub = 0.55 * np.sin(2 * np.pi * 41.0 * t) + 0.55 * np.sin(2 * np.pi * 41.7 * t)
# 2) Средний слой: минорная терция, медленно сползающая вниз (dread)
f2 = 82.4 - 3.0 * (t / dur)
mid = 0.30 * np.sin(2 * np.pi * f2 * t) + 0.22 * np.sin(2 * np.pi * (f2 * 1.189) * t)
mid *= 0.7 + 0.3 * np.sin(2 * np.pi * 0.07 * t)
# 3) Тёмный шумовой пласт (глухой рокот)
rng = np.random.default_rng(404)
noise = rng.standard_normal(n)
k = 180
noise = np.convolve(noise, np.ones(k) / k, mode="same")
noise = 0.9 * noise / (np.abs(noise).max() + 1e-9)
# 4) Редкие глубокие удары — «сердцебиение» каждые ~6.5 сек
thump = np.zeros(n)
for start in range(int(3 * sr), n - int(2 * sr), int(6.5 * sr)):
    d = int(0.9 * sr)
    tt = np.arange(d) / sr
    thump[start:start + d] += 0.9 * np.sin(2 * np.pi * 34 * tt) * np.exp(-tt * 5.5)
# 5) Вступление из тишины и финальный провал (готовность к фазе 3)
fade = np.ones(n)
head = int(4 * sr)
fade[:head] = np.linspace(0, 1, head)
tail = int(8 * sr)
fade[-tail:] = np.linspace(1, 0, tail)

mix = (sub + mid + noise * 0.35 + thump) * fade
mix = mix / (np.abs(mix).max() + 1e-9) * 0.85
sf.write("src/main/resources/assets/animod/sounds/anya_boss_stage-2.ogg",
         mix.astype(np.float32), sr, format="OGG", subtype="VORBIS")
print("anya_boss_stage-2.ogg OK,", dur, "sec")
