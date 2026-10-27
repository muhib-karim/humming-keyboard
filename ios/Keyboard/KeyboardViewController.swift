import HummingCore
import SwiftUI
import UIKit

/// The Humming keyboard extension. iOS keyboards cannot use the microphone, so dictation happens in the Humming app
/// and the keyboard's top strip inserts the latest result (or, with Full Access, whatever you copied).
final class KeyboardViewController: UIInputViewController {
    private var host: UIHostingController<KeyboardView>?
    /// The keyboard's own last dictation insert: the only thing "undo that" may remove.
    private var lastDictationInsert: String?

    override func viewDidLoad() {
        super.viewDidLoad()
        let h = UIHostingController(rootView: makeView())
        h.view.translatesAutoresizingMaskIntoConstraints = false
        h.view.backgroundColor = .clear
        addChild(h)
        view.addSubview(h.view)
        h.didMove(toParent: self)
        let height = h.view.heightAnchor.constraint(equalToConstant: 270)
        height.priority = .defaultHigh
        NSLayoutConstraint.activate([
            h.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            h.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            h.view.topAnchor.constraint(equalTo: view.topAnchor),
            h.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            height,
        ])
        host = h
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        host?.rootView = makeView()
    }

    private func makeView() -> KeyboardView {
        KeyboardView(
            dictation: pendingDictation(),
            showGlobe: needsInputModeSwitchKey,
            canPaste: hasFullAccess,
            onInsert: { [weak self] s in self?.textDocumentProxy.insertText(s) },
            onBackspace: { [weak self] in self?.textDocumentProxy.deleteBackward() },
            onNextKeyboard: { [weak self] in self?.advanceToNextInputMode() },
            onPaste: { [weak self] in
                if let s = UIPasteboard.general.string { self?.textDocumentProxy.insertText(s) }
            },
            onInsertDictation: { [weak self] s in self?.insertDictation(s) }
        )
    }

    /// The strip's text: the latest dictation, or, when clean-up left nothing (e.g. the whole recording was
    /// "undo that"), the latest recording's own words if they are an edit command, so the command can still run.
    private func pendingDictation() -> String {
        let cleaned = SharedStore.latestDictation
        let spoken = SharedStore.latestUtterance
        if cleaned.isEmpty, SharedStore.voiceEdit, VoiceEdit.parse(spoken) != nil { return spoken }
        return cleaned
    }

    /// Edit by voice: a dictated "change Tuesday to Wednesday" (etc.) edits the text before the cursor; "undo that"
    /// removes only this keyboard's own last insert. Anything else is inserted as normal dictation. The command is
    /// read from the latest recording's raw words (not the accumulated, cleaned dictation), and iOS may hand over
    /// only part of the field, so a match at the very start of that context is refused (it may be mid-word).
    private func insertDictation(_ s: String) {
        let proxy = textDocumentProxy
        let before = proxy.documentContextBeforeInput ?? ""
        let spoken = SharedStore.latestUtterance
        if SharedStore.voiceEdit, !spoken.isEmpty,
           let r = VoiceEdit.voiceCommand(text: before, utterance: spoken, truncated: true) {
            if let out = r.outcome {
                let edit = VoiceEdit.fieldEdit(before: before, outcome: out)
                for _ in 0..<edit.deleteCount { proxy.deleteBackward() }
                if !edit.insert.isEmpty { proxy.insertText(edit.insert) }
            } else {
                let n = VoiceEdit.undoCount(before: before, lastInsert: lastDictationInsert)
                for _ in 0..<n { proxy.deleteBackward() }
            }
            lastDictationInsert = nil
            SharedStore.latestUtterance = ""  // one edit per recording
            return
        }
        proxy.insertText(s)
        lastDictationInsert = s
    }
}
