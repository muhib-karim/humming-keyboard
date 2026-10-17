import AVFoundation
import Foundation
import HummingCore
import Speech

/// Offline dictation with Apple's on-device speech recogniser (`requiresOnDeviceRecognition`): audio never leaves
/// the phone, and when the on-device model is missing Humming says so instead of falling back to a server.
/// Each recognition request is restarted as it ends, so there is no length limit. Every finished phrase goes
/// through Humming's clean-up (the same rules as Android and desktop); accuracy mode turns on the strict rules.
final class Dictation: ObservableObject {
    enum Phase: Equatable { case idle, listening, unavailable(String) }

    @Published var phase: Phase = .idle
    @Published var committed = ""
    @Published var partial = ""

    // Keep list markers and sentence punctuation until commands from later chunks have been applied.
    private var transcript = ""

    weak var settings: AppSettings?

    private let engine = AVAudioEngine()
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private let lock = NSLock()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var generation = 0
    private var failures = 0
    private var demoTimer: Timer?

    var isListening: Bool { phase == .listening }

    func toggle() { isListening ? stop() : start() }

    func clear() {
        committed = ""
        partial = ""
        transcript = ""
        SharedStore.latestDictation = ""
    }

    func start() {
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else {
                    self.phase = .unavailable("Speech recognition is off for Humming. Turn it on in Settings > Privacy & Security > Speech Recognition.")
                    return
                }
                AVAudioSession.sharedInstance().requestRecordPermission { ok in
                    DispatchQueue.main.async {
                        guard ok else {
                            self.phase = .unavailable("Microphone access is off for Humming. Turn it on in Settings > Privacy & Security > Microphone.")
                            return
                        }
                        self.begin()
                    }
                }
            }
        }
    }

    private func begin() {
        guard let rec = recognizer, rec.supportsOnDeviceRecognition else {
            phase = .unavailable("On-device English recognition isn't installed on this iPhone. Add English in Settings > General > Keyboard > Dictation Languages, then try again. Humming never sends your voice to a server.")
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                guard let self = self else { return }
                self.lock.lock()
                let r = self.request
                self.lock.unlock()
                r?.append(buffer)
            }
            engine.prepare()
            try engine.start()
            failures = 0
            phase = .listening
            startTask(rec)
        } catch {
            phase = .unavailable("Couldn't start the microphone: \(error.localizedDescription)")
        }
    }

    private func startTask(_ rec: SFSpeechRecognizer) {
        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        req.requiresOnDeviceRecognition = true
        req.addsPunctuation = true
        // Hints are the written forms only ("cube cuddle -> kubectl" hints "kubectl").
        if let lines = settings?.customWords, !lines.isEmpty { req.contextualStrings = TextCleanup.vocabulary(lines).terms }
        lock.lock()
        request = req
        lock.unlock()
        generation += 1
        let gen = generation
        var heard = false
        task = rec.recognitionTask(with: req) { [weak self] result, error in
            DispatchQueue.main.async {
                guard let self = self, gen == self.generation else { return }
                if let r = result {
                    heard = true
                    let s = r.bestTranscription.formattedString
                    if r.isFinal { self.commit(s) } else { self.partial = s }
                }
                let ended = error != nil || (result?.isFinal ?? false)
                guard ended else { return }
                if !self.partial.isEmpty { self.commit(self.partial) }
                guard self.isListening else { return }
                self.failures = heard ? 0 : self.failures + 1
                if self.failures >= 3 {
                    self.stop()
                    self.phase = .unavailable("On-device recognition stopped responding. Tap the mic to try again.")
                } else {
                    self.startTask(rec)   // keep going: no length limit
                }
            }
        }
    }

    func stop() {
        guard isListening else { return }
        phase = .idle
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        lock.lock()
        request?.endAudio()
        request = nil
        lock.unlock()
        if !partial.isEmpty { commit(partial) }
        generation += 1
        task?.finish()
        task = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func commit(_ raw: String) {
        partial = ""
        let vocab = TextCleanup.vocabulary(settings?.customWords ?? [])
        committed = TextCleanup.appendChunk(raw, to: &transcript, clean: settings?.cleanMode ?? true,
                                            dictionary: vocab.terms, strict: settings?.accuracyMode ?? false,
                                            fillers: settings?.removeFillers ?? true, replacements: vocab.replacements)
        SharedStore.latestDictation = committed
    }

    // MARK: CI screenshots (the simulator has no microphone)

    func demo(committed: String, partial: String, listening: Bool = false) {
        self.committed = committed
        transcript = committed
        self.partial = partial
        phase = listening ? .listening : .idle
    }

    /// Plays a spoken sentence word by word, then the cleaned result, for the walkthrough recording.
    func demoWalkthrough() {
        let spoken = "um so I was like thinking we could meet at 3 no 4pm on Friday".split(separator: " ").map(String.init)
        var i = 0
        committed = ""
        transcript = ""
        partial = ""
        phase = .idle
        demoTimer?.invalidate()
        demoTimer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: true) { [weak self] t in
            guard let self = self else { t.invalidate(); return }
            if i == 0 { self.phase = .listening }
            if i < spoken.count {
                self.partial = spoken[0...i].joined(separator: " ")
            } else if i == spoken.count + 2 {
                self.commit("Um, so, I was, like, thinking we could meet at 3, no, 4pm on Friday")
                self.phase = .idle
                t.invalidate()
            }
            i += 1
        }
    }
}
