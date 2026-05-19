package io.github.barsia.speqa.editor.ui.theme

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Centralised Speqa theme tokens. UI code MUST pull colors from here (or from
 * Jewel / IntelliJ `UIManager` / `EditorColorsManager` directly) — never embed
 * raw `Color(...)` literals at call sites. The literal RGB defaults are
 * concentrated in this single file.
 *
 * Each token is a `JBColor(light, dark)`, which dynamically returns the
 * variant matching the current IDE theme at paint time. Themes that want to
 * override Speqa-specific colors can do so by intercepting these tokens (e.g.
 * via reflection or by swapping the entire object) — exposing them here keeps
 * a single replaceable surface instead of scattered constants.
 */
object SpeqaThemeColors {

    /** Selected verdict pill foreground (label color on top of the tint). */
    val verdictSelectedForeground: JBColor = JBColor(Color.WHITE, Color.WHITE)

    /** Tint background for a Passed verdict pill / left strip. */
    val verdictPassedBackground: JBColor = JBColor(Color(0x2E7D32), Color(0x388E3C))

    /** Tint background for a Failed verdict pill / left strip. */
    val verdictFailedBackground: JBColor = JBColor(Color(0xC62828), Color(0xD32F2F))

    /** Tint background for a Skipped verdict pill / left strip. */
    val verdictSkippedBackground: JBColor = JBColor(Color(0x546E7A), Color(0x607D8B))

    /** Tint background for a Blocked verdict pill / left strip. */
    val verdictBlockedBackground: JBColor = JBColor(Color(0xE65100), Color(0xF57C00))
}
