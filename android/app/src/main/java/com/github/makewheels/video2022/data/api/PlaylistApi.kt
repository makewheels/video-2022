package com.github.makewheels.video2022.data.api

import com.github.makewheels.video2022.data.model.*
import retrofit2.http.*

interface PlaylistApi {
    @POST("/playlist/createPlaylist")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequest): ApiResponse<Playlist>

    @POST("/playlist/updatePlaylist")
    suspend fun updatePlaylist(@Body request: UpdatePlaylistRequest): ApiResponse<Playlist>

    @GET("/playlist/deletePlaylist")
    suspend fun deletePlaylist(@Query("playlistId") playlistId: String): ApiResponse<Any?>

    @GET("/playlist/recoverPlaylist")
    suspend fun recoverPlaylist(@Query("playlistId") playlistId: String): ApiResponse<Any?>

    @GET("/playlist/getPlaylistById")
    suspend fun getPlaylistById(
        @Query("playlistId") playlistId: String,
        @Query("showVideoList") showVideoList: Boolean = false
    ): ApiResponse<Playlist>

    @GET("/playlist/getPlayItemListDetail")
    suspend fun getPlayItemListDetail(@Query("playlistId") playlistId: String): ApiResponse<List<PlaylistItem>>

    @GET("/playlist/getMyPlaylistByPage")
    suspend fun getMyPlaylistByPage(
        @Query("skip") skip: Int,
        @Query("limit") limit: Int
    ): ApiResponse<List<Playlist>>

    @POST("/playlist/addPlaylistItem")
    suspend fun addPlaylistItem(@Body request: AddPlaylistItemRequest): ApiResponse<Playlist>

    @POST("/playlist/deletePlaylistItem")
    suspend fun deletePlaylistItem(@Body request: DeletePlaylistItemRequest): ApiResponse<Any?>

    @POST("/playlist/movePlaylistItem")
    suspend fun movePlaylistItem(@Body request: MovePlaylistItemRequest): ApiResponse<Any?>

    @GET("/playlist/getPlaylistByVideoId")
    suspend fun getPlaylistByVideoId(@Query("videoId") videoId: String): ApiResponse<List<String>>
}
