package io.github.barsia.speqa.model

enum class Priority(val label: String) {
    CRITICAL("critical"),
    MAJOR("major"),
    NORMAL("normal"),
    LOW("low");

    companion object {
        fun fromString(value: String): Priority {
            return entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) } ?: NORMAL
        }
    }
}

enum class Status(val label: String) {
    DRAFT("draft"),
    READY("ready"),
    DEPRECATED("deprecated");

    companion object {
        fun fromString(value: String): Status {
            return entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) } ?: DRAFT
        }
    }
}

data class Attachment(val path: String)

data class Link(val title: String, val url: String)

data class TestStep(
    val action: String = "",
    val expected: String? = null,
    val expectedGroupSize: Int = 1,
    val attachments: List<Attachment> = emptyList(),
    val tickets: List<String> = emptyList(),
    val links: List<Link> = emptyList(),
    val uid: Long = nextUid(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestStep) return false
        return action == other.action &&
            expected == other.expected &&
            expectedGroupSize == other.expectedGroupSize &&
            attachments == other.attachments &&
            tickets == other.tickets &&
            links == other.links
    }

    override fun hashCode(): Int {
        var result = action.hashCode()
        result = 31 * result + (expected?.hashCode() ?: 0)
        result = 31 * result + expectedGroupSize
        result = 31 * result + attachments.hashCode()
        result = 31 * result + tickets.hashCode()
        result = 31 * result + links.hashCode()
        return result
    }

    companion object {
        private val uidCounter = java.util.concurrent.atomic.AtomicLong(0L)
        fun nextUid(): Long = uidCounter.incrementAndGet()
    }
}

enum class PreconditionsMarkerStyle(val marker: String) {
    PRECONDITIONS("Preconditions:"),
    PRE_CONDITIONS("Pre-conditions:");

    companion object {
        fun fromMarker(value: String): PreconditionsMarkerStyle? {
            return entries.firstOrNull { it.marker.equals(value.trim(), ignoreCase = true) }
        }
    }
}

sealed interface TestCaseBodyBlock {
    val markdown: String
}

data class DescriptionBlock(
    override val markdown: String = "",
) : TestCaseBodyBlock

data class PreconditionsBlock(
    val markerStyle: PreconditionsMarkerStyle = PreconditionsMarkerStyle.PRECONDITIONS,
    override val markdown: String = "",
) : TestCaseBodyBlock

data class TestCase(
    val id: Int? = null,
    val title: String = "Untitled Test Case",
    val priority: Priority? = null,
    val status: Status? = null,
    val environment: List<String>? = null,
    val tags: List<String>? = null,
    val attachments: List<Attachment> = emptyList(),
    val links: List<Link> = emptyList(),
    val bodyBlocks: List<TestCaseBodyBlock> = emptyList(),
    val steps: List<TestStep> = emptyList(),
)
