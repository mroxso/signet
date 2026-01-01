package tech.geektoshi.signet.ui.screens.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tech.geektoshi.signet.data.api.ServerEvent
import tech.geektoshi.signet.data.api.SignetApiClient
import tech.geektoshi.signet.data.model.ConnectedApp
import tech.geektoshi.signet.data.repository.EventBusRepository
import tech.geektoshi.signet.data.repository.SettingsRepository
import tech.geektoshi.signet.ui.components.AppDetailSheet
import tech.geektoshi.signet.ui.components.EmptyState
import tech.geektoshi.signet.ui.components.SkeletonAppCard
import tech.geektoshi.signet.ui.components.pressScale
import tech.geektoshi.signet.ui.theme.BgSecondary
import tech.geektoshi.signet.ui.theme.Danger
import tech.geektoshi.signet.ui.theme.SignetPurple
import tech.geektoshi.signet.ui.theme.Success
import tech.geektoshi.signet.ui.theme.TextMuted
import tech.geektoshi.signet.ui.theme.TextPrimary
import tech.geektoshi.signet.ui.theme.Warning
import tech.geektoshi.signet.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val daemonUrl by settingsRepository.daemonUrl.collectAsState(initial = "")

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<ConnectedApp>>(emptyList()) }
    var selectedApp by remember { mutableStateOf<ConnectedApp?>(null) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    val eventBus = remember { EventBusRepository.getInstance() }

    // Connect to SSE when daemon URL is available
    LaunchedEffect(daemonUrl) {
        if (daemonUrl.isNotEmpty()) {
            eventBus.connect(daemonUrl)
        }
    }

    // Subscribe to SSE events for real-time updates
    LaunchedEffect(Unit) {
        eventBus.events.collect { event ->
            when (event) {
                is ServerEvent.AppConnected -> {
                    // New app connected, refresh list
                    refreshCounter++
                }
                is ServerEvent.AppRevoked -> {
                    // App revoked, remove from list
                    apps = apps.filter { it.id != event.appId }
                }
                is ServerEvent.AppUpdated -> {
                    // App updated (trust level or description changed), refresh list
                    refreshCounter++
                }
                else -> {}
            }
        }
    }

    // Show bottom sheet when an app is selected
    selectedApp?.let { app ->
        AppDetailSheet(
            app = app,
            daemonUrl = daemonUrl,
            onDismiss = { selectedApp = null; Unit },
            onActionComplete = { refreshCounter++ }
        )
    }

    LaunchedEffect(daemonUrl, refreshCounter) {
        if (daemonUrl.isNotEmpty()) {
            if (!isRefreshing) isLoading = true
            error = null
            try {
                val client = SignetApiClient(daemonUrl)
                apps = client.getApps().apps
                client.close()
            } catch (e: Exception) {
                error = e.message ?: "Failed to connect"
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Apps",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            SkeletonAppCard()
            SkeletonAppCard()
            SkeletonAppCard()
        }
        return
    }

    if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Connection Error",
                    style = MaterialTheme.typography.titleLarge,
                    color = Danger
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshCounter++
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Connected Apps",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (apps.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Apps,
                    message = "No connected apps",
                    subtitle = "Apps that connect to your keys will appear here"
                )
            }
        } else {
            items(apps) { app ->
                AppCard(
                    app = app,
                    onClick = { selectedApp = app; Unit }
                )
            }
        }
        }
    }
}

@Composable
private fun AppCard(
    app: ConnectedApp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: App name + Trust level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = app.description ?: (app.userPubkey.take(12) + "..."),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                TrustLevelBadge(trustLevel = app.trustLevel)
            }

            // Key name • Request count • Last used
            val lastUsed = app.lastUsedAt?.let { formatRelativeTime(it) }
            Text(
                text = listOfNotNull(
                    app.keyName,
                    "${app.requestCount} requests",
                    lastUsed
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun TrustLevelBadge(trustLevel: String) {
    val (color, label) = when (trustLevel.lowercase()) {
        "full" -> Success to "Full"
        "reasonable" -> SignetPurple to "Reasonable"
        "paranoid" -> Warning to "Paranoid"
        else -> TextMuted to trustLevel
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

