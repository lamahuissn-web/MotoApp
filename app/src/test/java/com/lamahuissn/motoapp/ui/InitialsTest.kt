package com.lamahuissn.motoapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The circle on a house card.
 *
 * Every household in an area is called «بيت فلان», so initials taken from the whole name made every
 * circle in the list read the same two letters — a mark that marks nothing. The leading «بيت» is
 * dropped, which is what makes the circles tell eleven houses apart instead of repeating one word.
 */
class InitialsTest {

    @Test
    fun `two houses of Abu-something do not wear the same circle`() {
        val ali = initials("بيت أبو علي")
        val fadi = initials("بيت أبو فادي")

        assertEquals("أع", ali)
        assertEquals("أف", fadi)
        assertNotEquals(ali, fadi)
    }

    @Test
    fun `a shop and a bakery keep their own letters`() {
        assertEquals("سن", initials("سوبرماركت النور"))
        assertEquals("مش", initials("مخبز الشارع"))
    }

    @Test
    fun `a name with nothing but the word house still gets one letter`() {
        assertEquals("ب", initials("بيت"))
        assertEquals("ب", initials("  بيت  "))
    }

    @Test
    fun `letters never exceed two, however long the name is`() {
        assertEquals(2, initials("بيت أبو علي الحاج محمد").length)
    }
}
