package com.mowazze3.ui.theme

import androidx.compose.ui.unit.dp

/** Design px == dp. Spacing base is 4. */
object Space {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 24.dp; val xxl = 32.dp; val x3 = 48.dp; val x4 = 64.dp
}

object Radius { val small = 6.dp; val medium = 10.dp; val large = 16.dp; val sheet = 20.dp }

object Sizes {
    val minTouch = 44.dp            // absolute minimum; Android recommends 48
    val button = 48.dp
    val powerButton = 88.dp         // 96.dp from 600dp window width
    val powerButtonLarge = 96.dp
    val icon = 22.dp
    val deviceAvatar = 40.dp
    val deviceRowMin = 72.dp
    val optionCardMin = 64.dp
    val switchTrackW = 52.dp; val switchTrackH = 30.dp; val switchThumb = 22.dp
    val switchTouchW = 56.dp; val switchTouchH = 44.dp
    val navBarItem = 56.dp
    val railWidth = 96.dp; val railItemW = 80.dp; val railItemH = 64.dp
    // 104 instead of the handoff's 64: it's now a button that opens the full-screen QR (design board HomeCard).
    val qr = 104.dp; val qrLarge = 120.dp
    val focusRing = 3.dp; val focusRingOffset = 2.dp
    val border = 1.5.dp; val borderStrong = 2.dp; val borderSelected = 2.5.dp; val borderPower = 3.dp
    val progressHeight = 12.dp
    val sheetHandleW = 44.dp; val sheetHandleH = 5.dp
}

/** Window width classes (Material window size classes). */
object Breakpoints { val medium = 600.dp; val expanded = 840.dp }
object Layout {
    val marginCompact = 16.dp; val marginMedium = 24.dp
    val settingsMaxWidth = 560.dp
    val homePaneWidth = 460.dp       // expanded: fixed left/first pane
}
