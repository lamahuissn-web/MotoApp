package com.lamahuissn.motoapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The office's own palette.
 *
 * The panel the office already reads is what this has to look like on a phone: a light, quiet
 * background, white cards, one blue for anything interactive, and one accent that is reserved for
 * money — amber when something is owed, green when something is done. Amber never decorates: if it is
 * on the screen, it is asking for something.
 */

// Light — the panel's own daytime look.
val Blue = Color(0xFF0B6BCB)
val BlueDeep = Color(0xFF084F97)
val BlueContainer = Color(0xFFDCEAFB)
val BlueSoft = Color(0xFFEAF3FD)

val Money = Color(0xFFB45309)
val MoneyContainer = Color(0xFFFCEFD8)

val Good = Color(0xFF157347)
val GoodContainer = Color(0xFFDBF2E5)

val Danger = Color(0xFFB42318)
val DangerContainer = Color(0xFFFBE3E0)

val Ink = Color(0xFF0F172A)
val InkMuted = Color(0xFF55637A)
val Paper = Color(0xFFF1F4F9)
val Card = Color(0xFFFFFFFF)
val CardQuiet = Color(0xFFF6F8FC)
val Line = Color(0xFFD8E1EC)

// Dark — the same system at night, not a different one: the blues and ambers are lifted so they stay
// legible on a dark card, and the surfaces keep their layers so a card is still a card.
val NightBlue = Color(0xFF6CB6FF)
val NightBlueContainer = Color(0xFF12365C)
val NightBlueDeep = Color(0xFFCFE4FF)

val NightMoney = Color(0xFFFFC46B)
val NightMoneyContainer = Color(0xFF4A3413)

val NightGood = Color(0xFF7BE0A6)
val NightGoodContainer = Color(0xFF123D29)

val NightDanger = Color(0xFFFF9A8F)
val NightDangerContainer = Color(0xFF4E1F1B)

val NightInk = Color(0xFFECF0F5)
val NightMuted = Color(0xFF9DA8B7)
val NightPaper = Color(0xFF0F1115)
val NightCard = Color(0xFF181B22)
val NightCardQuiet = Color(0xFF21262F)
val NightLine = Color(0xFF2E3743)
