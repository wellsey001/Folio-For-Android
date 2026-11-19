package com.wellsey.folio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wellsey.folio.ui.ReaderViewModel
import com.wellsey.folio.ui.screens.LibraryScreen
import com.wellsey.folio.ui.screens.MergeScreen
import com.wellsey.folio.ui.screens.ReaderScreen
import com.wellsey.folio.ui.theme.FolioTheme

class MainActivity : ComponentActivity() {
    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            FolioTheme {
                Surface(color = MaterialTheme.colorScheme.background) { FolioRoot(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** "Open with Folio" from another app. */
    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri: Uri -> vm.open(uri) }
        }
    }
}

@Composable
private fun FolioRoot(vm: ReaderViewModel) {
    val nav = rememberNavController()
    val doc by vm.doc.collectAsStateWithLifecycle()
    val opening by vm.opening.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Whenever a document opens (from the library, a merge, or another app), show it.
    LaunchedEffect(doc?.uri) {
        if (doc != null && route != "reader") nav.navigate("reader") { launchSingleTop = true }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = "library",
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(150)) },
        ) {
            composable("library") {
                LibraryScreen(vm, onOpen = { vm.open(it) }, onMerge = { nav.navigate("merge") })
            }
            composable("merge") {
                MergeScreen(vm, onBack = { nav.popBackStack() })
            }
            composable("reader") {
                val d = doc
                if (d == null) {
                    LaunchedEffect(Unit) { nav.popBackStack("library", inclusive = false) }
                } else {
                    ReaderScreen(vm, d, onClose = {
                        vm.close()
                        nav.popBackStack("library", inclusive = false)
                    })
                }
            }
        }
        if (opening) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
    }
}
