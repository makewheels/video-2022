package com.github.makewheels.video2022.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LikeButtons(
    likeCount: Int,
    dislikeCount: Int,
    userAction: String?,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Like button
        IconButton(onClick = onLike) {
            Icon(
                imageVector = if (userAction == "LIKE") Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = "点赞",
                tint = if (userAction == "LIKE") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "$likeCount",
            style = MaterialTheme.typography.bodyMedium,
            color = if (userAction == "LIKE") MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.width(8.dp))

        // Dislike button
        IconButton(onClick = onDislike) {
            Icon(
                imageVector = if (userAction == "DISLIKE") Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = "踩",
                tint = if (userAction == "DISLIKE") MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "$dislikeCount",
            style = MaterialTheme.typography.bodyMedium,
            color = if (userAction == "DISLIKE") MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
