import Foundation
import SwiftUI
import UIKit

/// State the app and the keyboard extension share: the latest dictation, through an App Group when the signing
/// identity carries one, otherwise each side's own defaults (the keyboard then offers the clipboard instead).
enum SharedStore {
    static let groupID = "group.com.humming.ios"
    static let defaults: UserDefaults = UserDefaults(suiteName: groupID) ?? .standard
    private static let latestKey = "latest_dictation"

    private static let voiceEditKey = "voice_edit"
    /// Edit by voice (1.8, default on): a dictated "change X to Y", "delete X", "capitalize X" or "undo that" edits
    /// the text before the cursor instead of being typed (HummingCore VoiceEdit, same rules as Android and the core).
    static var voiceEdit: Bool {
        get { defaults.object(forKey: voiceEditKey) as? Bool ?? true }
        set { defaults.set(newValue, forKey: voiceEditKey) }
    }

    static var latestDictation: String {
        get { defaults.string(forKey: latestKey) ?? "" }
        set { defaults.set(newValue, forKey: latestKey) }
    }

    private static let utteranceKey = "latest_utterance"
    /// The raw words of the latest recording (every recognition chunk since the mic was turned on), before
    /// clean-up (1.8): what edit by voice reads, so an
    /// earlier recording in `latestDictation` or a clean-up rule can't turn dictation into a command or hide one.
    static var latestUtterance: String {
        get { defaults.string(forKey: utteranceKey) ?? "" }
        set { defaults.set(newValue, forKey: utteranceKey) }
    }
}

/// Humming's colours: the same tokens as Android and desktop (docs/BRAND.md "Design tokens").
enum Brand {
    /// Brand fill: icons, rings, tints. Not for small text (3.6:1 on white).
    static let accent = Color(red: 0xE5 / 255, green: 0x48 / 255, blue: 0x6B / 255)
    /// Filled controls with white text (4.95:1).
    static let accentStrong = Color(red: 0xC9 / 255, green: 0x3A / 255, blue: 0x5B / 255)
    /// Accent text: #C93A5B on light (4.6:1+), #F0607F on dark (5.5:1+).
    static let accentText = Color(UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0xF0 / 255, green: 0x60 / 255, blue: 0x7F / 255, alpha: 1)
            : UIColor(red: 0xC9 / 255, green: 0x3A / 255, blue: 0x5B / 255, alpha: 1)
    })
    static let ink = Color(red: 0x0F / 255, green: 0x0F / 255, blue: 0x13 / 255)
}
