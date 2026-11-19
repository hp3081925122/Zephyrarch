import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "zephyrarch", "sounds")
rng = np.random.default_rng(20261120)


def t_axis(dur):
    return np.arange(max(1, int(SR * dur)), dtype=np.float64) / SR


def pink_noise(n):
    white = rng.standard_normal(n)
    spec = np.fft.rfft(white)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    scale = np.where(freqs > 25.0, 1.0 / np.sqrt(np.maximum(freqs, 25.0)), 0.0)
    y = np.fft.irfft(spec * scale, n=n)
    return y / (np.std(y) + 1e-8)


def brown_noise(n):
    white = rng.standard_normal(n)
    spec = np.fft.rfft(white)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    scale = np.where(freqs > 25.0, 1.0 / np.maximum(freqs, 25.0), 0.0)
    y = np.fft.irfft(spec * scale, n=n)
    return y / (np.std(y) + 1e-8)


def fft_filter(x, hp_hz=None, lp_hz=None, order=4):
    n = len(x)
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    gain = np.ones_like(freqs)
    if hp_hz is not None and hp_hz > 0:
        ratio = np.maximum(freqs, 1e-6) / float(hp_hz)
        gain *= 1.0 / np.sqrt(1.0 + np.power(1.0 / ratio, 2 * order))
        gain[0] = 0.0
    if lp_hz is not None and lp_hz > 0:
        ratio = freqs / float(lp_hz)
        gain *= 1.0 / np.sqrt(1.0 + np.power(ratio, 2 * order))
    return np.fft.irfft(spec * gain, n=n)


def biquad_sweep(x, fc, q=1.0, mode="bp", passes=1):
    n = len(x)
    fc_arr = np.clip(np.broadcast_to(np.asarray(fc, dtype=np.float64), (n,)), 30.0, SR * 0.42)
    w0 = 2.0 * np.pi * fc_arr / SR
    cos_w0 = np.cos(w0)
    sin_w0 = np.sin(w0)
    alpha = sin_w0 / (2.0 * max(q, 0.3))
    a0 = 1.0 + alpha

    if mode == "lp":
        b0 = (1.0 - cos_w0) * 0.5 / a0
        b1 = (1.0 - cos_w0) / a0
        b2 = b0
    elif mode == "hp":
        b0 = (1.0 + cos_w0) * 0.5 / a0
        b1 = -(1.0 + cos_w0) / a0
        b2 = b0
    else:
        b0 = alpha / a0
        b1 = np.zeros(n, dtype=np.float64)
        b2 = -alpha / a0

    a1 = (-2.0 * cos_w0) / a0
    a2 = (1.0 - alpha) / a0

    sig = x
    for _ in range(passes):
        out = np.empty(n, dtype=np.float64)
        z1 = 0.0
        z2 = 0.0
        for i in range(n):
            xi = sig[i]
            yi = b0[i] * xi + z1
            z1 = b1[i] * xi - a1[i] * yi + z2
            z2 = b2[i] * xi - a2[i] * yi
            out[i] = yi
        sig = out
    return sig


def env_smooth(t, peak_t, dur, up_pow=1.4, down_pow=2.2):
    peak_t = max(peak_t, 1e-4)
    up = np.clip(t / peak_t, 0.0, 1.0) ** up_pow
    down = np.clip(1.0 - (t - peak_t) / max(dur - peak_t, 1e-4), 0.0, 1.0) ** down_pow
    return np.where(t < peak_t, up, down)


def clean_sub_thump(dur, f_top=165.0, f_sub=42.0, drop=18.0, decay=6.0, warmth=0.14):
    t = t_axis(dur)
    freq = f_sub + (f_top - f_sub) * np.exp(-t * drop)
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    sig = np.sin(phase) + warmth * np.sin(2.0 * phase)
    amp = (1.0 - np.exp(-t * 320.0)) * np.exp(-t * decay)
    return sig * amp


def organic_impact(dur, thump_fc=340.0, crack_fc=1350.0, crack_decay=36.0, body_decay=10.5):
    t = t_axis(dur)
    n = len(t)
    thump_f = 75.0 + (thump_fc - 75.0) * np.exp(-t * 10.0)
    thump = biquad_sweep(brown_noise(n), thump_f, q=0.85, mode="lp", passes=2)
    thump *= (1.0 - np.exp(-t * 450.0)) * np.exp(-t * body_decay)

    crack_f = 320.0 + (crack_fc - 320.0) * np.exp(-t * 24.0)
    crack = biquad_sweep(pink_noise(n), crack_f, q=1.0, mode="bp", passes=2)
    crack *= (1.0 - np.exp(-t * 600.0)) * np.exp(-t * crack_decay)
    return 1.35 * thump + 0.55 * crack


def natural_wind_whoosh(dur, f0, f1, peak_t, q=1.15, air_fc=1650.0, air_mix=0.22, gust_hz=5.5):
    t = t_axis(dur)
    n = len(t)
    k = np.clip(t / max(dur, 1e-4), 0.0, 1.0)
    fc = f0 * ((f1 / max(f0, 1.0)) ** k)

    base = 0.65 * brown_noise(n) + 0.35 * pink_noise(n)
    body = biquad_sweep(base, np.clip(fc, 90.0, 1500.0), q=q, mode="bp", passes=2)

    low = biquad_sweep(brown_noise(n), np.clip(fc * 0.45, 65.0, 480.0), q=0.8, mode="lp", passes=1)
    air = biquad_sweep(pink_noise(n), np.clip(air_fc * (0.7 + 0.4 * k), 500.0, 2300.0), q=1.2, mode="bp", passes=2)

    gust = 0.86 + 0.14 * np.sin(2.0 * np.pi * gust_hz * t + rng.uniform(0.0, 6.28))
    sig = (1.15 * body + 0.65 * low + air_mix * air) * gust
    return sig * env_smooth(t, peak_t, dur, 1.35, 2.1)


def wind_whistle(dur, f0, f1, peak_t, q_res=2.8, tone_mix=0.14):
    t = t_axis(dur)
    n = len(t)
    k = np.clip(t / max(dur, 1e-4), 0.0, 1.0)
    fc = f0 + (f1 - f0) * np.sin(np.pi * (k ** 0.85))

    air_cut = biquad_sweep(pink_noise(n), np.clip(fc, 280.0, 2100.0), q=q_res, mode="bp", passes=2)
    ph = 2.0 * np.pi * np.cumsum(np.clip(fc * 0.65, 220.0, 1100.0)) / SR
    soft_tone = np.sin(ph)
    sig = air_cut + tone_mix * soft_tone
    return sig * env_smooth(t, peak_t, dur, 1.25, 2.2)


def deep_wind_hum(dur, f0, f1, pulse_hz=3.6, exp_curve=1.3):
    t = t_axis(dur)
    k = np.clip(t / max(dur, 1e-4), 0.0, 1.0)
    freq = f0 * ((f1 / max(f0, 1.0)) ** (k ** exp_curve))
    ph = 2.0 * np.pi * np.cumsum(freq) / SR
    tone = 0.65 * np.sin(ph) + 0.28 * np.sin(ph * 1.5) + 0.14 * np.sin(ph * 2.0)
    tone = fft_filter(tone, hp_hz=35.0, lp_hz=680.0, order=3)
    breath = 0.84 + 0.16 * np.sin(2.0 * np.pi * pulse_hz * (1.0 + 0.5 * k) * t)
    env = (np.clip(k * 1.35, 0.0, 1.0) ** 1.2) * np.clip((1.0 - k) * 6.5, 0.0, 1.0)
    return tone * breath * env


def add_gentle_space(x, room_scale=1.0, wet=0.18):
    taps_ms = [23.0, 41.0, 67.0, 97.0, 139.0]
    gains = [0.36, 0.27, 0.20, 0.14, 0.09]
    extra = int(SR * 0.24 * room_scale)
    out = np.pad(x, (0, extra))
    tail = np.zeros_like(out)
    for ms, g in zip(taps_ms, gains):
        d = int(SR * (ms * room_scale) / 1000.0)
        if 0 < d < len(out):
            tail[d:] += g * out[:-d]
    tail = fft_filter(tail, hp_hz=60.0, lp_hz=1150.0, order=3)
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


def soft_limit(x, threshold=0.80, ceiling=0.95):
    ax = np.abs(x)
    over = np.maximum(ax - threshold, 0.0)
    span = max(ceiling - threshold, 1e-4)
    compressed = threshold + span * np.tanh(over / span)
    return np.sign(x) * np.where(ax > threshold, compressed, ax)


def write_ogg(name, x, lp_hz=3800.0, target_peak_db=-1.8):
    x = x - np.mean(x)
    x = fft_filter(x, hp_hz=28.0, lp_hz=lp_hz, order=4)

    peak = np.max(np.abs(x)) or 1.0
    x = x / peak
    x = soft_limit(x, threshold=0.80, ceiling=0.94)

    peak2 = np.max(np.abs(x)) or 1.0
    x = x / peak2 * (10.0 ** (target_peak_db / 20.0))

    fade_in = min(len(x) // 8, int(SR * 0.006))
    fade_out = min(len(x) // 3, int(SR * 0.045))
    if fade_in > 1:
        x[:fade_in] *= 0.5 - 0.5 * np.cos(np.linspace(0.0, np.pi, fade_in))
    if fade_out > 1:
        x[-fade_out:] *= 0.5 + 0.5 * np.cos(np.linspace(0.0, np.pi, fade_out))

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
    rms = np.sqrt(np.mean(x * x))
    print(f"generated {name}.ogg ({len(x) / SR:.2f}s, rms={rms:.2f})")


def build():
    write_ogg("punch_swing", mix(
        (1.05, natural_wind_whoosh(0.30, 260, 1150, 0.08, 1.1, 1500, 0.22, 6.0)),
        (0.55, wind_whistle(0.26, 420, 1350, 0.07, 2.4, 0.10)),
        (0.50, clean_sub_thump(0.20, 135, 50, 20.0, 10.5, 0.10))
    ), lp_hz=3200.0)

    write_ogg("punch_impact", add_gentle_space(mix(
        (1.20, clean_sub_thump(0.42, 185, 40, 22.0, 6.8, 0.16)),
        (1.10, organic_impact(0.34, 420, 1450, 38.0, 11.5)),
        (0.55, natural_wind_whoosh(0.36, 1100, 220, 0.03, 0.95, 1300, 0.18, 5.0))
    ), 0.8, 0.16), lp_hz=3000.0)

    write_ogg("sweep_spin", add_gentle_space(mix(
        (1.10, natural_wind_whoosh(0.76, 180, 1250, 0.24, 1.05, 1650, 0.24, 6.5)),
        (0.65, wind_whistle(0.68, 360, 1450, 0.22, 2.6, 0.11)),
        (0.75, pad(clean_sub_thump(0.42, 165, 42, 18.0, 6.5, 0.14), 0.14)),
        (0.60, pad(organic_impact(0.28, 380, 1300, 34.0, 10.0), 0.15))
    ), 0.95, 0.18), lp_hz=3200.0)

    write_ogg("rush_charge", mix(
        (1.00, natural_wind_whoosh(0.74, 1050, 220, 0.60, 1.35, 1350, 0.18, 5.0)),
        (0.70, deep_wind_hum(0.74, 56, 138, 4.2, 1.45)),
        (0.45, pad(clean_sub_thump(0.20, 125, 48, 18.0, 10.0, 0.10), 0.54))
    ), lp_hz=2600.0)

    write_ogg("rush_dash", add_gentle_space(mix(
        (1.10, clean_sub_thump(0.44, 180, 42, 22.0, 6.8, 0.15)),
        (0.90, organic_impact(0.28, 440, 1500, 40.0, 12.0)),
        (1.05, natural_wind_whoosh(0.56, 240, 1350, 0.07, 1.0, 1700, 0.26, 7.0)),
        (0.45, wind_whistle(0.42, 480, 1500, 0.08, 2.5, 0.09))
    ), 0.85, 0.16), lp_hz=3400.0)

    write_ogg("hammer_rise", mix(
        (1.05, natural_wind_whoosh(0.60, 160, 1100, 0.40, 1.1, 1450, 0.22, 5.5)),
        (0.75, clean_sub_thump(0.34, 155, 46, 16.0, 7.2, 0.13)),
        (0.60, deep_wind_hum(0.60, 65, 165, 3.8, 1.3))
    ), lp_hz=2800.0)

    write_ogg("hammer_slam", add_gentle_space(mix(
        (1.35, clean_sub_thump(0.95, 220, 34, 20.0, 4.2, 0.18)),
        (1.15, organic_impact(0.58, 360, 1350, 30.0, 7.8)),
        (0.75, pad(clean_sub_thump(0.70, 110, 32, 10.0, 4.6, 0.12), 0.035)),
        (0.80, pad(natural_wind_whoosh(0.84, 1350, 180, 0.04, 0.85, 1400, 0.22, 5.0), 0.015))
    ), 1.25, 0.24), lp_hz=2900.0)

    write_ogg("grab_catch", mix(
        (1.10, organic_impact(0.24, 460, 1550, 44.0, 13.0)),
        (0.95, clean_sub_thump(0.26, 175, 52, 24.0, 9.0, 0.14)),
        (0.55, natural_wind_whoosh(0.24, 380, 1100, 0.04, 1.2, 1300, 0.18, 6.0))
    ), lp_hz=3000.0)

    write_ogg("grab_throw", add_gentle_space(mix(
        (1.10, natural_wind_whoosh(0.56, 220, 1400, 0.10, 1.0, 1750, 0.25, 6.5)),
        (0.90, clean_sub_thump(0.38, 170, 46, 20.0, 7.2, 0.14)),
        (0.60, wind_whistle(0.46, 420, 1550, 0.11, 2.6, 0.10)),
        (0.55, organic_impact(0.24, 400, 1350, 38.0, 12.0))
    ), 0.85, 0.16), lp_hz=3300.0)

    write_ogg("stomp_pull", mix(
        (1.05, natural_wind_whoosh(1.30, 140, 820, 1.12, 1.35, 1200, 0.18, 4.5)),
        (0.80, deep_wind_hum(1.30, 46, 102, 3.6, 1.4)),
        (0.60, pad(clean_sub_thump(0.30, 135, 40, 16.0, 8.0, 0.12), 0.98))
    ), lp_hz=2500.0)

    write_ogg("stomp_burst", add_gentle_space(mix(
        (1.30, clean_sub_thump(0.88, 210, 35, 21.0, 4.5, 0.18)),
        (1.10, organic_impact(0.52, 400, 1450, 34.0, 8.5)),
        (0.90, natural_wind_whoosh(0.78, 1450, 200, 0.03, 0.85, 1550, 0.25, 5.5)),
        (0.40, wind_whistle(0.48, 1100, 340, 0.05, 2.4, 0.08))
    ), 1.15, 0.22), lp_hz=3100.0)

    write_ogg("phase_shift", add_gentle_space(mix(
        (0.95, natural_wind_whoosh(1.88, 130, 1350, 0.98, 1.05, 1600, 0.24, 4.8)),
        (0.85, deep_wind_hum(1.02, 50, 175, 4.0, 1.45)),
        (1.35, pad(clean_sub_thump(0.92, 230, 33, 21.0, 4.2, 0.18), 0.98)),
        (1.10, pad(organic_impact(0.56, 420, 1550, 32.0, 8.2), 0.98)),
        (0.75, pad(deep_wind_hum(0.86, 165, 78, 3.0, 1.0), 1.00))
    ), 1.30, 0.25), lp_hz=3100.0)

    write_ogg("wind_dash", mix(
        (0.85, organic_impact(0.16, 480, 1600, 48.0, 16.0)),
        (1.05, natural_wind_whoosh(0.36, 1300, 260, 0.04, 1.0, 1650, 0.25, 7.5)),
        (0.65, clean_sub_thump(0.24, 165, 52, 24.0, 10.0, 0.12))
    ), lp_hz=3400.0)

    write_ogg("stagger", add_gentle_space(mix(
        (1.10, organic_impact(0.34, 380, 1250, 32.0, 10.5)),
        (0.95, clean_sub_thump(0.44, 160, 42, 18.0, 6.8, 0.14)),
        (0.70, deep_wind_hum(0.66, 145, 58, 4.5, 0.85)),
        (0.65, natural_wind_whoosh(0.60, 820, 170, 0.05, 1.0, 1100, 0.16, 4.5))
    ), 0.85, 0.16), lp_hz=2600.0)

    write_ogg("bolt_shoot", mix(
        (0.95, organic_impact(0.18, 500, 1650, 46.0, 15.0)),
        (0.85, clean_sub_thump(0.22, 195, 64, 26.0, 11.5, 0.12)),
        (0.90, natural_wind_whoosh(0.28, 460, 1450, 0.04, 1.2, 1700, 0.24, 8.0)),
        (0.40, wind_whistle(0.22, 620, 1500, 0.04, 2.5, 0.09))
    ), lp_hz=3400.0)

    write_ogg("lance_charge", mix(
        (0.95, deep_wind_hum(1.45, 62, 245, 4.8, 1.5)),
        (0.85, natural_wind_whoosh(1.45, 180, 1450, 1.30, 1.45, 1850, 0.24, 5.5)),
        (0.50, pad(wind_whistle(0.42, 680, 1750, 0.34, 2.8, 0.12), 1.00))
    ), lp_hz=3200.0)

    write_ogg("lance_fire", add_gentle_space(mix(
        (1.30, clean_sub_thump(0.82, 235, 36, 23.0, 4.8, 0.18)),
        (1.15, organic_impact(0.46, 480, 1750, 36.0, 9.5)),
        (1.00, natural_wind_whoosh(0.80, 1700, 240, 0.025, 0.9, 1950, 0.28, 7.0)),
        (0.60, wind_whistle(0.64, 1550, 380, 0.035, 2.5, 0.11)),
        (0.50, deep_wind_hum(0.60, 210, 68, 5.0, 0.85))
    ), 1.25, 0.22), lp_hz=3500.0)

    write_ogg("twister_spawn", add_gentle_space(mix(
        (1.10, natural_wind_whoosh(0.82, 170, 1150, 0.20, 1.3, 1450, 0.22, 6.0)),
        (0.75, clean_sub_thump(0.40, 150, 48, 18.0, 7.0, 0.12)),
        (0.55, wind_whistle(0.70, 380, 1250, 0.24, 2.6, 0.10))
    ), 0.90, 0.18), lp_hz=3000.0)

    write_ogg("downdraft_impact", add_gentle_space(mix(
        (0.75, natural_wind_whoosh(0.16, 1450, 380, 0.12, 1.15, 1600, 0.24, 7.0)),
        (1.25, pad(clean_sub_thump(0.58, 200, 38, 22.0, 5.4, 0.16), 0.06)),
        (1.05, pad(organic_impact(0.40, 420, 1450, 34.0, 9.8), 0.06)),
        (0.85, pad(natural_wind_whoosh(0.50, 1200, 220, 0.03, 0.9, 1400, 0.22, 5.5), 0.06))
    ), 1.05, 0.20), lp_hz=3000.0)

    write_ogg("blade_throw", mix(
        (0.95, wind_whistle(0.56, 520, 1750, 0.09, 2.9, 0.12)),
        (0.95, natural_wind_whoosh(0.52, 340, 1450, 0.08, 1.2, 1800, 0.26, 7.5)),
        (0.65, organic_impact(0.20, 480, 1600, 45.0, 15.0)),
        (0.55, clean_sub_thump(0.24, 165, 56, 22.0, 10.0, 0.11))
    ), lp_hz=3500.0)

    write_ogg("cage_form", add_gentle_space(mix(
        (1.00, natural_wind_whoosh(1.42, 160, 1300, 0.94, 1.1, 1550, 0.22, 5.0)),
        (0.80, deep_wind_hum(1.04, 52, 160, 3.8, 1.35)),
        (1.20, pad(clean_sub_thump(0.64, 195, 38, 20.0, 5.2, 0.16), 0.92)),
        (0.95, pad(organic_impact(0.40, 420, 1450, 34.0, 9.5), 0.92))
    ), 1.15, 0.20), lp_hz=3000.0)

    write_ogg("orb_shoot", add_gentle_space(mix(
        (1.05, clean_sub_thump(0.44, 185, 48, 20.0, 6.8, 0.14)),
        (0.85, organic_impact(0.26, 460, 1450, 38.0, 12.0)),
        (0.75, deep_wind_hum(0.50, 195, 88, 5.5, 0.9)),
        (0.80, natural_wind_whoosh(0.46, 1150, 300, 0.04, 1.1, 1450, 0.22, 6.0))
    ), 0.90, 0.16), lp_hz=3000.0)


if __name__ == "__main__":
    build()
