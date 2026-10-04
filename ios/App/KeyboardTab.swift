import SwiftUI

struct KeyboardTab: View {
    @EnvironmentObject var dictation: Dictation
    @State private var tryText = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Turn on the Humming keyboard")
                        .font(.headline)
                    VStack(alignment: .leading, spacing: 10) {
                        Step(n: 1, text: "Open Settings > General > Keyboard > Keyboards.")
                        Step(n: 2, text: "Tap Add New Keyboard and choose Humming.")
                        Step(n: 3, text: "Optional: tap Humming > Allow Full Access to paste what you copied. Humming has no network code either way.")
                        Step(n: 4, text: "In any app, hold the globe key and pick Humming. Dictate here, then tap the red strip to insert.")
                    }
                    Text("Try it").font(.headline).padding(.top, 6)
                    Text(tryText.isEmpty ? "Type with the keyboard below, or tap the strip to insert your dictation." : tryText)
                        .foregroundColor(tryText.isEmpty ? .secondary : .primary)
                        .frame(maxWidth: .infinity, minHeight: 60, alignment: .topLeading)
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                }
                .padding()
            }
            .safeAreaInset(edge: .bottom) {
                KeyboardView(
                    dictation: dictation.committed,
                    showGlobe: true,
                    onInsert: { tryText += $0 },
                    onBackspace: { if !tryText.isEmpty { tryText.removeLast() } }
                )
            }
            .navigationTitle("Keyboard")
        }
    }
}

struct Step: View {
    var n: Int
    var text: String
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Text("\(n)")
                .font(.caption.bold()).foregroundColor(.white)
                .frame(width: 22, height: 22)
                .background(Circle().fill(Brand.accent))
            Text(text).font(.subheadline)
        }
    }
}
