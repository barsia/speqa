package io.github.barsia.speqa.toolwindow

import org.jdom.Element
import org.jdom.output.XMLOutputter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the persisted tool-window tree state: the empty-means-no-state filter in [read] and the
 * defensive copies on every read/write that keep the live tree and the persisted element from
 * mutating one another (a corruption that would silently outlive a single session).
 */
class SpeqaToolWindowTreeStateTest {

    private fun xml(element: Element?): String? = element?.let { XMLOutputter().outputString(it) }

    private fun stateWith(childName: String): Element =
        Element("state").addContent(Element(childName).setAttribute("path", "a/b"))

    @Test
    fun `read returns null before anything is stored`() {
        assertNull(SpeqaToolWindowTreeState().read())
    }

    @Test
    fun `read returns null for a stored element that has no children`() {
        val store = SpeqaToolWindowTreeState()
        store.write(Element("state"))
        assertNull(store.read())
    }

    @Test
    fun `read returns the stored content once a non-empty element is written`() {
        val store = SpeqaToolWindowTreeState()
        val stored = stateWith("node")
        store.write(stored)

        val read = store.read()
        assertNotNull(read)
        assertEquals(xml(stored), xml(read))
    }

    @Test
    fun `getState returns a defensive copy that cannot mutate the stored state`() {
        val store = SpeqaToolWindowTreeState()
        store.write(stateWith("node"))

        store.getState().addContent(Element("injected"))

        // A fresh read must not see the injection done on the earlier copy.
        assertNull(store.getState().getChild("injected"))
    }

    @Test
    fun `write copies the element so later source mutations do not leak in`() {
        val store = SpeqaToolWindowTreeState()
        val source = stateWith("node")
        store.write(source)

        source.addContent(Element("late"))

        assertNull(store.read()!!.getChild("late"))
    }

    @Test
    fun `loadState copies the element so later source mutations do not leak in`() {
        val store = SpeqaToolWindowTreeState()
        val source = stateWith("node")
        store.loadState(source)

        source.addContent(Element("late"))

        val state = store.getState()
        assertEquals("a/b", state.getChild("node").getAttributeValue("path"))
        assertNull(state.getChild("late"))
    }

    @Test
    fun `the test-runs tree state stores and reads back the same way`() {
        val store = SpeqaTestRunsToolWindowTreeState()
        assertNull(store.read())

        val stored = stateWith("run")
        store.write(stored)

        assertEquals(xml(stored), xml(store.read()))
    }
}
