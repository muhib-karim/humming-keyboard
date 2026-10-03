//! Speech models: the same NVIDIA Parakeet models (sherpa-onnx exports) the Android app ships, pinned by size and
//! SHA-256. Downloaded once from the sherpa-onnx GitHub release, checked, and unpacked (only the files the engine
//! needs) into the app data folder. Nothing is bundled in the installer.

use serde::Serialize;
use sha2::{Digest, Sha256};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Copy, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Kind {
    NemoCtc,
    NemoTransducer,
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
    pub wer_pct: f32,
}

const REL: &str = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/";

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
    wer_pct: 3.7,
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
    wer_pct: 2.6,
};

pub const CATALOG: &[VoiceModel] = &[FAST, BEST];

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

/// Downloads the archive (hashing as it streams), checks size and SHA-256, then unpacks the needed files.
/// `progress(done, total)` is called about every 1 % of the download.
pub fn download(models_dir: &Path, m: &VoiceModel, mut progress: impl FnMut(u64, u64)) -> Result<(), String> {
    std::fs::create_dir_all(models_dir).map_err(|e| format!("cannot create the models folder: {e}"))?;
    let archive = models_dir.join(format!("{}.tar.bz2.part", m.dir_name));
    let resp = ureq::get(m.url)
        .timeout(std::time::Duration::from_secs(60 * 60))
        .call()
        .map_err(|e| format!("download failed: {e}"))?;
    let mut reader = resp.into_reader();
    let mut file = std::fs::File::create(&archive).map_err(|e| format!("cannot write the model: {e}"))?;
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; 256 * 1024];
    let (mut done, mut last_pct) = (0u64, u64::MAX);
    loop {
        let n = reader.read(&mut buf).map_err(|e| format!("download interrupted: {e}"))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
        file.write_all(&buf[..n]).map_err(|e| format!("cannot write the model: {e}"))?;
        done += n as u64;
        let pct = done * 100 / m.download_bytes.max(1);
        if pct != last_pct {
            last_pct = pct;
            progress(done, m.download_bytes);
        }
    }
    file.flush().map_err(|e| e.to_string())?;
    drop(file);
    let got = hex::encode(hasher.finalize());
    if done != m.download_bytes || got != m.sha256 {
        let _ = std::fs::remove_file(&archive);
        return Err(format!("the download failed its integrity check ({done} bytes, sha256 {got}); please try again"));
    }
    let r = unpack(&archive, models_dir, m);
    let _ = std::fs::remove_file(&archive);
    r
}

fn unpack(archive: &Path, models_dir: &Path, m: &VoiceModel) -> Result<(), String> {
    let dest = dir_for(models_dir, m);
    std::fs::create_dir_all(&dest).map_err(|e| e.to_string())?;
    let f = std::fs::File::open(archive).map_err(|e| e.to_string())?;
    let mut tar = tar::Archive::new(bzip2::read::BzDecoder::new(std::io::BufReader::new(f)));
    let mut found = 0usize;
    for entry in tar.entries().map_err(|e| format!("bad model archive: {e}"))? {
        let mut entry = entry.map_err(|e| format!("bad model archive: {e}"))?;
        let path = entry.path().map_err(|e| e.to_string())?.into_owned();
        let Some(name) = path.file_name().and_then(|n| n.to_str()).map(|s| s.to_string()) else { continue };
        let in_root = path.parent().and_then(|p| p.file_name()).and_then(|n| n.to_str()) == Some(m.dir_name);
        if in_root && m.files.contains(&name.as_str()) {
            let tmp = dest.join(format!("{name}.part"));
            let mut out = std::fs::File::create(&tmp).map_err(|e| e.to_string())?;
            std::io::copy(&mut entry, &mut out).map_err(|e| format!("cannot unpack {name}: {e}"))?;
            drop(out);
            std::fs::rename(&tmp, dest.join(&name)).map_err(|e| e.to_string())?;
            found += 1;
        }
    }
    if found != m.files.len() {
        return Err(format!("the model archive is missing files ({found} of {})", m.files.len()));
    }
    Ok(())
}

#[allow(dead_code)]
pub fn release_base() -> &'static str {
    REL
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catalog_matches_android() {
        assert_eq!(CATALOG.len(), 2);
        for m in CATALOG {
            assert_eq!(m.sha256.len(), 64, "{}", m.id);
            assert!(m.url.starts_with(REL) && m.url.ends_with(&format!("{}.tar.bz2", m.dir_name)));
            assert!(m.files.contains(&"tokens.txt"));
        }
        assert_eq!(find("fast").unwrap().kind, Kind::NemoCtc);
        assert_eq!(find("best").unwrap().kind, Kind::NemoTransducer);
    }
}
