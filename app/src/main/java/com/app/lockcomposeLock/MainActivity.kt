package com.app.lockcomposeLock

import ShowAppList
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.lockcomposeLock.screens.MainScreen
import com.app.lockcomposeLock.screens.WelcomeScreen
import com.app.lockcomposeLock.ui.theme.LockComposeTheme


class MainActivity : ComponentActivity() {

    companion object {
        const val OVERLAY_REQUEST_CODE = 1005
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_REQUEST_CODE)
        }
        setContent {
            LockComposeTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "welcome") {
                    composable("welcome") { WelcomeScreen(navController) }
                    composable("main") { MainScreen(navController) }
                    composable("showAppList") { ShowAppList() }
                }
            }
        }

    }
}

