package app.signull.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.signull.data.FloorChoice

/** Asks which floor to drop a new spot on. */
@Composable
fun FloorPickerSheet(
    choices: List<FloorChoice>,
    onPick: (FloorChoice) -> Unit,
    onCreateBuilding: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                "Save to which floor?",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "You'll tap where you're standing next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (choices.isEmpty()) {
                EmptyState(
                    title = "No floors yet",
                    body = "Add a building and a floor in Maps, then save spots to it.",
                    illustration = { MorphingBlob(SigShapes.cookie9, MaterialTheme.colorScheme.primaryContainer, Modifier.size(96.dp)) },
                    action = {
                        Button(onClick = onCreateBuilding) {
                            Icon(Icons.Rounded.Map, null)
                            Text("Open Maps", Modifier.padding(start = 8.dp))
                        }
                    },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(choices, key = { it.floorId }) { choice ->
                        ListItem(
                            headlineContent = { Text(choice.floorName) },
                            supportingContent = { Text(choice.buildingName) },
                            leadingContent = { Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .clickable { onPick(choice) },
                        )
                    }
                }
            }
        }
    }
}
