//! Audio helpers. The engine consumes 16 kHz mono f32 in [-1, 1].

use crate::CoreError;
use std::path::Path;

pub fn i16_to_f32(samples: &[i16]) -> Vec<f32> {
    samples.iter().map(|&s| s as f32 / 32768.0).collect()
}

/// Average interleaved channels down to mono.
pub fn downmix(interleaved: &[f32], channels: u16) -> Vec<f32> {
    let ch = channels.max(1) as usize;
    if ch == 1 {
        return interleaved.to_vec();
    }
    interleaved
        .chunks(ch)
        .map(|frame| frame.iter().sum::<f32>() / frame.len() as f32)
        .collect()
}

/// Linear-interpolation resampler. Fine for speech going into whisper; not for music.
pub fn resample_linear(samples: &[f32], from_hz: u32, to_hz: u32) -> Vec<f32> {
    if from_hz == to_hz || samples.is_empty() || from_hz == 0 || to_hz == 0 {
        return samples.to_vec();
    }
    let ratio = from_hz as f64 / to_hz as f64;
    let out_len = ((samples.len() as f64) / ratio).floor() as usize;
    let last = samples.len() - 1;
    (0..out_len)
        .map(|i| {
            let pos = i as f64 * ratio;
            let idx = pos.floor() as usize;
            let frac = (pos - idx as f64) as f32;
            let a = samples[idx.min(last)];
            let b = samples[(idx + 1).min(last)];
            a + (b - a) * frac
        })
        .collect()
}

/// Read any PCM (8/16/24/32-bit int) or 32-bit float WAV into mono f32 plus its sample rate.
pub fn read_wav(path: &Path) -> Result<(Vec<f32>, u32), CoreError> {
    let mut reader =
        hound::WavReader::open(path).map_err(|e| CoreError::Audio(format!("{}: {e}", path.display())))?;
    let spec = reader.spec();
    let interleaved: Vec<f32> = match spec.sample_format {
        hound::SampleFormat::Float => reader
            .samples::<f32>()
            .collect::<Result<_, _>>()
            .map_err(|e| CoreError::Audio(e.to_string()))?,
        hound::SampleFormat::Int => {
            let scale = (1i64 << (spec.bits_per_sample.max(1) - 1)) as f32;
            reader
                .samples::<i32>()
                .map(|s| s.map(|v| v as f32 / scale))
                .collect::<Result<_, _>>()
                .map_err(|e| CoreError::Audio(e.to_string()))?
        }
    };
    if spec.sample_rate == 0 {
        return Err(CoreError::Audio("sample rate is 0".into()));
    }
    Ok((downmix(&interleaved, spec.channels), spec.sample_rate))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resample_halves_length() {
        let s: Vec<f32> = (0..32_000).map(|i| (i as f32 * 0.01).sin()).collect();
        let r = resample_linear(&s, 32_000, 16_000);
        assert_eq!(r.len(), 16_000);
    }

    #[test]
    fn downmix_stereo() {
        assert_eq!(downmix(&[1.0, 0.0, 0.5, 0.5], 2), vec![0.5, 0.5]);
    }

    #[test]
    fn i16_scale() {
        assert_eq!(i16_to_f32(&[0, -32768]), vec![0.0, -1.0]);
    }
}
