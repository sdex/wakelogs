package de.sanniki.wakesleuth.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.sanniki.wakesleuth.R
import de.sanniki.wakesleuth.ShizukuState
import de.sanniki.wakesleuth.ui.common.PreviewSurface

@Composable
internal fun SetupStatusCard(
    notificationAccessEnabled: Boolean,
    notificationsAllowed: Boolean,
    shizukuState: ShizukuState,
    onOpenNotificationAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    val shizukuReady = shizukuState == ShizukuState.RUNNING_GRANTED

    val setupComplete = notificationAccessEnabled && notificationsAllowed && shizukuReady

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = if (setupComplete) {
                    stringResource(R.string.main_setup_complete)
                } else {
                    stringResource(R.string.main_setup_required)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (setupComplete) {
                Text(
                    text = stringResource(R.string.main_setup_all_access_active),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (!notificationAccessEnabled) {
                    SetupRequirementRow(
                        title = stringResource(R.string.main_setup_notification_access_title),
                        description = stringResource(R.string.main_setup_notification_access_description),
                        buttonText = stringResource(R.string.main_setup_notification_access_button),
                        onClick = onOpenNotificationAccess,
                    )
                }

                if (!notificationsAllowed) {
                    SetupRequirementRow(
                        title = stringResource(R.string.main_setup_post_notifications_title),
                        description = stringResource(R.string.main_setup_post_notifications_description),
                        buttonText = stringResource(R.string.main_setup_post_notifications_button),
                        onClick = onRequestNotifications,
                    )
                }

                when (shizukuState) {
                    ShizukuState.RUNNING_GRANTED -> {}

                    ShizukuState.RUNNING_DENIED -> {
                        SetupRequirementRow(
                            title = stringResource(R.string.main_setup_shizuku_permission_title),
                            description = stringResource(R.string.main_setup_shizuku_permission_description),
                            buttonText = stringResource(R.string.main_setup_shizuku_permission_button),
                            onClick = onRequestShizukuPermission,
                        )
                    }

                    ShizukuState.NOT_RUNNING -> {
                        SetupRequirementRow(
                            title = stringResource(R.string.main_setup_shizuku_not_running_title),
                            description = stringResource(R.string.main_setup_shizuku_not_running_description),
                            buttonText = stringResource(R.string.main_setup_shizuku_not_running_button),
                            onClick = onOpenShizuku,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRequirementRow(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))

    Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)

    Text(
        text = description,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )

    Spacer(modifier = Modifier.height(7.dp))

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(buttonText)
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupStatusCardIncompletePreview() {
    PreviewSurface {
        SetupStatusCard(
            notificationAccessEnabled = false,
            notificationsAllowed = false,
            shizukuState = ShizukuState.NOT_RUNNING,
            onOpenNotificationAccess = {},
            onRequestNotifications = {},
            onRequestShizukuPermission = {},
            onOpenShizuku = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupStatusCardCompletePreview() {
    PreviewSurface {
        SetupStatusCard(
            notificationAccessEnabled = true,
            notificationsAllowed = true,
            shizukuState = ShizukuState.RUNNING_GRANTED,
            onOpenNotificationAccess = {},
            onRequestNotifications = {},
            onRequestShizukuPermission = {},
            onOpenShizuku = {},
        )
    }
}
