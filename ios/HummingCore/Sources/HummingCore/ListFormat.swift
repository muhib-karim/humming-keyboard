import Foundation

/// 1.7: lists in dictation, the Swift port of Android's `ListFormat.kt` (desktop: `text_cleanup::bullet_list` and core's
/// `format_spoken_list`). Runs on the finished text, after `TextCleanup.finish`. Covered by `ListFormatTests`.
///
/// - "bullet point" / "next bullet" / "new bullet" starts an item: "Groceries bullet point eggs bullet point milk" →
///   "Groceries:\n- Eggs\n- Milk". A marker after "the", "a", "second"... is the thing itself and stays prose.
/// - A spoken list ("first, eggs, second, milk" / "number one, ..., number two, ...") becomes bullets too; it needs an
///   opener and a second marker, so a lone "first" in prose is left alone.
public enum ListFormat {
    private static let bullet = try! NSRegularExpression(
        pattern: "[ \\t]*[,;:]?[ \\t]*\\b(?:bullet point|next bullet|new bullet)\\b[,.;:]?[ \\t]*", options: [.caseInsensitive])
    private static let notAfter: Set<String> = [
        "a", "an", "the", "this", "that", "each", "every", "another", "my", "your", "our", "first", "second", "third",
        "last", "one",
    ]
    private static let listMarker = try! NSRegularExpression(
        pattern: "(?:^|[\\s,.;:])((?:and\\s+)?(?:first(?:ly)?|second(?:ly)?|third(?:ly)?|fourth(?:ly)?|fifth(?:ly)?|finally|lastly|"
            + "number\\s+(?:one|two|three|four|five|[1-5])))\\b,\\s+",
        options: [.caseInsensitive])

    public static func apply(_ text: String) -> String { bullets(text) ?? spokenList(text) ?? text }

    /// The text with "bullet point" items as "- " lines, or nil when it has no (non-empty) item.
    public static func bullets(_ text: String) -> String? {
        let ns = text as NSString
        var parts: [String] = []
        var from = 0
        for m in bullet.matches(in: text, options: [], range: NSRange(location: 0, length: ns.length)) {
            let before = ns.substring(to: m.range.location)
            let prev = before.split(whereSeparator: { $0.isWhitespace }).last.map(String.init) ?? ""
            if notAfter.contains(prev.lowercased()) { continue }
            parts.append(ns.substring(with: NSRange(location: from, length: m.range.location - from)))
            from = m.range.location + m.range.length
        }
        parts.append(ns.substring(from: from))
        if parts.count < 2 { return nil }
        var items: [String] = []
        var tail = ""
        for i in 1..<parts.count {
            var p = parts[i]
            if let nl = p.firstIndex(of: "\n") {
                // The last item ends at its line break and the rest follows the list; earlier items are one line.
                if i == parts.count - 1 {
                    tail = String(p[nl...])
                    p = String(p[..<nl])
                } else {
                    p = p.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
                }
            }
            let item = trimItem(p)
            if !item.isEmpty { items.append(capitalize(item)) }
        }
        if items.isEmpty { return nil }
        var intro = parts[0].trimmingCharacters(in: .whitespacesAndNewlines)
        if let last = intro.last {
            if last.isLetter || last.isNumber {
                intro += ":"
            } else if last == "," || last == ";" {
                intro = String(intro.dropLast()) + ":"
            }
        }
        let head = intro.isEmpty ? "" : intro + "\n"
        return head + items.map { "- " + $0 }.joined(separator: "\n") + tail
    }

    /// "The list is first, eggs, second, milk." → "The list is:\n- Eggs\n- Milk", or nil when it is not a list.
    public static func spokenList(_ text: String) -> String? {
        let ns = text as NSString
        let marks: [(start: Int, end: Int, word: String)] =
            listMarker.matches(in: text, options: [], range: NSRange(location: 0, length: ns.length)).map {
                (m: NSTextCheckingResult) -> (start: Int, end: Int, word: String) in
                let g = m.range(at: 1)
                return (start: g.location, end: m.range.location + m.range.length, word: ns.substring(with: g).lowercased())
            }
        func strip(_ w: String) -> String { w.hasPrefix("and ") ? String(w.dropFirst(4)) : w }
        guard let opener = marks.firstIndex(where: { m in
            let w = strip(m.word).trimmingCharacters(in: .whitespaces)
            return w.hasPrefix("first") || w == "number one" || w == "number 1"
        }), marks.count - opener >= 2 else { return nil }
        let second = strip(marks[opener + 1].word)
        guard second.hasPrefix("second") || second.hasSuffix("two") || second.hasSuffix("2") || second == "finally" else {
            return nil
        }
        var intro = ns.substring(to: marks[opener].start)
        while let c = intro.last, c.isWhitespace || ",.;:".contains(c) { intro.removeLast() }
        var items: [String] = []
        for k in opener..<marks.count {
            let end = k + 1 < marks.count ? marks[k + 1].start : ns.length
            var raw = trimItem(ns.substring(with: NSRange(location: marks[k].end, length: end - marks[k].end)))
            if raw.hasSuffix(" and") { raw = trimItem(String(raw.dropLast(4))) }
            if raw.isEmpty { return nil }
            items.append(capitalize(raw))
        }
        return (intro.isEmpty ? "" : intro + ":\n") + "- " + items.joined(separator: "\n- ")
    }

    private static func trimItem(_ s: String) -> String {
        var t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        while let c = t.last, ",.;:".contains(c) { t.removeLast() }
        return t.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func capitalize(_ s: String) -> String { s.prefix(1).uppercased() + s.dropFirst() }
}
