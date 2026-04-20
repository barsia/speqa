package io.github.barsia.speqa.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BasicTooltipBox
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberBasicTooltipState
import androidx.compose.foundation.shape.RoundedCornerShape as TooltipShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import io.github.barsia.speqa.SpeqaBundle
import io.github.barsia.speqa.registry.IdType
import org.jetbrains.jewel.bridge.icon.fromPlatformIcon
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.textAreaStyle
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import java.awt.Color as AwtColor
import java.awt.Cursor
import javax.swing.UIManager

private val handPointerIcon = androidx.compose.ui.input.pointer.PointerIcon(Cursor(Cursor.HAND_CURSOR))

internal class HoverFocusState(
    val interactionSource: MutableInteractionSource,
    val isHovered: Boolean,
    private val focusedState: MutableState<Boolean>,
) {
    val isFocused: Boolean
        get() = focusedState.value

    fun updateFocus(isFocused: Boolean) {
        focusedState.value = isFocused
    }
}

@Composable
internal fun rememberHoverFocusState(): HoverFocusState {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val focusedState = remember { mutableStateOf(false) }
    return remember(interactionSource, isHovered, focusedState) {
        HoverFocusState(
            interactionSource = interactionSource,
            isHovered = isHovered,
            focusedState = focusedState,
        )
    }
}

data class IconMenuItem(
    val label: String,
    val icon: javax.swing.Icon? = null,
    val action: () -> Unit,
)

/**
 * Modifier that shows an IntelliJ action popup menu on right-click.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.contextMenuWithIcon(
    items: () -> List<IconMenuItem>,
): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val isRightClick = event.type == androidx.compose.ui.input.pointer.PointerEventType.Press &&
                event.changes.any { it.pressed && !it.previousPressed } &&
                event.button == androidx.compose.ui.input.pointer.PointerButton.Secondary
            if (isRightClick) {
                event.changes.forEach { it.consume() }
                val screenLocation = java.awt.MouseInfo.getPointerInfo()?.location
                val menuItems = items()
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    val group = com.intellij.openapi.actionSystem.DefaultActionGroup()
                    menuItems.forEach { item ->
                        group.add(object : com.intellij.openapi.actionSystem.AnAction(item.label, null, item.icon) {
                            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                                item.action()
                            }
                        })
                    }
                    val component = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                    if (component != null && screenLocation != null) {
                        val componentLocation = component.locationOnScreen
                        val popupMenu = com.intellij.openapi.actionSystem.ActionManager.getInstance()
                            .createActionPopupMenu("SpeqaContextMenu", group)
                        popupMenu.component.show(
                            component,
                            screenLocation.x - componentLocation.x,
                            screenLocation.y - componentLocation.y,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Show hand cursor on hover. Apply to any interactive element —
 * custom clickable areas, wrappers around Jewel components, etc.
 */
fun Modifier.handOnHover(): Modifier = this.pointerHoverIcon(handPointerIcon)

@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = SpeqaThemeColors.foreground,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = 20.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = renderInlineMarkdown(markdown, color),
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}

internal fun renderInlineMarkdown(markdown: String, baseColor: Color): AnnotatedString {
    val input = markdown.replace("\r\n", "\n")
    val result = buildAnnotatedString {
        var index = 0
        while (index < input.length) {
            when {
                input.startsWith("~~", index) -> {
                    val end = input.indexOf("~~", startIndex = index + 2)
                    if (end > index + 2) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor)) {
                            append(renderInlineMarkdown(input.substring(index + 2, end), baseColor))
                        }
                        index = end + 2
                    } else {
                        append(input[index])
                        index++
                    }
                }
                input.startsWith("**", index) -> {
                    val end = input.indexOf("**", startIndex = index + 2)
                    if (end > index + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                            append(renderInlineMarkdown(input.substring(index + 2, end), baseColor))
                        }
                        index = end + 2
                    } else {
                        append(input[index])
                        index++
                    }
                }
                input[index] == '*' -> {
                    val end = input.indexOf('*', startIndex = index + 1)
                    if (end > index + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                            append(renderInlineMarkdown(input.substring(index + 1, end), baseColor))
                        }
                        index = end + 1
                    } else {
                        append(input[index])
                        index++
                    }
                }
                input[index] == '_' -> {
                    val end = input.indexOf('_', startIndex = index + 1)
                    if (end > index + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                            append(renderInlineMarkdown(input.substring(index + 1, end), baseColor))
                        }
                        index = end + 1
                    } else {
                        append(input[index])
                        index++
                    }
                }
                input[index] == '`' -> {
                    val end = input.indexOf('`', startIndex = index + 1)
                    if (end > index + 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = baseColor)) {
                            append(input.substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(input[index])
                        index++
                    }
                }
                else -> {
                    append(input[index])
                    index++
                }
            }
        }
    }
    return if (result.text.isBlank()) AnnotatedString(markdown) else result
}

/**
 * [clickable] + hand cursor on hover. Drop-in replacement for [Modifier.clickable].
 */
fun Modifier.clickableWithPointer(
    enabled: Boolean = true,
    focusable: Boolean = false,
    showFocusBorder: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val base = this.handOnHover()
    if (!focusable) {
        return base
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, onClick = onClick)
    }

    return composed {
        var isFocused by remember { mutableStateOf(false) }
        val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
        base
            .then(
                if (showFocusBorder) {
                    Modifier.border(1.dp, if (isFocused) SpeqaThemeColors.accent else Color.Transparent, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> { if (enabled) currentOnClick(); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = {
                        if (enabled) currentOnClick()
                    },
                )
            }
    }
}

fun Modifier.clickableWithPointer(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    focusable: Boolean = false,
    showFocusBorder: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val base = this.handOnHover()
    if (!focusable) {
        return base
            .focusProperties { canFocus = false }
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
    }

    return composed {
        var isFocused by remember { mutableStateOf(false) }
        val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
        base
            .then(
                if (showFocusBorder) {
                    Modifier.border(1.dp, if (isFocused) SpeqaThemeColors.accent else Color.Transparent, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> { if (enabled) currentOnClick(); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(enabled, interactionSource) {
                detectTapGestures(
                    onTap = {
                        if (enabled) currentOnClick()
                    },
                )
            }
    }
}

/**
 * [IconButton] with hand cursor on hover.
 * When [focusable] is true, the button participates in Tab navigation
 * and shows an accent focus ring. Enter/Space activates the button.
 */
@Composable
fun SpeqaIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusable: Boolean = false,
    keyboardFocusRingOnly: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (focusable) {
        var isFocused by remember { mutableStateOf(false) }
        var suppressFocusRing by remember { mutableStateOf(false) }
        val showFocusBorder = isFocused && (!keyboardFocusRingOnly || !suppressFocusRing)
        val focusBorder = if (showFocusBorder) SpeqaThemeColors.accent else Color.Transparent
        Box(
            modifier = modifier
                .border(1.dp, focusBorder, RoundedCornerShape(4.dp))
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (!it.isFocused) {
                        suppressFocusRing = false
                    }
                }
                .pointerInput(enabled, keyboardFocusRingOnly) {
                    if (!enabled || !keyboardFocusRingOnly) return@pointerInput
                    detectTapGestures(onPress = {
                        suppressFocusRing = true
                        tryAwaitRelease()
                    })
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            suppressFocusRing = false
                            if (enabled) onClick()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .handOnHover()
                    .focusProperties { canFocus = false },
                enabled = enabled,
            ) { content() }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .handOnHover()
                .focusProperties { canFocus = false },
            enabled = enabled,
        ) { content() }
    }
}

private fun themeColor(key: String, fallback: AwtColor): Color {
    val color = UIManager.getColor(key) ?: fallback
    return Color(color.rgb)
}

internal fun editorBackgroundAwt(): AwtColor {
    return currentEditorScheme().defaultBackground
}

private fun editorBackgroundColor(): Color = Color(editorBackgroundAwt().rgb)

private fun currentEditorScheme(): EditorColorsScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme

internal object SpeqaThemeColors {
    val border: Color
        get() = themeColor("Component.borderColor", AwtColor(0x66, 0x66, 0x66))

    val surface: Color
        get() = editorBackgroundColor()

    val fieldSurface: Color
        get() = themeColor("TextField.background", editorBackgroundAwt())

    val foreground: Color
        get() = themeColor("Label.foreground", AwtColor(0xBB, 0xBB, 0xBB))

    val placeholder: Color
        get() = themeColor("TextField.inactiveForeground", AwtColor(0x88, 0x88, 0x88))

    val mutedForeground: Color
        get() = placeholder

    val expectedForeground: Color
        get() {
            val scheme = currentEditorScheme()
            val attrs = scheme.getAttributes(org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors.CODE_BLOCK)
                ?: scheme.getAttributes(org.intellij.plugins.markdown.highlighting.MarkdownHighlighterColors.BLOCK_QUOTE)
            val awtColor = attrs?.foregroundColor ?: AwtColor(0x6A, 0x99, 0x55)
            return Color(awtColor.rgb)
        }

    val subtleSurface: Color
        get() = lerp(surface, fieldSurface, 0.3f)

    val subtleBorder: Color
        get() = border.copy(alpha = 0.7f)

    val headerSurface: Color
        get() = lerp(surface, fieldSurface, 0.45f)

    val blockSurface: Color
        get() = lerp(surface, fieldSurface, 0.24f)

    val chipSurface: Color
        get() = lerp(surface, fieldSurface, 0.4f)

    val actionHover: Color
        get() = themeColor("ActionButton.hoverBackground", AwtColor(0x4D, 0x4D, 0x4D))

    val destructive: Color
        get() = themeColor("Component.errorFocusColor", AwtColor(0xCC, 0x33, 0x33))

    val commitFlash: Color
        get() = themeColor("Component.focusColor", AwtColor(0x31, 0xA2, 0x4C))

    val dropTarget: Color
        get() = accent

    val divider: Color
        get() = border.copy(alpha = 0.28f)

    val accent: Color
        get() = themeColor("Link.activeForeground", AwtColor(0x3F, 0x7F, 0xDB))

    val accentSubtle: Color
        get() = accent.copy(alpha = 0.1f)

    val verdictPassed: Color
        get() = passedIndicator.copy(alpha = 0.22f)

    val verdictFailed: Color
        get() = destructive.copy(alpha = 0.22f)

    val verdictSkipped: Color
        get() = skippedIndicator.copy(alpha = 0.22f)

    val verdictBlocked: Color
        @Composable get() = accent.copy(alpha = 0.22f)

    val passedIndicator: Color
        get() = themeColor("Component.validFocusColor", AwtColor(0x2D, 0xA4, 0x4E))

    val skippedIndicator: Color
        get() = themeColor("Component.infoForeground", AwtColor(0x91, 0x91, 0x91))
}

internal object SpeqaLayout {
    // === 4dp base scale (canonical) ===
    val s0 = 0.dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp

    // === Legacy aliases — existing callsites stay compiling. New code uses s0..s6. ===
    val pagePadding = s4
    val sectionGap = s5
    val blockGap = s3
    val compactGap = s2
    val tightGap = s1
    val itemGap = s0
    val blockPadding = s3
    val headerPadding = s3
    val contentInset = s2

    // === Non-spacing (kept as-is) ===
    val headerRadius = 8.dp
    val blockRadius = 8.dp
    val stepNumberColumnWidth = s5
    val actionPillRadius = 6.dp
    val controlHeight = 28.dp
    val titleControlHeight = 44.dp
    val controlCornerRadius = 8.dp
    val chipHeight = 28.dp
}

internal object SpeqaTypography {
    val titleFontSize = 20.sp
    val titleWeight = FontWeight.SemiBold
    val titleLineHeight = 24.sp

    val h2FontSize = 14.sp
    val h2Weight = FontWeight.SemiBold
    val h2LineHeight = 17.sp

    val bodyFontSize = 13.sp
    val bodyWeight = FontWeight.Normal
    val bodyLineHeight = 19.sp

    val metaFontSize = 12.sp
    val metaWeight = FontWeight.Normal
    val metaLineHeight = 16.sp

    val numericFontSize = 11.sp
    val numericWeight = FontWeight.SemiBold
    val numericTracking = 0.6.sp
    val numericLineHeight = 14.sp

    val placeholderFontSize = 12.sp
    val placeholderLineHeight = 16.sp
}

@Composable
internal fun AccentBar(active: Boolean, modifier: Modifier = Modifier) {
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) SpeqaThemeColors.accent else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 120),
        label = "AccentBar.color",
    )
    Box(
        modifier = modifier
            .width(2.dp)
            .fillMaxHeight()
            .background(color),
    )
}

/**
 * Draws a 2dp left-edge accent bar on this composable using [drawWithContent].
 * Use this instead of the [AccentBar] composable sibling when the parent Row
 * cannot use [androidx.compose.foundation.layout.IntrinsicSize.Min] (e.g. when
 * the Row contains a Jewel [TextArea] whose ScrollableContainer returns
 * Int.MAX_VALUE from minIntrinsicHeight).
 */
internal fun Modifier.accentBar(active: Boolean, width: androidx.compose.ui.unit.Dp = 1.dp): Modifier = composed {
    val color by animateColorAsState(
        targetValue = if (active) SpeqaThemeColors.accent else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "AccentBar.color",
    )
    drawWithContent {
        drawContent()
        val strokeWidthPx = width.toPx()
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = GeometrySize(strokeWidthPx, size.height),
        )
    }
}

@Composable
internal fun BlockIcon(emoji: String, contentDescription: String, modifier: Modifier = Modifier) {
    Text(
        text = emoji,
        color = SpeqaThemeColors.mutedForeground,
        fontSize = SpeqaTypography.metaFontSize,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    )
}

@Composable
internal fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(SpeqaThemeColors.mutedForeground.copy(alpha = 0.2f)),
    )
}

@Composable
internal fun HorizontalHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SpeqaThemeColors.mutedForeground.copy(alpha = 0.2f)),
    )
}

internal object FocusedMultilineInsertion {
    @Volatile
    private var handler: (() -> Unit)? = null
    fun set(h: (() -> Unit)?) { handler = h }
    fun invokeNewline(): Boolean {
        val h = handler ?: return false
        h()
        return true
    }
}

@Composable
internal fun PlainTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minHeight: Int = 40,
    focusAtEndRequest: Int = 0,
    onFocusStateChange: ((Boolean) -> Unit)? = null,
    onCommitRequest: ((String) -> Unit)? = null,
    onPlainEnter: (() -> Boolean)? = null,
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    val state = remember { androidx.compose.foundation.text.input.TextFieldState(value) }
    val resolvedMinHeight = if (minHeight > 40) minHeight.dp else SpeqaLayout.controlHeight
    val inputModifier = if (singleLine) {
        modifier.fillMaxWidth().heightIn(min = resolvedMinHeight)
    } else {
        modifier.fillMaxWidth().heightIn(max = 200.dp)
    }
        .onFocusChanged {
            val focused = it.isFocused || it.hasFocus
            isFocused = focused
            onFocusStateChange?.invoke(focused)
            if (!singleLine) {
                if (focused) {
                    FocusedMultilineInsertion.set {
                        val sel = state.selection
                        state.edit {
                            replace(sel.min, sel.max, "\n")
                            selection = TextRange(sel.min + 1)
                        }
                    }
                } else {
                    FocusedMultilineInsertion.set(null)
                }
            }
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Tab -> {
                    focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                    true
                }
                Key.Enter -> if (singleLine) {
                    onCommitRequest?.invoke(state.text.toString())
                    if (onCommitRequest != null) {
                        focusManager.clearFocus()
                        true
                    } else {
                        false
                    }
                } else {
                    if (event.isShiftPressed || event.isMetaPressed || event.isCtrlPressed || event.isAltPressed) {
                        val sel = state.selection
                        state.edit {
                            replace(sel.min, sel.max, "\n")
                            selection = TextRange(sel.min + 1)
                        }
                        true
                    } else {
                        val result = ListContinuation.onEnter(
                            text = state.text.toString(),
                            cursor = state.selection.start,
                        )
                        if (result != null) {
                            state.edit {
                                replace(0, length, result.text)
                                selection = TextRange(result.cursor)
                            }
                            true
                        } else if (onPlainEnter != null) {
                            val current = state.text.toString()
                            val cursorPos = state.selection.start
                            val onEmptyTrailingLine = cursorPos == current.length &&
                                (current.isEmpty() || current.endsWith("\n"))
                            if (onEmptyTrailingLine && current.endsWith("\n")) {
                                val trimmed = current.trimEnd('\n')
                                state.edit {
                                    replace(0, length, trimmed)
                                    selection = TextRange(trimmed.length)
                                }
                            }
                            onPlainEnter()
                            true
                        } else false
                    }
                }
                else -> false
            }
        }

    val currentValue by androidx.compose.runtime.rememberUpdatedState(value)
    val currentOnValueChange by androidx.compose.runtime.rememberUpdatedState(onValueChange)

    // Sync external value → state only when NOT focused (avoid overwriting user edits)
    LaunchedEffect(value, isFocused) {
        if (!isFocused && state.text.toString() != value) {
            state.edit {
                replace(0, length, value)
            }
        }
    }

    LaunchedEffect(focusAtEndRequest) {
        if (focusAtEndRequest == 0) return@LaunchedEffect
        state.edit {
            selection = TextRange(length)
        }
    }

    // Sync state → external onValueChange (when user types)
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.text.toString() }
            .collect { text: String ->
                if (text != currentValue) {
                    currentOnValueChange(text)
                }
            }
    }

    val placeholderContent: @Composable (() -> Unit)? =
        if (placeholder.isNotBlank()) {{ Text(placeholder) }} else null

    if (singleLine) {
        TextField(
            state = state,
            readOnly = readOnly,
            placeholder = placeholderContent,
            modifier = inputModifier,
            enabled = true,
        )
    } else {
        val defaultStyle = JewelTheme.textAreaStyle
        val compactStyle = org.jetbrains.jewel.ui.component.styling.TextAreaStyle(
            colors = defaultStyle.colors,
            metrics = org.jetbrains.jewel.ui.component.styling.TextAreaMetrics(
                borderWidth = defaultStyle.metrics.borderWidth,
                contentPadding = defaultStyle.metrics.contentPadding,
                cornerSize = defaultStyle.metrics.cornerSize,
                minSize = androidx.compose.ui.unit.DpSize(0.dp, 0.dp),
            ),
        )
        TextArea(
            state = state,
            readOnly = readOnly,
            placeholder = placeholderContent,
            modifier = inputModifier,
            enabled = true,
            style = compactStyle,
        )
    }
}

@Composable
internal fun QuietActionText(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    icon: IconKey? = null,
    iconLeadingPad: androidx.compose.ui.unit.Dp = 5.dp,
    uppercase: Boolean = true,
    plain: Boolean = false,
    labelMaxLines: Int = Int.MAX_VALUE,
    labelOverflow: TextOverflow = TextOverflow.Clip,
    fillLabelWidth: Boolean = false,
    previousFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
) {
    val hoverFocus = rememberHoverFocusState()
    val focusManager = LocalFocusManager.current
    val bg = when {
        plain -> Color.Transparent
        !enabled -> SpeqaThemeColors.chipSurface.copy(alpha = 0.35f)
        hoverFocus.isHovered || hoverFocus.isFocused -> SpeqaThemeColors.actionHover
        else -> SpeqaThemeColors.chipSurface
    }
    val focusBorder = if (hoverFocus.isFocused) SpeqaThemeColors.accent else Color.Transparent
    val shape = if (plain) RoundedCornerShape(2.dp) else RoundedCornerShape(SpeqaLayout.actionPillRadius)
    Box(
        modifier = modifier
            .background(bg, shape)
            .border(1.dp, focusBorder, shape)
            .onFocusChanged { hoverFocus.updateFocus(it.isFocused) }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> { if (enabled) onClick(); true }
                    Key.Tab -> {
                        focusManager.moveFocus(
                            if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                        )
                        true
                    }
                    else -> false
                }
            }
            .focusProperties {
                if (previousFocusRequester != null) previous = previousFocusRequester
                if (nextFocusRequester != null) next = nextFocusRequester
            }
            .focusable()
            .clickableWithPointer(interactionSource = hoverFocus.interactionSource, enabled = enabled, onClick = onClick)
            .then(if (plain) Modifier.padding(horizontal = 5.dp, vertical = 1.dp) else Modifier.padding(horizontal = 7.dp, vertical = 4.dp)),
    ) {
        val textColor = when {
            !enabled -> SpeqaThemeColors.mutedForeground.copy(alpha = 0.45f)
            hoverFocus.isHovered || hoverFocus.isFocused -> SpeqaThemeColors.foreground
            else -> SpeqaThemeColors.mutedForeground
        }
        if (icon != null) {
            Row(
                modifier = if (fillLabelWidth) Modifier.fillMaxWidth() else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                val effectiveIconPad = if (plain) 0.dp else iconLeadingPad
                if (effectiveIconPad > 0.dp) Spacer(modifier = Modifier.width(effectiveIconPad))
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = textColor)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (uppercase) label.uppercase() else label,
                    modifier = if (fillLabelWidth) Modifier.weight(1f) else Modifier,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = textColor,
                    maxLines = labelMaxLines,
                    overflow = labelOverflow,
                )
            }
        } else {
            Text(
                text = if (uppercase) label.uppercase() else label,
                modifier = if (fillLabelWidth) Modifier.fillMaxWidth() else Modifier,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = textColor,
                maxLines = labelMaxLines,
                overflow = labelOverflow,
            )
        }
    }
}

/**
 * Shared flash-on-commit state for inline-editable fields (title, ID).
 * Call [triggerFlash] when a real value change is committed.
 * The icon shows checkmark during [isEditing] or [showFlash]; pencil otherwise.
 * [greenTint] provides the animated background color for the icon container.
 */
internal class CommitFlashState {
    var showFlash by mutableStateOf(false)
    val flashAlpha = androidx.compose.animation.core.Animatable(0f)
    val saveIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.MenuSaveall, SpeqaLayout::class.java)
    val editIcon = IntelliJIconKey.fromPlatformIcon(AllIcons.Actions.Edit, SpeqaLayout::class.java)

    fun triggerFlash() { showFlash = true }

    fun displaySave(isEditing: Boolean) = isEditing || showFlash
    fun greenTint() = if (showFlash) SpeqaThemeColors.commitFlash.copy(alpha = flashAlpha.value) else Color.Transparent
    fun iconKey(isEditing: Boolean) = if (displaySave(isEditing)) saveIcon else editIcon
}

@Composable
internal fun rememberCommitFlashState(): CommitFlashState {
    val state = remember { CommitFlashState() }
    LaunchedEffect(state.showFlash) {
        if (state.showFlash) {
            state.flashAlpha.snapTo(1f)
            delay(200)
            state.showFlash = false
        }
    }
    return state
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun InlineEditableTitleRow(
    title: String,
    onTitleCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var draftTitle by rememberSaveable(title) { mutableStateOf(title) }
    val focusRequester = remember { FocusRequester() }
    val titlePencilFocusRequester = remember { FocusRequester() }

    var wasEditing by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            wasEditing = true
            yield()
            focusRequester.requestFocus()
        } else if (wasEditing) {
            titlePencilFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(title) {
        if (!isEditing) {
            draftTitle = title
        }
    }

    val titleStyle = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = SpeqaThemeColors.foreground,
    )

    var textFieldValue by remember(draftTitle) {
        mutableStateOf(TextFieldValue(draftTitle, selection = TextRange(draftTitle.length)))
    }

    LaunchedEffect(draftTitle) {
        if (textFieldValue.text != draftTitle) {
            textFieldValue = TextFieldValue(draftTitle, selection = TextRange(draftTitle.length))
        }
    }

    val flash = rememberCommitFlashState()

    fun doCommit() {
        val normalizedTitle = draftTitle.trim()
        if (normalizedTitle != title) {
            onTitleCommit(normalizedTitle)
            flash.triggerFlash()
        }
        isEditing = false
    }

    fun doCancel() {
        draftTitle = title
        isEditing = false
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.compactGap),
        verticalAlignment = Alignment.Top,
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = {
                if (isEditing) {
                    textFieldValue = it
                    draftTitle = it.text
                }
            },
            textStyle = titleStyle,
            singleLine = false,
            readOnly = !isEditing,
            cursorBrush = SolidColor(if (isEditing) SpeqaThemeColors.foreground else Color.Transparent),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .focusProperties { canFocus = FocusAccessibilityPolicy.titleTextCanFocus(isEditing) }
                .onFocusChanged { state ->
                    if (!state.isFocused && isEditing) {
                        doCommit()
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (!isEditing) return@onPreviewKeyEvent false
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> { doCommit(); true }
                        Key.Escape -> { doCancel(); true }
                        else -> false
                    }
                },
        )

        val titleIconTooltip = if (flash.displaySave(isEditing)) SpeqaBundle.message("tooltip.save") else SpeqaBundle.message("tooltip.editTitle")
        val titleHoverSource = remember { MutableInteractionSource() }
        val isTitleIconHovered by titleHoverSource.collectIsHoveredAsState()
        var titleIconFocused by remember { mutableStateOf(false) }
        val titleIconAlpha = if (isEditing || flash.showFlash || isTitleIconHovered || titleIconFocused) 1f else 0.5f
        val titleIconFocusBorder = if (titleIconFocused) SpeqaThemeColors.accent else Color.Transparent
        val titleIconBg = when {
            flash.showFlash -> flash.greenTint()
            isTitleIconHovered || titleIconFocused -> SpeqaThemeColors.actionHover
            else -> Color.Transparent
        }
        val titleFocusManager = LocalFocusManager.current
        Tooltip(tooltip = { Text(titleIconTooltip) }) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .alpha(titleIconAlpha)
                    .background(titleIconBg, RoundedCornerShape(4.dp))
                    .border(1.dp, titleIconFocusBorder, RoundedCornerShape(4.dp))
                    .hoverable(titleHoverSource)
                    .pointerHoverIcon(handPointerIcon)
                    .focusRequester(titlePencilFocusRequester)
                    .onFocusChanged { titleIconFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                when {
                                    isEditing -> doCommit()
                                    flash.showFlash -> {}
                                    else -> isEditing = true
                                }
                                true
                            }
                            Key.Tab -> { titleFocusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next); true }
                            else -> false
                        }
                    }
                    .focusTarget()
                    .pointerInput(isEditing, flash.showFlash) {
                        detectTapGestures(
                            onTap = {
                                when {
                                    isEditing -> doCommit()
                                    flash.showFlash -> {}
                                    else -> isEditing = true
                                }
                            },
                        )
                    }
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    key = flash.iconKey(isEditing),
                    contentDescription = titleIconTooltip,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
internal fun InlineEditableIdRow(
    id: Int?,
    idType: IdType,
    nextFreeId: Int,
    isDuplicate: Boolean,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onIdAssign: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefix = when (idType) {
        IdType.TEST_CASE -> SpeqaBundle.message("label.idPrefix.tc")
        IdType.TEST_RUN -> SpeqaBundle.message("label.idPrefix.tr")
    }

    if (id == null) {
        // Unassigned: red placeholder, click assigns
        Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.assignId")) }) {
            Row(
                modifier = modifier.clickableWithPointer { onIdAssign(nextFreeId) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "$prefix$nextFreeId",
                    color = SpeqaThemeColors.destructive,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    } else {
        // Assigned: TC-N with pencil/checkmark, inline edit
        var draftValue by remember {
            mutableStateOf(TextFieldValue(id.toString(), TextRange(id.toString().length)))
        }
        val focusRequester = remember { FocusRequester() }
        val pencilFocusRequester = remember { FocusRequester() }
        var hadFocus by remember { mutableStateOf(false) }
        var wasEditing by remember { mutableStateOf(false) }

        // Reset hadFocus when editing state changes
        LaunchedEffect(isEditing) {
            hadFocus = false
            if (isEditing) {
                wasEditing = true
                val text = draftValue.text
                draftValue = draftValue.copy(selection = TextRange(text.length))
                focusRequester.requestFocus()
            } else if (wasEditing) {
                pencilFocusRequester.requestFocus()
            }
        }

        // Sync draft from external id changes when not editing
        LaunchedEffect(id) {
            if (!isEditing) {
                draftValue = TextFieldValue(id.toString(), TextRange(id.toString().length))
            }
        }

        val idColor = if (isDuplicate) SpeqaThemeColors.destructive else SpeqaThemeColors.foreground
        val duplicateMsg = if (isDuplicate) {
            when (idType) {
                IdType.TEST_CASE -> SpeqaBundle.message("id.duplicate", id)
                IdType.TEST_RUN -> SpeqaBundle.message("id.duplicateTr", id)
            }
        } else null

        val idTextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = idColor)
        val cursorBrush = remember(idColor) { SolidColor(idColor) }

        // Measure text width to keep view/edit mode the same size
        val textMeasurer = rememberTextMeasurer()
        val numberText = if (isEditing) draftValue.text else id.toString()
        val measuredWidth = remember(numberText, idTextStyle) {
            textMeasurer.measure(numberText.ifEmpty { "0" }, idTextStyle).size.width
        }
        val cursorPadding = 4.dp
        val fieldWidth = with(LocalDensity.current) { measuredWidth.toDp() + cursorPadding }

        val flash = rememberCommitFlashState()

        fun commit() {
            if (!isEditing) return
            val parsed = draftValue.text.trim().toIntOrNull()
            if (parsed != null && parsed != id) {
                onIdAssign(parsed)
                flash.triggerFlash()
            }
            onEditingChange(false)
        }

        fun cancel() {
            draftValue = TextFieldValue(id.toString(), TextRange(id.toString().length))
            onEditingChange(false)
        }

        if (isEditing) {
            // Edit mode: text area (no pointer) + icon with "Save" tooltip + pointer
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(prefix, style = idTextStyle)
                BasicTextField(
                    value = draftValue,
                    onValueChange = { newValue ->
                        draftValue = newValue.copy(text = newValue.text.filter(Char::isDigit))
                    },
                    textStyle = idTextStyle,
                    singleLine = true,
                    cursorBrush = cursorBrush,
                    modifier = Modifier
                        .width(fieldWidth)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) hadFocus = true
                            if (!state.isFocused && hadFocus && isEditing) commit()
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> { commit(); true }
                                Key.Escape -> { cancel(); true }
                                else -> false
                            }
                        },
                )
                Tooltip(tooltip = { Text(SpeqaBundle.message("tooltip.save")) }) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(flash.greenTint(), RoundedCornerShape(4.dp))
                            .clickableWithPointer { hadFocus = false; commit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(flash.iconKey(isEditing), SpeqaBundle.message("tooltip.save"), Modifier.size(12.dp))
                    }
                }
            }
        } else {
            // View mode: whole row is clickable with pointer + tooltip
            val viewTooltip = if (isDuplicate) duplicateMsg!! else SpeqaBundle.message("tooltip.editId")
            val idFocusManager = LocalFocusManager.current
            Tooltip(tooltip = { Text(viewTooltip) }) {
                Row(
                    modifier = modifier
                        .clickableWithPointer { if (!flash.showFlash) onEditingChange(true) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(prefix, style = idTextStyle)
                    Text(id.toString(), style = idTextStyle, modifier = Modifier.width(fieldWidth))
                    val idViewHoverSource = remember { MutableInteractionSource() }
                    val isIdIconHovered by idViewHoverSource.collectIsHoveredAsState()
                    var idIconFocused by remember { mutableStateOf(false) }
                    val idIconAlpha = if (flash.showFlash || isIdIconHovered || idIconFocused) 1f else 0.5f
                    val idIconFocusBorder = if (idIconFocused) SpeqaThemeColors.accent else Color.Transparent
                    val idIconBg = when {
                        flash.showFlash -> flash.greenTint()
                        isIdIconHovered || idIconFocused -> SpeqaThemeColors.actionHover
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .alpha(idIconAlpha)
                            .background(idIconBg, RoundedCornerShape(4.dp))
                            .border(1.dp, idIconFocusBorder, RoundedCornerShape(4.dp))
                            .hoverable(idViewHoverSource)
                            .focusRequester(pencilFocusRequester)
                            .onFocusChanged { idIconFocused = it.isFocused }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> { if (!flash.showFlash) onEditingChange(true); true }
                                    Key.Tab -> { idFocusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next); true }
                                    else -> false
                                }
                            }
                            .focusTarget()
                            .handOnHover()
                            .focusProperties { canFocus = false }
                            .pointerInput(flash.showFlash) {
                                detectTapGestures(
                                    onTap = {
                                        if (!flash.showFlash) onEditingChange(true)
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(flash.iconKey(isEditing), viewTooltip, Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Shared pencil / floppy-disk toggle used by every inline-editable field.
 * Shows [AllIcons.Actions.Edit] when [isEditing] is false and
 * [AllIcons.Actions.MenuSaveall] when true.
 * Container: 16 dp, icon: 12 dp, uses [clickableWithPointer].
 * Pencil is muted (0.3 alpha) by default; becomes fully visible only when
 * the icon itself is hovered. Save icon is always fully visible.
 */
@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun EditToggleIcon(
    isEditing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hoverFocus = rememberHoverFocusState()
    val iconKey = IntelliJIconKey.fromPlatformIcon(
        if (isEditing) AllIcons.Actions.MenuSaveall else AllIcons.Actions.Edit,
        SpeqaLayout::class.java,
    )
    val description = if (isEditing) SpeqaBundle.message("tooltip.save") else SpeqaBundle.message("tooltip.edit")
    val focusManager = LocalFocusManager.current
    val alpha = if (isEditing || hoverFocus.isHovered || hoverFocus.isFocused) 1f else 0.5f
    val focusBorder = if (hoverFocus.isFocused) SpeqaThemeColors.accent else Color.Transparent
    val hoverBg = if (hoverFocus.isHovered || hoverFocus.isFocused) SpeqaThemeColors.actionHover else Color.Transparent
    Tooltip(
        tooltip = { Text(description) },
    ) {
        Box(
            modifier = modifier
                .size(20.dp)
                .alpha(alpha)
                .background(hoverBg, RoundedCornerShape(4.dp))
                .border(1.dp, focusBorder, RoundedCornerShape(4.dp))
                .semantics { role = Role.Button }
                .hoverable(hoverFocus.interactionSource)
                .pointerHoverIcon(handPointerIcon)
                .onFocusChanged { hoverFocus.updateFocus(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> { onClick(); true }
                        Key.Tab -> { focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next); true }
                        else -> false
                    }
                }
                .focusTarget()
                .pointerInput(onClick) {
                    detectTapGestures(
                        onTap = { onClick() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                key = iconKey,
                contentDescription = description,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * Persistent rich-content tooltip. Previous implementation used [BasicTooltipBox]
 * but it remounts the tooltip composable at ~45 Hz in narrow layouts (visible as
 * flicker). This implementation tracks hover manually via [collectIsHoveredAsState]
 * with a small show-delay and a grace period on hide, and renders the tooltip
 * through a plain [Popup] using a custom position policy that keeps the popup
 * inside the visible window area without covering the anchor when an alternate
 * placement fits. Since our state is stable across parent recompositions, the
 * Popup composable is not torn down on each tick and the tooltip renders once,
 * without flicker.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RichTooltip(
    edgeToEdgeContent: Boolean = false,
    tooltip: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val density = LocalDensity.current
    val gapPx = remember(density) { with(density) { 8.dp.roundToPx() } }
    val marginPx = remember(density) { with(density) { 8.dp.roundToPx() } }
    val chrome = remember(edgeToEdgeContent) { richTooltipChrome(edgeToEdgeContent) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            kotlinx.coroutines.delay(300)
            if (isHovered) visible = true
        } else {
            kotlinx.coroutines.delay(150)
            if (!isHovered) visible = false
        }
    }

    Box(modifier = Modifier.hoverable(interactionSource)) {
        content()
        if (visible) {
            val positionProvider = remember(gapPx, marginPx) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        return calculateRichTooltipPosition(
                            windowSize = windowSize,
                            anchorBounds = anchorBounds,
                            popupContentSize = popupContentSize,
                            gapPx = gapPx,
                            marginPx = marginPx,
                        )
                    }
                }
            }
            androidx.compose.ui.window.Popup(
                popupPositionProvider = positionProvider,
                properties = androidx.compose.ui.window.PopupProperties(focusable = false),
            ) {
                Box(
                    modifier = Modifier
                        .background(SpeqaThemeColors.blockSurface, TooltipShape(6.dp))
                        .border(1.dp, SpeqaThemeColors.mutedForeground.copy(alpha = 0.35f), TooltipShape(6.dp))
                        .clip(TooltipShape(6.dp))
                        .then(
                            when (chrome) {
                                RichTooltipChrome.Default -> Modifier.padding(SpeqaLayout.s2)
                                RichTooltipChrome.EdgeToEdge -> Modifier
                            },
                        ),
                ) {
                    tooltip()
                }
            }
        }
    }
}

@Composable
internal fun SurfaceDivider(visible: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .then(if (visible) Modifier.background(SpeqaThemeColors.divider) else Modifier),
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.semantics { heading() },
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = SpeqaThemeColors.mutedForeground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun UtilityText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 12.sp,
        color = SpeqaThemeColors.mutedForeground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalJewelApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun DateIconLabel(
    iconKey: IconKey,
    label: String,
    dateLabel: String,
    modifier: Modifier = Modifier,
) {
    val color = SpeqaThemeColors.mutedForeground
    var isTruncated by remember { mutableStateOf(false) }
    val tooltipText = if (isTruncated) "$label $dateLabel" else label

    Tooltip(tooltip = { Text(tooltipText) }) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                key = iconKey,
                contentDescription = label,
                modifier = Modifier.size(14.dp).offset(y = (-1).dp),
                tint = color,
            )
            Text(
                text = dateLabel,
                fontSize = 12.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result -> isTruncated = result.hasVisualOverflow },
            )
        }
    }
}

@Composable
internal fun SectionHeaderWithDivider(
    title: String,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpeqaLayout.blockPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title)
        Box(
            Modifier.weight(1f).height(1.dp).background(SpeqaThemeColors.divider)
        )
        actions()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalJewelApi::class)
@Composable
internal fun HeaderAddIconButton(
    tooltip: String,
    onClick: () -> Unit,
    addRequester: FocusRequester? = null,
) {
    val addIcon = remember { IntelliJIconKey.fromPlatformIcon(com.intellij.icons.AllIcons.General.Add, SpeqaLayout::class.java) }
    val hoverFocus = rememberHoverFocusState()
    val border = if (hoverFocus.isFocused) SpeqaThemeColors.accent else androidx.compose.ui.graphics.Color.Transparent
    val bg = if (hoverFocus.isHovered || hoverFocus.isFocused) SpeqaThemeColors.actionHover else androidx.compose.ui.graphics.Color.Transparent
    org.jetbrains.jewel.ui.component.Tooltip(tooltip = { org.jetbrains.jewel.ui.component.Text(tooltip) }) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .semantics { role = Role.Button; contentDescription = tooltip }
                .then(if (addRequester != null) Modifier.focusRequester(addRequester) else Modifier)
                .background(bg, RoundedCornerShape(4.dp))
                .border(1.dp, border, RoundedCornerShape(4.dp))
                .hoverable(hoverFocus.interactionSource)
                .onFocusChanged { hoverFocus.updateFocus(it.isFocused) }
                .handOnHover()
                .clickableWithPointer(focusable = true, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val iconTint = if (hoverFocus.isHovered || hoverFocus.isFocused) SpeqaThemeColors.foreground else SpeqaThemeColors.mutedForeground
            Icon(addIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = iconTint)
        }
    }
}
