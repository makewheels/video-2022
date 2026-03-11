package com.github.makewheels.video2022.data.model

data class Playlist(
    val id: String,
    val title: String?,
    val description: String?,
    val ownerId: String?,
    val visibility: String?,
    val deleted: Boolean?,
    val createTime: String?,
    val updateTime: String?
)

data class PlaylistItem(
    val videoId: String?,
    val watchId: String?,
    val title: String?,
    val coverUrl: String?,
    val watchCount: Int?,
    val videoCreateTime: String?
)

data class CreatePlaylistRequest(
    val title: String,
    val description: String? = null
)

data class UpdatePlaylistRequest(
    val playlistId: String,
    val title: String? = null,
    val description: String? = null,
    val visibility: String? = null
)

data class AddPlaylistItemRequest(
    val playlistId: String,
    val videoIdList: List<String>,
    val addMode: String = "APPEND"
)

data class DeletePlaylistItemRequest(
    val playlistId: String,
    val deleteMode: String = "BY_VIDEO_ID",
    val videoIdList: List<String>
)

data class MovePlaylistItemRequest(
    val playlistId: String,
    val videoId: String,
    val moveMode: String = "TO_INDEX",
    val toIndex: Int
)
