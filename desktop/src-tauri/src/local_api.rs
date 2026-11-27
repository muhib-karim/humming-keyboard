//! Optional authenticated local HTTP transcription & cleanup service on 127.0.0.1 (off by default).
//! Lets scripts, editor plugins, CLIs, and local AI agents reuse Humming's warm Parakeet engine with zero
//! duplicate model RAM. Binds strictly to loopback and requires a Bearer token for all POST endpoints.

use std::collections::HashMap;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

const MAX_BODY_BYTES: usize = 32 * 1024 * 1024; // 32 MB (~16 minutes of 16 kHz mono WAV)

#[derive(Debug)]
pub struct HttpRequest {
    pub method: String,
    pub path: String,
    pub query: HashMap<String, String>,
    pub headers: HashMap<String, String>,
    pub body: Vec<u8>,
}

pub struct HttpResponse {
    pub status: u16,
    pub body: String,
}

impl HttpResponse {
    pub fn json(status: u16, v: &serde_json::Value) -> Self {
        Self { status, body: v.to_string() }
    }
    pub fn err(status: u16, msg: impl Into<String>) -> Self {
        Self::json(status, &serde_json::json!({ "error": msg.into() }))
    }
}

pub fn generate_token() -> String {
    use sha2::{Digest, Sha256};
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    let mut h = Sha256::new();
    h.update(now.to_le_bytes());
    h.update(std::process::id().to_le_bytes());
    let addr = &now as *const u128 as usize;
    h.update(addr.to_le_bytes());
    hex::encode(&h.finalize()[..16])
}

pub fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (&x, &y) in a.iter().zip(b.iter()) {
        diff |= x ^ y;
    }
    diff == 0
}

pub fn authorized(req: &HttpRequest, expected_token: &str) -> bool {
    if expected_token.is_empty() {
        return false;
    }
    let Some(val) = req.headers.get("authorization") else { return false };
    let Some(tok) = val.strip_prefix("Bearer ").or_else(|| val.strip_prefix("bearer ")) else { return false };
    constant_time_eq(tok.trim().as_bytes(), expected_token.as_bytes())
}

/// Minimal RIFF/WAVE PCM (16-bit int or 32-bit float) parser in memory; returns mono f32 samples and sample rate.
pub fn parse_wav_bytes(data: &[u8]) -> Result<(Vec<f32>, u32), String> {
    if data.len() < 44 || &data[0..4] != b"RIFF" || &data[8..12] != b"WAVE" {
        return Err("not a valid RIFF/WAVE file".into());
    }
    let mut pos = 12usize;
    let mut fmt_tag = 0u16;
    let mut channels = 0u16;
    let mut sample_rate = 0u32;
    let mut bits_per_sample = 0u16;
    let mut audio_slice: Option<&[u8]> = None;

    while pos + 8 <= data.len() {
        let id = &data[pos..pos + 4];
        let size = u32::from_le_bytes(data[pos + 4..pos + 8].try_into().unwrap()) as usize;
        let start = pos + 8;
        let end = start.saturating_add(size).min(data.len());
        if id == b"fmt " && end - start >= 16 {
            fmt_tag = u16::from_le_bytes([data[start], data[start + 1]]);
            channels = u16::from_le_bytes([data[start + 2], data[start + 3]]);
            sample_rate = u32::from_le_bytes(data[start + 4..start + 8].try_into().unwrap());
            bits_per_sample = u16::from_le_bytes([data[start + 14], data[start + 15]]);
        } else if id == b"data" {
            audio_slice = Some(&data[start..end]);
            break;
        }
        pos = end + (size & 1);
    }

    let raw = audio_slice.ok_or_else(|| "WAV missing data chunk".to_string())?;
    if channels == 0 || sample_rate == 0 {
        return Err("WAV missing valid fmt chunk".into());
    }
    let ch = channels as usize;
    let samples = match (fmt_tag, bits_per_sample) {
        (1, 16) => raw
            .chunks_exact(2 * ch)
            .map(|frame| {
                let sum: f32 = frame
                    .chunks_exact(2)
                    .map(|c| i16::from_le_bytes([c[0], c[1]]) as f32 / 32768.0)
                    .sum();
                sum / ch as f32
            })
            .collect(),
        (3, 32) => raw
            .chunks_exact(4 * ch)
            .map(|frame| {
                let sum: f32 = frame
                    .chunks_exact(4)
                    .map(|c| f32::from_le_bytes([c[0], c[1], c[2], c[3]]))
                    .sum();
                sum / ch as f32
            })
            .collect(),
        _ => return Err(format!("unsupported WAV format (tag={fmt_tag}, bits={bits_per_sample}); send 16-bit PCM or 32-bit float")),
    };
    Ok((samples, sample_rate))
}

fn parse_query(q: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for pair in q.split('&') {
        if let Some((k, v)) = pair.split_once('=') {
            if !k.is_empty() {
                map.insert(k.to_string(), v.replace("%20", " "));
            }
        }
    }
    map
}

pub fn read_request(stream: &mut TcpStream) -> Result<HttpRequest, String> {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(10)));
    let mut buf = Vec::with_capacity(4096);
    let mut tmp = [0u8; 4096];
    let header_end = loop {
        if buf.len() > 64 * 1024 {
            return Err("headers too large".into());
        }
        let n = stream.read(&mut tmp).map_err(|e| e.to_string())?;
        if n == 0 {
            return Err("unexpected EOF".into());
        }
        buf.extend_from_slice(&tmp[..n]);
        if let Some(idx) = buf.windows(4).position(|w| w == b"\r\n\r\n") {
            break idx;
        }
    };
    let head = std::str::from_utf8(&buf[..header_end]).map_err(|_| "invalid UTF-8 headers")?;
    let mut lines = head.split("\r\n");
    let first = lines.next().ok_or("empty request")?;
    let mut parts = first.split_whitespace();
    let method = parts.next().ok_or("missing method")?.to_string();
    let target = parts.next().ok_or("missing target")?;
    let (path, query) = match target.split_once('?') {
        Some((p, q)) => (p.to_string(), parse_query(q)),
        None => (target.to_string(), HashMap::new()),
    };
    let mut headers = HashMap::new();
    for line in lines {
        if let Some((k, v)) = line.split_once(':') {
            headers.insert(k.trim().to_ascii_lowercase(), v.trim().to_string());
        }
    }
    let content_len: usize = headers
        .get("content-length")
        .and_then(|v| v.parse().ok())
        .unwrap_or(0);
    if content_len > MAX_BODY_BYTES {
        return Err("request body exceeds 32 MB limit".into());
    }
    let mut body = buf[header_end + 4..].to_vec();
    while body.len() < content_len {
        let want = (content_len - body.len()).min(tmp.len());
        let n = stream.read(&mut tmp[..want]).map_err(|e| e.to_string())?;
        if n == 0 {
            break;
        }
        body.extend_from_slice(&tmp[..n]);
    }
    body.truncate(content_len);
    Ok(HttpRequest { method, path, query, headers, body })
}

fn write_response(stream: &mut TcpStream, resp: HttpResponse) {
    let reason = match resp.status {
        200 => "OK",
        202 => "Accepted",
        400 => "Bad Request",
        401 => "Unauthorized",
        404 => "Not Found",
        405 => "Method Not Allowed",
        503 => "Service Unavailable",
        _ => "Internal Server Error",
    };
    let raw = format!(
        "HTTP/1.1 {} {}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        resp.status,
        reason,
        resp.body.len(),
        resp.body
    );
    let _ = stream.write_all(raw.as_bytes());
}

pub struct ServerHandle {
    stop: Arc<AtomicBool>,
    pub port: u16,
}

impl ServerHandle {
    pub fn stop(&self) {
        self.stop.store(true, Ordering::SeqCst);
    }
}

/// Starts a loopback-only HTTP server on `127.0.0.1:<port>`.
pub fn start<F>(port: u16, handler: F) -> Result<ServerHandle, String>
where
    F: Fn(HttpRequest) -> HttpResponse + Send + Sync + 'static,
{
    let listener = TcpListener::bind(("127.0.0.1", port)).map_err(|e| format!("cannot bind 127.0.0.1:{port}: {e}"))?;
    let actual_port = listener.local_addr().map(|a| a.port()).unwrap_or(port);
    listener.set_nonblocking(true).map_err(|e| e.to_string())?;
    let stop = Arc::new(AtomicBool::new(false));
    let flag = stop.clone();
    let handler = Arc::new(handler);
    std::thread::Builder::new()
        .name("humming-local-api".into())
        .spawn(move || {
            while !flag.load(Ordering::SeqCst) {
                match listener.accept() {
                    Ok((mut stream, _)) => {
                        let _ = stream.set_nonblocking(false);
                        let h = handler.clone();
                        std::thread::spawn(move || {
                            let resp = match read_request(&mut stream) {
                                Ok(req) => h(req),
                                Err(e) => HttpResponse::err(400, e),
                            };
                            write_response(&mut stream, resp);
                        });
                    }
                    Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                        std::thread::sleep(Duration::from_millis(50));
                    }
                    Err(_) => break,
                }
            }
        })
        .map_err(|e| e.to_string())?;
    Ok(ServerHandle { stop, port: actual_port })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_pcm16_wav(samples: &[i16], rate: u32, channels: u16) -> Vec<u8> {
        let data_len = (samples.len() * 2) as u32;
        let mut out = Vec::with_capacity(44 + data_len as usize);
        out.extend_from_slice(b"RIFF");
        out.extend_from_slice(&(36 + data_len).to_le_bytes());
        out.extend_from_slice(b"WAVEfmt ");
        out.extend_from_slice(&16u32.to_le_bytes());
        out.extend_from_slice(&1u16.to_le_bytes()); // PCM
        out.extend_from_slice(&channels.to_le_bytes());
        out.extend_from_slice(&rate.to_le_bytes());
        out.extend_from_slice(&(rate * channels as u32 * 2).to_le_bytes());
        out.extend_from_slice(&(channels * 2).to_le_bytes());
        out.extend_from_slice(&16u16.to_le_bytes());
        out.extend_from_slice(b"data");
        out.extend_from_slice(&data_len.to_le_bytes());
        for &s in samples {
            out.extend_from_slice(&s.to_le_bytes());
        }
        out
    }

    #[test]
    fn parses_mono_and_stereo_wav_bytes() {
        let wav = make_pcm16_wav(&[0, 16384, -16384], 16_000, 1);
        let (s, r) = parse_wav_bytes(&wav).unwrap();
        assert_eq!(r, 16_000);
        assert_eq!(s.len(), 3);
        assert!((s[1] - 0.5).abs() < 1e-3);

        let stereo = make_pcm16_wav(&[16384, -16384, 16384, 16384], 48_000, 2);
        let (s2, r2) = parse_wav_bytes(&stereo).unwrap();
        assert_eq!(r2, 48_000);
        assert_eq!(s2.len(), 2);
        assert!(s2[0].abs() < 1e-3);
        assert!((s2[1] - 0.5).abs() < 1e-3);
    }

    #[test]
    fn loopback_server_enforces_bearer_auth() {
        let token = "secret-token-123".to_string();
        let tok_clone = token.clone();
        let srv = start(0, move |req| {
            if req.method == "GET" && req.path == "/health" {
                return HttpResponse::json(200, &serde_json::json!({ "ok": true }));
            }
            if !authorized(&req, &tok_clone) {
                return HttpResponse::err(401, "unauthorized");
            }
            HttpResponse::json(200, &serde_json::json!({ "path": req.path, "len": req.body.len() }))
        })
        .unwrap();

        let base = format!("http://127.0.0.1:{}", srv.port);
        let h: serde_json::Value = serde_json::from_str(&ureq::get(&format!("{base}/health")).call().unwrap().into_string().unwrap()).unwrap();
        assert_eq!(h["ok"], true);

        let unauth = ureq::post(&format!("{base}/v1/cleanup")).send_string("{}");
        assert!(matches!(unauth, Err(ureq::Error::Status(401, _))));

        let authed = ureq::post(&format!("{base}/v1/cleanup"))
            .set("Authorization", &format!("Bearer {token}"))
            .send_string(r#"{"text":"hi"}"#)
            .unwrap()
            .into_string()
            .unwrap();
        let j: serde_json::Value = serde_json::from_str(&authed).unwrap();
        assert_eq!(j["path"], "/v1/cleanup");
        srv.stop();
    }
}
