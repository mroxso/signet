package tech.geektoshi.signet.data.api

import tech.geektoshi.signet.data.model.ApproveRequestBody
import tech.geektoshi.signet.data.model.AppsResponse
import tech.geektoshi.signet.data.model.DashboardResponse
import tech.geektoshi.signet.data.model.KeysResponse
import tech.geektoshi.signet.data.model.OperationResponse
import tech.geektoshi.signet.data.model.RelaysResponse
import tech.geektoshi.signet.data.model.RequestsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class SignetApiClient(
    private val baseUrl: String
) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            })
        }
        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
            // Bearer auth skips CSRF checks (token value doesn't matter when requireAuth=false)
            bearerAuth("android-client")
        }
    }

    /**
     * Get dashboard stats and recent activity
     */
    suspend fun getDashboard(): DashboardResponse {
        return client.get("/dashboard").body()
    }

    /**
     * Get list of requests
     */
    suspend fun getRequests(
        status: String = "pending",
        limit: Int = 50,
        offset: Int = 0
    ): RequestsResponse {
        return client.get("/requests") {
            parameter("status", status)
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
    }

    /**
     * Approve a request
     */
    suspend fun approveRequest(
        id: String,
        trustLevel: String? = null,
        alwaysAllow: Boolean = false,
        appName: String? = null,
        passphrase: String? = null
    ): OperationResponse {
        return client.post("/requests/$id") {
            setBody(ApproveRequestBody(
                trustLevel = trustLevel,
                alwaysAllow = if (alwaysAllow) true else null,
                appName = appName?.ifBlank { null },
                passphrase = passphrase?.ifBlank { null }
            ))
        }.body()
    }

    /**
     * Deny a request
     */
    suspend fun denyRequest(id: String): OperationResponse {
        return client.delete("/requests/$id").body()
    }

    /**
     * Get list of keys
     */
    suspend fun getKeys(): KeysResponse {
        return client.get("/keys").body()
    }

    /**
     * Create a new key
     */
    suspend fun createKey(
        keyName: String,
        passphrase: String? = null,
        nsec: String? = null
    ): OperationResponse {
        return client.post("/keys") {
            setBody(mapOf(
                "keyName" to keyName,
                "passphrase" to passphrase,
                "nsec" to nsec
            ).filterValues { it != null })
        }.body()
    }

    /**
     * Delete a key
     */
    suspend fun deleteKey(
        keyName: String,
        passphrase: String? = null
    ): OperationResponse {
        return client.delete("/keys/$keyName") {
            setBody(mapOf(
                "passphrase" to passphrase
            ).filterValues { it != null })
        }.body()
    }

    /**
     * Unlock an encrypted key
     */
    suspend fun unlockKey(
        keyName: String,
        passphrase: String
    ): OperationResponse {
        return client.post("/keys/$keyName/unlock") {
            setBody(mapOf("passphrase" to passphrase))
        }.body()
    }

    /**
     * Get list of connected apps
     */
    suspend fun getApps(): AppsResponse {
        return client.get("/apps").body()
    }

    /**
     * Revoke an app
     */
    suspend fun revokeApp(id: Int): OperationResponse {
        return client.post("/apps/$id/revoke") {
            setBody(emptyMap<String, String>())
        }.body()
    }

    /**
     * Update an app's name or trust level
     */
    suspend fun updateApp(
        id: Int,
        description: String? = null,
        trustLevel: String? = null
    ): OperationResponse {
        return client.patch("/apps/$id") {
            setBody(mapOf(
                "description" to description,
                "trustLevel" to trustLevel
            ).filterValues { it != null })
        }.body()
    }

    /**
     * Get relay status
     */
    suspend fun getRelays(): RelaysResponse {
        return client.get("/relays").body()
    }

    /**
     * Check if the daemon is reachable
     */
    suspend fun healthCheck(): Boolean {
        return try {
            client.get("/health")
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Close the client
     */
    fun close() {
        client.close()
    }
}
