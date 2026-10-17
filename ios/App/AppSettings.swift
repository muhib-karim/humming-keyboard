import Foundation

/// Voice settings, stored in the shared defaults so the keyboard extension can read them too.
final class AppSettings: ObservableObject {
    private let d = SharedStore.defaults

    @Published var cleanMode: Bool { didSet { d.set(cleanMode, forKey: "clean_mode") } }
    @Published var accuracyMode: Bool { didSet { d.set(accuracyMode, forKey: "accuracy_mode") } }
    /// Remove filler words (1.7, default on): um, uh, er, hmm and ", you know,". Part of Clean mode.
    @Published var removeFillers: Bool { didSet { d.set(removeFillers, forKey: "remove_fillers") } }
    @Published var customWords: [String] { didSet { d.set(customWords, forKey: "custom_words") } }

    init() {
        cleanMode = d.object(forKey: "clean_mode") as? Bool ?? true
        accuracyMode = d.object(forKey: "accuracy_mode") as? Bool ?? false
        removeFillers = d.object(forKey: "remove_fillers") as? Bool ?? true
        customWords = d.stringArray(forKey: "custom_words") ?? []
        if Demo.mode != nil {
            accuracyMode = true
            customWords = ["Humming", "Okonkwo"]
        }
    }
}
