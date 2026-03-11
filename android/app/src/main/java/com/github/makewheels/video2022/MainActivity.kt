package com.github.makewheels.video2022

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.github.makewheels.video2022.navigation.AppNavGraph
import com.github.makewheels.video2022.ui.theme.VideoTheme
import com.github.makewheels.video2022.util.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    isLoggedIn = tokenManager.isLoggedIn()
                )
            }
        }
    }
}
