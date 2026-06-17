package io.github.barsia.speqa.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ResolveDuplicateIdsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.let { DuplicateIdResolution.reviewAndResolve(it) }
    }
}
