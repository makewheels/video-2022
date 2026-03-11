package com.github.makewheels.video2022.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.github.makewheels.video2022.ui.components.BottomNavBar
import com.github.makewheels.video2022.ui.components.bottomNavItems
import com.github.makewheels.video2022.ui.home.HomeScreen
import com.github.makewheels.video2022.ui.login.LoginScreen
import com.github.makewheels.video2022.ui.watch.WatchScreen

@Composable
fun AppNavGraph(navController: NavHostController, isLoggedIn: Boolean) {
    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route

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
            composable(Screen.Login.route) {
                LoginScreen(onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) {
                HomeScreen(onVideoClick = { watchId ->
                    navController.navigate(Screen.Watch.createRoute(watchId))
                })
            }
            composable(Screen.Playlist.route) {
                PlaceholderScreen("播放列表")
            }
            composable(Screen.Upload.route) {
                PlaceholderScreen("上传")
            }
            composable(Screen.MyVideos.route) {
                PlaceholderScreen("我的视频")
            }
            composable(Screen.Settings.route) {
                PlaceholderScreen("设置")
            }
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
                PlaceholderScreen("编辑: $videoId")
            }
            composable(
                Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
            ) { entry ->
                val playlistId = entry.arguments?.getString("playlistId") ?: return@composable
                PlaceholderScreen("播放列表详情: $playlistId")
            }
            composable(Screen.YouTube.route) {
                PlaceholderScreen("YouTube 下载")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name, style = MaterialTheme.typography.headlineMedium)
    }
}
