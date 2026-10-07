package com.lamahuissn.motoapp.data

import kotlin.math.round

/**
 * «هل هذا الرقم معقول؟» — the office's own question, asked at the door.
 *
 * The office's rule is three lines (`ReadingQuality` with `BillingService::suggestedConsumption`): no
 * consumption at all, or above three times the household's own recent average — the last three real
 * readings before this cycle. It lives on the SERVER and it stays there: the server is the judge, and a
 * reading arriving from an old build, a paper sheet or the web portal is judged by it exactly the same.
 *
 * What this file exists for is TIMING. A collector standing at the meter is the only person who can still
 * walk back and re-read a digit; three hours later the same number is an argument with a customer. So the
 * phone asks the question the moment the digits are typed — from the very numbers the office sent in the
 * household's file — and puts it under his thumb BEFORE he presses save.
 *
 * It invents no number and no price: the previous value and the log come from the office, and the
 * arithmetic here is a comparison of kilowatt-hours, not money. When the phone cannot tell (no history,
 * no cycle known) it says NOTHING rather than crying wolf — a warning that fires on ordinary months is a
 * warning men learn to press through.
 */
object ReadingSuspicion {

    /** The office's own factor (`ReadingQuality::JUMP_FACTOR`): one number, one meaning. */
    const val JUMP_FACTOR = 3.0

    /** How many past readings the office averages (`BillingService::suggestedConsumption`). */
    const val SAMPLE = 3

    enum class Verdict {
        /** The meter has not moved. */
        ZERO,

        /** Far above this household's own habit. */
        JUMP,
    }

    data class Warning(val verdict: Verdict, val consumption: Double, val average: Double?)

    /**
     * This household's own average for the cycle being read, exactly as the office computes it: the real
     * (never estimated) readings with consumption above zero, newest first, at most [SAMPLE] of them,
     * rounded to one decimal — because a preview that disagrees with the judge on a tenth is a preview
     * that argues with the office.
     */
    fun average(house: House, period: String): Double? {
        val consumptions = house.readings
            .filter { ! it.estimated && it.period < period }
            .sortedByDescending { it.period }
            .mapNotNull { it.consumption?.toDoubleOrNull() }
            .filter { it > 0.0 }
            .take(SAMPLE)

        if (consumptions.isEmpty()) return null

        return round(consumptions.sum() / consumptions.size * 10) / 10
    }

    /**
     * The question about a number he is typing right now, or null when it looks ordinary.
     *
     * [entered] is what is in the box — half-typed values return null, never a warning about a number
     * nobody meant.
     */
    fun check(house: House, entered: String): Warning? {
        val period = house.period ?: return null

        val value = entered.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val previous = house.previousValue.toDoubleOrNull() ?: 0.0
        val consumption = value - previous

        // A value BELOW the one it continues from is deliberately NOT warned about here. The engine refuses
        // it outright (`refuse.reading_below_previous`) — no confirmation, no question — and while a man is
        // typing, «1» on the way to «188» looks exactly like a dropped digit: a warning that flashes on every
        // first keystroke is a warning he learns to ignore. What this preview is for is the QUESTION the
        // engine asks, and that question is only ever «the meter has not moved» or «this is a jump».
        if (consumption < 0.0) return null
        if (consumption == 0.0) return Warning(Verdict.ZERO, 0.0, null)

        val average = average(house, period) ?: return null

        return if (average > 0.0 && consumption > average * JUMP_FACTOR) {
            Warning(Verdict.JUMP, consumption, average)
        } else {
            null
        }
    }
}
