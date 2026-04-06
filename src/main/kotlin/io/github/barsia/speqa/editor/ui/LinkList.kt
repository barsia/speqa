package io.github.barsia.speqa.editor.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.model.Link
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@Composable
internal fun LinkList(
    links: List<Link>,
    onLinksChange: (List<Link>) -> Unit,
    modifier: Modifier = Modifier,
    project: Project? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        links.forEachIndexed { index, link ->
            LinkRow(
                link = link,
                onClick = { BrowserUtil.browse(link.url) },
                onEdit = {
                    ApplicationManager.getApplication().invokeLater {
                        val edited = AddEditLinkDialog.show(project, editLink = link)
                        if (edited != null) {
                            onLinksChange(links.toMutableList().apply { set(index, edited) })
                        }
                    }
                },
                onDelete = {
                    val result = Messages.showOkCancelDialog(
                        SpeqaBundle.message("dialog.removeLink.message"),
                        SpeqaBundle.message("dialog.removeLink.title"),
                        Messages.getOkButton(),
                        Messages.getCancelButton(),
                        Messages.getWarningIcon(),
                    )
                    if (result == Messages.OK) {
                        onLinksChange(links - link)
                    }
                },
            )
        }

        AddLinkButton(links, onLinksChange, project)
    }
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun AddLinkButton(
    links: List<Link>,
    onLinksChange: (List<Link>) -> Unit,
    project: Project? = null,
) {
    val linkIcon = IntelliJIconKey("/icons/chainLink.svg", "/icons/chainLink.svg", iconClass = SpeqaLayout::class.java)
    val hoverFocus = rememberHoverFocusState()
    val tint = if (hoverFocus.isHovered || hoverFocus.isFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
    Row(
        modifier = Modifier
            .semantics { role = Role.Button }
            .hoverable(hoverFocus.interactionSource)
            .onFocusChanged { hoverFocus.updateFocus(it.hasFocus) }
            .clickableWithPointer(focusable = true) {
                ApplicationManager.getApplication().invokeLater {
                    val newLink = AddEditLinkDialog.show(project)
                    if (newLink != null) {
                        onLinksChange(links + newLink)
                    }
                }
            }
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(linkIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        }
        Text(
            SpeqaBundle.message("tooltip.addLink"),
            fontSize = 12.sp,
            color = tint,
        )
    }
}
