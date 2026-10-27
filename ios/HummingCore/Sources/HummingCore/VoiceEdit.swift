import Foundation

/// D5 voice-command editing: a whole dictated utterance such as "change Tuesday to Wednesday" edits the text the
/// user already has instead of being inserted. Swift port of core/src/voice_edit.rs (and the Android VoiceEdit.kt)
/// with the same rules, checked against the same shared vectors (core/testdata/voice_edit_vectors.json).
///
/// Safety rules: whole utterance only (a leading "um"/"okay" and a trailing full stop are ignored); at most
/// 14 words; the target must exist in the text before the cursor, otherwise the utterance stays dictation.
/// The match closest to the cursor (last, whole-word, case-insensitive) is the one edited.
///
/// Offsets are UTF-16 indices (NSString); `fieldEdit` turns them into the character count that
/// `UITextDocumentProxy.deleteBackward()` works in.
public enum VoiceEdit {
    public enum Command: Equatable {
        case replace(target: String, replacement: String)
        case delete(target: String)
        case capitalize(target: String)
        /// "undo that" / "scratch that": the host removes its own last insertion.
        case undo
    }

    /// `text` is the whole edited text; the UTF-16 span [start, start+oldLen) of the original now reads `inserted`.
    public struct Outcome: Equatable {
        public let text: String
        public let start: Int
        public let oldLen: Int
        public let inserted: String
    }

    public struct Result: Equatable {
        public let command: Command
        public let outcome: Outcome?
    }

    static let maxWords = 14
    private static func re(_ p: String) -> NSRegularExpression {
        // swiftlint:disable:next force_try
        try! NSRegularExpression(pattern: p, options: [.caseInsensitive])
    }
    private static let lead = re("^(?:(?:um+|uh+|okay|ok|so|please|hey humming|humming)[,\\s]+)+")
    private static let trail = re("(?:[,\\s]+please)?[\\s.!?,]*$")
    private static let reReplace = re(
        "^(?:change|replace|switch)\\s+[\"\u{201C}]?(.+?)[\"\u{201D}]?\\s+(?:to|with|into)\\s+[\"\u{201C}]?(.+?)[\"\u{201D}]?$")
    private static let reDelete = re("^(?:delete|remove|erase)\\s+(?:the\\s+word\\s+)?[\"\u{201C}]?(.+?)[\"\u{201D}]?$")
    private static let reCap = re("^(?:capitalize|capitalise)\\s+(?:the\\s+word\\s+)?[\"\u{201C}]?(.+?)[\"\u{201D}]?$")
    private static let reUndo = re(
        "^(?:undo(?:\\s+that)?|scratch\\s+that|delete\\s+that|undo\\s+the\\s+last\\s+(?:bit|part|sentence))$")

    private static func replaceFirst(_ r: NSRegularExpression, _ s: String) -> String {
        let ns = s as NSString
        guard let m = r.firstMatch(in: s, options: [], range: NSRange(location: 0, length: ns.length)) else { return s }
        return ns.replacingCharacters(in: m.range, with: "")
    }

    private static func fullMatch(_ r: NSRegularExpression, _ s: String) -> [String]? {
        let ns = s as NSString
        guard let m = r.firstMatch(in: s, options: [.anchored], range: NSRange(location: 0, length: ns.length)),
              m.range.location == 0, m.range.length == ns.length else { return nil }
        var groups: [String] = []
        for i in 1..<max(1, m.numberOfRanges) {
            let g = m.range(at: i)
            groups.append(g.location == NSNotFound ? "" : ns.substring(with: g))
        }
        return groups
    }

    private static func trimmed(_ s: String) -> String { s.trimmingCharacters(in: .whitespacesAndNewlines) }

    static func normalise(_ utterance: String) -> String? {
        var t = trimmed(utterance)
        t = replaceFirst(lead, t)
        t = replaceFirst(trail, t)
        t = trimmed(t)
        if t.isEmpty { return nil }
        let words = t.split(whereSeparator: { $0.isWhitespace })
        if words.count > maxWords { return nil }
        return t
    }

    /// Parse an utterance as a command without looking at the text (cheap: no editor read needed).
    public static func parse(_ utterance: String) -> Command? {
        guard let t = normalise(utterance) else { return nil }
        if fullMatch(reUndo, t) != nil { return .undo }
        if let g = fullMatch(reReplace, t), g.count >= 2 {
            let a = trimmed(g[0]), b = trimmed(g[1])
            if !a.isEmpty && !b.isEmpty && a.lowercased() != b.lowercased() { return .replace(target: a, replacement: b) }
            return nil
        }
        if let g = fullMatch(reCap, t), let a = g.first { return .capitalize(target: trimmed(a)) }
        if let g = fullMatch(reDelete, t), let a = g.first { return .delete(target: trimmed(a)) }
        return nil
    }

    /// Last whole-word, case-insensitive occurrence of `target` in `text` as a UTF-16 (start, end).
    static func findLast(_ text: String, _ target: String) -> (Int, Int)? {
        let r = re("(?:^|\\b|\\s)" + NSRegularExpression.escapedPattern(for: target) + "(?:\\b|$)")
        let ns = text as NSString
        var last: (Int, Int)? = nil
        var pos = 0
        while pos <= ns.length {
            let range = NSRange(location: pos, length: ns.length - pos)
            guard let m = r.firstMatch(in: text, options: [.withTransparentBounds, .withoutAnchoringBounds], range: range)
            else { break }
            let s = ns.substring(with: m.range) as NSString
            var lead = 0
            while lead < s.length, let u = Unicode.Scalar(s.character(at: lead)), CharacterSet.whitespacesAndNewlines.contains(u) {
                lead += 1
            }
            last = (m.range.location + lead, m.range.location + m.range.length)
            if m.range.length > 0 {
                pos = m.range.location + ns.rangeOfComposedCharacterSequence(at: m.range.location).length
            } else {
                pos = m.range.location + 1
            }
        }
        return last
    }

    private static func upperFirst(_ s: String) -> String {
        guard let f = s.first else { return s }
        return String(f).uppercased() + s.dropFirst()
    }

    static func matchCase(_ original: String, _ replacement: String) -> String {
        let firstUpper = original.first?.isUppercase ?? false
        let letters = original.filter { $0.isLetter }
        if letters.count > 1 && letters.allSatisfy({ $0.isUppercase }) { return replacement.uppercased() }
        if firstUpper && !replacement.contains(where: { $0.isUppercase }) { return upperFirst(replacement) }
        return replacement
    }

    /// Apply `cmd` to `text` (the text before the cursor); nil when it can't apply (target absent, or undo).
    public static func apply(_ text: String, _ cmd: Command) -> Outcome? {
        let ns = text as NSString
        func sub(_ a: Int, _ b: Int) -> String { ns.substring(with: NSRange(location: a, length: b - a)) }
        switch cmd {
        case .undo:
            return nil
        case let .replace(target, replacement):
            guard let (s, e) = findLast(text, target) else { return nil }
            let ins = matchCase(sub(s, e), replacement)
            return Outcome(text: sub(0, s) + ins + sub(e, ns.length), start: s, oldLen: e - s, inserted: ins)
        case let .capitalize(target):
            guard let (s, e) = findLast(text, target) else { return nil }
            let ins = sub(s, e).components(separatedBy: " ").map(upperFirst).joined(separator: " ")
            return Outcome(text: sub(0, s) + ins + sub(e, ns.length), start: s, oldLen: e - s, inserted: ins)
        case let .delete(target):
            guard let (s, e) = findLast(text, target) else { return nil }
            let before = sub(0, s), after = sub(e, ns.length)
            if before.hasSuffix(" ") && (after.isEmpty || after.hasPrefix(" ") || ",.!?;:".contains(after.first!)) {
                return Outcome(text: sub(0, s - 1) + after, start: s - 1, oldLen: e - s + 1, inserted: "")
            } else if after.hasPrefix(" ") {
                return Outcome(text: before + String(after.dropFirst()), start: s, oldLen: e + 1 - s, inserted: "")
            } else {
                return Outcome(text: before + after, start: s, oldLen: e - s, inserted: "")
            }
        }
    }

    /// The one call hosts make. Undo comes back with a nil outcome; anything else that isn't a command which
    /// applies to `text` returns nil and the host inserts the utterance as dictation. `truncated`: `text` is a
    /// window cut from a longer field, so its first character may sit mid-word and a match there is refused.
    public static func voiceCommand(text: String, utterance: String, truncated: Bool = false) -> Result? {
        guard let cmd = parse(utterance) else { return nil }
        if cmd == .undo { return Result(command: cmd, outcome: nil) }
        guard let out = apply(text, cmd) else { return nil }
        if truncated && out.start == 0 { return nil }
        return Result(command: cmd, outcome: out)
    }

    /// How a text field applies an outcome computed on `before` (the text before the cursor): call
    /// deleteBackward() `deleteCount` times (characters), then insertText(`insert`). Leaves exactly `outcome.text`.
    public static func fieldEdit(before: String, outcome: Outcome) -> (deleteCount: Int, insert: String) {
        let ns = before as NSString
        let removed = ns.substring(from: outcome.start)
        let insert = (outcome.text as NSString).substring(from: outcome.start)
        return (removed.count, insert)
    }

    /// Characters to delete for "undo that": the keyboard's own last insertion, only when it still sits right
    /// before the cursor (never anything the user typed or another app wrote).
    public static func undoCount(before: String, lastInsert: String?) -> Int {
        guard let l = lastInsert, !l.isEmpty, before.hasSuffix(l) else { return 0 }
        return l.count
    }
}
