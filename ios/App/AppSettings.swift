import Foundation

/// Voice settings, stored in the shared defaults so the keyboard extension can read them too.
final class AppSettings: ObservableObject {
    private let d = SharedStore.defaults

    @Published var cleanMode: Bool { didSet { d.set(cleanMode, forKey: "clean_mode") } }
    @Published var accuracyMode: Bool { didSet { d.set(accuracyMode, forKey: "accuracy_mode") } }
    @Published var customWords: [String] { didSet { d.set(customWords, forKey: "custom_words") } }

    init() {
        cleanMode = d.object(forKey: "clean_mode") as? Bool ?? true
        accuracyMode = d.object(forKey: "accuracy_mode") as? Bool ?? false
        customWords = d.stringArray(forKey: "custom_words") ?? []
        if Demo.mode != nil {
            accuracyMode = true
            customWords = ["Humming", "Okonkwo"]
        }
    }
}
