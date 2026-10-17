package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.7: "bullet point" lists and spoken lists ("first, ... second, ..."). Same cases on desktop and iOS. */
class ListFormatTest {
    @Test fun bulletsAfterAnIntroWord() =
        assertEquals("Groceries:\n- Eggs\n- Milk", ListFormat.apply("Groceries bullet point eggs bullet point milk."))

    @Test fun bulletsWithEnginePunctuation() =
        assertEquals("- Eggs\n- Milk", ListFormat.apply("Bullet point, eggs. Bullet point, milk."))

    @Test fun introSentenceKeepsItsMark() = assertEquals(
        "Here is the plan.\n- Call Sam\n- Book the room",
        ListFormat.apply("Here is the plan. Bullet point call Sam next bullet book the room."),
    )

    @Test fun pluralBulletPointsIsNotAMarker() {
        assertNull(ListFormat.bullets("Bullet points."))
        assertEquals("Use bullet points here.", ListFormat.apply("Use bullet points here."))
    }

    @Test fun bulletPointInProseIsNotAMarker() {
        assertNull(ListFormat.bullets("The second bullet point is wrong."))
        assertEquals("Fix the bullet point on slide two.", ListFormat.apply("Fix the bullet point on slide two."))
    }

    @Test fun emptyBulletIsDropped() =
        assertEquals("Groceries:\n- Eggs", ListFormat.apply("Groceries bullet point eggs bullet point."))

    @Test fun textAfterAListFollowsIt() =
        assertEquals("To do:\n- Milk\n\nThanks.", ListFormat.apply("To do bullet point milk.\n\nThanks."))

    @Test fun spokenList() = assertEquals(
        "The list is:\n- Eggs\n- Milk\n- Bread",
        ListFormat.apply("The list is first, eggs, second, milk, third, bread."),
    )

    @Test fun numberedSpokenList() =
        assertEquals("- Call Sam\n- Book the room", ListFormat.apply("Number one, call Sam, number two, book the room."))

    @Test fun loneFirstIsNotAList() {
        assertNull(ListFormat.spokenList("First, we eat. Then we talk."))
        assertEquals("First, we eat. Then we talk.", ListFormat.apply("First, we eat. Then we talk."))
    }

    @Test fun plainTextIsUnchanged() = assertEquals("See you soon.", ListFormat.apply("See you soon."))

    @Test fun cleanupFormatsLists() {
        assertEquals("Groceries:\n- Eggs\n- Milk", TextCleanup.apply("groceries bullet point eggs bullet point milk"))
        assertEquals("The list is:\n- Eggs\n- Milk", TextCleanup.apply("the list is first, eggs, second, milk"))
    }
}
