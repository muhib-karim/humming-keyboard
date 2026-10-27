package com.humming.keyboard

/**
 * Humming (M5): the on-device speech models. Pure data + choice logic (VoiceModelsTest).
 *
 * Chosen by the M5 bake-off (73 LibriSpeech clips, same clips for every engine): NVIDIA Parakeet beat every
 * Whisper / FUTO ACFT model on accuracy and speed. Weights: CC-BY-4.0 (NVIDIA), ONNX export by the sherpa-onnx
 * project (Apache-2.0). Attribution is in LICENSES.md and on Settings > Voice.
 */
data class VoiceModel(
    val id: String,
    val label: String,
    val blurb: String,
    /** tar.bz2 on the sherpa-onnx GitHub release, pinned by size and SHA-256. */
    val url: String,
    val downloadBytes: Long,
    val sha256: String,
    /** Top-level directory inside the archive. */
    val dirName: String,
    /** The files the engine needs (everything else in the archive, e.g. test clips, is skipped). */
    val files: List<String>,
    val diskBytes: Long,
    /** Peak RAM while dictating, measured in the bake-off. */
    val ramMb: Int,
    /** Phones with less total RAM than this don't get the model (the keyboard would be killed). */
    val minRamMb: Int,
    val kind: Kind,
    /** LibriSpeech test-clean WER, first 200 clips, sherpa-onnx 1.13.8, all models in one run (docs/BENCHMARKS.md). */
    val werPct: Double,
) {
    enum class Kind { NEMO_CTC, NEMO_TRANSDUCER }
}

object VoiceModels {
    private const val REL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"

    val FAST = VoiceModel(
        id = "fast",
        label = "Fast",
        blurb = "Parakeet 110M. Instant, very accurate. Recommended.",
        url = REL + "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2",
        downloadBytes = 104_337_827L,
        sha256 = "17f945007b52ccd8b7200ffc7c5652e9e8e961dfdf479cefcabd06cf5703630b",
        dirName = "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8",
        files = listOf("model.int8.onnx", "tokens.txt"),
        diskBytes = 131_662_124L,
        ramMb = 500,
        minRamMb = 2_500,
        kind = VoiceModel.Kind.NEMO_CTC,
        werPct = 2.7,
    )

    val BEST = VoiceModel(
        id = "best",
        label = "Best",
        blurb = "Parakeet 0.6B. The most accurate; needs a phone with 6 GB of RAM or more.",
        url = REL + "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2",
        downloadBytes = 482_468_385L,
        sha256 = "157c157bc51155e03e37d2466522a3a737dd9c72bb25f36eb18912964161e1ad",
        dirName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
        files = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
        diskBytes = 661_190_513L,
        ramMb = 1_300,
        minRamMb = 5_500,
        kind = VoiceModel.Kind.NEMO_TRANSDUCER,
        werPct = 1.8,
    )

    /** New in 1.8: English plus 24 more European languages, detected automatically (see docs/BENCHMARKS.md). */
    val MULTI = VoiceModel(
        id = "multi",
        label = "Multilingual",
        blurb = "Parakeet 0.6B v3. English plus 24 more European languages; needs a phone with 6 GB of RAM or more.",
        url = REL + "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2",
        downloadBytes = 487_170_055L,
        sha256 = "5793d0fd397c5778d2cf2126994d58e9d56b1be7c04d13c7a15bb1b4eafb16bf",
        dirName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8",
        files = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt"),
        diskBytes = 670_478_772L,
        ramMb = 1_200,
        minRamMb = 5_500,
        kind = VoiceModel.Kind.NEMO_TRANSDUCER,
        werPct = 2.2,
    )

    val all: List<VoiceModel> = listOf(FAST, BEST, MULTI)

    fun byId(id: String?): VoiceModel = all.firstOrNull { it.id == id } ?: FAST

    /** Whether [m] may be used on a phone with [totalRamMb] of memory. */
    fun fits(m: VoiceModel, totalRamMb: Long): Boolean = totalRamMb <= 0 || totalRamMb >= m.minRamMb

    /** The model to use: the user's pick when this phone can run it, otherwise Fast. */
    fun resolve(pickedId: String?, totalRamMb: Long): VoiceModel {
        val m = byId(pickedId)
        return if (fits(m, totalRamMb)) m else FAST
    }

    /** "104 MB", "1.3 GB". */
    fun formatBytes(bytes: Long): String = if (bytes >= 1_000_000_000L) {
        String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
    } else {
        "${(bytes + 500_000) / 1_000_000} MB"
    }
}
