package com.github.makewheels.video2022.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Playlist : Screen("playlist")
    data object Upload : Screen("upload")
    data object MyVideos : Screen("myvideos")
    data object Settings : Screen("settings")
    data object Watch : Screen("watch/{watchId}") {
        fun createRoute(watchId: String) = "watch/$watchId"
    }
    data object Edit : Screen("edit/{videoId}") {
        fun createRoute(videoId: String) = "edit/$videoId"
    }
    data object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }
    data object Search : Screen("search")
    data object YouTube : Screen("youtube")
    data object Notification : Screen("notification")
    data object Channel : Screen("channel/{userId}") {
        fun createRoute(userId: String) = "channel/$userId"
    }
    data object WatchHistory : Screen("watch-history")
}
