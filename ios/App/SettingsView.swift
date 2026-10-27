import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var settings: AppSettings
    @State private var newWord = ""

    private var version: String {
        let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        let b = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        return "\(v) (\(b))"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("Clean mode", isOn: $settings.cleanMode)
                    Toggle("Remove filler words", isOn: $settings.removeFillers)
                        .disabled(!settings.cleanMode)
                    Toggle("Accuracy mode", isOn: $settings.accuracyMode)
                        .disabled(!settings.cleanMode || !settings.removeFillers)
                    Toggle("Edit by voice", isOn: $settings.voiceEdit)
                } header: {
                    Text("Voice")
                } footer: {
                    Text("Clean mode removes stutters and self-corrections (\"3, no, 4pm\" becomes \"4pm\") and turns \"bullet point\" and \"first, ... second, ...\" into lists; say \"scratch that\" to drop the sentence before. Remove filler words drops um, uh and \"you know\" between commas. Accuracy mode also removes hedge fillers like \"like\", \"basically\" and \"you know\" when they are set off by commas, and a spoken \"period\" at the end. Punctuation comes from the on-device recogniser.")
                }

                Section {
                    ForEach(settings.customWords, id: \.self) { Text($0) }
                        .onDelete { settings.customWords.remove(atOffsets: $0) }
                    HStack {
                        TextField("Add a word, or spoken -> written", text: $newWord)
                            .textInputAutocapitalization(.never)
                            .onSubmit(add)
                        Button("Add", action: add).disabled(newWord.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                } header: {
                    Text("Custom words")
                } footer: {
                    Text("Spelled your way every time, and passed to the recogniser as hints. To replace what you say, add \"spoken -> written\", e.g. \"cube cuddle -> kubectl\".")
                }

                Section("Privacy") {
                    Label("Your voice is recognised on this iPhone. Humming asks iOS for on-device recognition only and never falls back to a server.", systemImage: "lock.shield")
                    Label("No account, no analytics, no ads, and no network code.", systemImage: "wifi.slash")
                }

                Section("About") {
                    LabeledContent("Version", value: version)
                    LabeledContent("Speech engine", value: "Apple on-device")
                    LabeledContent("Licence", value: "Apache-2.0")
                }
            }
            .navigationTitle("Settings")
        }
    }

    private func add() {
        let w = newWord.trimmingCharacters(in: .whitespaces)
        guard !w.isEmpty, !settings.customWords.contains(w) else { return }
        settings.customWords.append(w)
        newWord = ""
    }
}
