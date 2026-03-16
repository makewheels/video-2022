package com.github.makewheels.video2022.ui.watchhistory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

data class WatchHistoryItem(
    val videoId: String = "",
    val title: String? = null,
    val coverUrl: String? = null,
    val watchTime: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryScreen(
    onVideoClick: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var items by remember { mutableStateOf<List<WatchHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Placeholder — in a full implementation this would use a ViewModel with API calls
    LaunchedEffect(Unit) {
        isLoading = false
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除观看历史") },
            text = { Text("确定要清除所有观看历史吗？") },
            confirmButton = {
                TextButton(onClick = {
                    items = emptyList()
                    showClearDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("观看历史") },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "清除观看历史")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                items.isEmpty() -> {
                    Text(
                        text = "暂无观看历史",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items, key = { "${it.videoId}-${it.watchTime}" }) { item ->
                            WatchHistoryCard(item = item, onClick = { onVideoClick(item.videoId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchHistoryCard(item: WatchHistoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title ?: "未命名视频",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "观看时间: ${item.watchTime ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
