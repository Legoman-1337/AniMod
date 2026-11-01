"""Пересборка боссовых сонгов:
- anya_boss_stage-3: из этапа 1 — темнее и агрессивнее (тон -3 пт, темп +8%, саб-гул).
- anya_boss_stage-2: осветлить (глуховатый) — поднять середину/верх, нормализовать.
"""
import numpy as np
import soundfile as sf
import os
import threading


DIR = "src/main/resources/assets/animod/sounds"

def load(path):
    data, sr = sf.read(path, always_2d=True)
    return data, sr

def save(path, data, sr):
    peak = np.max(np.abs(data)) or 1.0
    data = (data / peak * 0.95).astype(np.float32)
    with sf.SoundFile(path, "w", samplerate=sr, channels=data.shape[1], format="OGG", subtype="VORBIS") as f:
        CH = 1 << 20
        for i in range(0, len(data), CH):
            f.write(data[i:i + CH])

def resample(data, sr, factor):
    # простая линейная интерполяция
    n = int(len(data) / factor)
    idx = np.linspace(0, len(data) - 1, n)
    out = np.zeros((n, data.shape[1]))
    for ch in range(data.shape[1]):
        out[:, ch] = np.interp(idx, np.arange(len(data)), data[:, ch])
    return out

import threading

threading.stack_size(64 * 1024 * 1024)

def run():
    # ---- ЭТАП 3: из этапа 1 ----
    d1, sr = load(f"{DIR}/anya_boss_stage-1.ogg")
    # тон ниже в 1.19x (~ -3 полутона), затем темп плотнее: растянем обратно на 0.92 по длине
    d3 = resample(d1, sr, 1.19)
    d3 = resample(d3, sr, 1 / 1.08)
    # саб-гул: синус 55 Гц, качается
    t = np.arange(len(d3)) / sr
    sub = (0.10 * np.sin(2 * np.pi * 55 * t) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.15 * t)))[:, None]
    d3 = d3 * 0.9 + sub
    save(f"{DIR}/anya_boss_stage-3.ogg", d3, sr)
    print("stage-3 rebuilt:", len(d3) / sr, "sec")

    # ---- ЭТАП 2: осветлить глуховатый ----
    d2, sr2 = load(f"{DIR}/anya_boss_stage-2.ogg")
    # лёгкий highshelf через простой фильтр: добавить сглаженную производную (поднятие верхов)
    hp = np.diff(d2, axis=0, prepend=d2[:1]) * 0.35
    d2 = d2 * 0.85 + hp
    d2 = resample(d2, sr2, 1.0)  # no-op, оставляем длину
    save(f"{DIR}/anya_boss_stage-2.ogg", d2, sr2)
    print("stage-2 remastered:", len(d2) / sr2, "sec")

    def _main():
        pass  # весь код выше выполняется в большом потоке

    # весь модуль уже внутри потока с большим стеком — см. ниже

threading.stack_size(64 * 1024 * 1024)
t = threading.Thread(target=run)
t.start()
t.join()
print('DONE')
