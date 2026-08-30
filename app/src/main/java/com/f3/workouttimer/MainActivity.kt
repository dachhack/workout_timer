package com.f3.workouttimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.f3.workouttimer.ui.EditScreen
import com.f3.workouttimer.ui.HomeScreen
import com.f3.workouttimer.ui.RunScreen
import com.f3.workouttimer.ui.theme.F3WorkoutTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            F3WorkoutTimerTheme {
                AppNav()
            }
        }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
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
