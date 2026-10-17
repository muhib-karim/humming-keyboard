import XCTest
@testable import HummingCore

/// 1.7: the same cases as Android's ListFormatTest.kt and desktop's `bullet_list` tests (parity).
final class ListFormatTests: XCTestCase {
    func testBulletsAfterAnIntroWord() {
        XCTAssertEqual("Groceries:\n- Eggs\n- Milk", ListFormat.apply("Groceries bullet point eggs bullet point milk."))
    }

    func testBulletsWithEnginePunctuation() {
        XCTAssertEqual("- Eggs\n- Milk", ListFormat.apply("Bullet point, eggs. Bullet point, milk."))
    }

    func testIntroSentenceKeepsItsMark() {
        XCTAssertEqual("Here is the plan.\n- Call Sam\n- Book the room",
                       ListFormat.apply("Here is the plan. Bullet point call Sam next bullet book the room."))
    }

    func testPluralBulletPointsIsNotAMarker() {
        XCTAssertNil(ListFormat.bullets("Bullet points."))
        XCTAssertEqual("Use bullet points here.", ListFormat.apply("Use bullet points here."))
    }

    func testBulletPointInProseIsNotAMarker() {
        XCTAssertNil(ListFormat.bullets("The second bullet point is wrong."))
        XCTAssertEqual("Fix the bullet point on slide two.", ListFormat.apply("Fix the bullet point on slide two."))
    }

    func testEmptyBulletIsDropped() {
        XCTAssertEqual("Groceries:\n- Eggs", ListFormat.apply("Groceries bullet point eggs bullet point."))
    }

    func testTextAfterAListFollowsIt() {
        XCTAssertEqual("To do:\n- Milk\n\nThanks.", ListFormat.apply("To do bullet point milk.\n\nThanks."))
    }

    func testSpokenList() {
        XCTAssertEqual("The list is:\n- Eggs\n- Milk\n- Bread", ListFormat.apply("The list is first, eggs, second, milk, third, bread."))
        XCTAssertEqual("- Call Sam\n- Book the room", ListFormat.apply("Number one, call Sam, number two, book the room."))
    }

    func testLoneFirstIsNotAList() {
        XCTAssertNil(ListFormat.spokenList("First, we eat. Then we talk."))
        XCTAssertEqual("First, we eat. Then we talk.", ListFormat.apply("First, we eat. Then we talk."))
    }

    func testPlainTextIsUnchanged() {
        XCTAssertEqual("See you soon.", ListFormat.apply("See you soon."))
    }

    func testCleanupFormatsLists() {
        XCTAssertEqual("Groceries:\n- Eggs\n- Milk", TextCleanup.apply("groceries bullet point eggs bullet point milk"))
        XCTAssertEqual("The list is:\n- Eggs\n- Milk", TextCleanup.apply("the list is first, eggs, second, milk"))
    }
}
