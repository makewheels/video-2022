package com.github.makewheels.video2022.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.github.makewheels.video2022.ui.components.BottomNavBar
import com.github.makewheels.video2022.ui.components.bottomNavItems
import com.github.makewheels.video2022.ui.channel.ChannelScreen
import com.github.makewheels.video2022.ui.edit.EditScreen
import com.github.makewheels.video2022.ui.home.HomeScreen
import com.github.makewheels.video2022.ui.login.LoginScreen
import com.github.makewheels.video2022.ui.myvideos.MyVideosScreen
import com.github.makewheels.video2022.ui.playlist.PlaylistDetailScreen
import com.github.makewheels.video2022.ui.playlist.PlaylistScreen
import com.github.makewheels.video2022.ui.settings.SettingsScreen
import com.github.makewheels.video2022.ui.upload.UploadScreen
import com.github.makewheels.video2022.ui.watch.WatchScreen
import com.github.makewheels.video2022.ui.search.SearchScreen
import com.github.makewheels.video2022.ui.watchhistory.WatchHistoryScreen
import com.github.makewheels.video2022.ui.youtube.YouTubeScreen
import com.github.makewheels.video2022.ui.notification.NotificationScreen

private fun NavGraphBuilder.authRoutes(navController: NavHostController) {
    composable(Screen.Login.route) {
        LoginScreen(onLoginSuccess = {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        })
    }
}

private fun NavGraphBuilder.homeRoutes(navController: NavHostController) {
    composable(Screen.Home.route) {
        HomeScreen(
            onVideoClick = { watchId ->
                navController.navigate(Screen.Watch.createRoute(watchId))
            },
            onSearchClick = {
                navController.navigate(Screen.Search.route)
            }
        )
    }
    composable(Screen.Search.route) {
        SearchScreen(
            onVideoClick = { watchId ->
                navController.navigate(Screen.Watch.createRoute(watchId))
            },
            onBack = { navController.popBackStack() }
        )
    }
    composable(Screen.Playlist.route) {
        PlaylistScreen(onPlaylistClick = { id ->
            navController.navigate(Screen.PlaylistDetail.createRoute(id))
        })
    }
    composable(Screen.Upload.route) {
        UploadScreen()
    }
    composable(Screen.MyVideos.route) {
        MyVideosScreen(
            onVideoClick = { watchId ->
                navController.navigate(Screen.Watch.createRoute(watchId))
            },
            onEditClick = { videoId ->
                navController.navigate(Screen.Edit.createRoute(videoId))
            }
        )
    }
    composable(Screen.Settings.route) {
        SettingsScreen(
            onNavigateToYouTube = { navController.navigate(Screen.YouTube.route) },
            onLogout = {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }
    composable(Screen.Notification.route) {
        NotificationScreen()
    }
    composable(Screen.WatchHistory.route) {
        WatchHistoryScreen(
            onVideoClick = { watchId ->
                navController.navigate(Screen.Watch.createRoute(watchId))
            },
            onBack = { navController.popBackStack() }
        )
    }
}

private fun NavGraphBuilder.videoRoutes(navController: NavHostController) {
    composable(
        Screen.Watch.route,
        arguments = listOf(navArgument("watchId") { type = NavType.StringType })
    ) { entry ->
        val watchId = entry.arguments?.getString("watchId") ?: return@composable
        WatchScreen(onBack = { navController.popBackStack() })
    }
    composable(
        Screen.Edit.route,
        arguments = listOf(navArgument("videoId") { type = NavType.StringType })
    ) { entry ->
        val videoId = entry.arguments?.getString("videoId") ?: return@composable
        EditScreen(
            onBack = { navController.popBackStack() },
            onDeleted = { navController.popBackStack() }
        )
    }
    composable(
        Screen.PlaylistDetail.route,
        arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
    ) { entry ->
        val playlistId = entry.arguments?.getString("playlistId") ?: return@composable
        PlaylistDetailScreen(
            onVideoClick = { watchId ->
                navController.navigate(Screen.Watch.createRoute(watchId))
            },
            onBack = { navController.popBackStack() }
        )
    }
    composable(Screen.YouTube.route) {
        YouTubeScreen()
    }
    composable(
        Screen.Channel.route,
        arguments = listOf(navArgument("userId") { type = NavType.StringType })
    ) { _ ->
        ChannelScreen(
            onVideoClick = { watchId ->
                navController.navigate(Screen.Watch.createRoute(watchId))
            },
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
fun AppNavGraph(navController: NavHostController, isLoggedIn: Boolean) {
    val startDestination = Screen.Home.route

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val showBottomBar = currentRoute in bottomNavItems.map { it.screen.route }
            if (showBottomBar) {
                BottomNavBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            authRoutes(navController)
            homeRoutes(navController)
            videoRoutes(navController)
        }
    }
}
