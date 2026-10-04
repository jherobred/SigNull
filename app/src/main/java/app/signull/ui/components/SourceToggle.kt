package app.signull.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.signull.core.signal.SignalSource

val SignalSource.icon: ImageVector
    get() = when (this) {
        SignalSource.CELLULAR -> Icons.Rounded.SignalCellularAlt
        SignalSource.WIFI -> Icons.Rounded.Wifi
    }

@Composable
fun SourceToggle(
    selected: SignalSource,
    onSelect: (SignalSource) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val sources = SignalSource.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        sources.forEachIndexed { index, source ->
            SegmentedButton(
                selected = source == selected,
                onClick = { onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = sources.size),
                enabled = enabled,
                icon = {
                    SegmentedButtonDefaults.Icon(active = source == selected) {
                        Icon(source.icon, contentDescription = null)
                    }
                },
            ) {
                Text(if (compact && source == SignalSource.CELLULAR) "Mobile" else source.label)
            }
        }
    }
}
