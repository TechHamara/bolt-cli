package com.techhamara.bolt.testing

/**
 * Standard Android Window Size Classes according to official Android Guidelines:
 * https://developer.android.com/training/testing/different-screens
 */
enum class ScreenSizeClass(
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val defaultDpi: Int,
    val label: String
) {
    COMPACT(0, 599, 360, 640, 160, "Compact Phone"),
    MEDIUM(600, 839, 720, 1280, 240, "Medium / Foldable"),
    EXPANDED(840, Int.MAX_VALUE, 1280, 800, 320, "Expanded / Tablet");

    companion object {
        fun fromWidthDp(widthDp: Int): ScreenSizeClass {
            return when {
                widthDp < 600 -> COMPACT
                widthDp < 840 -> MEDIUM
                else -> EXPANDED
            }
        }

        fun parse(name: String): ScreenSizeClass? {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) || it.label.contains(name, ignoreCase = true) }
        }
    }
}

enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE;

    fun flipped(): ScreenOrientation = if (this == PORTRAIT) LANDSCAPE else PORTRAIT
}

enum class ScreenPosture {
    NORMAL,
    TABLETOP,
    BOOK,
    FLAT
}

/**
 * Encapsulates a simulated screen profile for testing App Inventor extension layouts and state.
 */
data class ScreenProfile(
    var sizeClass: ScreenSizeClass,
    var widthDp: Int,
    var heightDp: Int,
    var dpi: Int = 160,
    var orientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
    var posture: ScreenPosture = ScreenPosture.NORMAL
) {
    fun copyFlipped(): ScreenProfile {
        return copy(
            widthDp = heightDp,
            heightDp = widthDp,
            orientation = orientation.flipped(),
            sizeClass = ScreenSizeClass.fromWidthDp(heightDp)
        )
    }

    override fun toString(): String {
        return "${sizeClass.name} (${widthDp}x${heightDp}dp @ ${dpi}dpi, $orientation, posture: $posture)"
    }

    companion object {
        val PHONE_PORTRAIT = ScreenProfile(ScreenSizeClass.COMPACT, 360, 640, 160, ScreenOrientation.PORTRAIT)
        val PHONE_LANDSCAPE = ScreenProfile(ScreenSizeClass.COMPACT, 640, 360, 160, ScreenOrientation.LANDSCAPE)
        val FOLDABLE_UNFOLDED = ScreenProfile(ScreenSizeClass.MEDIUM, 720, 1280, 240, ScreenOrientation.PORTRAIT)
        val FOLDABLE_TABLETOP = ScreenProfile(ScreenSizeClass.MEDIUM, 720, 1280, 240, ScreenOrientation.PORTRAIT, ScreenPosture.TABLETOP)
        val TABLET_LANDSCAPE = ScreenProfile(ScreenSizeClass.EXPANDED, 1280, 800, 320, ScreenOrientation.LANDSCAPE)
        val TABLET_PORTRAIT = ScreenProfile(ScreenSizeClass.EXPANDED, 800, 1280, 320, ScreenOrientation.PORTRAIT)
        val DESKTOP_WINDOW = ScreenProfile(ScreenSizeClass.EXPANDED, 1920, 1080, 160, ScreenOrientation.LANDSCAPE)

        fun fromPresetName(name: String): ScreenProfile {
            return when (name.lowercase().trim()) {
                "phone", "compact", "phone_portrait" -> PHONE_PORTRAIT
                "phone_landscape" -> PHONE_LANDSCAPE
                "foldable", "medium", "foldable_unfolded" -> FOLDABLE_UNFOLDED
                "tabletop", "foldable_tabletop" -> FOLDABLE_TABLETOP
                "tablet", "expanded", "tablet_landscape" -> TABLET_LANDSCAPE
                "tablet_portrait" -> TABLET_PORTRAIT
                "desktop", "chromeos", "large" -> DESKTOP_WINDOW
                else -> PHONE_PORTRAIT
            }
        }
    }
}
