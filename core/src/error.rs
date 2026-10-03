use std::fmt;
use std::path::PathBuf;

#[derive(Debug)]
#[non_exhaustive]
pub enum CoreError {
    ModelNotFound(PathBuf),
    ModelLoad(String),
    /// Built without the `whisper` feature.
    SttUnavailable,
    Audio(String),
    Transcribe(String),
}

impl fmt::Display for CoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            CoreError::ModelNotFound(p) => write!(
                f,
                "whisper model not found at {} (run scripts/fetch-model.sh or set HUMMING_MODEL)",
                p.display()
            ),
            CoreError::ModelLoad(e) => write!(f, "could not load the whisper model: {e}"),
            CoreError::SttUnavailable => {
                write!(f, "speech-to-text is not compiled in (build with the `whisper` feature)")
            }
            CoreError::Audio(e) => write!(f, "audio error: {e}"),
            CoreError::Transcribe(e) => write!(f, "transcription failed: {e}"),
        }
    }
}

impl std::error::Error for CoreError {}
