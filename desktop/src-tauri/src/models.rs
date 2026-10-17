//! Speech models (sherpa-onnx exports), pinned by size and SHA-256: Fast (Parakeet 110M) and Best (Parakeet 0.6B),
//! the same two models the Android app ships, plus desktop-only Lite (Moonshine v2 tiny, 30 MB, MIT). Each is downloaded only when the
//! user picks it, unpacked while it streams (no second pass over the archive), resumed if the connection drops, and
//! checked against its pinned SHA-256 before anything is used. Nothing is bundled in the installer.

use serde::Serialize;
use sha2::{Digest, Sha256};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Copy, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Kind {
    NemoCtc,
    NemoTransducer,
    /// Moonshine v2 (encoder + merged decoder), new in 1.5.
    MoonshineV2,
}

#[derive(Debug, Clone, Serialize)]
pub struct VoiceModel {
    pub id: &'static str,
    pub label: &'static str,
    pub blurb: &'static str,
    pub url: &'static str,
    pub download_bytes: u64,
    pub sha256: &'static str,
    /// Top-level directory inside the archive (also the folder name on disk).
    pub dir_name: &'static str,
    /// The files the engine needs; everything else in the archive (test clips...) is skipped.
    pub files: &'static [&'static str],
    pub disk_bytes: u64,
    /// Peak RAM while dictating (Android bake-off measurement).
    pub ram_mb: u32,
    pub kind: Kind,
    /// LibriSpeech test-clean word error rate from the README benchmark; None = not measured (shown as such).
    pub wer_pct: Option<f32>,
}

const REL: &str = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/";

pub const LITE: VoiceModel = VoiceModel {
    id: "lite",
    label: "Lite",
    blurb: "Moonshine tiny. A 30 MB download, ready in seconds; good for short notes and slower machines.",
    url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27.tar.bz2",
    download_bytes: 29_858_559,
    sha256: "9ec31b342d8fa3240c3b81b8f82e1cf7e3ac467c93ca5a999b741d5887164f8d",
    dir_name: "sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27",
    files: &["encoder_model.ort", "decoder_model_merged.ort", "tokens.txt"],
    disk_bytes: 44_243_206,
    ram_mb: 250,
    kind: Kind::MoonshineV2,
    // Lite is not in the WER benchmark (README covers Fast and Best only), so no number is shown for it.
    wer_pct: None,
};

pub const FAST: VoiceModel = VoiceModel {
    id: "fast",
    label: "Fast",
    blurb: "Parakeet 110M. Instant and very accurate. Recommended.",
    url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2",
    download_bytes: 104_337_827,
    sha256: "17f945007b52ccd8b7200ffc7c5652e9e8e961dfdf479cefcabd06cf5703630b",
    dir_name: "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8",
    files: &["model.int8.onnx", "tokens.txt"],
    disk_bytes: 131_662_124,
    ram_mb: 500,
    kind: Kind::NemoCtc,
    wer_pct: Some(2.78),
};

pub const BEST: VoiceModel = VoiceModel {
    id: "best",
    label: "Best",
    blurb: "Parakeet 0.6B. The most accurate; wants 8 GB of RAM or more.",
    url: "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2",
    download_bytes: 482_468_385,
    sha256: "157c157bc51155e03e37d2466522a3a737dd9c72bb25f36eb18912964161e1ad",
    dir_name: "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
    files: &["encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"],
    disk_bytes: 661_190_513,
    ram_mb: 1300,
    kind: Kind::NemoTransducer,
    wer_pct: Some(1.68),
};

pub const CATALOG: &[VoiceModel] = &[LITE, FAST, BEST];

pub fn find(id: &str) -> Option<&'static VoiceModel> {
    CATALOG.iter().find(|m| m.id == id)
}

pub fn dir_for(models_dir: &Path, m: &VoiceModel) -> PathBuf {
    models_dir.join(m.dir_name)
}

pub fn is_downloaded(models_dir: &Path, m: &VoiceModel) -> bool {
    let d = dir_for(models_dir, m);
    m.files.iter().all(|f| std::fs::metadata(d.join(f)).map(|md| md.len() > 0).unwrap_or(false))
}

pub fn remove(models_dir: &Path, m: &VoiceModel) -> Result<(), String> {
    let d = dir_for(models_dir, m);
    for f in m.files {
        let _ = std::fs::remove_file(d.join(f));
    }
    let _ = std::fs::remove_dir(&d);
    Ok(())
}

/// Hashes and counts every byte that passes through, reporting progress about every 1 %.
struct Metered<R, F: FnMut(u64, u64)> {
    inner: R,
    hasher: Sha256,
    done: u64,
    total: u64,
    last_pct: u64,
    progress: F,
}

impl<R: Read, F: FnMut(u64, u64)> Read for Metered<R, F> {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let n = self.inner.read(buf)?;
        if n > 0 {
            self.hasher.update(&buf[..n]);
            self.done += n as u64;
            let pct = self.done * 100 / self.total.max(1);
            if pct != self.last_pct {
                self.last_pct = pct;
                (self.progress)(self.done, self.total);
            }
        }
        Ok(n)
    }
}

/// Copies what the network sends into the `.part` file as it is read, so a dropped connection can resume.
struct Tee<R> {
    inner: R,
    sink: std::fs::File,
}

impl<R: Read> Read for Tee<R> {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        let n = self.inner.read(buf)?;
        if n > 0 {
            self.sink.write_all(&buf[..n])?;
        }
        Ok(n)
    }
}

/// Byte offset to resume from: the size of a `.part` left by an interrupted download (0 when absent or bad).
pub fn resume_offset(part_len: Option<u64>, total: u64) -> u64 {
    match part_len {
        Some(n) if n > 0 && n < total => n,
        _ => 0,
    }
}

/// Downloads the archive and unpacks it in one streaming pass: bytes are hashed, saved to a `.part` file (so an
/// interrupted download resumes with an HTTP Range request) and decompressed straight into `<file>.part`
/// outputs. Only when size and SHA-256 match the pin are the outputs renamed into place.
/// `progress(done, total)` is called about every 1 % of the download.
pub fn download(models_dir: &Path, m: &VoiceModel, progress: impl FnMut(u64, u64)) -> Result<(), String> {
    std::fs::create_dir_all(models_dir).map_err(|e| format!("cannot create the models folder: {e}"))?;
    let archive = models_dir.join(format!("{}.tar.bz2.part", m.dir_name));
    let have = resume_offset(std::fs::metadata(&archive).ok().map(|md| md.len()), m.download_bytes);
    let mut req = ureq::get(m.url).timeout(std::time::Duration::from_secs(60 * 60));
    if have > 0 {
        req = req.set("Range", &format!("bytes={have}-"));
    }
    let resp = req.call().map_err(|e| format!("download failed: {e}"))?;
    let resumed = have > 0 && resp.status() == 206;
    let (earlier, sink): (Box<dyn Read>, std::fs::File) = if resumed {
        let earlier = std::fs::File::open(&archive).map_err(|e| format!("cannot read the partial download: {e}"))?;
        let sink = std::fs::OpenOptions::new()
            .append(true)
            .open(&archive)
            .map_err(|e| format!("cannot write the model: {e}"))?;
        (Box::new(earlier.take(have)), sink)
    } else {
        let sink = std::fs::File::create(&archive).map_err(|e| format!("cannot write the model: {e}"))?;
        (Box::new(std::io::empty()), sink)
    };
    let net = Tee { inner: resp.into_reader(), sink };
    let mut metered = Metered {
        inner: earlier.chain(net),
        hasher: Sha256::new(),
        done: 0,
        total: m.download_bytes,
        last_pct: u64::MAX,
        progress,
    };
    let dest = dir_for(models_dir, m);
    let unpacked = unpack_stream(&mut metered, &dest, m);
    // Read whatever follows the end of the tar stream so the hash covers the whole file.
    let drained = std::io::copy(&mut metered, &mut std::io::sink());
    let streamed: Result<(), String> = match (unpacked, drained) {
        (Err(e), _) => Err(e),
        (Ok(()), Err(e)) => Err(format!("download interrupted: {e}")),
        (Ok(()), Ok(_)) => Ok(()),
    };
    if let Err(e) = streamed {
        // A network error keeps the .part for a resume; a corrupt archive does not.
        if metered.done >= m.download_bytes {
            let _ = std::fs::remove_file(&archive);
        }
        discard_parts(&dest, m);
        return Err(e);
    }
    let done = metered.done;
    let got = hex::encode(metered.hasher.finalize());
    if done != m.download_bytes || got != m.sha256 {
        let _ = std::fs::remove_file(&archive);
        discard_parts(&dest, m);
        return Err(format!("the download failed its integrity check ({done} bytes, sha256 {got}); please try again"));
    }
    for f in m.files {
        std::fs::rename(dest.join(format!("{f}.part")), dest.join(f)).map_err(|e| e.to_string())?;
    }
    let _ = std::fs::remove_file(&archive);
    Ok(())
}

fn discard_parts(dest: &Path, m: &VoiceModel) {
    for f in m.files {
        let _ = std::fs::remove_file(dest.join(format!("{f}.part")));
    }
}

/// Unpacks the needed files from a .tar.bz2 stream into `<dest>/<file>.part`.
fn unpack_stream(reader: &mut dyn Read, dest: &Path, m: &VoiceModel) -> Result<(), String> {
    std::fs::create_dir_all(dest).map_err(|e| e.to_string())?;
    let mut tar = tar::Archive::new(bzip2::read::BzDecoder::new(std::io::BufReader::new(reader)));
    let mut found = 0usize;
    for entry in tar.entries().map_err(|e| format!("bad model archive: {e}"))? {
        let mut entry = entry.map_err(|e| format!("download interrupted or bad archive: {e}"))?;
        let path = entry.path().map_err(|e| e.to_string())?.into_owned();
        let Some(name) = path.file_name().and_then(|n| n.to_str()).map(|s| s.to_string()) else { continue };
        let in_root = path.parent().and_then(|p| p.file_name()).and_then(|n| n.to_str()) == Some(m.dir_name);
        if in_root && m.files.contains(&name.as_str()) {
            let tmp = dest.join(format!("{name}.part"));
            let mut out = std::fs::File::create(&tmp).map_err(|e| e.to_string())?;
            std::io::copy(&mut entry, &mut out).map_err(|e| format!("cannot unpack {name}: {e}"))?;
            found += 1;
        }
    }
    if found != m.files.len() {
        return Err(format!("the model archive is missing files ({found} of {})", m.files.len()));
    }
    Ok(())
}

/// The model to load right now: the picked one when it is on disk, else the best downloaded model that fits
/// (so a first run can dictate with Lite while Fast or Best is still downloading).
pub fn usable<'a>(picked: &str, downloaded: impl Fn(&VoiceModel) -> bool) -> Option<&'static VoiceModel> {
    if let Some(m) = find(picked) {
        if downloaded(m) {
            return Some(m);
        }
    }
    CATALOG.iter().rev().find(|m| downloaded(m))
}

#[allow(dead_code)]
pub fn release_base() -> &'static str {
    REL
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lite_wer_is_not_measured() {
        assert_eq!(find("lite").unwrap().wer_pct, None);
        assert!(find("fast").unwrap().wer_pct.is_some());
        assert!(find("best").unwrap().wer_pct.is_some());
    }

    #[test]
    fn wer_matches_the_committed_benchmark() {
        let bench: serde_json::Value =
            serde_json::from_str(include_str!("../../../docs/bench/ci-37204875974-wer.json")).unwrap();
        for m in [&FAST, &BEST] {
            let want = bench["models"][m.id]["test-clean"]["wer_pct"].as_f64().unwrap();
            assert!((f64::from(m.wer_pct.unwrap()) - want).abs() < 1e-4, "{}", m.id);
        }
    }

    #[test]
    fn catalog_matches_android() {
        assert_eq!(CATALOG.len(), 3);
        for m in CATALOG {
            assert_eq!(m.sha256.len(), 64, "{}", m.id);
            assert!(m.url.starts_with(REL) && m.url.ends_with(&format!("{}.tar.bz2", m.dir_name)));
            assert!(m.files.contains(&"tokens.txt"));
        }
        assert_eq!(find("fast").unwrap().kind, Kind::NemoCtc);
        assert_eq!(find("best").unwrap().kind, Kind::NemoTransducer);
        assert_eq!(find("lite").unwrap().kind, Kind::MoonshineV2);
        assert!(LITE.download_bytes < FAST.download_bytes / 3);
    }

    #[test]
    fn resume_offset_ignores_empty_and_complete_parts() {
        assert_eq!(resume_offset(None, 100), 0);
        assert_eq!(resume_offset(Some(0), 100), 0);
        assert_eq!(resume_offset(Some(40), 100), 40);
        assert_eq!(resume_offset(Some(100), 100), 0);
        assert_eq!(resume_offset(Some(140), 100), 0);
    }

    #[test]
    fn usable_falls_back_to_a_downloaded_model() {
        assert_eq!(usable("best", |m| m.id == "best").unwrap().id, "best");
        assert_eq!(usable("best", |m| m.id == "lite").unwrap().id, "lite");
        assert_eq!(usable("fast", |m| m.id != "fast").unwrap().id, "best");
        assert!(usable("fast", |_| false).is_none());
    }

    #[test]
    fn streaming_unpack_extracts_only_needed_files() {
        let mut tarbuf = Vec::new();
        {
            let mut b = tar::Builder::new(&mut tarbuf);
            for (name, body) in [("encoder_model.ort", "e"), ("decoder_model_merged.ort", "d"), ("tokens.txt", "t"), ("LICENSE", "l")] {
                let data = body.as_bytes();
                let mut h = tar::Header::new_gnu();
                h.set_size(data.len() as u64);
                h.set_mode(0o644);
                h.set_cksum();
                b.append_data(&mut h, format!("{}/{}", LITE.dir_name, name), data).unwrap();
            }
            b.finish().unwrap();
        }
        let mut bz = Vec::new();
        {
            let mut enc = bzip2::write::BzEncoder::new(&mut bz, bzip2::Compression::fast());
            enc.write_all(&tarbuf).unwrap();
            enc.finish().unwrap();
        }
        let dir = std::env::temp_dir().join(format!("hum-unpack-{}", std::process::id()));
        let mut rd: &[u8] = &bz;
        unpack_stream(&mut rd, &dir, &LITE).unwrap();
        assert_eq!(std::fs::read_to_string(dir.join("tokens.txt.part")).unwrap(), "t");
        assert!(!dir.join("LICENSE.part").exists());
        let _ = std::fs::remove_dir_all(&dir);
    }
}
