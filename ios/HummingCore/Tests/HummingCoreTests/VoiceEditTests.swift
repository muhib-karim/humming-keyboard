import Foundation
import XCTest
@testable import HummingCore

/// Runs the SAME vectors as the Rust core and Android (core/testdata/voice_edit_vectors.json), so all three stay in step.
final class VoiceEditTests: XCTestCase {
    private struct Vector: Decodable {
        let text: String
        let utterance: String
        let command: Bool
        let undo: Bool?
        let expect: String?
    }

    private func vectors() throws -> [Vector] {
        // Tests/HummingCoreTests/VoiceEditTests.swift -> repo root is five levels up.
        var dir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        for _ in 0..<8 {
            let f = dir.appendingPathComponent("core/testdata/voice_edit_vectors.json")
            if FileManager.default.fileExists(atPath: f.path) {
                return try JSONDecoder().decode([Vector].self, from: Data(contentsOf: f))
            }
            dir = dir.deletingLastPathComponent()
        }
        XCTFail("core/testdata/voice_edit_vectors.json not found above \(#filePath)")
        return []
    }

    func testSharedVectors() throws {
        let vs = try vectors()
        XCTAssertGreaterThanOrEqual(vs.count, 30, "need a real vector set")
        for v in vs {
            let got = VoiceEdit.voiceCommand(text: v.text, utterance: v.utterance)
            if !v.command { XCTAssertNil(got, "false positive: \(v.utterance) on \(v.text)"); continue }
            guard let r = got else { XCTFail("missed command: \(v.utterance) on \(v.text)"); continue }
            if v.undo == true { XCTAssertEqual(r.command, .undo); continue }
            guard let out = r.outcome else { XCTFail("no outcome: \(v.utterance)"); continue }
            XCTAssertEqual(out.text, v.expect, "utterance \(v.utterance)")
            let ns = v.text as NSString
            let rebuilt = ns.substring(to: out.start) + out.inserted + ns.substring(from: out.start + out.oldLen)
            XCTAssertEqual(out.text, rebuilt, "span mismatch for \(v.utterance)")
            // What the keyboard does to the field (deleteBackward n times, insertText) leaves exactly the edit.
            let fe = VoiceEdit.fieldEdit(before: v.text, outcome: out)
            XCTAssertEqual(out.text, String(v.text.dropLast(fe.deleteCount)) + fe.insert, "field edit for \(v.utterance)")
        }
    }

    func testLongSentenceIsNeverACommand() {
        XCTAssertNil(VoiceEdit.parse("change of plans we are going to the beach to meet with Sam and his family later today after lunch"))
    }

    func testOffsetsAreUtf16() {
        let o = VoiceEdit.voiceCommand(text: "café is open", utterance: "change open to closed")?.outcome
        XCTAssertEqual(o?.start, 8)
        XCTAssertEqual(o?.text, "café is closed")
    }

    func testFieldEditCountsCharactersForDeleteBackward() {
        let before = "I love 👍🏽 emoji"
        let o = VoiceEdit.voiceCommand(text: before, utterance: "change love to like")!.outcome!
        let fe = VoiceEdit.fieldEdit(before: before, outcome: o)
        XCTAssertEqual(String(before.dropLast(fe.deleteCount)) + fe.insert, "I like 👍🏽 emoji")
    }

    func testUndoOnlyRemovesOwnLastInsert() {
        XCTAssertEqual(VoiceEdit.undoCount(before: "Hello there. See you soon", lastInsert: "See you soon"), 12)
        XCTAssertEqual(VoiceEdit.undoCount(before: "Hello there. typed by me", lastInsert: "See you soon"), 0)
        XCTAssertEqual(VoiceEdit.undoCount(before: "anything", lastInsert: nil), 0)
    }

    func testTruncatedWindowRefusesAMatchAtItsStart() {
        // Codex review finding 6: "Mon|day" cut by the context window is not the word "day".
        let full = "Monday " + String(repeating: "x", count: 996)
        let window = String(full.suffix(1000))
        XCTAssertTrue(window.hasPrefix("day "))
        XCTAssertNil(VoiceEdit.voiceCommand(text: window, utterance: "change day to night", truncated: true))
        XCTAssertNotNil(VoiceEdit.voiceCommand(text: "day one", utterance: "change day to night"))
        XCTAssertNotNil(VoiceEdit.voiceCommand(text: "the day one", utterance: "change day to night", truncated: true))
    }
}
