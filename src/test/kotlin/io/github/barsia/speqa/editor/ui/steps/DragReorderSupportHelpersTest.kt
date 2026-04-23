package io.github.barsia.speqa.editor.ui.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Point
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JPanel

class DragReorderSupportHelpersTest {

    @Test
    fun `stepSlotsFromComponents ignores spacers and add button`() {
        val firstWrapper = JPanel().apply {
            setLocation(0, 10)
            setSize(200, 100)
        }
        val spacer = Box.createVerticalStrut(6).apply {
            setLocation(0, 110)
            setSize(200, 6)
        }
        val secondWrapper = JPanel().apply {
            setLocation(0, 116)
            setSize(200, 100)
        }
        val addButton = JButton("+ Add Step").apply {
            setLocation(0, 222)
            setSize(120, 28)
        }

        val slots = stepSlotsFromComponents(
            components = arrayOf(firstWrapper, spacer, secondWrapper, addButton),
            originalIndexOf = { component ->
                when (component) {
                    firstWrapper -> 0
                    secondWrapper -> 1
                    else -> null
                }
            },
        )

        assertEquals(
            listOf(
                DragCardSlot(originalIndex = 0, top = 10, height = 100),
                DragCardSlot(originalIndex = 1, top = 116, height = 100),
            ),
            slots,
        )
    }

    @Test
    fun `siblingBoundsFromSlots excludes dragged slot and keeps model indices`() {
        val slots = listOf(
            DragCardSlot(originalIndex = 0, top = 10, height = 100),
            DragCardSlot(originalIndex = 1, top = 116, height = 100),
            DragCardSlot(originalIndex = 2, top = 222, height = 100),
        )

        val siblings = siblingBoundsFromSlots(slots, draggedIndex = 1)

        assertEquals(
            listOf(
                SiblingBounds(originalIndex = 0, top = 10, height = 100),
                SiblingBounds(originalIndex = 2, top = 222, height = 100),
            ),
            siblings,
        )
    }

    @Test
    fun `cardPressPointFromHandlePress converts handle-local point into card coordinates`() {
        val pointOnCard = cardPressPointFromHandlePress(
            handlePressPoint = Point(4, 5),
            handleLocationOnCard = Point(8, 39),
        )

        assertEquals(Point(12, 44), pointOnCard)
    }

    @Test
    fun `interSlotGapPx uses actual spacer between adjacent cards`() {
        assertEquals(6, interSlotGapPx(cardTop = 10, cardHeight = 100, nextCardTop = 116))
        assertEquals(0, interSlotGapPx(cardTop = 10, cardHeight = 100, nextCardTop = 100))
    }
}
