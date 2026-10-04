import Foundation
import SwiftUI

/// State the app and the keyboard extension share: the latest dictation, through an App Group when the signing
/// identity carries one, otherwise each side's own defaults (the keyboard then offers the clipboard instead).
enum SharedStore {
    static let groupID = "group.com.humming.ios"
    static let defaults: UserDefaults = UserDefaults(suiteName: groupID) ?? .standard
    private static let latestKey = "latest_dictation"

    static var latestDictation: String {
        get { defaults.string(forKey: latestKey) ?? "" }
        set { defaults.set(newValue, forKey: latestKey) }
    }
}

/// Humming's colours (the same accent as Android and desktop).
enum Brand {
    static let accent = Color(red: 0xE5 / 255, green: 0x48 / 255, blue: 0x6B / 255)
    static let ink = Color(red: 0x0F / 255, green: 0x0F / 255, blue: 0x13 / 255)
}
