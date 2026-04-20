package io.github.barsia.speqa.parser

import io.github.barsia.speqa.model.Attachment
import io.github.barsia.speqa.model.Link
import io.github.barsia.speqa.model.PreconditionsBlock
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status
import io.github.barsia.speqa.model.TestCase
import io.github.barsia.speqa.model.TestStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestCaseSerializerTest {
    @Test
    fun `serialize full test case`() {
        val testCase = TestCase(
            title = "Login test",
            priority = Priority.MAJOR,
            status = Status.READY,
            environment = listOf("Chrome 120", "Firefox 121"),
            tags = listOf("auth", "smoke"),
            bodyBlocks = listOf(PreconditionsBlock(markdown = "- User exists\n- User is on login page")),
            steps = listOf(
                TestStep("Enter username", "Username accepted"),
                TestStep("Enter password", "Password masked"),
                TestStep("Click login", "Redirected to dashboard"),
            ),
        )

        val result = TestCaseSerializer.serialize(testCase)

        assertTrue(result.contains("title: \"Login test\""))
        assertTrue(result.contains("priority: major"))
        assertTrue(result.contains("status: ready"))
        assertTrue(result.contains("- \"Chrome 120\""))
        assertTrue(result.contains("- \"auth\""))
        assertTrue(result.contains("Preconditions:"))
        assertTrue(result.contains("- User exists"))
        assertTrue(result.contains("1. Enter username"))
        assertTrue(result.contains("   > Username accepted"))
        assertTrue(result.contains("2. Enter password"))
        assertTrue(result.contains("3. Click login"))
    }

    @Test
    fun `serialize special character tags safely`() {
        val testCase = TestCase(
            tags = listOf("auth:smoke", "needs review", "qa#1", "quoted\"tag"),
        )

        val result = TestCaseSerializer.serialize(testCase)
        val parsed = TestCaseParser.parse(result)

        assertTrue(result.contains("- \"auth:smoke\""))
        assertTrue(result.contains("- \"needs review\""))
        assertTrue(result.contains("- \"qa#1\""))
        assertTrue(result.contains("- \"quoted\\\"tag\""))
        assertEquals(testCase.tags, parsed.tags)
    }

    @Test
    fun `serialize step without expected result`() {
        val testCase = TestCase(
            title = "Minimal",
            steps = listOf(
                TestStep("Open page"),
                TestStep("Click submit", "Form submitted"),
            ),
        )

        val result = TestCaseSerializer.serialize(testCase)
        val lines = result.lines()

        val step1Index = lines.indexOfFirst { it.startsWith("1. Open page") }
        assertTrue(step1Index >= 0)
        val nextLine = lines.getOrNull(step1Index + 1) ?: ""
        assertFalse(nextLine.trimStart().startsWith(">"))
        assertTrue(result.contains("   > Form submitted"))
    }

    @Test
    fun `round trip preserves data`() {
        val original = TestCase(
            title = "Round trip test",
            priority = Priority.CRITICAL,
            status = Status.DRAFT,
            environment = listOf("Safari, macOS"),
            tags = listOf("e2e"),
            bodyBlocks = listOf(PreconditionsBlock(markdown = "- Precondition one")),
            steps = listOf(
                TestStep("Do something", "Something happens"),
                TestStep("Do another thing", "Then this happens"),
            ),
        )

        val serialized = TestCaseSerializer.serialize(original)
        val parsed = TestCaseParser.parse(serialized)

        assertEquals(original.title, parsed.title)
        assertEquals(original.priority, parsed.priority)
        assertEquals(original.status, parsed.status)
        assertEquals(original.environment, parsed.environment)
        assertEquals(original.tags, parsed.tags)
        assertEquals(original.steps.size, parsed.steps.size)
        assertEquals(original.steps[0].action, parsed.steps[0].action)
        assertEquals(original.steps[0].expected, parsed.steps[0].expected)
        assertEquals(original.steps[1].expected, parsed.steps[1].expected)
        assertEquals(original.steps[1].expectedGroupSize, parsed.steps[1].expectedGroupSize)
    }

    @Test
    fun `serialize id in frontmatter`() {
        val testCase = TestCase(id = 7, title = "With ID")
        val result = TestCaseSerializer.serialize(testCase)
        assertTrue(result.contains("id: 7"))
        // id should come before title
        val idIndex = result.indexOf("id: 7")
        val titleIndex = result.indexOf("title:")
        assertTrue(idIndex < titleIndex)
    }

    @Test
    fun `serialize null id omits field`() {
        val testCase = TestCase(id = null, title = "No ID")
        val result = TestCaseSerializer.serialize(testCase)
        assertFalse(result.contains("\nid:"))
    }

    @Test
    fun `serialize empty test case`() {
        val result = TestCaseSerializer.serialize(TestCase())

        assertTrue(result.startsWith("---"))
        assertTrue(result.contains("title: \"Untitled Test Case\""))
        assertFalse(result.contains("## Steps"))
        assertFalse(result.contains("## Expected Result"))
    }

    @Test
    fun `serialize group expectation round trip`() {
        val original = TestCase(
            steps = listOf(
                TestStep("Open page"),
                TestStep("Enter credentials"),
                TestStep("Submit", "User reaches dashboard", expectedGroupSize = 3),
            ),
        )

        val parsed = TestCaseParser.parse(TestCaseSerializer.serialize(original))

        assertEquals(3, parsed.steps.size)
        assertEquals("Submit", parsed.steps[2].action)
        assertEquals("User reaches dashboard", parsed.steps[2].expected)
        assertEquals(3, parsed.steps[2].expectedGroupSize)
    }

    @Test
    fun `round trip preserves attachments`() {
        val original = TestCase(
            title = "Attachment round trip",
            attachments = listOf(
                Attachment("attachments/attachment-round-trip/spec.pdf"),
                Attachment("attachments/attachment-round-trip/screenshot.png"),
            ),
            steps = listOf(
                TestStep(
                    action = "Click login",
                    expected = "Dashboard loads",
                    attachments = listOf(
                        Attachment("attachments/attachment-round-trip/action.png"),
                        Attachment("attachments/attachment-round-trip/expected.png"),
                    ),
                ),
            ),
        )

        val serialized = TestCaseSerializer.serialize(original)
        val parsed = TestCaseParser.parse(serialized)

        assertEquals(original.attachments.size, parsed.attachments.size)
        assertEquals(original.attachments[0].path, parsed.attachments[0].path)
        assertEquals(original.attachments[1].path, parsed.attachments[1].path)
        assertEquals(2, parsed.steps[0].attachments.size)
        assertEquals(original.steps[0].attachments[0].path, parsed.steps[0].attachments[0].path)
        assertEquals(original.steps[0].attachments[1].path, parsed.steps[0].attachments[1].path)
    }

    @Test
    fun `clearing tags does not affect other fields`() {
        val original = TestCase(
            id = 1,
            title = "Full test",
            priority = Priority.MAJOR,
            status = Status.READY,
            environment = listOf("Chrome"),
            tags = listOf("smoke"),
            steps = listOf(TestStep("Step 1", "Expected 1")),
        )
        val withoutTags = original.copy(tags = null)
        val serialized = TestCaseSerializer.serialize(withoutTags)
        val parsed = TestCaseParser.parse(serialized)

        assertEquals(1, parsed.id)
        assertEquals("Full test", parsed.title)
        assertEquals(Priority.MAJOR, parsed.priority)
        assertEquals(Status.READY, parsed.status)
        assertEquals(listOf("Chrome"), parsed.environment)
        assertNull(parsed.tags)
        assertEquals(1, parsed.steps.size)
    }

    @Test
    fun `clearing priority does not affect status or tags`() {
        val original = TestCase(
            priority = Priority.MAJOR,
            status = Status.READY,
            tags = listOf("smoke", "regression"),
        )
        val withoutPriority = original.copy(priority = null)
        val parsed = TestCaseParser.parse(TestCaseSerializer.serialize(withoutPriority))

        assertNull(parsed.priority)
        assertEquals(Status.READY, parsed.status)
        assertEquals(listOf("smoke", "regression"), parsed.tags)
    }

    @Test
    fun `serialize filters blank tags`() {
        val testCase = TestCase(tags = listOf("smoke", "", "  ", "regression"))
        val result = TestCaseSerializer.serialize(testCase)

        assertTrue(result.contains("\"smoke\""))
        assertTrue(result.contains("\"regression\""))
        assertFalse(result.contains("\"\""))
        assertFalse(result.contains("\"  \""))
    }

    @Test
    fun `serialize empty list produces bracket notation`() {
        val testCase = TestCase(tags = emptyList())
        val result = TestCaseSerializer.serialize(testCase)
        assertTrue(result.contains("tags: []"))
    }

    @Test
    fun `round trip with description and preconditions`() {
        val original = TestCase(
            title = "Both blocks",
            bodyBlocks = listOf(
                io.github.barsia.speqa.model.DescriptionBlock("Test description"),
                PreconditionsBlock(markdown = "Precondition text"),
            ),
            steps = listOf(TestStep("Do thing", "Thing done")),
        )
        val parsed = TestCaseParser.parse(TestCaseSerializer.serialize(original))

        assertEquals(2, parsed.bodyBlocks.size)
        assertTrue(parsed.bodyBlocks[0] is io.github.barsia.speqa.model.DescriptionBlock)
        assertTrue(parsed.bodyBlocks[1] is PreconditionsBlock)
        assertEquals("Test description", parsed.bodyBlocks[0].markdown.trim())
        assertTrue(parsed.bodyBlocks[1].markdown.contains("Precondition text"))
    }

    @Test
    fun `clearing description preserves preconditions in round trip`() {
        val withEmptyDesc = TestCase(
            bodyBlocks = listOf(
                io.github.barsia.speqa.model.DescriptionBlock(""),
                PreconditionsBlock(markdown = "Precondition"),
            ),
        )
        val parsed = TestCaseParser.parse(TestCaseSerializer.serialize(withEmptyDesc))

        val precBlocks = parsed.bodyBlocks.filterIsInstance<PreconditionsBlock>()
        assertEquals(1, precBlocks.size)
        assertTrue(precBlocks[0].markdown.contains("Precondition"))
    }

    @Test
    fun `wrong-order blocks are reordered in round trip`() {
        val original = TestCase(
            title = "Wrong order",
            bodyBlocks = listOf(
                PreconditionsBlock(markdown = "Precondition text"),
                io.github.barsia.speqa.model.DescriptionBlock("Test description"),
            ),
            steps = listOf(TestStep("Do thing", "Thing done")),
        )
        val serialized = TestCaseSerializer.serialize(original)
        val parsed = TestCaseParser.parse(serialized)

        assertEquals(2, parsed.bodyBlocks.size)
        assertTrue(parsed.bodyBlocks[0] is io.github.barsia.speqa.model.DescriptionBlock)
        assertTrue(parsed.bodyBlocks[1] is PreconditionsBlock)
        assertEquals("Test description", parsed.bodyBlocks[0].markdown.trim())
        assertTrue(parsed.bodyBlocks[1].markdown.contains("Precondition text"))
    }

    @Test
    fun `adding description to existing preconditions preserves both in round trip`() {
        val initial = TestCaseParser.parse("""
            ---
            title: "Test"
            ---

            Preconditions:

            - User exists

            Scenario:

            1. Do something
        """.trimIndent())

        val withDescription = initial.copy(
            bodyBlocks = listOf(io.github.barsia.speqa.model.DescriptionBlock("New description")) +
                initial.bodyBlocks.filterIsInstance<PreconditionsBlock>(),
        )

        val serialized = TestCaseSerializer.serialize(withDescription)
        val reparsed = TestCaseParser.parse(serialized)

        val desc = reparsed.bodyBlocks.filterIsInstance<io.github.barsia.speqa.model.DescriptionBlock>()
        val prec = reparsed.bodyBlocks.filterIsInstance<PreconditionsBlock>()
        assertEquals(1, desc.size)
        assertEquals(1, prec.size)
        assertEquals("New description", desc[0].markdown.trim())
        assertTrue(prec[0].markdown.contains("User exists"))
    }

    @Test
    fun `round trip preserves links`() {
        val original = TestCase(
            title = "Link round trip",
            links = listOf(
                Link("Jira ticket", "https://jira.example.com/TC-123"),
                Link("Design doc", "https://figma.com/file/abc123"),
            ),
            steps = listOf(
                TestStep(action = "Open page", expected = "Page loads"),
            ),
        )

        val serialized = TestCaseSerializer.serialize(original)
        val parsed = TestCaseParser.parse(serialized)

        assertEquals(original.links.size, parsed.links.size)
        assertEquals(original.links[0].title, parsed.links[0].title)
        assertEquals(original.links[0].url, parsed.links[0].url)
        assertEquals(original.links[1].title, parsed.links[1].title)
        assertEquals(original.links[1].url, parsed.links[1].url)

        // Verify section ordering: Links appears between Attachments and Steps
        assertTrue(serialized.contains("Links:"))
        val linksIdx = serialized.indexOf("Links:")
        val stepsIdx = serialized.indexOf("Scenario:")
        assertTrue("Links should be before Scenario", linksIdx < stepsIdx)
    }

    @Test
    fun `round trip preserves links with attachments`() {
        val original = TestCase(
            title = "Links and attachments",
            attachments = listOf(Attachment("attachments/spec.pdf")),
            links = listOf(
                Link("Jira", "https://jira.example.com/TC-1"),
            ),
            steps = listOf(
                TestStep(action = "Do thing"),
            ),
        )

        val serialized = TestCaseSerializer.serialize(original)
        val parsed = TestCaseParser.parse(serialized)

        assertEquals(1, parsed.attachments.size)
        assertEquals(1, parsed.links.size)
        assertEquals("Jira", parsed.links[0].title)

        // Verify ordering: Attachments < Links < Steps
        val attIdx = serialized.indexOf("Attachments:")
        val linksIdx = serialized.indexOf("Links:")
        val stepsIdx = serialized.indexOf("Scenario:")
        assertTrue(attIdx < linksIdx)
        assertTrue(linksIdx < stepsIdx)
    }

    @Test
    fun `clearing preconditions preserves description in round trip`() {
        val withEmptyPrec = TestCase(
            bodyBlocks = listOf(
                io.github.barsia.speqa.model.DescriptionBlock("Description text"),
                PreconditionsBlock(markdown = ""),
            ),
        )
        val parsed = TestCaseParser.parse(TestCaseSerializer.serialize(withEmptyPrec))

        val descBlocks = parsed.bodyBlocks.filterIsInstance<io.github.barsia.speqa.model.DescriptionBlock>()
        assertEquals(1, descBlocks.size)
        assertEquals("Description text", descBlocks[0].markdown.trim())
    }

    @Test
    fun `blank line between links and steps sections`() {
        val testCase = TestCase(
            links = listOf(Link("test", "http://tttt.com")),
            steps = listOf(TestStep("Do thing")),
        )
        val result = TestCaseSerializer.serialize(testCase)
        // There must be a blank line between the last link and "Scenario:"
        assertTrue(
            "Expected blank line between Links and Scenario, got:\n$result",
            result.contains("(http://tttt.com)\n\nScenario:"),
        )
    }

    @Test
    fun `blank line between attachments and links sections`() {
        val testCase = TestCase(
            attachments = listOf(Attachment("file.pdf")),
            links = listOf(Link("test", "http://tttt.com")),
        )
        val result = TestCaseSerializer.serialize(testCase)
        assertTrue(
            "Expected blank line between Attachments and Links, got:\n$result",
            result.contains("[file.pdf]\n\nLinks:"),
        )
    }

    @Test
    fun `blank line between body blocks and steps`() {
        val testCase = TestCase(
            bodyBlocks = listOf(io.github.barsia.speqa.model.DescriptionBlock("Description")),
            steps = listOf(TestStep("Do thing")),
        )
        val result = TestCaseSerializer.serialize(testCase)
        assertTrue(
            "Expected blank line between description and Scenario, got:\n$result",
            result.contains("Description\n\nScenario:"),
        )
    }

    @Test
    fun `no double blank lines anywhere in output`() {
        val testCase = TestCase(
            title = "Full",
            priority = Priority.MAJOR,
            status = Status.DRAFT,
            tags = listOf("smoke"),
            bodyBlocks = listOf(
                io.github.barsia.speqa.model.DescriptionBlock("Desc"),
                PreconditionsBlock(markdown = "Prec"),
            ),
            attachments = listOf(Attachment("file.pdf")),
            links = listOf(Link("test", "http://t.com")),
            steps = listOf(TestStep("Step 1", "Exp 1")),
        )
        val result = TestCaseSerializer.serialize(testCase)
        assertFalse(
            "Should not have triple newlines (double blank lines), got:\n$result",
            result.contains("\n\n\n"),
        )
    }

    @Test
    fun `only links and steps have blank line between`() {
        val testCase = TestCase(
            links = listOf(Link("link", "http://l.com")),
            steps = listOf(TestStep("Step")),
        )
        val result = TestCaseSerializer.serialize(testCase)
        assertFalse(
            "No triple newlines, got:\n$result",
            result.contains("\n\n\n"),
        )
        assertTrue(result.contains("(http://l.com)\n\nScenario:"))
    }

    @Test
    fun `only attachments and steps have blank line between`() {
        val testCase = TestCase(
            attachments = listOf(Attachment("f.pdf")),
            steps = listOf(TestStep("Step")),
        )
        val result = TestCaseSerializer.serialize(testCase)
        assertFalse(
            "No triple newlines, got:\n$result",
            result.contains("\n\n\n"),
        )
        assertTrue(result.contains("[f.pdf]\n\nScenario:"))
    }

    @Test
    fun `step with ticket serializes Ticket line`() {
        val tc = TestCase(
            title = "Test",
            steps = listOf(TestStep(action = "Do something", expected = "Result", tickets = listOf("PROJ-123", "PROJ-456"))),
        )
        val md = TestCaseSerializer.serialize(tc)
        assertTrue("Should contain Ticket line, got:\n$md", md.contains("   Ticket: PROJ-123, PROJ-456"))
    }

    @Test
    fun `step without ticket omits Ticket line`() {
        val tc = TestCase(
            title = "Test",
            steps = listOf(TestStep(action = "Do something", expected = "Result")),
        )
        val md = TestCaseSerializer.serialize(tc)
        assertFalse("Should not contain Ticket, got:\n$md", md.contains("Ticket:"))
    }

    @Test
    fun `ticket round-trips through parse and serialize`() {
        val tc = TestCase(
            title = "Test",
            steps = listOf(
                TestStep(action = "Step one", expected = "Expected one", tickets = listOf("BUG-42")),
                TestStep(action = "Step two", expected = "Expected two"),
            ),
        )
        val md = TestCaseSerializer.serialize(tc)
        val parsed = TestCaseParser.parse(md)
        assertEquals(listOf("BUG-42"), parsed.steps[0].tickets)
        assertEquals(emptyList<String>(), parsed.steps[1].tickets)
    }

    @Test
    fun `round trip preserves step links`() {
        val original = TestCase(
            title = "Round trip step links",
            steps = listOf(
                TestStep(
                    action = "Open Figma mock",
                    expected = "Matches screen",
                    links = listOf(
                        Link(title = "Figma login spec", url = "https://figma.com/file/abc"),
                        Link(title = "", url = "https://docs.google.com/requirements"),
                    ),
                ),
                TestStep(action = "No links here", expected = "Fine"),
            ),
        )
        val serialized = TestCaseSerializer.serialize(original)
        val parsed = TestCaseParser.parse(serialized)
        assertEquals(original.steps, parsed.steps)
    }

    @Test
    fun `round trip preserves step link without title`() {
        val original = TestCase(
            title = "Link without title",
            steps = listOf(
                TestStep(action = "Open", links = listOf(Link(title = "", url = "https://example.com/x"))),
            ),
        )
        val serialized = TestCaseSerializer.serialize(original)
        val parsed = TestCaseParser.parse(serialized)
        assertEquals(original.steps.single().links, parsed.steps.single().links)
    }
}
