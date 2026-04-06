package io.github.barsia.speqa.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentRangeLocatorTest {

    private fun String.normalized(): String = SpeqaMarkdown.normalizeLineEndings(this)

    private fun String.substr(range: TextRange): String =
        normalized().substring(range.start, range.end)

    @Test
    fun `full document with all sections`() {
        val doc = """
            |---
            |id: 1
            |title: "Login test"
            |priority: high
            |status: draft
            |environment:
            |  - "Chrome"
            |  - "Firefox"
            |tags:
            |  - smoke
            |  - auth
            |---
            |
            |This is the description text.
            |
            |Preconditions:
            |
            |- User exists
            |- User is on login page
            |
            |Attachments:
            |
            |[spec.pdf]
            |[screenshot.png]
            |
            |Scenario:
            |
            |1. Type username
            |   [action-attachment.png]
            |   > Username accepted
            |   [expected-attachment.png]
            |
            |2. Click login
            |   > Redirected to dashboard
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        // Frontmatter
        assertNotNull(layout.frontmatter)
        val fm = layout.frontmatter!!
        assertEquals("---\n", text.substring(fm.openDelimiter.start, fm.openDelimiter.end))
        assertEquals("---\n", text.substring(fm.closeDelimiter.start, fm.closeDelimiter.end))
        assertEquals(6, fm.fields.size)

        assertEquals(
            listOf("id", "title", "priority", "status", "environment", "tags"),
            fm.fields.map { it.key },
        )

        // environment field should include continuation lines
        val envField = fm.fields.first { it.key == "environment" }
        val envText = text.substring(envField.wholeRange.start, envField.wholeRange.end)
        assertTrue(envText.contains("environment:"))
        assertTrue(envText.contains("\"Chrome\""))
        assertTrue(envText.contains("\"Firefox\""))

        // tags field should include continuation lines
        val tagsField = fm.fields.first { it.key == "tags" }
        val tagsText = text.substring(tagsField.wholeRange.start, tagsField.wholeRange.end)
        assertTrue(tagsText.contains("tags:"))
        assertTrue(tagsText.contains("smoke"))
        assertTrue(tagsText.contains("auth"))

        // Description
        assertNotNull(layout.descriptionRange)
        val descText = text.substring(layout.descriptionRange!!.start, layout.descriptionRange!!.end)
        assertTrue(descText.contains("This is the description text."))

        // Preconditions
        assertNotNull(layout.preconditionsMarkerRange)
        val precMarker = text.substring(layout.preconditionsMarkerRange!!.start, layout.preconditionsMarkerRange!!.end)
        assertEquals("Preconditions:\n", precMarker)

        assertNotNull(layout.preconditionsBodyRange)
        val precBody = text.substring(layout.preconditionsBodyRange!!.start, layout.preconditionsBodyRange!!.end)
        assertTrue(precBody.contains("- User exists"))
        assertTrue(precBody.contains("- User is on login page"))

        // Attachments
        assertNotNull(layout.attachmentsMarkerRange)
        val attMarker = text.substring(layout.attachmentsMarkerRange!!.start, layout.attachmentsMarkerRange!!.end)
        assertEquals("Attachments:\n", attMarker)

        assertNotNull(layout.attachmentsBodyRange)
        val attBody = text.substring(layout.attachmentsBodyRange!!.start, layout.attachmentsBodyRange!!.end)
        assertTrue(attBody.contains("[spec.pdf]"))
        assertTrue(attBody.contains("[screenshot.png]"))

        // Steps
        assertNotNull(layout.stepsMarkerRange)
        assertEquals("Scenario:\n", text.substring(layout.stepsMarkerRange!!.start, layout.stepsMarkerRange!!.end))
        assertEquals(2, layout.steps.size)

        // Step 1
        val step1 = layout.steps[0]
        assertEquals("1", text.substring(step1.numberRange.start, step1.numberRange.end))
        val action1 = text.substring(step1.actionRange.start, step1.actionRange.end)
        assertTrue(action1.startsWith("Type username"))

        assertNotNull(step1.actionAttachmentsRange)
        val actAtt1 = text.substring(step1.actionAttachmentsRange!!.start, step1.actionAttachmentsRange!!.end)
        assertTrue(actAtt1.contains("[action-attachment.png]"))

        assertNotNull(step1.expectedRange)
        val exp1 = text.substring(step1.expectedRange!!.start, step1.expectedRange!!.end)
        assertTrue(exp1.contains("> Username accepted"))

        assertNotNull(step1.expectedAttachmentsRange)
        val expAtt1 = text.substring(step1.expectedAttachmentsRange!!.start, step1.expectedAttachmentsRange!!.end)
        assertTrue(expAtt1.contains("[expected-attachment.png]"))

        // Step 2
        val step2 = layout.steps[1]
        assertEquals("2", text.substring(step2.numberRange.start, step2.numberRange.end))
        assertNotNull(step2.expectedRange)
        assertNull(step2.actionAttachmentsRange)
        assertNull(step2.expectedAttachmentsRange)
    }

    @Test
    fun `document with only frontmatter`() {
        val doc = """
            |---
            |id: 1
            |title: "Only frontmatter"
            |---
        """.trimMargin()

        val layout = DocumentRangeLocator.locate(doc)

        assertNotNull(layout.frontmatter)
        assertEquals(2, layout.frontmatter!!.fields.size)
        assertNull(layout.descriptionRange)
        assertNull(layout.preconditionsMarkerRange)
        assertNull(layout.stepsMarkerRange)
        assertTrue(layout.steps.isEmpty())
    }

    @Test
    fun `document with description but no preconditions`() {
        val doc = """
            |---
            |title: "Desc only"
            |---
            |
            |This is description.
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertNotNull(layout.descriptionRange)
        val desc = text.substring(layout.descriptionRange!!.start, layout.descriptionRange!!.end)
        assertEquals("This is description.\n", desc)

        assertNull(layout.preconditionsMarkerRange)
        assertNull(layout.preconditionsBodyRange)
        assertEquals(1, layout.steps.size)
    }

    @Test
    fun `document with preconditions but no description`() {
        val doc = """
            |---
            |title: "Prec only"
            |---
            |
            |Preconditions:
            |
            |- Must be logged in
            |
            |Scenario:
            |
            |1. Do something
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertNull(layout.descriptionRange)
        assertNotNull(layout.preconditionsMarkerRange)
        assertNotNull(layout.preconditionsBodyRange)

        val precBody = text.substring(layout.preconditionsBodyRange!!.start, layout.preconditionsBodyRange!!.end)
        assertTrue(precBody.contains("- Must be logged in"))
    }

    @Test
    fun `document with extra blank lines between sections`() {
        val doc = """
            |---
            |title: "Extra blanks"
            |---
            |
            |
            |
            |Description here.
            |
            |
            |
            |Preconditions:
            |
            |
            |- Item one
            |
            |
            |
            |Scenario:
            |
            |
            |1. Step one
            |
            |
            |2. Step two
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertNotNull(layout.descriptionRange)
        val desc = text.substring(layout.descriptionRange!!.start, layout.descriptionRange!!.end)
        assertEquals("Description here.\n", desc)

        assertNotNull(layout.preconditionsBodyRange)
        val precBody = text.substring(layout.preconditionsBodyRange!!.start, layout.preconditionsBodyRange!!.end)
        assertTrue(precBody.contains("- Item one"))

        assertEquals(2, layout.steps.size)
    }

    @Test
    fun `document with multiline step action`() {
        val doc = """
            |---
            |title: "Multiline"
            |---
            |
            |Scenario:
            |
            |1. First line of action
            |   continuation line one
            |   continuation line two
            |   > Expected result
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertEquals(1, layout.steps.size)
        val step = layout.steps[0]

        val action = text.substring(step.actionRange.start, step.actionRange.end)
        assertTrue(action.contains("First line of action"))
        assertTrue(action.contains("continuation line one"))
        assertTrue(action.contains("continuation line two"))

        assertNotNull(step.expectedRange)
    }

    @Test
    fun `step with no expected result`() {
        val doc = """
            |---
            |title: "No expected"
            |---
            |
            |Scenario:
            |
            |1. Do something without expected
            |
            |2. Another step
            |   > Has expected
        """.trimMargin()

        val layout = DocumentRangeLocator.locate(doc)

        assertEquals(2, layout.steps.size)
        assertNull(layout.steps[0].expectedRange)
        assertNotNull(layout.steps[1].expectedRange)
    }

    @Test
    fun `document with attachments section`() {
        val doc = """
            |---
            |title: "Attachments"
            |---
            |
            |Attachments:
            |
            |[file1.pdf]
            |[file2.png]
            |
            |Scenario:
            |
            |1. Check files
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertNotNull(layout.attachmentsMarkerRange)
        assertNotNull(layout.attachmentsBodyRange)
        val attBody = text.substring(layout.attachmentsBodyRange!!.start, layout.attachmentsBodyRange!!.end)
        assertTrue(attBody.contains("[file1.pdf]"))
        assertTrue(attBody.contains("[file2.png]"))
    }

    @Test
    fun `step-level attachments for both action and expected`() {
        val doc = """
            |---
            |title: "Step attachments"
            |---
            |
            |Scenario:
            |
            |1. Do action
            |   [action-file.png]
            |   > Expected outcome
            |   [expected-file.png]
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertEquals(1, layout.steps.size)
        val step = layout.steps[0]

        assertNotNull(step.actionAttachmentsRange)
        val actAtt = text.substring(step.actionAttachmentsRange!!.start, step.actionAttachmentsRange!!.end)
        assertTrue(actAtt.contains("[action-file.png]"))

        assertNotNull(step.expectedRange)

        assertNotNull(step.expectedAttachmentsRange)
        val expAtt = text.substring(step.expectedAttachmentsRange!!.start, step.expectedAttachmentsRange!!.end)
        assertTrue(expAtt.contains("[expected-file.png]"))
    }

    @Test
    fun `empty document`() {
        val layout = DocumentRangeLocator.locate("")

        assertNull(layout.frontmatter)
        assertNull(layout.descriptionRange)
        assertNull(layout.preconditionsMarkerRange)
        assertNull(layout.stepsMarkerRange)
        assertTrue(layout.steps.isEmpty())
    }

    @Test
    fun `document with no frontmatter`() {
        val doc = """
            |This is just text.
            |
            |Scenario:
            |
            |1. A step
            |   > Result
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertNull(layout.frontmatter)
        assertNotNull(layout.descriptionRange)
        val desc = text.substring(layout.descriptionRange!!.start, layout.descriptionRange!!.end)
        assertTrue(desc.contains("This is just text."))

        assertNotNull(layout.stepsMarkerRange)
        assertEquals(1, layout.steps.size)
    }

    @Test
    fun `frontmatter field ranges cover list fields correctly`() {
        val doc = """
            |---
            |id: 42
            |environment:
            |  - "Chrome"
            |  - "Firefox"
            |  - "Safari"
            |title: "After list"
            |---
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)
        val fm = layout.frontmatter!!

        assertEquals(3, fm.fields.size)
        assertEquals("id", fm.fields[0].key)
        assertEquals("environment", fm.fields[1].key)
        assertEquals("title", fm.fields[2].key)

        val envText = text.substring(fm.fields[1].wholeRange.start, fm.fields[1].wholeRange.end)
        assertEquals("environment:\n  - \"Chrome\"\n  - \"Firefox\"\n  - \"Safari\"\n", envText)

        val idText = text.substring(fm.fields[0].wholeRange.start, fm.fields[0].wholeRange.end)
        assertEquals("id: 42\n", idText)
    }

    @Test
    fun `body start offset is correct after frontmatter`() {
        val doc = "---\ntitle: \"Test\"\n---\nBody text\n"
        val layout = DocumentRangeLocator.locate(doc)
        val fm = layout.frontmatter!!

        assertEquals("Body text\n", doc.substring(fm.bodyStart))
    }

    @Test
    fun `multiple expected lines in a step`() {
        val doc = """
            |---
            |title: "Multi expected"
            |---
            |
            |Scenario:
            |
            |1. Do action
            |   > First expected line
            |   > Second expected line
            |   > Third expected line
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertEquals(1, layout.steps.size)
        val step = layout.steps[0]
        assertNotNull(step.expectedRange)

        val exp = text.substring(step.expectedRange!!.start, step.expectedRange!!.end)
        assertTrue(exp.contains("> First expected line"))
        assertTrue(exp.contains("> Second expected line"))
        assertTrue(exp.contains("> Third expected line"))
    }

    @Test
    fun `step number range is accurate for multi-digit numbers`() {
        val doc = """
            |---
            |title: "Multi digit"
            |---
            |
            |Scenario:
            |
            |10. Tenth step
            |   > Done
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertEquals(1, layout.steps.size)
        val step = layout.steps[0]
        assertEquals("10", text.substring(step.numberRange.start, step.numberRange.end))
    }

    @Test
    fun `windows line endings are normalized`() {
        val doc = "---\r\ntitle: \"Test\"\r\n---\r\n\r\nScenario:\r\n\r\n1. Step\r\n   > Expected\r\n"

        val layout = DocumentRangeLocator.locate(doc)
        val text = SpeqaMarkdown.normalizeLineEndings(doc)

        assertNotNull(layout.frontmatter)
        assertNotNull(layout.stepsMarkerRange)
        assertEquals(1, layout.steps.size)
        assertEquals("1", text.substring(layout.steps[0].numberRange.start, layout.steps[0].numberRange.end))
    }

    @Test
    fun `document with only steps no other sections`() {
        val doc = """
            |---
            |title: "Steps only"
            |---
            |
            |Scenario:
            |
            |1. First
            |2. Second
            |3. Third
        """.trimMargin()

        val layout = DocumentRangeLocator.locate(doc)

        assertNull(layout.descriptionRange)
        assertNull(layout.preconditionsMarkerRange)
        assertNull(layout.attachmentsMarkerRange)
        assertEquals(3, layout.steps.size)
    }

    @Test
    fun `document with links section`() {
        val doc = """
            |---
            |title: "With links"
            |---
            |
            |Attachments:
            |
            |[spec.pdf]
            |
            |Links:
            |
            |[Jira ticket](https://jira.example.com/TC-123)
            |[Design doc](https://figma.com/file/abc123)
            |
            |Scenario:
            |
            |1. Open page
        """.trimMargin()

        val text = doc.normalized()
        val layout = DocumentRangeLocator.locate(doc)

        assertNotNull(layout.linksMarkerRange)
        val linksMarker = text.substring(layout.linksMarkerRange!!.start, layout.linksMarkerRange!!.end)
        assertEquals("Links:\n", linksMarker)

        assertNotNull(layout.linksBodyRange)
        val linksBody = text.substring(layout.linksBodyRange!!.start, layout.linksBodyRange!!.end)
        assertTrue(linksBody.contains("[Jira ticket](https://jira.example.com/TC-123)"))
        assertTrue(linksBody.contains("[Design doc](https://figma.com/file/abc123)"))

        // Verify section ordering
        assertTrue(layout.attachmentsMarkerRange!!.start < layout.linksMarkerRange!!.start)
        assertTrue(layout.linksMarkerRange!!.start < layout.stepsMarkerRange!!.start)
    }

    @Test
    fun `ranges do not overlap for top-level sections`() {
        val doc = """
            |---
            |id: 1
            |title: "Overlap check"
            |---
            |
            |Description paragraph.
            |
            |Preconditions:
            |
            |- Pre 1
            |
            |Scenario:
            |
            |1. Action one
            |   > Expected one
            |
            |2. Action two
        """.trimMargin()

        val layout = DocumentRangeLocator.locate(doc)

        val topLevel = listOfNotNull(
            layout.descriptionRange?.let { "desc" to it },
            layout.preconditionsMarkerRange?.let { "precMarker" to it },
            layout.preconditionsBodyRange?.let { "precBody" to it },
            layout.stepsMarkerRange?.let { "stepsMarker" to it },
        )

        for (i in topLevel.indices) {
            for (j in i + 1 until topLevel.size) {
                val (nameA, a) = topLevel[i]
                val (nameB, b) = topLevel[j]
                assertTrue(
                    "$nameA [${a.start},${a.end}) overlaps $nameB [${b.start},${b.end})",
                    a.end <= b.start || b.end <= a.start,
                )
            }
        }
    }
}
