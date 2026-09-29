package de.sanniki.wakesleuth.ui.sessions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.ArchivedSession
import de.sanniki.wakesleuth.DetailLevel
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ui.common.PreviewSamples
import de.sanniki.wakesleuth.ui.common.PreviewSurface
import de.sanniki.wakesleuth.ui.common.formatComparisonDuration
import de.sanniki.wakesleuth.ui.common.formatNetworkBytes
import de.sanniki.wakesleuth.ui.sessions.buildSessionExportFileName
import de.sanniki.wakesleuth.ui.sessions.buildSessionExportText
import de.sanniki.wakesleuth.ui.sessions.networkBytesPerMinute
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SessionHistoryCard(
    sessions: List<ArchivedSession>,
    detailLevel: DetailLevel,
    onSaveNote: (sessionId: Long, note: String?) -> Unit,
    onDelete: (sessionId: Long) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val showDeleteAllDialog = remember { androidx.compose.runtime.mutableStateOf(false) }

    if (sessions.isEmpty()) {
        return
    }

    val expanded = remember { androidx.compose.runtime.mutableStateOf(false) }

    val visibleSessions = if (expanded.value) {
        sessions
    } else {
        sessions.take(3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = stringResource(R.string.main_session_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = pluralStringResource(R.plurals.main_sessions_saved, sessions.size, sessions.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(14.dp))

            visibleSessions.forEachIndexed {
                index,
                session,
                ->

                SessionHistoryEntry(
                    session = session,
                    detailLevel = detailLevel,
                    onSaveNote = { note ->
                        onSaveNote(session.id, note)
                    },
                    onDelete = {
                        onDelete(session.id)
                    },
                )

                if (
                    index <
                    visibleSessions.lastIndex
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (sessions.size > 3) {
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        expanded.value = !expanded.value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        androidx.compose.ui.graphics
                            .Color(0xFF687181),
                    ),
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.ui.graphics.Color.White),
                ) {
                    Text(
                        text = if (expanded.value) {
                            stringResource(R.string.main_sessions_show_less)
                        } else {
                            stringResource(R.string.main_sessions_show_all)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            androidx.compose.material3
                .OutlinedButton(
                    onClick = {
                        showDeleteAllDialog.value = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.main_sessions_delete_all))
                }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.main_sessions_storage_limit),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    if (showDeleteAllDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDeleteAllDialog.value = false
            },
            title = {
                Text(text = stringResource(R.string.main_sessions_delete_all_title))
            },
            text = {
                Text(text = stringResource(R.string.main_sessions_delete_all_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog.value = false

                        expanded.value = false

                        onDeleteAll()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.main_delete_all))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog.value = false
                    },
                ) {
                    Text(text = stringResource(R.string.main_cancel))
                }
            },
        )
    }
}

@Composable
private fun SessionHistoryEntry(
    session: ArchivedSession,
    detailLevel: DetailLevel,
    onSaveNote: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    val resources = LocalResources.current

    val pendingSessionExport = remember(session.id) { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val sessionExportLauncher = androidx.activity.compose
        .rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts
                .CreateDocument("text/plain"),
        ) { uri ->
            val exportText = pendingSessionExport.value

            if (
                uri != null && exportText != null
            ) {
                val succeeded = runCatching {
                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { writer -> writer.write(exportText) }
                        ?: error(resources.getString(R.string.main_error_output_file_open_failed))
                }.isSuccess

                android.widget.Toast
                    .makeText(
                        context,
                        if (succeeded) {
                            resources.getString(R.string.main_toast_session_export_saved)
                        } else {
                            resources.getString(R.string.main_toast_session_export_failed)
                        },
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
            }

            pendingSessionExport.value = null
        }

    val expanded = remember(session.id) { androidx.compose.runtime.mutableStateOf(false) }

    val showDeleteDialog = remember(session.id) { androidx.compose.runtime.mutableStateOf(false) }

    val showNoteDialog = remember(session.id) { androidx.compose.runtime.mutableStateOf(false) }

    val normalizedNote =
        session.note?.trim()?.takeUnless { note -> note.isBlank() || note.equals("null", ignoreCase = true) }

    val noteDraft = remember(
        session.id,
        session.note,
    ) {
        androidx.compose.runtime.mutableStateOf(normalizedNote.orEmpty())
    }

    val topApp = session.topApps.firstOrNull()

    Card(
        onClick = {
            expanded.value = !expanded.value
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = formatSessionHistoryDate(session.startMillis),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = formatSessionHistoryTimeRange(
                            startMillis = session.startMillis,
                            endMillis = session.endMillis,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    normalizedNote
                        ?.let { note ->
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = note,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = formatComparisonDuration(session.durationMillis),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = if (expanded.value) {
                            stringResource(R.string.main_details_hide)
                        } else {
                            stringResource(R.string.main_details_show)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionHistoryMetric(
                    value = session.displayWakeups.toString(),
                    label = stringResource(R.string.main_metric_display),
                    modifier = Modifier.weight(1f),
                )

                SessionHistoryMetric(
                    value = session.cpuWakeups.toString(),
                    label = stringResource(R.string.main_metric_cpu),
                    modifier = Modifier.weight(1f),
                )

                SessionHistoryMetric(
                    value = formatNetworkBytes(session.networkTotalBytes),
                    label = stringResource(R.string.main_metric_network),
                    modifier = Modifier.weight(1.2f),
                )
            }

            topApp?.let { app ->
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.main_most_active_app),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = app.name,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = formatNetworkBytes(app.totalBytes),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }

            if (expanded.value) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(10.dp))

                SessionHistoryValueRow(
                    label = stringResource(R.string.main_metric_active_apps),
                    value = session.networkActiveApps.toString(),
                )

                SessionHistoryValueRow(
                    label = stringResource(R.string.main_metric_received),
                    value = formatNetworkBytes(session.networkRxBytes),
                )

                SessionHistoryValueRow(
                    label = stringResource(R.string.main_metric_sent),
                    value = formatNetworkBytes(session.networkTxBytes),
                )

                SessionHistoryValueRow(
                    label = stringResource(R.string.main_metric_network_per_minute),
                    value = formatNetworkBytes(
                        networkBytesPerMinute(
                            bytes = session.networkTotalBytes,
                            durationMillis = session.durationMillis,
                        ),
                    ),
                )

                if (
                    detailLevel == DetailLevel.EXPERT && session.topApps.size > 1
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.main_more_active_apps),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    session.topApps
                        .drop(1)
                        .take(4)
                        .forEach { app ->
                            SessionHistoryValueRow(label = app.name, value = formatNetworkBytes(app.totalBytes))
                        }
                }

                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.material3
                    .OutlinedButton(
                        onClick = {
                            pendingSessionExport.value = buildSessionExportText(context, session)

                            sessionExportLauncher.launch(buildSessionExportFileName(context, session))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.main_session_summary))
                    }

                Spacer(modifier = Modifier.height(6.dp))

                androidx.compose.material3
                    .OutlinedButton(
                        onClick = {
                            noteDraft.value = normalizedNote.orEmpty()

                            showNoteDialog.value = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (
                                normalizedNote == null
                            ) {
                                stringResource(R.string.main_note_add)
                            } else {
                                stringResource(R.string.main_note_edit)
                            },
                        )
                    }

                Spacer(modifier = Modifier.height(4.dp))

                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.main_session_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
            }
        }
    }

    if (showNoteDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showNoteDialog.value = false
            },
            title = {
                Text(
                    text = if (
                        normalizedNote == null
                    ) {
                        stringResource(R.string.main_note_add)
                    } else {
                        stringResource(R.string.main_note_edit)
                    },
                )
            },
            text = {
                Column {
                    androidx.compose.material3
                        .OutlinedTextField(
                            value = noteDraft.value,
                            onValueChange = { value ->
                                noteDraft.value = value.take(120)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(stringResource(R.string.main_note))
                            },
                            placeholder = {
                                Text(stringResource(R.string.main_note_placeholder))
                            },
                            supportingText = {
                                Text(noteDraft.value.length.toString() + " / 120")
                            },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                        )
                }
            },
            confirmButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showNoteDialog.value = false

                            onSaveNote(noteDraft.value)
                        },
                    ) {
                        Text(stringResource(R.string.main_save))
                    }
            },
            dismissButton = {
                Row {
                    if (
                        normalizedNote != null
                    ) {
                        androidx.compose.material3
                            .TextButton(
                                onClick = {
                                    showNoteDialog.value = false

                                    noteDraft.value = ""

                                    onSaveNote(null)
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.main_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                    }

                    androidx.compose.material3
                        .TextButton(
                            onClick = {
                                showNoteDialog.value = false
                            },
                        ) {
                            Text(stringResource(R.string.main_cancel))
                        }
                }
            },
        )
    }

    if (showDeleteDialog.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDeleteDialog.value = false
            },
            title = {
                Text(text = stringResource(R.string.main_session_delete_title))
            },
            text = {
                Text(text = stringResource(R.string.main_session_delete_message))
            },
            confirmButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value = false
                            onDelete()
                        },
                    ) {
                        Text(text = stringResource(R.string.main_delete), color = MaterialTheme.colorScheme.error)
                    }
            },
            dismissButton = {
                androidx.compose.material3
                    .TextButton(
                        onClick = {
                            showDeleteDialog.value = false
                        },
                    ) {
                        Text(stringResource(R.string.main_cancel))
                    }
            },
        )
    }
}

@Composable
private fun SessionHistoryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SessionHistoryValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            modifier = Modifier.weight(1.45f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

private fun formatSessionHistoryDate(timestamp: Long): String = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))

private fun formatSessionHistoryTimeRange(
    startMillis: Long,
    endMillis: Long,
): String {
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    return formatter.format(Date(startMillis)) + " – " + formatter.format(Date(endMillis))
}

@Preview(showBackground = true)
@Composable
private fun SessionHistoryCardPreview() {
    PreviewSurface {
        SessionHistoryCard(
            sessions = PreviewSamples.archivedSessions,
            detailLevel = DetailLevel.NORMAL,
            onSaveNote = { _, _ -> },
            onDelete = {},
            onDeleteAll = {},
        )
    }
}
