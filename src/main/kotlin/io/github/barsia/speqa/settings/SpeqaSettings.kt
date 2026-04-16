package io.github.barsia.speqa.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.barsia.speqa.model.Priority
import io.github.barsia.speqa.model.Status

@Service(Service.Level.PROJECT)
@State(name = "SpeqaSettings", storages = [Storage("speqa.xml")])
class SpeqaSettings : PersistentStateComponent<SpeqaSettings.State> {
    data class State(
        var defaultPriority: String = Priority.NORMAL.label,
        var defaultStatus: String = Status.DRAFT.label,
        var defaultEnvironments: MutableList<String> = mutableListOf(),
        var defaultRunDestination: String = DEFAULT_RUN_DESTINATION,
        var defaultAttachmentsFolder: String = DEFAULT_ATTACHMENTS_FOLDER,
        var scrollSyncEnabled: Boolean = true,
        var ticketTracker: String = TRACKER_YOUTRACK,
        var customTicketUrl: String = "",
    )

    companion object {
        const val DEFAULT_RUN_DESTINATION = "test-runs"
        const val DEFAULT_ATTACHMENTS_FOLDER = "attachments"
        const val TRACKER_YOUTRACK = "YouTrack"
        const val TRACKER_LINEAR = "Linear"
        const val TRACKER_CUSTOM = "Custom"
        val TRACKER_OPTIONS = listOf(TRACKER_YOUTRACK, TRACKER_LINEAR, TRACKER_CUSTOM)

        private val TRACKER_URLS = mapOf(
            TRACKER_YOUTRACK to "https://youtrack.jetbrains.com/issue/",
            TRACKER_LINEAR to "https://linear.app/issue/",
        )

        fun getInstance(project: Project): SpeqaSettings = project.service()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var defaultPriority: Priority
        get() = Priority.fromString(state.defaultPriority)
        set(value) {
            state.defaultPriority = value.label
        }

    var defaultStatus: Status
        get() = Status.fromString(state.defaultStatus)
        set(value) {
            state.defaultStatus = value.label
        }

    var defaultEnvironments: List<String>
        get() = state.defaultEnvironments
        set(value) {
            state.defaultEnvironments = value.toMutableList()
        }

    var defaultRunDestination: String
        get() = state.defaultRunDestination
        set(value) {
            state.defaultRunDestination = value
        }

    var defaultAttachmentsFolder: String
        get() = state.defaultAttachmentsFolder
        set(value) {
            state.defaultAttachmentsFolder = value
        }

    var scrollSyncEnabled: Boolean
        get() = state.scrollSyncEnabled
        set(value) {
            state.scrollSyncEnabled = value
        }

    var ticketTracker: String
        get() = state.ticketTracker
        set(value) {
            state.ticketTracker = value
        }

    var customTicketUrl: String
        get() = state.customTicketUrl
        set(value) {
            state.customTicketUrl = value
        }

    fun resolveTicketUrl(ticketId: String): String {
        val base = when (ticketTracker) {
            TRACKER_CUSTOM -> customTicketUrl
            else -> TRACKER_URLS[ticketTracker] ?: TRACKER_URLS[TRACKER_YOUTRACK]!!
        }.trimEnd('/')
        return "$base/$ticketId"
    }
}
