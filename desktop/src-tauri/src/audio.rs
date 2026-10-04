//! Microphone capture. The stream is opened when dictation starts and closed when it stops, so the OS mic
//! indicator is only on while the user is actually dictating.

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread::JoinHandle;
use std::time::{Duration, Instant};

struct Session {
    stop_tx: mpsc::Sender<()>,
    join: JoinHandle<()>,
    buf: Arc<Mutex<Vec<f32>>>,
    rate: u32,
    started: Instant,
}

#[derive(Default)]
pub struct Recorder {
    session: Mutex<Option<Session>>,
}

pub fn input_devices() -> Vec<String> {
    let host = cpal::default_host();
    match host.input_devices() {
        Ok(devs) => devs.filter_map(|d| d.name().ok()).collect(),
        Err(_) => Vec::new(),
    }
}

/// Streaming linear resampler to 16 kHz, so a dictation of any length is stored at speech rate (an hour is ~230 MB
/// of f32 instead of ~690 MB at 48 kHz) and nothing needs resampling after the key is released.
struct Down16 {
    ratio: f64,
    /// Input-sample time of the next output sample.
    pos: f64,
    /// Index of the next input sample.
    i: u64,
    last: f32,
}

impl Down16 {
    fn new(rate: u32) -> Self {
        Down16 { ratio: rate as f64 / crate::stt::SAMPLE_RATE as f64, pos: 0.0, i: 0, last: 0.0 }
    }
    fn push(&mut self, input: &[f32], out: &mut Vec<f32>, cap: usize) {
        for &s in input {
            if self.i == 0 {
                self.last = s;
            }
            let i = self.i as f64;
            while self.pos <= i {
                if out.len() < cap {
                    let f = (self.pos - (i - 1.0)) as f32;
                    out.push(self.last + (s - self.last) * f.clamp(0.0, 1.0));
                }
                self.pos += self.ratio;
            }
            self.last = s;
            self.i += 1;
        }
    }
}

fn to_mono<T: Copy>(data: &[T], channels: usize, conv: impl Fn(T) -> f32) -> Vec<f32> {
    let ch = channels.max(1);
    data.chunks(ch).map(|frame| frame.iter().map(|&s| conv(s)).sum::<f32>() / ch as f32).collect()
}

impl Recorder {
    pub fn is_recording(&self) -> bool {
        self.session.lock().map(|s| s.is_some()).unwrap_or(false)
    }

    /// Opens the input stream on its own thread (cpal streams are not `Send`) and starts buffering mono f32.
    /// `level` gets the RMS of the latest audio about 20 times a second.
    pub fn start(
        &self,
        device_name: &str,
        max_seconds: u32,
        level: impl Fn(f32) + Send + 'static,
    ) -> Result<(), String> {
        let mut guard = self.session.lock().map_err(|_| "recorder lock poisoned".to_string())?;
        if guard.is_some() {
            return Ok(());
        }
        let buf = Arc::new(Mutex::new(Vec::<f32>::with_capacity(16_000 * 30)));
        let (stop_tx, stop_rx) = mpsc::channel::<()>();
        let (ready_tx, ready_rx) = mpsc::channel::<Result<u32, String>>();
        let device_name = device_name.to_string();
        let thread_buf = buf.clone();
        let join = std::thread::Builder::new()
            .name("humming-mic".into())
            .spawn(move || {
                let host = cpal::default_host();
                let device = if device_name.is_empty() {
                    host.default_input_device()
                } else {
                    host.input_devices()
                        .ok()
                        .and_then(|mut it| it.find(|d| d.name().map(|n| n == device_name).unwrap_or(false)))
                        .or_else(|| host.default_input_device())
                };
                let Some(device) = device else {
                    let _ = ready_tx.send(Err("no microphone found".into()));
                    return;
                };
                let config = match device.default_input_config() {
                    Ok(c) => c,
                    Err(e) => {
                        let _ = ready_tx.send(Err(format!("the microphone cannot be opened: {e}")));
                        return;
                    }
                };
                let rate = config.sample_rate().0;
                let channels = config.channels() as usize;
                // 0 = no limit (the default): record until the user stops.
                let cap = if max_seconds == 0 { usize::MAX } else { crate::stt::SAMPLE_RATE as usize * max_seconds as usize };
                let fmt = config.sample_format();
                let stream_cfg: cpal::StreamConfig = config.into();

                macro_rules! build {
                    ($t:ty, $conv:expr) => {{
                        let b = thread_buf.clone();
                        let mut down = Down16::new(rate);
                        let mut last = Instant::now();
                        let mut acc = 0.0f32;
                        let mut n = 0usize;
                        device.build_input_stream(
                            &stream_cfg,
                            move |data: &[$t], _: &cpal::InputCallbackInfo| {
                                let mono = to_mono(data, channels, $conv);
                                for s in &mono {
                                    acc += s * s;
                                }
                                n += mono.len();
                                if let Ok(mut v) = b.lock() {
                                    down.push(&mono, &mut v, cap);
                                }
                                if last.elapsed() >= Duration::from_millis(50) && n > 0 {
                                    level((acc / n as f32).sqrt());
                                    acc = 0.0;
                                    n = 0;
                                    last = Instant::now();
                                }
                            },
                            |e| eprintln!("humming: microphone stream error: {e}"),
                            None,
                        )
                    }};
                }

                let stream = match fmt {
                    cpal::SampleFormat::F32 => build!(f32, |s: f32| s),
                    cpal::SampleFormat::I16 => build!(i16, |s: i16| s as f32 / 32768.0),
                    cpal::SampleFormat::U16 => build!(u16, |s: u16| (s as f32 - 32768.0) / 32768.0),
                    cpal::SampleFormat::I32 => build!(i32, |s: i32| s as f32 / 2_147_483_648.0),
                    other => {
                        let _ = ready_tx.send(Err(format!("unsupported microphone sample format {other:?}")));
                        return;
                    }
                };
                let stream = match stream {
                    Ok(s) => s,
                    Err(e) => {
                        let _ = ready_tx.send(Err(format!("the microphone cannot be opened: {e}")));
                        return;
                    }
                };
                if let Err(e) = stream.play() {
                    let _ = ready_tx.send(Err(format!("the microphone cannot start: {e}")));
                    return;
                }
                let _ = ready_tx.send(Ok(rate));
                let _ = stop_rx.recv();
                drop(stream);
            })
            .map_err(|e| e.to_string())?;

        let rate = match ready_rx.recv_timeout(Duration::from_secs(5)) {
            Ok(Ok(r)) => r,
            Ok(Err(e)) => {
                let _ = join.join();
                return Err(e);
            }
            Err(_) => {
                let _ = stop_tx.send(());
                return Err("the microphone did not start within 5 s".into());
            }
        };
        // Samples are stored at 16 kHz whatever the device rate.
        let _device_rate = rate;
        *guard = Some(Session { stop_tx, join, buf, rate: crate::stt::SAMPLE_RATE, started: Instant::now() });
        Ok(())
    }

    /// Stops capture and returns (mono samples at 16 kHz, sample rate, wall-clock ms recorded).
    pub fn stop(&self) -> Option<(Vec<f32>, u32, u64)> {
        let session = self.session.lock().ok()?.take()?;
        let _ = session.stop_tx.send(());
        let _ = session.join.join();
        let samples = std::mem::take(&mut *session.buf.lock().ok()?);
        Some((samples, session.rate, session.started.elapsed().as_millis() as u64))
    }
}

#[cfg(test)]
mod tests {
    use super::Down16;

    #[test]
    fn streaming_resampler_matches_rate_and_has_no_cap() {
        let mut d = Down16::new(48_000);
        let mut out = Vec::new();
        // One minute of 48 kHz in 10 ms callbacks.
        let block = vec![0.25f32; 480];
        for _ in 0..6_000 {
            d.push(&block, &mut out, usize::MAX);
        }
        assert!((out.len() as i64 - 960_000).abs() <= 1, "got {}", out.len());
        assert!((out[1234] - 0.25).abs() < 1e-6);
        let mut d = Down16::new(44_100);
        let mut out = Vec::new();
        d.push(&vec![0.0f32; 44_100], &mut out, usize::MAX);
        assert!((out.len() as i64 - 16_000).abs() <= 1, "got {}", out.len());
    }
}
