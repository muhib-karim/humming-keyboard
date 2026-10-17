import Foundation

/// Rule-based dictation clean-up, a Swift port of the Android `TextCleanup.kt` and desktop `text_cleanup.rs`, so the
/// same words come out on every platform. Pure Foundation (no UIKit), covered by `TextCleanupTests`.
///
/// Order: drop non-speech/hallucinations → fillers → spoken punctuation → (accuracy mode: hedge fillers) → leading
/// discourse markers → self-corrections ("3, no, 4pm" → "4pm") → repeated words → dictionary casing → capitals and
/// final punctuation.
public enum TextCleanup {
    private static let W = "[\\p{L}\\p{N}_']"

    private static func re(_ pattern: String, _ ci: Bool = true) -> NSRegularExpression {
        // Patterns are compile-time constants covered by the unit tests.
        return try! NSRegularExpression(pattern: pattern, options: ci ? [.caseInsensitive] : [])
    }

    static func sub(_ s: String, _ r: NSRegularExpression, _ template: String) -> String {
        r.stringByReplacingMatches(in: s, options: [], range: NSRange(s.startIndex..., in: s), withTemplate: template)
    }

    static func subFn(_ s: String, _ r: NSRegularExpression, _ fn: ([String]) -> String) -> String {
        let ns = s as NSString
        var out = ""
        var last = 0
        for m in r.matches(in: s, options: [], range: NSRange(location: 0, length: ns.length)) {
            out += ns.substring(with: NSRange(location: last, length: m.range.location - last))
            var groups: [String] = []
            for i in 0..<m.numberOfRanges {
                let g = m.range(at: i)
                groups.append(g.location == NSNotFound ? "" : ns.substring(with: g))
            }
            out += fn(groups)
            last = m.range.location + m.range.length
        }
        out += ns.substring(from: last)
        return out
    }

    private static let nonSpeech = re("\\[[^\\]]*]|\\((?:[^)]*(?:music|applause|silence|blank|inaudible|laugh)[^)]*)\\)|\\*[^*]+\\*")
    private static let filler = re("(?<!\(W))(?:um+|uh+|erm+|er|hmm+)(?!\(W))[,.]?\\s*")
    private static let youKnow = re(",?\\s*\\byou know\\b,\\s*")
    private static let leading = re("^(?:(?:so|well|okay|ok|like|right)\\s*,\\s*)+")
    private static let marker = re(",\\s*(no wait|wait,? no|sorry,? i mean|i mean|sorry|no|scratch that|make that|or rather)\\s*,?\\s+")
    private static let unit = "(?:[ap]\\.?\\s?m\\.?|o'?clock|%|percent|dollars?|bucks|minutes?|mins?|hours?|days?|weeks?|k|kg|km|miles?)"
    private static let noFused = re("(?<![\\d.])(\\d{1,2})\\.0\\.(\\d{1,2}(?::\\d\\d)?\\s*(?:[ap]\\.?\\s?m\\.?|o'?clock))(?!\\w)")
    private static let noMisheard = re("(?<![\\w.])(\\d+(?:[.:]\\d+)?)\\s*[,.]?\\s*(?:no|oh|know|nope)\\s*[,.]?\\s+(\\d+(?:[.:]\\d+)?\\s*\(unit))(?!\\w)")
    private static let spoken: [(NSRegularExpression, String)] = [
        (re("\\s*\\bquestion mark\\b"), "?"),
        (re("\\s*\\bexclamation (?:mark|point)\\b"), "!"),
        (re("\\s*\\bfull stop\\b"), "."),
        (re("\\s*\\bnew paragraph\\b\\s*"), "\n\n"),
        (re("\\s*\\bnew line\\b\\s*"), "\n"),
        (re("\\s*\\bcomma\\b"), ","),
        // 1.7: the same additions as Android and desktop. "period" and "dash" stay out: too often meant as words.
        (re("\\s*\\bsemicolon\\b"), ";"),
        (re("\\s*\\bcolon\\b"), ":"),
        // Openers and closers also take the commas a punctuating engine puts around them, and keep a preceding line break.
        (re("[ \\t]*\\b(?:open|begin) quotes?\\b[,.]?\\s*"), " \""),
        (re("[ \\t]*,?[ \\t]*\\b(?:(?:close|end) quotes?|unquote)\\b"), "\""),
        (re("[ \\t]*,?[ \\t]*\\bopen (?:bracket|paren|parenthesis)\\b[,.]?\\s*"), " ("),
        (re("[ \\t]*,?[ \\t]*\\bclose (?:bracket|paren|parenthesis)\\b"), ")"),
        (re("\\s*\\b(?:ellipsis|dot dot dot)\\b"), "..."),
    ]
    // "delete last word" / "scratch the last word" removes the word said before it (or just itself at the start).
    private static let deleteWord = re("[\\p{L}\\p{N}'\u{2019}]+[,.!?]?\\s+(?:delete|scratch) (?:the )?last word\\b[,.!?]?")
    private static let deleteWordStart = re("^\\s*(?:delete|scratch) (?:the )?last word\\b[,.!?]?\\s*")

    // Accuracy mode (strict): hedge words that are only filler when set off by commas, extra hesitation sounds,
    // a spoken "period" at the very end, and doubled punctuation.
    private static let strictFiller = re("(?<!\(W))(?:ah+|eh+|mm+|mhm|hm+)(?!\(W))[,.]?\\s*")
    private static let strictHedge = re(",\\s*(?:like|basically|actually|literally|honestly|sort of|kind of|i mean|you see|anyway)\\s*,\\s*")
    private static let strictLeading = re("(^|[.?!]\\s+)(?:(?:basically|actually|honestly|i mean|you know|anyway|anyways|so yeah)\\s*,\\s*)+")
    private static let strictTrailing = re(",\\s*(?:you know|i guess|or something)\\s*(?=[.?!]|$)")
    private static let strictPeriod = re("\\s*\\bperiod\\s*$")
    private static let doubled = re("([,;:])\\s*([,.?!;:])", false)

    private static let keepRepeat: Set<String> = [
        "had", "that", "very", "really", "so", "no", "bye", "ha", "haha", "go", "too", "more", "knock", "yeah", "yes",
    ]
    private static let questionStart: Set<String> = [
        "what", "why", "how", "when", "where", "who", "which", "is", "are", "do", "does", "did", "can", "could",
        "would", "will", "should", "shall", "may", "am", "was", "were", "have", "has",
    ]
    private static let hallucinations: Set<String> = [
        "thank you for watching", "thanks for watching", "thank you so much for watching", "please subscribe",
        "like and subscribe", "you",
    ]

    /// Clean one transcript. `strict` is accuracy mode: it also removes hedge fillers ("like", "basically",
    /// "you know") when they are set off by commas, so it never drops a "like" that carries meaning.
    public static func apply(_ raw: String, dictionary: [String] = [], strict: Bool = false) -> String {
        var t = sub(raw, nonSpeech, "")
        if isHallucination(t) { return "" }
        t = sub(t, filler, "")
        if strict { t = sub(t, strictFiller, "") }
        t = sub(t, youKnow, " ")
        for (r, rep) in spoken { t = sub(t, r, NSRegularExpression.escapedTemplate(for: rep)) }
        t = deleteLastWord(t)
        if strict {
            t = sub(t, strictPeriod, ".")
            t = sub(t, strictTrailing, "")
            t = sub(t, strictHedge, " ")
            t = sub(t, strictLeading, "$1")
        }
        t = collapseSpaces(t)
        t = sub(t, leading, "")
        t = selfCorrect(t)
        t = removeRepeats(t)
        t = applyDictionary(t, terms: dictionary)
        t = compactTimes(t)
        if strict { t = sub(t, doubled, "$2") }
        return finish(t)
    }

    private static let timeAmPm = re("(?<![\\p{L}\\p{N}])(\\d{1,2}(?::\\d{2})?)\\s?([AaPp])\\.?\\s?[Mm](?=[\\s,.;!?]|$)", false)

    /// "4 pm", "4 p.m.", "10:30 AM" → "4pm", "10:30am".
    public static func compactTimes(_ input: String) -> String {
        subFn(input, timeAmPm) { g in g[1] + g[2].lowercased() + "m" }
    }

    public static func isHallucination(_ text: String) -> Bool {
        let n = normalize(text)
        return hallucinations.contains(n) || n.hasPrefix("subtitles by") || n.hasPrefix("transcribed by")
    }

    private static func normalize(_ s: String) -> String {
        var t = sub(s.lowercased(), re("[^\\p{L}\\p{N}\\s]", false), "")
        t = t.trimmingCharacters(in: .whitespacesAndNewlines)
        return sub(t, re("\\s+", false), " ")
    }

    static func bare(_ token: String) -> String {
        sub(token, re("[^\\p{L}\\p{N}_']", false), "").lowercased()
    }

    private static func collapseSpaces(_ s: String) -> String {
        sub(s, re("[ \\t]+", false), " ").trimmingCharacters(in: .whitespaces)
    }

    private static func words(_ s: String) -> [String] {
        s.trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
    }

    /// "meet at 3, no, 4pm" → "meet at 4pm"; "to John, sorry, I mean Jane" → "to Jane".
    public static func selfCorrect(_ input: String) -> String {
        var text = sub(sub(input, noFused, "$1, no, $2"), noMisheard, "$1, no, $2")
        for _ in 0..<5 {
            let ns = text as NSString
            guard let m = marker.firstMatch(in: text, options: [], range: NSRange(location: 0, length: ns.length)) else { return text }
            let mk = ns.substring(with: m.range(at: 1)).lowercased()
            let pre = ns.substring(to: m.range.location)
            let preNs = pre as NSString
            var boundary = -1
            for sep in [". ", "? ", "! ", "\n"] {
                let r = preNs.range(of: sep, options: .backwards)
                if r.location != NSNotFound { boundary = max(boundary, r.location) }
            }
            let head: String
            if boundary < 0 { head = "" }
            else if preNs.substring(with: NSRange(location: boundary, length: 1)) == "\n" { head = preNs.substring(to: boundary + 1) }
            else { head = preNs.substring(to: boundary + 2) }
            let seg = preNs.substring(from: (head as NSString).length)
            let post = ns.substring(from: m.range.location + m.range.length)
            let postNs = post as NSString
            let endR = re("[.?!;\\n]", false).firstMatch(in: post, options: [], range: NSRange(location: 0, length: postNs.length))
            let y = endR != nil ? postNs.substring(to: endR!.range.location) : post
            let tail = endR != nil ? postNs.substring(from: endR!.range.location) : ""
            let bw = words(seg)
            let yw = words(y)
            if bw.isEmpty || yw.isEmpty { return text }
            let y0 = bare(yw[0])
            var cut: Int? = bw.lastIndex(where: { bare($0) == y0 })
            if cut == nil && yw[0].contains(where: { $0.isNumber }) {
                cut = bw.lastIndex(where: { w in w.contains(where: { $0.isNumber }) })
            }
            if cut == nil, let f = yw[0].first, f.isUppercase, bw.count > 1 {
                cut = (1..<bw.count).reversed().first(where: { bw[$0].first?.isUppercase == true })
            }
            if cut == nil {
                if mk == "no" { return text }
                let n = yw.count == 1 ? 1 : min(yw.count, bw.count - 1)
                cut = bw.count > 1 ? max(bw.count - n, 0) : 0
            }
            text = head + (Array(bw[0..<cut!]) + yw).joined(separator: " ") + tail
        }
        return text
    }

    private static func isWordChar(_ c: Character) -> Bool { c.isLetter || c.isNumber || c == "'" }

    /// "I I think we we should go" → "I think we should go"; "we should we should go" → "we should go".
    public static func removeRepeats(_ input: String) -> String {
        var out: [String] = []
        for tok in input.components(separatedBy: " ") {
            if let prev = out.last {
                let a = bare(prev), b = bare(tok)
                let prevEndsClause = prev.last.map { ",.?!;:".contains($0) } ?? false
                if !a.isEmpty && a == b && !keepRepeat.contains(a) && !prevEndsClause {
                    var trailing = ""
                    if let idx = tok.lastIndex(where: isWordChar) { trailing = String(tok[tok.index(after: idx)...]) }
                    var p = prev
                    while let c = p.last, !isWordChar(c) { p.removeLast() }
                    out[out.count - 1] = p + trailing
                    continue
                }
            }
            out.append(tok)
        }
        return sub(out.joined(separator: " "), re("\\b(\\p{L}+ \\p{L}+) \\1\\b"), "$1")
    }

    /// Dictionary terms win over whatever casing the engine produced ("okonkwo" → "Okonkwo").
    public static func applyDictionary(_ input: String, terms: [String]) -> String {
        var t = input
        for raw in terms {
            let term = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if term.isEmpty { continue }
            let body = words(term).map { NSRegularExpression.escapedPattern(for: $0) }.joined(separator: "\\s+")
            t = sub(t, re("(?<!\(W))\(body)(?!\(W))"), NSRegularExpression.escapedTemplate(for: term))
        }
        return t
    }

    /// "I love cats delete last word dogs" → "I love dogs"; repeated commands remove one word each.
    public static func deleteLastWord(_ input: String) -> String {
        var t = input
        for _ in 0..<5 {
            let next = sub(sub(t, deleteWord, ""), deleteWordStart, "")
            if next == t { break }
            t = next
        }
        return t
    }

    /// Spacing around punctuation, standalone "i", sentence capitals, and a final mark when one is missing.
    public static func finish(_ input: String) -> String {
        var t = collapseSpaces(input)
        t = sub(t, re("\\n[ \\t]+", false), "\n")
        t = sub(t, re("\\s+([,.?!;:])", false), "$1")
        t = sub(t, re("([,;:])(?=[^\\s\\d])", false), "$1 ")
        t = sub(t, re("(?<!\(W))i(?!\(W))", false), "I")
        // A sentence may open with a quote or bracket ("\"hello" → "\"Hello").
        t = subFn(t, re("(^|[.?!]\\s+|\\n)([\"(]?)(\\p{Ll})", false)) { g in g[1] + g[2] + g[3].uppercased() }
        if let last = t.last, last.isLetter || last.isNumber || last == "'" || last == "\"" {
            let tNs = t as NSString
            let ends = re("[.?!]\\s+", false).matches(in: t, options: [], range: NSRange(location: 0, length: tNs.length))
            let lastSentence = ends.last.map { tNs.substring(from: $0.range.location + $0.range.length) } ?? t
            let first = words(lastSentence).first.map { bare($0) } ?? ""
            t += questionStart.contains(first) ? "?" : "."
        }
        return t
    }
}
