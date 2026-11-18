import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "zephyrarch", "sounds")
rng = np.random.default_rng(20261119)


def t_axis(dur):
    return np.arange(max(1, int(SR * dur)), dtype=np.float64) / SR


def svf(x, fc, q=0.8, mode="bp"):
    n = len(x)
    fc_arr = np.broadcast_to(np.asarray(fc, dtype=np.float64), (n,))
    f = 2.0 * np.sin(np.pi * np.clip(fc_arr, 22.0, SR * 0.44) / SR)
    damp = 1.0 / max(q, 0.25)
    low = 0.0
    band = 0.0
    out = np.empty(n, dtype=np.float64)
    if mode == "bp":
        for i in range(n):
            fi = f[i]
            high = x[i] - low - damp * band
            band += fi * high
            low += fi * band
            out[i] = band
    elif mode == "lp":
        for i in range(n):
            fi = f[i]
            high = x[i] - low - damp * band
            band += fi * high
            low += fi * band
            out[i] = low
    else:
        for i in range(n):
            fi = f[i]
            high = x[i] - low - damp * band
            band += fi * high
            low += fi * band
            out[i] = high
    return out


def pink_noise(n):
    white = rng.standard_normal(n)
    spec = np.fft.rfft(white)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    scale = 1.0 / np.maximum(np.sqrt(freqs), 1.0)
    scale[0] = 0.0
    y = np.fft.irfft(spec * scale, n=n)
    return y / (np.std(y) + 1e-8)


def env_peak(t, peak_t, dur, up_pow=1.3, down_pow=2.0):
    peak_t = max(peak_t, 1e-4)
    up = np.clip(t / peak_t, 0.0, 1.0) ** up_pow
    down = np.clip(1.0 - (t - peak_t) / max(dur - peak_t, 1e-4), 0.0, 1.0) ** down_pow
    return np.where(t < peak_t, up, down)


def sub_kick(dur, f_top=220.0, f_sub=38.0, drop=18.0, decay=5.5, drive=2.2):
    t = t_axis(dur)
    freq = f_sub + (f_top - f_sub) * np.exp(-t * drop)
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    raw = np.sin(phase) + 0.35 * np.sin(2.0 * phase)
    sat = np.tanh(raw * drive)
    amp = (1.0 - np.exp(-t * 900.0)) * np.exp(-t * decay)
    return sat * amp


def impact_crack(dur, body_fc=520.0, snap_fc=3400.0, grit=1.8):
    t = t_axis(dur)
    n = len(t)
    snap = svf(rng.standard_normal(n), snap_fc * np.exp(-t * 28.0) + 600.0, 1.1, "bp") * np.exp(-t * 52.0)
    thud = svf(pink_noise(n), body_fc * np.exp(-t * 9.0) + 95.0, 0.9, "lp") * np.exp(-t * 11.0)
    crunch = np.tanh((1.3 * snap + 2.2 * thud) * grit)
    return crunch


def gale_roar(dur, f0, f1, peak_t, q=1.3, air_gain=0.45, gust_hz=13.0):
    t = t_axis(dur)
    n = len(t)
    k = np.clip(t / max(dur, 1e-4), 0.0, 1.0)
    fc = f0 * ((f1 / max(f0, 1.0)) ** k)
    pn = pink_noise(n)
    gust_mod = 0.78 + 0.22 * np.sin(2.0 * np.pi * gust_hz * t + rng.uniform(0, 6.28))
    low_body = svf(pn, np.clip(fc * 0.48, 70.0, 900.0), 0.9, "bp")
    mid_jet = svf(rng.standard_normal(n), fc, q, "bp")
    hi_shear = svf(rng.standard_normal(n), np.clip(fc * 2.7, 900.0, 8500.0), 1.8, "bp")
    sig = (1.35 * low_body + 1.15 * mid_jet + air_gain * hi_shear) * gust_mod
    return np.tanh(sig * 1.45) * env_peak(t, peak_t, dur, 1.25, 1.85)


def blade_edge(dur, f0, f1, peak_t, shimmer_hz=26.0):
    t = t_axis(dur)
    k = np.clip(t / max(dur, 1e-4), 0.0, 1.0)
    freq = f0 + (f1 - f0) * np.sin(np.pi * (k ** 0.8))
    vib = 1.0 + 0.025 * np.sin(2.0 * np.pi * shimmer_hz * t)
    ph = 2.0 * np.pi * np.cumsum(freq * vib) / SR
    metallic = 0.65 * np.sin(ph) + 0.35 * np.sin(ph * 2.41) + 0.20 * np.sin(ph * 3.83)
    shear = svf(rng.standard_normal(len(t)), np.clip(freq * 1.5, 800.0, 7500.0), 3.2, "bp")
    return np.tanh((0.55 * metallic + 1.1 * shear) * 1.6) * env_peak(t, peak_t, dur, 1.1, 1.9)


def arcane_power(dur, f0, f1, ratios=(1.0, 1.5, 2.01, 3.02), pulse_hz=9.0, exp_curve=1.35):
    t = t_axis(dur)
    k = np.clip(t / max(dur, 1e-4), 0.0, 1.0)
    freq = f0 * ((f1 / max(f0, 1.0)) ** (k ** exp_curve))
    ph = 2.0 * np.pi * np.cumsum(freq) / SR
    acc = np.zeros_like(t)
    for idx, r in enumerate(ratios):
        detune = 1.0 + (idx - 1.5) * 0.0035
        acc += (np.sin(ph * r * detune) + 0.4 * np.sin(ph * r * 2.0)) / (1.0 + idx * 0.55)
    trem = 0.72 + 0.28 * np.sin(2.0 * np.pi * pulse_hz * (1.0 + 1.4 * k) * t)
    env = (np.clip(k * 1.25, 0.0, 1.0) ** 1.1) * np.clip((1.0 - k) * 9.0, 0.0, 1.0)
    return np.tanh(acc * 1.35) * trem * env


def add_space_tail(x, room_scale=1.0, wet=0.26):
    taps_ms = [17.0, 29.0, 43.0, 61.0, 89.0, 127.0]
    gains = [0.48, 0.38, 0.30, 0.23, 0.17, 0.12]
    extra = int(SR * 0.22 * room_scale)
    out = np.pad(x, (0, extra))
    tail = np.zeros_like(out)
    for ms, g in zip(taps_ms, gains):
        d = int(SR * (ms * room_scale) / 1000.0)
        if 0 < d < len(out):
            tail[d:] += g * out[:-d]
    tail = svf(tail, 1800.0, 0.7, "lp")
    return out + wet * tail


def mix(*parts):
    n = max(len(p) for _, p in parts)
    out = np.zeros(n, dtype=np.float64)
    for gain, p in parts:
        out[:len(p)] += gain * p
    return out


def pad(x, delay_s):
    d = max(0, int(SR * delay_s))
    return np.concatenate([np.zeros(d, dtype=np.float64), x])


def write_ogg(name, x, drive=1.55, ceiling_db=-0.8):
    x = x - np.mean(x)
    x = svf(x, 26.0, 0.707, "hp")
    rms = np.sqrt(np.mean(x * x)) + 1e-8
    x = x / (rms * 3.0)
    x = np.tanh(x * drive)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * (10.0 ** (ceiling_db / 20.0))

    fade_in = min(len(x) // 8, int(SR * 0.002))
    fade_out = min(len(x) // 4, int(SR * 0.018))
    if fade_in > 1:
        x[:fade_in] *= np.linspace(0.0, 1.0, fade_in)
    if fade_out > 1:
        x[-fade_out:] *= np.linspace(1.0, 0.0, fade_out)

    pcm = (np.clip(x, -1.0, 1.0) * 32767.0).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "6",
             os.path.join(OUT, name + ".ogg")],
            check=True
        )
    print(f"generated {name}.ogg ({len(x) / SR:.2f}s)")


def build():
    write_ogg("punch_swing", mix(
        (1.15, gale_roar(0.30, 340, 2400, 0.07, 1.3, 0.55, 18)),
        (0.75, blade_edge(0.26, 750, 2900, 0.06, 28)),
        (0.65, sub_kick(0.22, 175, 52, 22, 9.5, 1.8))
    ), drive=1.5)

    write_ogg("punch_impact", add_space_tail(mix(
        (1.35, sub_kick(0.48, 260, 38, 22, 5.8, 2.5)),
        (1.15, impact_crack(0.38, 620, 3800, 2.2)),
        (0.80, gale_roar(0.36, 2200, 320, 0.025, 1.0, 0.5, 20))
    ), 0.85, 0.24), drive=1.75)

    write_ogg("sweep_spin", add_space_tail(mix(
        (1.15, gale_roar(0.76, 220, 2100, 0.22, 1.1, 0.65, 22)),
        (0.95, blade_edge(0.72, 680, 3200, 0.20, 34)),
        (0.90, pad(sub_kick(0.45, 210, 42, 18, 5.5, 2.1), 0.14)),
        (0.75, pad(impact_crack(0.32, 540, 3200, 1.8), 0.15))
    ), 1.0, 0.25), drive=1.65)

    write_ogg("rush_charge", mix(
        (1.05, gale_roar(0.78, 1900, 260, 0.66, 1.5, 0.4, 15)),
        (0.85, arcane_power(0.78, 58, 148, (1.0, 1.5, 2.02, 3.01), 12, 1.5)),
        (0.55, pad(sub_kick(0.22, 150, 48, 20, 9.0, 1.7), 0.56))
    ), drive=1.45)

    write_ogg("rush_dash", add_space_tail(mix(
        (1.30, sub_kick(0.48, 240, 40, 24, 6.0, 2.3)),
        (1.10, impact_crack(0.30, 680, 4200, 2.0)),
        (1.15, gale_roar(0.56, 280, 2600, 0.06, 0.95, 0.7, 24)),
        (0.65, blade_edge(0.45, 900, 3400, 0.07, 30))
    ), 0.9, 0.22), drive=1.7)

    write_ogg("hammer_rise", mix(
        (1.10, gale_roar(0.62, 180, 1750, 0.42, 1.15, 0.5, 16)),
        (0.80, sub_kick(0.35, 190, 46, 18, 7.0, 1.9)),
        (0.70, arcane_power(0.62, 72, 196, (1.0, 2.0, 3.02), 10, 1.3))
    ), drive=1.5)

    write_ogg("hammer_slam", add_space_tail(mix(
        (1.55, sub_kick(1.05, 310, 31, 20, 3.6, 2.8)),
        (1.35, impact_crack(0.65, 480, 3600, 2.5)),
        (0.95, pad(sub_kick(0.75, 130, 30, 10, 4.2, 2.2), 0.035)),
        (0.90, pad(gale_roar(0.88, 2600, 220, 0.03, 0.85, 0.6, 16), 0.015))
    ), 1.35, 0.34), drive=1.85)

    write_ogg("grab_catch", mix(
        (1.20, impact_crack(0.28, 720, 4100, 2.1)),
        (1.10, sub_kick(0.30, 230, 54, 24, 8.0, 2.1)),
        (0.75, gale_roar(0.26, 600, 2200, 0.04, 1.4, 0.5, 20))
    ), drive=1.65)

    write_ogg("grab_throw", add_space_tail(mix(
        (1.20, gale_roar(0.58, 280, 2900, 0.09, 1.05, 0.68, 22)),
        (1.05, sub_kick(0.42, 220, 44, 20, 6.5, 2.2)),
        (0.85, blade_edge(0.50, 820, 3100, 0.10, 28)),
        (0.70, impact_crack(0.25, 580, 3500, 1.8))
    ), 0.9, 0.22), drive=1.65)

    write_ogg("stomp_pull", mix(
        (1.10, gale_roar(1.38, 150, 1150, 1.22, 1.5, 0.42, 14)),
        (0.95, arcane_power(1.38, 44, 108, (1.0, 1.5, 2.01, 2.99), 13, 1.45)),
        (0.75, pad(sub_kick(0.32, 160, 38, 16, 7.5, 1.9), 1.05))
    ), drive=1.5)

    write_ogg("stomp_burst", add_space_tail(mix(
        (1.50, sub_kick(0.95, 290, 33, 21, 4.0, 2.7)),
        (1.25, impact_crack(0.55, 560, 4200, 2.4)),
        (1.05, gale_roar(0.80, 3200, 260, 0.025, 0.8, 0.72, 19)),
        (0.65, blade_edge(0.55, 2400, 580, 0.04, 32))
    ), 1.25, 0.32), drive=1.8)

    write_ogg("phase_shift", add_space_tail(mix(
        (0.95, gale_roar(1.95, 140, 2600, 1.02, 1.1, 0.65, 15)),
        (0.95, arcane_power(1.05, 52, 240, (1.0, 1.5, 2.0, 3.01, 4.02), 11, 1.5)),
        (1.60, pad(sub_kick(0.95, 320, 30, 22, 3.8, 2.8), 1.02)),
        (1.35, pad(impact_crack(0.60, 620, 4500, 2.5), 1.02)),
        (0.95, pad(arcane_power(0.90, 220, 110, (1.0, 1.498, 2.01), 7, 1.0), 1.04))
    ), 1.4, 0.35), drive=1.8)

    write_ogg("wind_dash", mix(
        (1.15, impact_crack(0.18, 780, 4600, 1.9)),
        (1.10, gale_roar(0.38, 2400, 320, 0.035, 1.0, 0.65, 24)),
        (0.85, sub_kick(0.26, 210, 50, 24, 9.0, 2.0))
    ), drive=1.6)

    write_ogg("stagger", add_space_tail(mix(
        (1.25, impact_crack(0.36, 520, 2900, 2.2)),
        (1.10, sub_kick(0.45, 190, 42, 18, 6.2, 2.2)),
        (0.85, arcane_power(0.68, 190, 62, (1.0, 1.41, 2.07), 18, 0.85)),
        (0.75, gale_roar(0.62, 1200, 190, 0.04, 1.0, 0.35, 16))
    ), 0.9, 0.22), drive=1.6)

    write_ogg("bolt_shoot", mix(
        (1.20, impact_crack(0.20, 820, 4400, 2.0)),
        (1.05, sub_kick(0.24, 280, 68, 26, 10.5, 2.1)),
        (1.00, gale_roar(0.28, 780, 3100, 0.035, 1.25, 0.65, 26)),
        (0.55, blade_edge(0.24, 1200, 2800, 0.04, 32))
    ), drive=1.65)

    write_ogg("lance_charge", mix(
        (1.15, arcane_power(1.55, 68, 560, (1.0, 1.5, 2.01, 3.02, 4.03), 12, 1.55)),
        (0.85, gale_roar(1.55, 220, 3600, 1.42, 1.8, 0.65, 18)),
        (0.75, pad(blade_edge(0.45, 1400, 4200, 0.36, 38), 1.08))
    ), drive=1.55)

    write_ogg("lance_fire", add_space_tail(mix(
        (1.55, sub_kick(0.88, 340, 34, 24, 4.2, 2.8)),
        (1.40, impact_crack(0.52, 740, 5200, 2.5)),
        (1.15, gale_roar(0.85, 4600, 340, 0.02, 0.85, 0.8, 26)),
        (0.95, blade_edge(0.72, 3600, 520, 0.03, 42)),
        (0.65, arcane_power(0.65, 440, 85, (1.0, 1.5, 2.02), 24, 0.8))
    ), 1.35, 0.34), drive=1.85)

    write_ogg("twister_spawn", add_space_tail(mix(
        (1.15, gale_roar(0.82, 200, 1650, 0.18, 1.45, 0.55, 20)),
        (0.85, sub_kick(0.42, 185, 48, 18, 6.5, 1.9)),
        (0.65, blade_edge(0.72, 620, 1950, 0.22, 24))
    ), 0.95, 0.24), drive=1.55)

    write_ogg("downdraft_impact", add_space_tail(mix(
        (0.90, gale_roar(0.16, 2800, 520, 0.12, 1.2, 0.65, 24)),
        (1.45, pad(sub_kick(0.62, 280, 36, 22, 5.0, 2.6), 0.06)),
        (1.25, pad(impact_crack(0.42, 580, 4000, 2.3), 0.06)),
        (0.95, pad(gale_roar(0.52, 2200, 280, 0.02, 0.9, 0.55, 18), 0.06))
    ), 1.15, 0.28), drive=1.75)

    write_ogg("blade_throw", mix(
        (1.25, blade_edge(0.62, 1100, 3900, 0.08, 38)),
        (1.05, gale_roar(0.56, 480, 2900, 0.07, 1.3, 0.7, 26)),
        (0.85, impact_crack(0.22, 760, 4800, 1.9)),
        (0.70, sub_kick(0.26, 210, 58, 22, 9.0, 1.9))
    ), drive=1.65)

    write_ogg("cage_form", add_space_tail(mix(
        (1.10, gale_roar(1.48, 180, 2400, 0.98, 1.15, 0.65, 18)),
        (0.95, arcane_power(1.10, 56, 210, (1.0, 1.5, 2.01, 3.0), 10, 1.4)),
        (1.35, pad(sub_kick(0.68, 260, 36, 20, 4.8, 2.5), 0.96)),
        (1.10, pad(impact_crack(0.42, 620, 3900, 2.2), 0.96))
    ), 1.25, 0.30), drive=1.7)

    write_ogg("orb_shoot", add_space_tail(mix(
        (1.20, sub_kick(0.46, 240, 46, 20, 6.2, 2.3)),
        (1.05, impact_crack(0.28, 660, 3600, 2.0)),
        (0.95, arcane_power(0.52, 340, 130, (1.0, 1.5, 2.03), 19, 0.9)),
        (0.85, gale_roar(0.48, 1900, 420, 0.04, 1.15, 0.55, 20))
    ), 0.95, 0.24), drive=1.65)


if __name__ == "__main__":
    build()
