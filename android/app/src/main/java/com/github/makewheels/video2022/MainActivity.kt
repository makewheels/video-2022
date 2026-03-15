package com.github.makewheels.video2022

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.github.makewheels.video2022.navigation.AppNavGraph
import com.github.makewheels.video2022.ui.theme.VideoTheme
import com.github.makewheels.video2022.ui.update.UpdateDialog
import com.github.makewheels.video2022.ui.update.UpdateViewModel
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
                val updateViewModel: UpdateViewModel = hiltViewModel()
                val updateState by updateViewModel.updateUiState.collectAsState()

                LaunchedEffect(Unit) { updateViewModel.checkForUpdate() }

                if (updateState.showDialog) {
                    UpdateDialog(
                        state = updateState,
                        onUpdate = { updateViewModel.startDownload(this@MainActivity) },
                        onDismiss = if (!updateState.isForceUpdate) {
                            { updateViewModel.dismissDialog() }
                        } else null
                    )
                }

                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    isLoggedIn = tokenManager.isLoggedIn()
                )
            }
        }
    }
}
