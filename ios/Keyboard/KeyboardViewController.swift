import SwiftUI
import UIKit

/// The Humming keyboard extension. iOS keyboards cannot use the microphone, so dictation happens in the Humming app
/// and the keyboard's top strip inserts the latest result (or, with Full Access, whatever you copied).
final class KeyboardViewController: UIInputViewController {
    private var host: UIHostingController<KeyboardView>?

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
            dictation: SharedStore.latestDictation,
            showGlobe: needsInputModeSwitchKey,
            canPaste: hasFullAccess,
            onInsert: { [weak self] s in self?.textDocumentProxy.insertText(s) },
            onBackspace: { [weak self] in self?.textDocumentProxy.deleteBackward() },
            onNextKeyboard: { [weak self] in self?.advanceToNextInputMode() },
            onPaste: { [weak self] in
                if let s = UIPasteboard.general.string { self?.textDocumentProxy.insertText(s) }
            }
        )
    }
}
