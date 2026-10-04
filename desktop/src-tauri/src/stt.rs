//! On-device speech-to-text through sherpa-onnx 1.13.8, configured exactly like the Android app's `SherpaEngine`:
//! Moonshine v2 for Lite, Parakeet CTC for Fast, Parakeet TDT transducer for Best (CPU provider, 16 kHz).

use crate::models::{dir_for, Kind, VoiceModel};
use sherpa_onnx::{
    OfflineModelConfig, OfflineMoonshineModelConfig, OfflineNemoEncDecCtcModelConfig, OfflineRecognizer,
    OfflineRecognizerConfig, OfflineTransducerModelConfig,
};
use std::path::Path;
use std::time::Instant;

pub const SAMPLE_RATE: u32 = 16_000;
/// Long dictations are decoded in windows of at most this many seconds, cut at the quietest moment.
const MAX_WINDOW_S: usize = 25;
/// Moonshine (Lite) windows: sherpa-onnx 1.13.8 returns an empty result for Moonshine v2 clips longer than ~10 s.
pub const MOONSHINE_WINDOW_S: usize = 8;

pub struct SherpaEngine {
    recognizer: OfflineRecognizer,
    /// Longest window one decode gets: Moonshine v2 returns nothing for clips over ~10 s in sherpa-onnx 1.13.8.
    max_window_s: usize,
    pub model_id: &'static str,
    pub load_ms: u64,
}

pub fn threads() -> i32 {
    std::thread::available_parallelism().map(|n| n.get()).unwrap_or(2).clamp(1, 4) as i32
}

impl SherpaEngine {
    pub fn load(models_dir: &Path, m: &'static VoiceModel) -> Result<Self, String> {
        let t0 = Instant::now();
        let dir = dir_for(models_dir, m);
        let p = |f: &str| Some(dir.join(f).to_string_lossy().into_owned());
        let mut mc = OfflineModelConfig {
            tokens: p("tokens.txt"),
            num_threads: threads(),
            provider: Some("cpu".into()),
            ..Default::default()
        };
        match m.kind {
            Kind::NemoCtc => {
                mc.nemo_ctc = OfflineNemoEncDecCtcModelConfig { model: p("model.int8.onnx"), ..Default::default() };
            }
            Kind::NemoTransducer => {
                mc.transducer = OfflineTransducerModelConfig {
                    encoder: p("encoder.int8.onnx"),
                    decoder: p("decoder.int8.onnx"),
                    joiner: p("joiner.int8.onnx"),
                };
                mc.model_type = Some("nemo_transducer".into());
            }
            Kind::MoonshineV2 => {
                mc.moonshine = OfflineMoonshineModelConfig {
                    encoder: p("encoder_model.ort"),
                    merged_decoder: p("decoder_model_merged.ort"),
                    ..Default::default()
                };
            }
        }
        let cfg = OfflineRecognizerConfig { model_config: mc, ..Default::default() };
        let recognizer = OfflineRecognizer::create(&cfg).ok_or_else(|| "the speech model could not be loaded".to_string())?;
        let max_window_s = if m.kind == Kind::MoonshineV2 { MOONSHINE_WINDOW_S } else { MAX_WINDOW_S };
        Ok(Self { recognizer, max_window_s, model_id: m.id, load_ms: t0.elapsed().as_millis() as u64 })
    }

    fn decode(&self, samples: &[f32]) -> String {
        if samples.is_empty() {
            return String::new();
        }
        let s = self.recognizer.create_stream();
        s.accept_waveform(SAMPLE_RATE as i32, samples);
        self.recognizer.decode(&s);
        s.get_result().map(|r| r.text.trim().to_string()).unwrap_or_default()
    }

    /// 16 kHz mono in, raw text out. Long audio is split into windows at quiet points so memory stays flat.
    pub fn transcribe(&self, samples: &[f32]) -> String {
        split_points_max(samples, SAMPLE_RATE as usize, self.max_window_s)
            .windows(2)
            .map(|w| self.decode(&samples[w[0]..w[1]]))
            .filter(|t| !t.is_empty())
            .collect::<Vec<_>>()
            .join(" ")
    }

    pub fn warmup(&self) {
        let _ = self.decode(&vec![0.0f32; SAMPLE_RATE as usize]);
    }
}

/// Boundaries [0, ..., len] so every window is at most MAX_WINDOW_S long; each cut sits at the lowest-energy
/// 50 ms frame in the last third of the window (usually a pause between words).
pub fn split_points(samples: &[f32], rate: usize) -> Vec<usize> {
    split_points_max(samples, rate, MAX_WINDOW_S)
}

/// As `split_points`, with windows of at most `max_s` seconds.
pub fn split_points_max(samples: &[f32], rate: usize, max_s: usize) -> Vec<usize> {
    let max = max_s * rate;
    let frame = rate / 20;
    let mut cuts = vec![0usize];
    let mut start = 0usize;
    while samples.len() - start > max {
        let lo = start + max * 2 / 3;
        let hi = start + max;
        let mut best = hi;
        let mut best_e = f32::MAX;
        let mut i = lo;
        while i + frame <= hi {
            let e: f32 = samples[i..i + frame].iter().map(|s| s * s).sum();
            if e < best_e {
                best_e = e;
                best = i + frame / 2;
            }
            i += frame;
        }
        cuts.push(best);
        start = best;
    }
    cuts.push(samples.len());
    cuts
}

/// Linear resampling to 16 kHz (speech-grade; the mic usually runs at 44.1 or 48 kHz).
pub fn to_16k(samples: &[f32], rate: u32) -> Vec<f32> {
    if rate == SAMPLE_RATE || samples.is_empty() {
        return samples.to_vec();
    }
    let ratio = rate as f64 / SAMPLE_RATE as f64;
    let n = (samples.len() as f64 / ratio).floor() as usize;
    (0..n)
        .map(|i| {
            let x = i as f64 * ratio;
            let j = x.floor() as usize;
            let f = (x - j as f64) as f32;
            let a = samples[j.min(samples.len() - 1)];
            let b = samples[(j + 1).min(samples.len() - 1)];
            a + (b - a) * f
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn moonshine_windows_stay_under_ten_seconds() {
        let s: Vec<f32> = (0..16_000 * 30).map(|i| ((i as f32) * 0.01).sin() * 0.3).collect();
        let cuts = split_points_max(&s, 16_000, MOONSHINE_WINDOW_S);
        assert!(cuts.windows(2).all(|w| w[1] - w[0] <= 16_000 * MOONSHINE_WINDOW_S));
        assert_eq!(*cuts.last().unwrap(), s.len());
    }

    #[test]
    fn short_audio_is_one_window() {
        assert_eq!(split_points(&vec![0.0; 16_000 * 10], 16_000), vec![0, 160_000]);
    }

    #[test]
    fn long_audio_is_cut_into_windows_of_at_most_25_s() {
        let mut s: Vec<f32> = (0..16_000 * 70).map(|i| ((i as f32) * 0.01).sin() * 0.3).collect();
        for x in &mut s[16_000 * 20..16_000 * 20 + 4000] {
            *x = 0.0; // a pause at 20 s
        }
        let cuts = split_points(&s, 16_000);
        assert!(cuts.windows(2).all(|w| w[1] - w[0] <= 25 * 16_000), "{cuts:?}");
        assert!(cuts[1] >= 16_000 * 20 && cuts[1] <= 16_000 * 20 + 4000, "{cuts:?}");
        assert_eq!(*cuts.last().unwrap(), s.len());
    }

    /// End to end on a real clip: model download + SHA-256 + unpack (the app's own code), load, transcribe, clean.
    /// Downloads 104 MB, so it only runs when HUMMING_E2E_WAV points at a WAV file (CI sets it).
    #[test]
    fn e2e_fast_model_on_a_real_clip() {
        let Some(wav) = std::env::var_os("HUMMING_E2E_WAV") else { return };
        let dir = std::env::temp_dir().join("humming-e2e-models");
        let m = crate::models::find("fast").unwrap();
        if !crate::models::is_downloaded(&dir, m) {
            crate::models::download(&dir, m, |_, _| {}).expect("download");
        }
        let t0 = Instant::now();
        let engine = SherpaEngine::load(&dir, m).expect("load");
        let load_ms = t0.elapsed().as_millis();
        let (samples, rate) = humming_core::audio::read_wav(std::path::Path::new(&wav)).expect("wav");
        let pcm = to_16k(&samples, rate);
        let t1 = Instant::now();
        let raw = engine.transcribe(&pcm);
        let stt_ms = t1.elapsed().as_millis();
        let clean = crate::text_cleanup::apply(&raw, &[], crate::text_cleanup::Opts::default());
        println!("E2E load_ms={load_ms} audio_ms={} stt_ms={stt_ms}", pcm.len() * 1000 / 16_000);
        println!("E2E raw:   {raw}");
        println!("E2E clean: {clean}");
        assert!(raw.split_whitespace().count() >= 3, "too few words: {raw:?}");
        assert!(clean.ends_with('.') || clean.ends_with('?') || clean.ends_with('!'), "{clean:?}");
    }

    #[test]
    fn resample_48k_to_16k() {
        let s = vec![0.5f32; 48_000];
        let r = to_16k(&s, 48_000);
        assert_eq!(r.len(), 16_000);
        assert!((r[100] - 0.5).abs() < 1e-6);
    }
}
