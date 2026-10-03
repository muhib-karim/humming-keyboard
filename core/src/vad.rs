//! Energy-based voice activity detection (M1). Silero can replace it behind `VadConfig` later.

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
pub struct VadConfig {
    pub enabled: bool,
    /// Frame RMS in dBFS above which a frame counts as speech.
    pub threshold_db: f32,
    pub frame_ms: u32,
    /// Speech runs shorter than this are dropped (clicks, taps).
    pub min_speech_ms: u32,
    /// Audio kept on each side of a speech run.
    pub padding_ms: u32,
}

impl Default for VadConfig {
    fn default() -> Self {
        Self { enabled: true, threshold_db: -40.0, frame_ms: 30, min_speech_ms: 150, padding_ms: 200 }
    }
}

fn frame_db(frame: &[f32]) -> f32 {
    if frame.is_empty() {
        return f32::NEG_INFINITY;
    }
    let mean_sq = frame.iter().map(|s| s * s).sum::<f32>() / frame.len() as f32;
    20.0 * (mean_sq.sqrt() + 1e-10).log10()
}

/// Sample ranges `[start, end)` that contain speech, padded and merged.
pub fn speech_ranges(samples: &[f32], sample_rate: u32, cfg: &VadConfig) -> Vec<(usize, usize)> {
    if samples.is_empty() || sample_rate == 0 {
        return Vec::new();
    }
    let ms = |m: u32| (sample_rate as u64 * m as u64 / 1000) as usize;
    let frame = ms(cfg.frame_ms.max(1)).max(1);
    let min_len = ms(cfg.min_speech_ms);
    let pad = ms(cfg.padding_ms);

    let mut raw: Vec<(usize, usize)> = Vec::new();
    let mut run_start: Option<usize> = None;
    for (i, chunk) in samples.chunks(frame).enumerate() {
        let start = i * frame;
        let voiced = frame_db(chunk) > cfg.threshold_db;
        match (voiced, run_start) {
            (true, None) => run_start = Some(start),
            (false, Some(s)) => {
                raw.push((s, start));
                run_start = None;
            }
            _ => {}
        }
    }
    if let Some(s) = run_start {
        raw.push((s, samples.len()));
    }

    let mut out: Vec<(usize, usize)> = Vec::new();
    for (s, e) in raw.into_iter().filter(|(s, e)| e - s >= min_len) {
        let s = s.saturating_sub(pad);
        let e = (e + pad).min(samples.len());
        match out.last_mut() {
            Some(last) if s <= last.1 => last.1 = last.1.max(e),
            _ => out.push((s, e)),
        }
    }
    out
}

/// Speech-only audio (ranges concatenated). Empty when nothing crosses the threshold.
pub fn trim(samples: &[f32], sample_rate: u32, cfg: &VadConfig) -> Vec<f32> {
    if !cfg.enabled {
        return samples.to_vec();
    }
    speech_ranges(samples, sample_rate, cfg)
        .into_iter()
        .flat_map(|(s, e)| samples[s..e].iter().copied())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tone(n: usize, amp: f32) -> Vec<f32> {
        (0..n).map(|i| amp * (i as f32 * 0.2).sin()).collect()
    }

    #[test]
    fn silence_is_empty() {
        assert!(trim(&vec![0.0; 16_000], 16_000, &VadConfig::default()).is_empty());
    }

    #[test]
    fn finds_tone_between_silence() {
        let mut s = vec![0.0; 16_000];
        s.extend(tone(8_000, 0.3));
        s.extend(vec![0.0; 16_000]);
        let r = speech_ranges(&s, 16_000, &VadConfig::default());
        assert_eq!(r.len(), 1);
        let (a, b) = r[0];
        assert!(a <= 16_000 && a >= 16_000 - 3_200 - 480, "start {a}");
        assert!(b >= 24_000 && b <= 24_000 + 3_200 + 480, "end {b}");
    }

    #[test]
    fn short_click_dropped() {
        let mut s = vec![0.0; 16_000];
        s.extend(tone(480, 0.5)); // 30 ms
        s.extend(vec![0.0; 16_000]);
        assert!(speech_ranges(&s, 16_000, &VadConfig::default()).is_empty());
    }
}
