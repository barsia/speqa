package io.github.barsia.speqa.editor.ui.primitives

import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy

/**
 * Client property key: when set to `true` on any [JComponent], that component
 * is skipped during Tab / Shift+Tab traversal even though it remains focusable
 * via explicit `requestFocus()` (e.g. the drag handle of a step card that is
 * activated via Space/Enter from its parent card).
 */
const val SPEQA_EXCLUDE_FROM_TAB_CHAIN: String = "speqa.excludeFromTabChain"

/**
 * Focus-traversal policy that obeys the Swing default layout order but skips:
 *  - any component currently `!isVisible`;
 *  - any [JComponent] carrying client property [SPEQA_EXCLUDE_FROM_TAB_CHAIN] = true.
 */
class SpeqaFocusTraversalPolicy : LayoutFocusTraversalPolicy() {

    public override fun accept(aComponent: Component): Boolean {
        if (!super.accept(aComponent)) return false
        if (!aComponent.isVisible) return false
        if (aComponent is JComponent) {
            val flag = aComponent.getClientProperty(SPEQA_EXCLUDE_FROM_TAB_CHAIN)
            if (flag == true) return false
        }
        return true
    }

    override fun getComponentAfter(aContainer: Container, aComponent: Component): Component? {
        return super.getComponentAfter(aContainer, aComponent)
    }

    override fun getComponentBefore(aContainer: Container, aComponent: Component): Component? {
        return super.getComponentBefore(aContainer, aComponent)
    }
}
