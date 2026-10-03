//! humming-core CLI.
//!
//!   humming-core transcribe <file.wav> [--model PATH] [--lang en|auto] [--no-cleanup] [--no-vad] [--json]
//!   humming-core cleanup "<text>"

use std::path::PathBuf;
use std::process::ExitCode;
use std::time::Instant;
use humming_core::{audio, cleanup, model, CleanupOpts, CoreError, Engine, EngineConfig};

const USAGE: &str = "usage:
  humming-core transcribe <file.wav> [--model PATH] [--lang en|auto] [--threads N] [--no-cleanup] [--no-vad] [--json]
         speed knobs: [--m2] [--audio-ctx dyn|full] [--pad-ms N] [--min-ctx N] [--fallback|--no-fallback]
                      [--timestamps] [--multi-segment] [--warmup] [--reps N]   (--reps prints one JSON line per run)
  humming-core cleanup \"<text>\"

Model: --model, else $HUMMING_MODEL, else the default data-dir path (see scripts/fetch-model.sh).";

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match args.first().map(String::as_str) {
        Some("transcribe") => transcribe(&args[1..]),
        Some("cleanup") => {
            let text = args[1..].join(" ");
            println!("{}", cleanup(&text, &CleanupOpts::default()));
            ExitCode::SUCCESS
        }
        Some("-h") | Some("--help") | Some("help") => {
            println!("{USAGE}");
            ExitCode::SUCCESS
        }
        _ => {
            eprintln!("{USAGE}");
            ExitCode::from(1)
        }
    }
}

fn transcribe(args: &[String]) -> ExitCode {
    let mut file: Option<PathBuf> = None;
    let mut model_path: Option<PathBuf> = None;
    let mut lang: Option<String> = Some("en".into());
    let mut threads: Option<u32> = None;
    let mut do_cleanup = true;
    let mut vad = true;
    let mut json = false;
    let mut speed = humming_core::SpeedOpts::default();
    let mut warmup = false;
    let mut reps: u32 = 0;

    let mut it = args.iter();
    while let Some(a) = it.next() {
        match a.as_str() {
            "--model" => model_path = it.next().map(PathBuf::from),
            "--lang" => lang = it.next().map(|l| l.to_string()).filter(|l| l != "auto"),
            "--threads" => threads = it.next().and_then(|t| t.parse().ok()),
            "--no-cleanup" => do_cleanup = false,
            "--no-vad" => vad = false,
            "--json" => json = true,
            "--m2" => speed = humming_core::SpeedOpts::m2(),
            "--audio-ctx" => match it.next().map(String::as_str) {
                Some("full") => speed.dynamic_audio_ctx = false,
                _ => speed.dynamic_audio_ctx = true,
            },
            "--pad-ms" => speed.audio_ctx_pad_ms = it.next().and_then(|t| t.parse().ok()).unwrap_or(1000),
            "--min-ctx" => speed.min_audio_ctx = it.next().and_then(|t| t.parse().ok()).unwrap_or(0),
            "--fallback" => speed.temperature_fallback = true,
            "--no-fallback" => speed.temperature_fallback = false,
            "--timestamps" => speed.no_timestamps = false,
            "--multi-segment" => speed.single_segment = false,
            "--warmup" => warmup = true,
            "--reps" => reps = it.next().and_then(|t| t.parse().ok()).unwrap_or(1),
            other if other.starts_with("--") => {
                eprintln!("unknown flag {other}\n{USAGE}");
                return ExitCode::from(1);
            }
            other => file = Some(PathBuf::from(other)),
        }
    }
    let Some(file) = file else {
        eprintln!("{USAGE}");
        return ExitCode::from(1);
    };

    let mut cfg = EngineConfig::new(model_path.unwrap_or_else(model::default_model_path));
    cfg.language = lang;
    cfg.threads = threads;
    cfg.vad.enabled = vad;
    cfg.speed = speed;
    if !do_cleanup {
        cfg.cleanup = CleanupOpts::none();
    }

    let load = Instant::now();
    let engine = match Engine::new(cfg) {
        Ok(e) => e,
        Err(e) => {
            eprintln!("error: {e}");
            return ExitCode::from(if matches!(e, CoreError::ModelNotFound(_)) { 2 } else { 1 });
        }
    };
    let load_ms = load.elapsed().as_millis();

    let (samples, rate) = match audio::read_wav(&file) {
        Ok(v) => v,
        Err(e) => {
            eprintln!("error: {e}");
            return ExitCode::from(1);
        }
    };

    if warmup || reps > 0 {
        let wt = if warmup { engine.warmup().ok() } else { None };
        for i in 0..reps.max(1) {
            let t = Instant::now();
            match engine.dictate(&samples, rate) {
                Ok(d) => {
                    let line = serde_json::json!({
                        "rep": i, "load_ms": load_ms, "warmup": wt, "wall_ms": t.elapsed().as_millis() as u64,
                        "text": d.text, "raw": d.raw.text, "audio_ms": d.raw.audio_ms, "speech_ms": d.raw.speech_ms,
                        "stt_ms": d.raw.ms, "cleanup_ms": d.cleanup_ms, "timing": d.raw.timing,
                    });
                    println!("{line}");
                }
                Err(e) => {
                    eprintln!("error: {e}");
                    return ExitCode::from(1);
                }
            }
        }
        return ExitCode::SUCCESS;
    }

    match engine.dictate(&samples, rate) {
        Ok(d) => {
            if json {
                match serde_json::to_string_pretty(&d) {
                    Ok(s) => println!("{s}"),
                    Err(e) => {
                        eprintln!("error: {e}");
                        return ExitCode::from(1);
                    }
                }
            } else {
                let rtf = if d.raw.audio_ms > 0 { d.raw.ms as f64 / d.raw.audio_ms as f64 } else { 0.0 };
                println!("{}", d.text);
                println!(
                    "timing: model_load={}ms audio={}ms speech={}ms stt={}ms cleanup={}ms rtf={:.2} lang={}",
                    load_ms,
                    d.raw.audio_ms,
                    d.raw.speech_ms,
                    d.raw.ms,
                    d.cleanup_ms,
                    rtf,
                    d.raw.language.as_deref().unwrap_or("?")
                );
            }
            ExitCode::SUCCESS
        }
        Err(e) => {
            eprintln!("error: {e}");
            ExitCode::from(1)
        }
    }
}
