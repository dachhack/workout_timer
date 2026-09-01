package com.f3.workouttimer

import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import com.f3.workouttimer.alarm.CueScheduler
import com.f3.workouttimer.data.TimerShare
import com.f3.workouttimer.ui.EditScreen
import com.f3.workouttimer.ui.HomeScreen
import com.f3.workouttimer.ui.RunScreen
import com.f3.workouttimer.ui.ScheduleScreen
import com.f3.workouttimer.ui.SplashScreen
import com.f3.workouttimer.ui.theme.F3WorkoutTimerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Held as state rather than read once, so a notification tap or a shared
    // link arriving while the app is already open is handled too.
    private val pendingRunId = mutableStateOf<String?>(null)
    private val pendingImport = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        // Cheap insurance: alarms can be dropped by a force-stop or a restore,
        // so re-book them whenever the app is opened.
        lifecycleScope.launch { CueScheduler.rescheduleAll(applicationContext) }
        setContent {
            F3WorkoutTimerTheme {
                val launchRunId by pendingRunId
                val importText by pendingImport
                // Jumping straight into a running workout from the notification
                // skips the splash.
                var showSplash by rememberSaveable { mutableStateOf(launchRunId == null) }
                Box {
                    AppNav(
                        launchRunId = launchRunId,
                        onRunLaunchHandled = { pendingRunId.value = null },
                        importText = importText,
                        onImportHandled = { pendingImport.value = null },
                    )
                    AnimatedVisibility(visible = showSplash, exit = fadeOut(tween(400))) {
                        SplashScreen(onDone = { showSplash = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_LAUNCH_RUN_ID)?.let { pendingRunId.value = it }
        val data = intent?.data
        if (data != null && data.scheme == TimerShare.SCHEME) {
            pendingImport.value = data.toString()
        }
    }

    companion object {
        /** Set by the run notification's tap intent to jump straight to the run screen. */
        const val EXTRA_LAUNCH_RUN_ID = "launch_run_id"
    }
}

@Composable
private fun AppNav(
    launchRunId: String?,
    onRunLaunchHandled: () -> Unit,
    importText: String?,
    onImportHandled: () -> Unit,
) {
    val nav = rememberNavController()
    LaunchedEffect(launchRunId) {
        if (launchRunId != null) {
            nav.navigate("run/$launchRunId")
            onRunLaunchHandled()
        }
    }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onCreate = { nav.navigate("edit") },
                onEdit = { id -> nav.navigate("edit?id=$id") },
                onRun = { id -> nav.navigate("run/$id") },
                onSchedule = { nav.navigate("schedule") },
                importText = importText,
                onImportHandled = onImportHandled,
            )
        }
        composable("schedule") {
            ScheduleScreen(onBack = { nav.popBackStack() })
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
