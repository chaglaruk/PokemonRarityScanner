package com.pokerarity.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.pokerarity.scanner.data.model.samplePokemon
import com.pokerarity.scanner.ui.screens.CollectionScreen
import com.pokerarity.scanner.ui.screens.ScanResultScreen
import com.pokerarity.scanner.ui.theme.PokeRarityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()   // full bleed AMOLED black

        setContent {
            PokeRarityTheme {
                PokeRarityApp()
            }
        }
    }
}

@Composable
fun PokeRarityApp() {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = "collection",
    ) {

        // ── Collection list ──────────────────────────────────
        composable(
            route = "collection",
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec  = tween(350),
                ) + fadeIn(tween(350))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = tween(350),
                ) + fadeOut(tween(350))
            },
        ) {
            CollectionScreen(
                pokemonList    = samplePokemon,
                onPokemonClick = { pokemon ->
                    navController.navigate("detail/${pokemon.id}")
                },
            )
        }

        // ── Scan result detail ───────────────────────────────
        composable(
            route = "detail/{pokemonId}",
            arguments = listOf(navArgument("pokemonId") { type = NavType.IntType }),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },      // slides in from right
                    animationSpec  = tween(400),
                ) + fadeIn(tween(400))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(350),
                ) + fadeOut(tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec  = tween(350),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(350),
                )
            },
        ) { backStackEntry ->
            val pokemonId = backStackEntry.arguments?.getInt("pokemonId") ?: return@composable
            val pokemon   = samplePokemon.find { it.id == pokemonId } ?: return@composable

            ScanResultScreen(
                pokemon = pokemon,
                onBack  = { navController.popBackStack() },
            )
        }
    }
}
