package tech.geektoshi.signet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.geektoshi.signet.data.model.RelaysResponse
import tech.geektoshi.signet.ui.theme.BgTertiary
import tech.geektoshi.signet.ui.theme.Danger
import tech.geektoshi.signet.ui.theme.Success
import tech.geektoshi.signet.ui.theme.TextMuted
import tech.geektoshi.signet.ui.theme.TextPrimary
import tech.geektoshi.signet.ui.theme.TextSecondary
import tech.geektoshi.signet.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayDetailSheet(
    relays: RelaysResponse,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgTertiary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Relay Status",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )

            // Summary
            Text(
                text = "${relays.connected} of ${relays.total} relays connected",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))

            // Relay list
            relays.relays.forEach { relay ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = relay.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        val statusText = if (relay.connected) {
                            relay.lastConnected?.let { "Connected ${formatRelativeTime(it)}" } ?: "Connected"
                        } else {
                            relay.lastDisconnected?.let { "Disconnected ${formatRelativeTime(it)}" } ?: "Disconnected"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Icon(
                        imageVector = if (relay.connected) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = if (relay.connected) "Connected" else "Disconnected",
                        modifier = Modifier.size(20.dp),
                        tint = if (relay.connected) Success else Danger
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
