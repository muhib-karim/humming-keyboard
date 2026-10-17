import XCTest
@testable import HummingCore

/// The same cases as Android's TextCleanupTest.kt (parity), plus accuracy mode.
final class TextCleanupTests: XCTestCase {
    private let dict = ["Humming", "Okonkwo"]
    private func clean(_ s: String) -> String { TextCleanup.apply(s, dictionary: dict) }
    private func strict(_ s: String) -> String { TextCleanup.apply(s, dictionary: dict, strict: true) }

    func testPlainSentence() { XCTAssertEqual("See you soon.", clean("see you soon")) }

    func testSilenceHallucinationsInsertNothing() {
        XCTAssertEqual("", clean("Thank you for watching!"))
        XCTAssertEqual("", clean(" Thanks for watching."))
        XCTAssertEqual("", clean("[BLANK_AUDIO]"))
        XCTAssertEqual("", clean("(music)"))
        XCTAssertEqual("", clean("Subtitles by the Amara.org community"))
        XCTAssertEqual("", clean("you"))
        XCTAssertEqual("Thank you.", clean("thank you"))
    }

    func testTimesAreCompact() {
        XCTAssertEqual("Meet at 4pm.", clean("Meet at 4 pm."))
        XCTAssertEqual("Meet at 4pm.", clean("meet at 4 p.m."))
        XCTAssertEqual("Call at 10:30am tomorrow.", clean("call at 10:30 AM tomorrow"))
        XCTAssertEqual("I am 4 feet away.", clean("I am 4 feet away"))
        XCTAssertEqual("Meet at 4pm. Then we eat.", clean("meet at 4 pm. then we eat"))
    }

    func testFillers() {
        XCTAssertEqual("I think we should ship it.", clean("um, so, uh, I think we should ship it"))
        XCTAssertEqual("I think we should ship it.", clean("Um, so, uh, I think we should ship it."))
        XCTAssertEqual("I think we should ship it.", clean("So, I think we should ship it."))
    }

    func testRepeats() {
        XCTAssertEqual("I think we should go.", clean("I I think we we should go"))
        XCTAssertEqual("We should go now.", clean("we should we should go now"))
        XCTAssertEqual("It was very very good.", clean("it was very very good"))
    }

    func testSelfCorrectionNumber() {
        XCTAssertEqual("Meet at 4pm.", clean("um, meet at 3, no, 4pm"))
        XCTAssertEqual("Meet at 4pm.", clean("Meet at 3, no, 4pm."))
    }

    func testMisheardNo() {
        XCTAssertEqual("Meet at 4pm.", clean("Meet at 3.0.4pm."))
        XCTAssertEqual("Meet at 4pm.", clean("meet at 3 oh 4pm"))
        XCTAssertEqual("Meet at 4pm.", clean("Meet at 3, know, 4 p.m."))
        XCTAssertEqual("Meet at 4pm.", clean("Meet at 3. No, 4pm."))
        XCTAssertEqual("It costs 6 dollars.", clean("it costs 5 nope 6 dollars"))
    }

    func testVersionNumbersUntouched() {
        XCTAssertEqual("Version 3.0.4 is out.", clean("version 3.0.4 is out"))
        XCTAssertEqual("I have 2 oh 3 kids.", clean("I have 2 oh 3 kids"))
    }

    func testSelfCorrectionName() {
        XCTAssertEqual("Send it to Jane.", clean("send it to John, sorry, I mean Jane"))
        XCTAssertEqual("Send it to Jane.", clean("Send it to John, sorry, I mean Jane."))
    }

    func testRealNoIsKept() { XCTAssertEqual("Is it ready, no, not yet.", clean("Is it ready, no, not yet.")) }

    func testPunctuation() {
        XCTAssertEqual("What time is it?", clean("what time is it question mark"))
        XCTAssertEqual("What time is it?", clean("what time is it"))
        XCTAssertEqual("Hello, world.", clean("hello comma world"))
        XCTAssertEqual("I think I can.", clean("i think i can"))
        XCTAssertEqual("First line\nSecond line.", clean("first line new line second line"))
    }

    func testDictionary() {
        XCTAssertEqual("Open Humming and ping Okonkwo.", clean("open humming and ping okonkwo"))
        XCTAssertEqual("Ask Nova Beta.", TextCleanup.apply("ask nova   beta", dictionary: ["Nova Beta"]))
    }

    func testEmptyStaysEmpty() {
        XCTAssertEqual("", clean(""))
        XCTAssertEqual("", clean("um, uh"))
        XCTAssertTrue(TextCleanup.isHallucination("Thanks for watching!"))
    }

    // Accuracy mode (strict)

    func testStrictRemovesHedgeFillers() {
        XCTAssertEqual("I was thinking we could meet at 4pm.",
                       strict("So, um, I was, like, thinking we could, you know, meet at 3, no, 4pm"))
        XCTAssertEqual("The build is fine.", strict("Basically, the build is, honestly, fine"))
        XCTAssertEqual("We ship on Friday.", strict("We ship on Friday, you know."))
        XCTAssertEqual("Let's go.", strict("ah, mm, let's go"))
        XCTAssertEqual("Send the report.", strict("send the report period"))
    }

    func testStrictKeepsMeaningfulWords() {
        XCTAssertEqual("I like this one.", strict("I like this one"))
        XCTAssertEqual("It looks like rain.", strict("it looks like rain"))
        XCTAssertEqual("It is actually done.", strict("it is actually done"))
        XCTAssertEqual("The period ends in May.", strict("the period ends in May"))
    }

    func testDefaultModeKeepsHedges() {
        XCTAssertEqual("It was, like, huge.", clean("it was, like, huge"))
        XCTAssertEqual("It was huge.", strict("it was, like, huge"))
    }

    // 1.7: the same spoken punctuation and "delete last word" cases as Android's TextCleanupTest.kt.
    func testSpokenColonSemicolonQuotesBracketsEllipsis() {
        XCTAssertEqual("Note: buy milk.", clean("note colon buy milk"))
        XCTAssertEqual("First; second.", clean("first semicolon second"))
        XCTAssertEqual("He said \"hi\".", clean("he said open quote hi close quote"))
        XCTAssertEqual("\"Hello\".", clean("open quote hello end quote"))
        XCTAssertEqual("Call me (maybe) tomorrow.", clean("call me open bracket maybe close bracket tomorrow"))
        XCTAssertEqual("Wait...", clean("wait dot dot dot"))
        XCTAssertEqual("Wait...", clean("wait ellipsis"))
    }

    func testSpokenPunctuationNeedsWholeWords() {
        XCTAssertEqual("The colonel quotes semicolons.", clean("the colonel quotes semicolons"))
    }

    func testDeleteLastWord() {
        XCTAssertEqual("I love dogs.", clean("I love cats delete last word dogs"))
        XCTAssertEqual("I love dogs.", clean("I love big cats delete last word delete last word dogs"))
        XCTAssertEqual("See you.", clean("see you soon. Scratch the last word."))
        XCTAssertEqual("", clean("delete last word"))
        XCTAssertEqual("Hello.", clean("delete last word hello"))
    }

    func testOpenersKeepLineBreaksAndAbsorbEngineCommas() {
        XCTAssertEqual("First line\n\"Hello\".", clean("first line new line open quote hello close quote"))
        XCTAssertEqual("He said, \"hi\".", clean("He said, open quote, hi, close quote."))
        XCTAssertEqual("Call me (maybe), tomorrow.", clean("Call me, open bracket, maybe, close bracket, tomorrow."))
        XCTAssertEqual("Can you remove the last word of the title?", clean("can you remove the last word of the title"))
    }
}
