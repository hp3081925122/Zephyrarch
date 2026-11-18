"""程序化生成罡风君主技能音效（风声、冲击、蓄力嗡鸣），输出 OGG。

用法：python tools/gen_sounds.py   （需要 numpy 和 PATH 里的 ffmpeg）
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "zephyrarch", "sounds")
rng = np.random.default_rng(20261118)


def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def svf(x, fc, q=0.7, mode="bp"):
    """时变状态变量滤波器。fc 可以是标量或与 x 等长的数组。"""
    fc = np.broadcast_to(np.asarray(fc, dtype=np.float64), x.shape)
    f = 2.0 * np.sin(np.pi * np.clip(fc, 20, SR * 0.45) / SR)
    damp = 1.0 / q
    low = band = 0.0
    out = np.empty_like(x)
    for i in range(len(x)):
        high = x[i] - low - damp * band
        band += f[i] * high
        low += f[i] * band
        out[i] = band if mode == "bp" else (low if mode == "lp" else high)
    return out


def env_ar(t, attack, release, curve=2.0):
    dur = t[-1] if len(t) else 1.0
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    r = np.clip((dur - t) / max(release, 1e-4), 0, 1)
    return a ** 0.7 * r ** curve


def env_peak(t, peak, dur):
    up = np.clip(t / peak, 0, 1) ** 1.6
    down = np.clip(1 - (t - peak) / max(dur - peak, 1e-4), 0, 1) ** 2.2
    return np.where(t < peak, up, down)


def noise(n):
    return rng.standard_normal(n)


def whoosh(dur, f0, f1, peak, q=1.2, bright=0.0):
    t = t_axis(dur)
    k = np.clip(t / dur, 0, 1)
    fc = f0 * (f1 / f0) ** k
    s = svf(noise(len(t)), fc, q, "bp")
    if bright:
        s += bright * svf(noise(len(t)), fc * 3, 2.0, "bp")
    return s * env_peak(t, peak, dur)


def boom(dur, f0, f1, noise_amt=0.6, lp=900):
    t = t_axis(dur)
    freq = f1 + (f0 - f1) * np.exp(-t * 9)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    tone = np.sin(phase) * np.exp(-t * 5.5)
    burst = svf(noise(len(t)), lp * np.exp(-t * 3) + 120, 0.8, "lp") * np.exp(-t * 7)
    click = svf(noise(len(t)), 3000, 1.0, "bp") * np.exp(-t * 60)
    return tone + noise_amt * burst * 2.5 + 0.5 * click


def hum(dur, f0, f1, harmonics=(1, 2, 3.01), trem=7.0):
    t = t_axis(dur)
    k = t / dur
    freq = f0 * (f1 / f0) ** (k ** 1.5)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    s = sum(np.sin(phase * h) / (i + 1) for i, h in enumerate(harmonics))
    s *= 0.75 + 0.25 * np.sin(2 * np.pi * trem * t * (1 + k))
    return s * np.clip(k * 1.2, 0, 1) ** 1.2


def whistle(dur, f0, f1, vib=9.0):
    t = t_axis(dur)
    k = t / dur
    freq = f0 + (f1 - f0) * np.sin(np.pi * k) + 25 * np.sin(2 * np.pi * vib * t)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase) * np.sin(np.pi * k) ** 1.5


def mix(*parts):
    n = max(len(p) for _, p in parts)
    out = np.zeros(n)
    for gain, p in parts:
        out[: len(p)] += gain * p
    return out


def pad(x, before):
    return np.concatenate([np.zeros(int(SR * before)), x])


def write(name, x, gain_db=-1.5):
    x = x - np.mean(x)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * (10 ** (gain_db / 20))
    fade = int(SR * 0.01)
    x[-fade:] *= np.linspace(1, 0, fade)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(OUT, name + ".ogg")], check=True)
    print("ok", name, round(len(x) / SR, 2), "s")


def build():
    # 近战
    write("punch_swing", whoosh(0.32, 500, 2600, 0.12, 1.4, 0.3))
    write("punch_impact", mix((1.0, boom(0.45, 140, 55, 0.8, 1400)), (0.6, whoosh(0.3, 1800, 500, 0.02, 0.9))))
    write("sweep_spin", mix((1.0, whoosh(0.75, 300, 1800, 0.45, 1.0, 0.4)), (0.5, whoosh(0.75, 900, 3200, 0.5, 2.5))))
    write("rush_charge", mix((1.0, whoosh(0.8, 2400, 400, 0.7, 1.5)), (0.35, hum(0.8, 70, 110))))
    write("rush_dash", mix((1.0, whoosh(0.55, 350, 1600, 0.08, 0.8, 0.6)), (0.6, boom(0.4, 90, 45, 0.4))))
    write("hammer_rise", mix((1.0, whoosh(0.6, 250, 1400, 0.5, 1.1)), (0.3, hum(0.6, 90, 160))))
    write("hammer_slam", mix((1.0, boom(1.1, 110, 32, 1.0, 700)), (0.5, pad(whoosh(0.8, 2000, 300, 0.03, 0.8), 0.02))))
    write("grab_catch", mix((1.0, boom(0.3, 220, 90, 0.5, 2500)), (0.5, whoosh(0.25, 600, 2000, 0.05, 1.5))))
    write("grab_throw", mix((1.0, whoosh(0.6, 400, 2400, 0.15, 1.0, 0.5)), (0.4, boom(0.35, 120, 60, 0.3))))
    write("stomp_pull", mix((1.0, whoosh(1.4, 180, 900, 1.3, 1.6, 0.3)), (0.45, hum(1.4, 45, 90, (1, 1.5, 2.02), 11))))
    write("stomp_burst", mix((1.0, boom(0.9, 95, 35, 1.0, 900)), (0.8, whoosh(0.7, 3000, 400, 0.02, 0.7))))
    write("phase_shift", mix((0.8, whoosh(2.0, 150, 2500, 1.15, 1.0, 0.5)), (0.5, hum(1.2, 55, 220, (1, 2, 2.99, 4.02), 6)),
                             (1.0, pad(boom(0.85, 120, 35, 1.0, 1200), 1.15))))
    write("wind_dash", whoosh(0.4, 1800, 400, 0.06, 1.0, 0.4))
    write("stagger", mix((1.0, whoosh(0.7, 900, 200, 0.05, 0.9)), (0.4, hum(0.7, 160, 70, (1, 2), 14))))
    # 远程
    write("bolt_shoot", mix((1.0, whoosh(0.28, 900, 2800, 0.04, 1.2, 0.4)), (0.4, boom(0.2, 300, 120, 0.2, 3000))))
    write("lance_charge", mix((1.0, hum(1.6, 80, 640, (1, 2.01, 3.0, 5.02), 9)), (0.5, whoosh(1.6, 300, 4000, 1.5, 3.0))))
    write("lance_fire", mix((1.0, boom(0.9, 260, 60, 0.9, 3000)), (0.8, whoosh(0.9, 4500, 600, 0.02, 0.8, 0.6)),
                            (0.4, hum(0.5, 900, 300, (1, 1.5), 30)[::-1])))
    write("twister_spawn", mix((1.0, whoosh(0.9, 250, 1300, 0.35, 2.2, 0.3)), (0.4, hum(0.9, 110, 70, (1, 2.03), 12))))
    write("downdraft_impact", mix((1.0, boom(0.6, 150, 50, 0.9, 1100)), (0.7, pad(whoosh(0.25, 600, 3500, 0.22, 1.0), 0.0))))
    write("blade_throw", mix((1.0, whoosh(0.7, 1200, 3500, 0.3, 4.0)), (0.35, whistle(0.7, 1400, 2400))))
    write("cage_form", mix((1.0, whoosh(1.5, 200, 2200, 1.2, 1.2, 0.5)), (0.5, hum(1.5, 60, 180, (1, 1.5, 2.0), 5)),
                           (0.6, pad(boom(0.5, 100, 45, 0.6), 1.0))))
    write("orb_shoot", mix((1.0, hum(0.5, 420, 180, (1, 2.0), 18)), (0.6, whoosh(0.5, 2000, 600, 0.05, 1.5))))


if __name__ == "__main__":
    build()
