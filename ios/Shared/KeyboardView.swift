import SwiftUI

/// The Humming keyboard layout. The keyboard extension hosts it, and the app shows the same view on its Keyboard tab
/// so you can try it before enabling it.
struct KeyboardView: View {
    var dictation: String
    var showGlobe: Bool
    var canPaste: Bool = false
    var onInsert: (String) -> Void
    var onBackspace: () -> Void
    var onNextKeyboard: () -> Void = {}
    var onPaste: () -> Void = {}

    @State private var shifted = true
    @State private var numbers = false

    private let letters = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
    private let symbols = ["1234567890", "-/:;()$&@\"", ".,?!'"]

    var body: some View {
        VStack(spacing: 8) {
            strip
            let rows = numbers ? symbols : letters
            row(rows[0])
            row(rows[1]).padding(.horizontal, numbers ? 0 : 16)
            HStack(spacing: 6) {
                special(numbers ? "#+=" : (shifted ? "\u{21E7}" : "\u{21EA}"), width: 44) {
                    if !numbers { shifted.toggle() }
                }
                row(rows[2])
                special("\u{232B}", width: 44, action: onBackspace)
            }
            HStack(spacing: 6) {
                special(numbers ? "ABC" : "123", width: 52) { numbers.toggle() }
                if showGlobe { special("\u{1F310}", width: 44, action: onNextKeyboard) }
                key("space", wide: true) { onInsert(" ") }
                special("return", width: 80) { onInsert("\n") }
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .background(Color(.systemGray5))
    }

    private var strip: some View {
        HStack(spacing: 8) {
            if !dictation.isEmpty {
                Button { onInsert(dictation) } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "waveform")
                        Text(dictation).lineLimit(1).truncationMode(.tail)
                    }
                    .font(.footnote.weight(.semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(Capsule().fill(Brand.accentStrong))
                }
                .accessibilityLabel("Insert your latest dictation")
            } else {
                Label("Dictate in the Humming app, then tap here to insert", systemImage: "mic")
                    .font(.footnote).foregroundColor(.secondary).lineLimit(1)
            }
            Spacer(minLength: 0)
            if canPaste {
                Button(action: onPaste) { Image(systemName: "doc.on.clipboard") }
                    .accessibilityLabel("Paste")
            }
        }
        .frame(height: 32)
        .padding(.horizontal, 6)
    }

    private func row(_ chars: String) -> some View {
        HStack(spacing: 6) {
            ForEach(Array(chars), id: \.self) { c in
                let s = shifted && !numbers ? String(c).uppercased() : String(c)
                key(s) {
                    onInsert(s)
                    if shifted && !numbers { shifted = false }
                }
            }
        }
    }

    private func key(_ label: String, wide: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: wide ? 16 : 22))
                .foregroundColor(.primary)
                .frame(maxWidth: .infinity, minHeight: 42)
                .background(RoundedRectangle(cornerRadius: 6).fill(Color(.systemBackground)))
                .shadow(color: .black.opacity(0.25), radius: 0, x: 0, y: 1)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func special(_ label: String, width: CGFloat, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 16))
                .foregroundColor(.primary)
                .frame(width: width, height: 42)
                .background(RoundedRectangle(cornerRadius: 6).fill(Color(.systemGray3)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
