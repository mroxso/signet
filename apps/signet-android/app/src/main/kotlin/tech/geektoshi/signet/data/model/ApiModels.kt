package tech.geektoshi.signet.data.model

import kotlinx.serialization.Serializable

/**
 * Dashboard statistics
 */
@Serializable
data class DashboardStats(
    val totalKeys: Int,
    val activeKeys: Int,
    val connectedApps: Int,
    val pendingRequests: Int,
    val recentActivity24h: Int
)

/**
 * Activity entry for dashboard timeline
 */
@Serializable
data class ActivityEntry(
    val id: Int,
    val timestamp: String,
    val type: String,
    val method: String? = null,
    val eventKind: Int? = null,
    val keyName: String? = null,
    val userPubkey: String? = null,
    val appName: String? = null,
    val autoApproved: Boolean
)

/**
 * Dashboard API response
 */
@Serializable
data class DashboardResponse(
    val stats: DashboardStats,
    val activity: List<ActivityEntry>
)

/**
 * Event preview for signing requests
 */
@Serializable
data class EventPreview(
    val kind: Int,
    val content: String,
    val tags: List<List<String>>
)

/**
 * Pending request
 */
@Serializable
data class PendingRequest(
    val id: String,
    val keyName: String?,
    val method: String,
    val remotePubkey: String,
    val params: String?,
    val eventPreview: EventPreview? = null,
    val createdAt: String,
    val expiresAt: String,
    val ttlSeconds: Int,
    val requiresPassword: Boolean = false,
    val processedAt: String? = null,
    val autoApproved: Boolean,
    val appName: String? = null,
    val allowed: Boolean? = null
)

/**
 * Requests list response
 */
@Serializable
data class RequestsResponse(
    val requests: List<PendingRequest>
)

/**
 * Key status
 */
enum class KeyStatus {
    online, locked, offline
}

/**
 * Key information
 */
@Serializable
data class KeyInfo(
    val name: String,
    val npub: String? = null,
    val bunkerUri: String? = null,
    val status: String,
    val isEncrypted: Boolean,
    val userCount: Int,
    val tokenCount: Int,
    val requestCount: Int,
    val lastUsedAt: String? = null
)

/**
 * Keys list response
 */
@Serializable
data class KeysResponse(
    val keys: List<KeyInfo>
)

/**
 * Trust level for apps
 */
enum class TrustLevel {
    paranoid, reasonable, full
}

/**
 * Method usage breakdown
 */
@Serializable
data class MethodBreakdown(
    val sign_event: Int = 0,
    val nip04_encrypt: Int = 0,
    val nip04_decrypt: Int = 0,
    val nip44_encrypt: Int = 0,
    val nip44_decrypt: Int = 0,
    val get_public_key: Int = 0,
    val other: Int = 0
)

/**
 * Connected app
 */
@Serializable
data class ConnectedApp(
    val id: Int,
    val keyName: String,
    val userPubkey: String,
    val description: String? = null,
    val trustLevel: String,
    val permissions: List<String>,
    val connectedAt: String,
    val lastUsedAt: String? = null,
    val requestCount: Int,
    val methodBreakdown: MethodBreakdown
)

/**
 * Apps list response
 */
@Serializable
data class AppsResponse(
    val apps: List<ConnectedApp>
)

/**
 * Relay status
 */
@Serializable
data class RelayStatus(
    val url: String,
    val connected: Boolean,
    val lastConnected: String? = null,
    val lastDisconnected: String? = null
)

/**
 * Relays response
 */
@Serializable
data class RelaysResponse(
    val connected: Int,
    val total: Int,
    val relays: List<RelayStatus>
)

/**
 * Generic operation response
 */
@Serializable
data class OperationResponse(
    val ok: Boolean = false,
    val error: String? = null
)

/**
 * Request body for approving a request
 */
@Serializable
data class ApproveRequestBody(
    val trustLevel: String? = null,
    val alwaysAllow: Boolean? = null,
    val appName: String? = null,
    val passphrase: String? = null
)
