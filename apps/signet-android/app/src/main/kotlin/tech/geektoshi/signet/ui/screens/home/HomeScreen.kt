package tech.geektoshi.signet.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import tech.geektoshi.signet.data.model.ActivityEntry
import tech.geektoshi.signet.data.model.DashboardResponse
import tech.geektoshi.signet.data.model.DashboardStats
import tech.geektoshi.signet.data.model.PendingRequest
import tech.geektoshi.signet.data.model.RelaysResponse
import tech.geektoshi.signet.data.repository.EventBusRepository
import tech.geektoshi.signet.data.repository.SettingsRepository
import tech.geektoshi.signet.ui.components.BadgeStatus
import tech.geektoshi.signet.ui.components.EmptyState
import tech.geektoshi.signet.ui.components.RelayDetailSheet
import tech.geektoshi.signet.ui.components.RequestDetailSheet
import tech.geektoshi.signet.ui.components.SkeletonActivityCard
import tech.geektoshi.signet.ui.components.SkeletonRequestCard
import tech.geektoshi.signet.ui.components.SkeletonStatCard
import tech.geektoshi.signet.ui.components.StatusBadge
import tech.geektoshi.signet.ui.components.pressScale
import tech.geektoshi.signet.ui.theme.BgSecondary
import tech.geektoshi.signet.ui.theme.Danger
import tech.geektoshi.signet.ui.theme.SignetPurple
import tech.geektoshi.signet.ui.theme.TextMuted
import tech.geektoshi.signet.ui.theme.TextPrimary
import tech.geektoshi.signet.ui.theme.TextSecondary
import tech.geektoshi.signet.ui.theme.Warning
import tech.geektoshi.signet.util.formatRelativeTime
import tech.geektoshi.signet.util.getMethodIcon
import tech.geektoshi.signet.util.getMethodLabel
import tech.geektoshi.signet.util.getMethodLabelPastTense

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToKeys: () -> Unit = {},
    onNavigateToApps: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val daemonUrl by settingsRepository.daemonUrl.collectAsState(initial = "")

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dashboard by remember { mutableStateOf<DashboardResponse?>(null) }
    var pendingRequests by remember { mutableStateOf<List<PendingRequest>>(emptyList()) }
    var relays by remember { mutableStateOf<RelaysResponse?>(null) }
    var selectedRequest by remember { mutableStateOf<PendingRequest?>(null) }
    var showRelaySheet by remember { mutableStateOf(false) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    val defaultTrustLevel by settingsRepository.defaultTrustLevel.collectAsState(initial = "reasonable")
    val eventBus = remember { EventBusRepository.getInstance() }

    // Connect to SSE when daemon URL is available
    LaunchedEffect(daemonUrl) {
        if (daemonUrl.isNotEmpty()) {
            eventBus.connect(daemonUrl)
        }
    }

    // Subscribe to SSE events for real-time updates
    // The backend emits stats:updated for ALL stat-changing events,
    // so we just replace stats entirely when we receive it
    LaunchedEffect(Unit) {
        eventBus.events.collect { event ->
            when (event) {
                is ServerEvent.RequestCreated -> {
                    // Add new request to pending list
                    // Stats will be updated via StatsUpdated event
                    pendingRequests = listOf(event.request) + pendingRequests
                }
                is ServerEvent.RequestApproved -> {
                    // Remove from pending list
                    pendingRequests = pendingRequests.filter { it.id != event.requestId }
                    // Refresh to get updated activity
                    refreshCounter++
                }
                is ServerEvent.RequestDenied -> {
                    pendingRequests = pendingRequests.filter { it.id != event.requestId }
                    refreshCounter++
                }
                is ServerEvent.RequestExpired -> {
                    pendingRequests = pendingRequests.filter { it.id != event.requestId }
                }
                is ServerEvent.StatsUpdated -> {
                    // Backend sends fresh stats for all stat changes - just replace entirely
                    dashboard = dashboard?.copy(stats = event.stats)
                }
                is ServerEvent.AppConnected, is ServerEvent.AppRevoked -> {
                    // Refresh to get updated app count
                    refreshCounter++
                }
                is ServerEvent.RequestAutoApproved -> {
                    // Auto-approved requests may update activity, refresh
                    refreshCounter++
                }
                else -> {}
            }
        }
    }

    // Show bottom sheet when a pending request is selected
    selectedRequest?.let { request ->
        RequestDetailSheet(
            request = request,
            daemonUrl = daemonUrl,
            defaultTrustLevel = defaultTrustLevel,
            onDismiss = { selectedRequest = null },
            onActionComplete = { refreshCounter++ }
        )
    }

    // Show relay status sheet
    if (showRelaySheet) {
        relays?.let { relayData ->
            RelayDetailSheet(
                relays = relayData,
                onDismiss = { showRelaySheet = false }
            )
        }
    }

    LaunchedEffect(daemonUrl, refreshCounter) {
        if (daemonUrl.isNotEmpty()) {
            if (!isRefreshing) isLoading = true
            error = null
            try {
                val client = SignetApiClient(daemonUrl)
                dashboard = client.getDashboard()
                pendingRequests = client.getRequests(status = "pending").requests
                relays = client.getRelays()
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
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeletonStatCard(modifier = Modifier.weight(1f))
                SkeletonStatCard(modifier = Modifier.weight(1f))
                SkeletonStatCard(modifier = Modifier.weight(1f))
            }
            Text(
                text = "Pending Requests",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            SkeletonRequestCard()
            SkeletonRequestCard()
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            SkeletonActivityCard()
            SkeletonActivityCard()
            SkeletonActivityCard()
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
                    color = TextSecondary
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
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Active Keys",
                    value = dashboard?.stats?.let {
                        if (it.totalKeys == 0) "0" else "${it.activeKeys}/${it.totalKeys}"
                    } ?: "-",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToKeys
                )
                StatCard(
                    title = "Apps",
                    value = dashboard?.stats?.connectedApps?.toString() ?: "0",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToApps
                )
                StatCard(
                    title = "Relays",
                    value = relays?.let { "${it.connected}/${it.total}" } ?: "-",
                    modifier = Modifier.weight(1f),
                    onClick = { relays?.let { showRelaySheet = true } }
                )
            }
        }

        // Onboarding - show when no keys exist
        if (dashboard?.stats?.totalKeys == 0) {
            item {
                OnboardingCard(onNavigateToKeys = onNavigateToKeys)
            }
        } else {
            // Pending Requests Section
            item {
                Text(
                    text = "Pending Requests",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (pendingRequests.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.CheckCircle,
                        message = "No pending requests",
                        compact = true
                    )
                }
            } else {
                items(pendingRequests) { request ->
                    PendingRequestCard(
                        request = request,
                        onClick = { selectedRequest = request }
                    )
                }
            }

            // Recent Activity Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val activities = dashboard?.activity ?: emptyList()
            if (activities.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        message = "No recent activity"
                    )
                }
            } else {
                items(activities.take(5)) { activity ->
                    ActivityCard(activity = activity)
                }
            }
        }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) {
            modifier.pressScale(onClick = onClick)
        } else {
            modifier
        },
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = if (highlight) Warning else SignetPurple
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PendingRequestCard(
    request: PendingRequest,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = request.appName ?: request.remotePubkey.take(12) + "...",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = getMethodLabel(request.method, request.eventPreview?.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = SignetPurple
                )
            }
            Text(
                text = request.keyName ?: "Unknown key",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ActivityCard(activity: ActivityEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: App name + Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activity.appName ?: activity.userPubkey?.take(12)?.plus("...") ?: "Unknown",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                StatusBadge(
                    status = if (activity.type == "approval") BadgeStatus.APPROVED else BadgeStatus.DENIED
                )
            }

            // Method icon + label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activity.method?.let { method ->
                    Icon(
                        imageVector = getMethodIcon(method),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = SignetPurple
                    )
                }
                Text(
                    text = activity.method?.let { getMethodLabelPastTense(it, activity.eventKind) } ?: activity.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = SignetPurple
                )
            }

            // Timestamp + Key name
            Text(
                text = "${formatRelativeTime(activity.timestamp)} • ${activity.keyName ?: "Unknown key"}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun OnboardingCard(onNavigateToKeys: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = SignetPurple
            )

            Text(
                text = "Welcome to Signet",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )

            Text(
                text = "Create your first signing key to start using Signet as a remote signer for your Nostr apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Button(
                onClick = onNavigateToKeys,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignetPurple,
                    contentColor = TextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Create Your First Key")
            }
        }
    }
}
