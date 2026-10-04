import SwiftUI

@main
struct HummingApp: App {
    @StateObject private var settings = AppSettings()
    @StateObject private var dictation = Dictation()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(settings)
                .environmentObject(dictation)
                .tint(Brand.accent)
                .onAppear { dictation.settings = settings }
        }
    }
}

/// Launch arguments used only by CI to take screenshots on the simulator (it has no microphone):
/// `-demo dictate|listening|keyboard|settings|walkthrough`.
enum Demo {
    static var mode: String? { UserDefaults.standard.string(forKey: "demo") }
    static let sample = "Please send the quarterly report to the whole team by Friday afternoon."
}

struct RootView: View {
    @EnvironmentObject var dictation: Dictation
    @State private var tab = 0

    var body: some View {
        TabView(selection: $tab) {
            DictateView()
                .tabItem { Label("Dictate", systemImage: "mic.fill") }.tag(0)
            KeyboardTab()
                .tabItem { Label("Keyboard", systemImage: "keyboard") }.tag(1)
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape") }.tag(2)
        }
        .onAppear {
            switch Demo.mode {
            case "dictate": dictation.demo(committed: Demo.sample, partial: "")
            case "listening": dictation.demo(committed: "Meet at 4pm on Friday.", partial: "and bring the slides", listening: true)
            case "walkthrough": dictation.demoWalkthrough()
            case "keyboard": dictation.demo(committed: Demo.sample, partial: ""); tab = 1
            case "settings": tab = 2
            default: break
            }
        }
    }
}
