package com.github.makewheels.video2022.ui.watch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.ExoPlayer
import com.github.makewheels.video2022.ui.components.CommentSheet
import com.github.makewheels.video2022.ui.components.LikeButtons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    onBack: () -> Unit,
    viewModel: WatchViewModel = hiltViewModel(),
    commentViewModel: CommentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showComments by remember { mutableStateOf(false) }
    var playerRef by remember { mutableStateOf<ExoPlayer?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopHeartbeat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.errorMessage != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    uiState.errorMessage ?: "加载失败",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Video player
                uiState.watchInfo?.multivariantPlaylistUrl?.let { url ->
                    VideoPlayerComposable(
                        hlsUrl = url,
                        resumePositionMs = uiState.watchInfo?.progressInMillis ?: 0,
                        onPlayerReady = { player ->
                            playerRef = player
                            viewModel.startHeartbeat(
                                getPlayerTimeMs = { player.currentPosition },
                                getPlayerStatus = {
                                    if (player.isPlaying) "PLAYING" else "PAUSED"
                                },
                                getVolume = { player.volume }
                            )
                        }
                    )
                }

                // Video info below player
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Video ID as title (WatchInfo doesn't carry title)
                    Text(
                        text = "视频 ${uiState.watchInfo?.videoId ?: ""}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "状态: ${uiState.watchInfo?.videoStatus ?: "未知"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // Like/dislike buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.likeStatus?.let { status ->
                            LikeButtons(
                                likeCount = status.likeCount,
                                dislikeCount = status.dislikeCount,
                                userAction = status.userAction,
                                onLike = { viewModel.like() },
                                onDislike = { viewModel.dislike() }
                            )
                        }

                        // Comment button
                        TextButton(onClick = {
                            val videoId = uiState.watchInfo?.videoId
                            if (videoId != null) {
                                commentViewModel.loadComments(videoId)
                                showComments = true
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Comment,
                                contentDescription = "评论",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("评论 ${uiState.commentCount}")
                        }

                        // Share button
                        TextButton(onClick = {
                            val videoId = uiState.watchInfo?.videoId
                            if (videoId != null) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "https://video.example.com/watch/$videoId")
                                    putExtra(Intent.EXTRA_SUBJECT, "分享视频")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享到"))
                            }
                        }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "分享",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("分享")
                        }
                    }
                }
            }
        }
    }

    // Comment bottom sheet
    if (showComments) {
        CommentSheet(
            videoId = uiState.watchInfo?.videoId ?: "",
            commentViewModel = commentViewModel,
            onDismiss = { showComments = false }
        )
    }
}
