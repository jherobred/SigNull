package app.signull.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Text whose characters roll like an odometer when they change. Rolls up when [value] grows and down
 * when it shrinks. Characters are keyed from the right so digits stay aligned when the length changes.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    value: Int? = null,
    color: Color = LocalContentColor.current,
) {
    val previous = remember { mutableStateOf(value) }
    val rising = (value ?: 0) >= (previous.value ?: 0)
    SideEffect { previous.value = value }
    Row(modifier) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val direction = if (rising) 1 else -1
                        val spec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.7f, stiffness = 500f)
                        (slideInVertically(spec) { it * direction } + fadeIn()) togetherWith
                            (slideOutVertically(spec) { -it * direction } + fadeOut()) using
                            SizeTransform(clip = false)
                    },
                    label = "rollingChar",
                ) { c ->
                    Text(text = c.toString(), style = style, color = color)
                }
            }
        }
    }
}
