package com.f3.workouttimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.f3.workouttimer.ui.EditScreen
import com.f3.workouttimer.ui.HomeScreen
import com.f3.workouttimer.ui.RunScreen
import com.f3.workouttimer.ui.SplashScreen
import com.f3.workouttimer.ui.theme.F3WorkoutTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launchRunId = intent?.getStringExtra(EXTRA_LAUNCH_RUN_ID)
        setContent {
            F3WorkoutTimerTheme {
                // Jumping straight into a running workout from the notification
                // skips the splash.
                var showSplash by rememberSaveable { mutableStateOf(launchRunId == null) }
                Box {
                    AppNav(launchRunId = launchRunId)
                    AnimatedVisibility(visible = showSplash, exit = fadeOut(tween(400))) {
                        SplashScreen(onDone = { showSplash = false })
                    }
                }
            }
        }
    }

    companion object {
        /** Set by the run notification's tap intent to jump straight to the run screen. */
        const val EXTRA_LAUNCH_RUN_ID = "launch_run_id"
    }
}

@Composable
private fun AppNav(launchRunId: String?) {
    val nav = rememberNavController()
    LaunchedEffect(launchRunId) {
        if (launchRunId != null) nav.navigate("run/$launchRunId")
    }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onCreate = { nav.navigate("edit") },
                onEdit = { id -> nav.navigate("edit?id=$id") },
                onRun = { id -> nav.navigate("run/$id") },
            )
        }
        composable(
            route = "edit?id={id}",
            arguments = listOf(navArgument("id") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { entry ->
            EditScreen(
                timerId = entry.arguments?.getString("id"),
                onDone = { nav.popBackStack() },
            )
        }
        composable(
            route = "run/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            RunScreen(
                timerId = entry.arguments?.getString("id").orEmpty(),
                onExit = { nav.popBackStack() },
            )
        }
    }
}
