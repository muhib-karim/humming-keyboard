import SwiftUI
import UIKit

struct DictateView: View {
    @EnvironmentObject var dictation: Dictation
    @EnvironmentObject var settings: AppSettings
    @State private var copied = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                HStack(spacing: 8) {
                    Chip(text: "Offline", systemImage: "airplane")
                    if settings.accuracyMode { Chip(text: "Accuracy mode", systemImage: "scope") }
                    Spacer()
                }
                ScrollView {
                    transcript
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                }
                .background(RoundedRectangle(cornerRadius: 18).fill(Color(.secondarySystemBackground)))

                status
                MicButton(listening: dictation.isListening) { dictation.toggle() }
                    .accessibilityIdentifier("mic")

                HStack(spacing: 12) {
                    Button {
                        UIPasteboard.general.string = dictation.committed
                        SharedStore.latestDictation = dictation.committed
                        copied = true
                    } label: {
                        Label(copied ? "Ready in keyboard" : "Copy & use in keyboard", systemImage: copied ? "checkmark" : "doc.on.doc")
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(dictation.committed.isEmpty)
                    Button(role: .destructive) { dictation.clear(); copied = false } label: {
                        Label("Clear", systemImage: "trash")
                    }
                    .buttonStyle(.bordered)
                    .disabled(dictation.committed.isEmpty && dictation.partial.isEmpty)
                }
            }
            .padding()
            .navigationTitle("Humming")
        }
    }

    private var transcript: some View {
        Group {
            if dictation.committed.isEmpty && dictation.partial.isEmpty {
                Text("Tap the mic and talk. Your words appear here as you speak, cleaned up, and nothing leaves this iPhone.")
                    .foregroundColor(.secondary)
            } else {
                (Text(dictation.committed)
                    + Text(dictation.committed.isEmpty || dictation.partial.isEmpty ? "" : " ")
                    + Text(dictation.partial).foregroundColor(.secondary))
            }
        }
        .font(.title3)
    }

    @ViewBuilder private var status: some View {
        switch dictation.phase {
        case .listening:
            Label("Listening, on this iPhone. Tap to stop.", systemImage: "waveform")
                .font(.subheadline).foregroundColor(Brand.accentText)
        case .unavailable(let why):
            Text(why).font(.footnote).foregroundColor(.orange).multilineTextAlignment(.center)
        case .idle:
            Text("One control: tap to start, tap again to stop. No time limit.")
                .font(.subheadline).foregroundColor(.secondary)
        }
    }
}

struct Chip: View {
    var text: String
    var systemImage: String
    var body: some View {
        Label(text, systemImage: systemImage)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10).padding(.vertical, 5)
            .background(Capsule().fill(Brand.accent.opacity(0.15)))
            .foregroundColor(Brand.accentText)
    }
}

struct MicButton: View {
    var listening: Bool
    var action: () -> Void
    @State private var pulse = false

    var body: some View {
        Button(action: action) {
            ZStack {
                if listening {
                    Circle().stroke(Brand.accent.opacity(0.35), lineWidth: 10)
                        .frame(width: 112, height: 112)
                        .scaleEffect(pulse ? 1.15 : 0.95)
                        .animation(.easeInOut(duration: 0.8).repeatForever(autoreverses: true), value: pulse)
                        .onAppear { pulse = true }
                        .onDisappear { pulse = false }
                }
                Circle().fill(Brand.accent).frame(width: 88, height: 88)
                    .shadow(color: Brand.accent.opacity(0.4), radius: 10, y: 4)
                Image(systemName: listening ? "stop.fill" : "mic.fill")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundColor(.white)
            }
            .frame(width: 120, height: 120)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(listening ? "Stop dictation" : "Start dictation")
    }
}
