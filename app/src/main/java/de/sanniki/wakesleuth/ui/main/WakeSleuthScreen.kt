package de.sanniki.wakesleuth.ui.main

import android.Manifest
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.sanniki.wakesleuth.ui.theme.WakesleuthTheme

/**
 * The stateful entry point: connects [WakeSleuthViewModel] to the UI, and
 * performs the one-shot [WakeSleuthEvent]s that need an Activity.
 */
@Composable
internal fun WakeSleuthScreen(viewModel: WakeSleuthViewModel = viewModel(factory = WakeSleuthViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> viewModel.onIntent(WakeSleuthIntent.ExportDestinationChosen(uri)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onIntent(WakeSleuthIntent.NotificationPermissionResult(granted)) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(WakeSleuthIntent.Resumed)

        onPauseOrDispose { viewModel.onIntent(WakeSleuthIntent.Paused) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WakeSleuthEvent.ShowToast -> {
                    Toast
                        .makeText(
                            context,
                            resources.getString(event.message),
                            if (event.long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
                        ).show()
                }

                WakeSleuthEvent.RequestNotificationPermission -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                is WakeSleuthEvent.StartActivity -> {
                    context.startActivity(event.intent)
                }

                is WakeSleuthEvent.CreateExportDocument -> {
                    exportLauncher.launch(event.fileName)
                }

                WakeSleuthEvent.Finish -> {
                    (context as? ComponentActivity)?.finish()
                }
            }
        }
    }

    BackHandler { viewModel.onIntent(WakeSleuthIntent.BackPressed) }

    WakesleuthTheme(accentColor = state.uiSettings.accentColor) {
        WakeSleuthContent(state = state, onIntent = viewModel::onIntent)
    }
}
