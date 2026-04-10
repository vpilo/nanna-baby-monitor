package org.vpilo.babymonitor.settings.presentation.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun MenuItem(
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    title: String,
    description: String? = null,
    endContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = { },
) {
    Column {
        Row(
            modifier =
                modifier
                    .padding(top = Theme.Paddings.Small, start = Theme.Paddings.Small, end = Theme.Paddings.Small)
                    .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (imageVector != null) {
                Icon(
                    modifier =
                        Modifier
                            .padding(end = Theme.Paddings.Small)
                            .size(Theme.Sizes.IconSmall),
                    imageVector = imageVector,
                    contentDescription = null,
                )
            } else {
                Spacer(modifier = Modifier.size(Theme.Sizes.IconSmall + Theme.Paddings.Small))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                )
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            endContent?.let {
                Box(modifier = Modifier.padding(start = Theme.Paddings.Small)) {
                    it()
                }
            }
        }
    }
    bottomContent?.let {
        Row(
            modifier =
                Modifier
                    .padding(
                        top = Theme.Paddings.Tiny,
                        start = Theme.Sizes.IconSmall + Theme.Paddings.Medium,
                        bottom = Theme.Paddings.Small,
                    ),
        ) {
            it()
        }
    }
}

@Preview
@Composable
private fun MenuItemPreview() =
    AppTheme {
        Column {
            MenuItem(imageVector = Icons.Default.SwapHoriz, title = "Change Role", description = "Switch between camera and view role")
            MenuItem(
                imageVector = Icons.Default.Notifications,
                title = "Enable Notifications",
                endContent = { ThemedSwitch(checked = true, onCheckedChange = {}) },
            )
            MenuItem(title = "Device name", bottomContent = { TextField(value = "Baby Bedroom", onValueChange = {}) })
            MenuItem(
                title = "Role",
                bottomContent = {
                    Row {
                        Text(text = "Camera")
                        Image(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "DropDown Icon",
                        )
                    }
                },
            )
            MenuItem(imageVector = Icons.Default.Star, title = "Test")
            MenuItem(title = "About")
        }
    }
