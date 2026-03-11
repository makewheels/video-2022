package com.github.makewheels.video2022.data.repository

import com.github.makewheels.video2022.data.api.PlaylistApi
import com.github.makewheels.video2022.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistApi: PlaylistApi
) {
    suspend fun getMyPlaylists(skip: Int, limit: Int): Result<List<Playlist>> = runCatching {
        val resp = playlistApi.getMyPlaylistByPage(skip, limit)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取播放列表失败")
        resp.data ?: emptyList()
    }

    suspend fun getPlaylistDetail(playlistId: String): Result<List<PlaylistItem>> = runCatching {
        val resp = playlistApi.getPlayItemListDetail(playlistId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "获取播放列表详情失败")
        resp.data ?: emptyList()
    }

    suspend fun createPlaylist(title: String, description: String? = null): Result<Playlist> = runCatching {
        val resp = playlistApi.createPlaylist(CreatePlaylistRequest(title, description))
        if (!resp.isSuccess) throw Exception(resp.message ?: "创建播放列表失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun updatePlaylist(request: UpdatePlaylistRequest): Result<Playlist> = runCatching {
        val resp = playlistApi.updatePlaylist(request)
        if (!resp.isSuccess) throw Exception(resp.message ?: "更新播放列表失败")
        resp.data ?: throw Exception("数据为空")
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        val resp = playlistApi.deletePlaylist(playlistId)
        if (!resp.isSuccess) throw Exception(resp.message ?: "删除播放列表失败")
    }

    suspend fun addVideos(playlistId: String, videoIds: List<String>): Result<Unit> = runCatching {
        val resp = playlistApi.addPlaylistItem(AddPlaylistItemRequest(playlistId, videoIds))
        if (!resp.isSuccess) throw Exception(resp.message ?: "添加视频失败")
    }

    suspend fun removeVideo(playlistId: String, videoId: String): Result<Unit> = runCatching {
        val resp = playlistApi.deletePlaylistItem(DeletePlaylistItemRequest(playlistId, videoIdList = listOf(videoId)))
        if (!resp.isSuccess) throw Exception(resp.message ?: "移除视频失败")
    }

    suspend fun moveVideo(playlistId: String, videoId: String, toIndex: Int): Result<Unit> = runCatching {
        val resp = playlistApi.movePlaylistItem(MovePlaylistItemRequest(playlistId, videoId, toIndex = toIndex))
        if (!resp.isSuccess) throw Exception(resp.message ?: "移动视频失败")
    }
}
