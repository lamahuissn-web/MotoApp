package com.lamahuissn.motoapp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The office's own palette — read out of the collector panel's stylesheet, not invented here.
 *
 * The panel is Metronic 9.5 (Tailwind 4) with KTUI components, and its brand colour is re-pointed from
 * Metronic's default blue to **amber**: *"the operator's colour is amber — the ⚡ mark on the sign-in
 * screen and the product's whole mental model (a generator running at night)"*. The first MotoApp
 * screens wore the default blue, which is exactly why the owner kept saying the app did not look like
 * the web. These are the panel's own tokens, copied value for value:
 *
 *  - neutrals are **zinc**; white page, white card, a hairline border, and a muted grey for the small
 *    labels — a card in the panel is told apart by its border, not by a shadow;
 *  - the brand is **amber-500**, and text ON amber is **zinc-950**, because amber is a light hue and
 *    white-on-amber fails contrast;
 *  - money owed is the **destructive** red, money clear is **green-600**, exactly as the panel paints
 *    a balance;
 *  - tiles (the small tinted squares) are a 12% tint of a hue carrying a deeper shade of the same hue.
 */

// ── Light: the panel's daytime look ───────────────────────────────────────────────────────────────

/** --foreground / text-mono. */
val Ink = Color(0xFF09090B)

/** --muted-foreground: micro-labels, meta lines, hints. */
val InkMuted = Color(0xFF71717A)

/** --secondary-foreground: body text inside a card, a step softer than a heading. */
val BodyInk = Color(0xFF18181B)

/** --background. */
val Paper = Color(0xFFFFFFFF)

/** --card. */
val Card = Color(0xFFFFFFFF)

/** --muted: the quiet fill behind a segmented control, a preview block, a receipt's inner panel. */
val Muted = Color(0xFFF4F4F5)

/** --border: the hairline that does the work a shadow would in another design. */
val Line = Color(0xFFE9E9EC)

/** --primary (amber-500) and --primary-foreground (zinc-950). */
val Amber = Color(0xFFF59E0B)
val OnAmber = Color(0xFF09090B)

/** amber-700: a glyph or a figure in brand colour on a light surface. */
val AmberInk = Color(0xFFB45309)

/** --destructive (red-600) and red-700 for a glyph tint. */
val Danger = Color(0xFFDC2626)
val DangerInk = Color(0xFFB91C1C)

/** green-600 / green-700: a balance that is clear, an amount that arrived. */
val Green = Color(0xFF16A34A)
val GreenInk = Color(0xFF15803D)

/** The other tile hues the panel uses: info (blue-700), violet (violet-700), warning (amber on yellow). */
val BlueInk = Color(0xFF1D4ED8)
val VioletInk = Color(0xFF6D28D9)
val Yellow = Color(0xFFFACC15)

// ── Dark: the same system at night, not a differently-coloured one ────────────────────────────────

val NightPaper = Color(0xFF09090B)
val NightCard = Color(0xFF18181B)
val NightInk = Color(0xFFFAFAFA)
val NightMuted = Color(0xFFA1A1AA)
val NightBodyInk = Color(0xFFE4E4E7)
val NightLine = Color(0xFF27272A)

val NightAmber = Color(0xFFFBBF24)
val NightGreen = Color(0xFF22C55E)
val NightDanger = Color(0xFFEF4444)
val NightBlueInk = Color(0xFF60A5FA)
val NightVioletInk = Color(0xFFA78BFA)
val NightAmberInk = Color(0xFFFBBF24)
