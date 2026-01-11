package tech.geektoshi.signet.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.geektoshi.signet.data.api.SignetApiClient
import tech.geektoshi.signet.data.model.KeyInfo
import tech.geektoshi.signet.util.ClearSensitiveDataOnDispose
import tech.geektoshi.signet.ui.theme.BgTertiary
import tech.geektoshi.signet.ui.theme.BorderDefault
import tech.geektoshi.signet.ui.theme.Danger
import tech.geektoshi.signet.ui.theme.SignetPurple
import tech.geektoshi.signet.ui.theme.Success
import tech.geektoshi.signet.ui.theme.TextMuted
import tech.geektoshi.signet.ui.theme.TextPrimary
import tech.geektoshi.signet.ui.theme.TextSecondary
import tech.geektoshi.signet.ui.theme.Warning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyDetailSheet(
    key: KeyInfo,
    daemonUrl: String,
    onDismiss: () -> Unit,
    onActionComplete: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var isLocking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    val passphraseState = remember { mutableStateOf("") }
    var passphrase by passphraseState
    var showBunkerURISheet by remember { mutableStateOf(false) }

    // Clear sensitive data when sheet is dismissed
    ClearSensitiveDataOnDispose(passphraseState)

    val isLocked = key.status.lowercase() == "locked"
    val isOnline = key.status.lowercase() == "online"

    // Bunker URI Sheet
    if (showBunkerURISheet && key.npub != null) {
        BunkerURISheet(
            keyName = key.name,
            daemonUrl = daemonUrl,
            onDismiss = { showBunkerURISheet = false }
        )
    }

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
            // Header: Status dot + Key name + Encryption badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status dot: green = online, orange = locked, gray = offline
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = when (key.status.lowercase()) {
                                    "online" -> Success
                                    "locked" -> Warning
                                    else -> TextMuted
                                },
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = key.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                }
                EncryptionBadge(isEncrypted = key.isEncrypted)
            }

            HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))

            // npub section
            if (key.npub != null) {
                CopyableField(
                    label = "Public Key (npub)",
                    value = key.npub,
                    context = context
                )
            }

            // Bunker URI section (only for online keys)
            if (isOnline && key.pubkey != null) {
                BunkerURIField(
                    pubkey = key.pubkey,
                    onClick = { showBunkerURISheet = true }
                )
            }

            HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))

            // Stats
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(label = "Connected Apps", value = key.userCount.toString())
                StatColumn(label = "Tokens", value = key.tokenCount.toString())
                StatColumn(label = "Requests", value = key.requestCount.toString())
            }

            // Additional info
            if (key.lastUsedAt != null) {
                HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
                InfoRow(label = "Last Used", value = formatDateTime(key.lastUsedAt))
            }

            InfoRow(
                label = "Encryption",
                value = if (key.isEncrypted) "Password protected" else "Not encrypted"
            )

            // Error message
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger
                )
            }

            HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))

            // Unlock section for locked keys
            if (isLocked) {
                if (showUnlock) {
                    Text(
                        text = "Unlock Key",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        placeholder = { Text("Enter passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SignetPurple,
                            unfocusedBorderColor = BorderDefault,
                            cursorColor = SignetPurple,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedPlaceholderColor = TextMuted,
                            unfocusedPlaceholderColor = TextMuted,
                            focusedContainerColor = BgTertiary,
                            unfocusedContainerColor = BgTertiary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showUnlock = false
                                passphrase = ""
                            },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    error = null
                                    try {
                                        val client = SignetApiClient(daemonUrl)
                                        val result = client.unlockKey(key.name, passphrase)
                                        client.close()
                                        if (result.ok) {
                                            onActionComplete()
                                            onDismiss()
                                        } else {
                                            error = result.error ?: "Failed to unlock"
                                        }
                                    } catch (e: Exception) {
                                        error = e.message ?: "Failed to unlock"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && passphrase.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SignetPurple,
                                contentColor = TextPrimary
                            )
                        ) {
                            Text("Unlock")
                        }
                    }
                } else {
                    Button(
                        onClick = { showUnlock = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignetPurple,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("Unlock Key")
                    }
                }
            }

            // Lock button for online encrypted keys
            if (isOnline && key.isEncrypted && !showDeleteConfirm) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isLocking = true
                            error = null
                            try {
                                val client = SignetApiClient(daemonUrl)
                                val result = client.lockKey(key.name)
                                client.close()
                                if (result.ok) {
                                    onActionComplete()
                                    onDismiss()
                                } else {
                                    error = result.error ?: "Failed to lock"
                                }
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to lock"
                            } finally {
                                isLocking = false
                            }
                        }
                    },
                    enabled = !isLocking && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Warning
                    )
                ) {
                    if (isLocking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Warning,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (isLocking) "Locking..." else "Lock Key",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // Delete confirmation
            if (showDeleteConfirm) {
                Text(
                    text = "Are you sure you want to delete this key? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )

                if (key.isEncrypted) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        placeholder = { Text("Enter passphrase to confirm") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Danger,
                            unfocusedBorderColor = BorderDefault,
                            cursorColor = Danger,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedPlaceholderColor = TextMuted,
                            unfocusedPlaceholderColor = TextMuted,
                            focusedContainerColor = BgTertiary,
                            unfocusedContainerColor = BgTertiary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showDeleteConfirm = false
                            passphrase = ""
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                error = null
                                try {
                                    val client = SignetApiClient(daemonUrl)
                                    val result = client.deleteKey(
                                        keyName = key.name,
                                        passphrase = if (key.isEncrypted) passphrase else null
                                    )
                                    client.close()
                                    if (result.ok) {
                                        onActionComplete()
                                        onDismiss()
                                    } else {
                                        error = result.error ?: "Failed to delete"
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to delete"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && (!key.isEncrypted || passphrase.isNotBlank()),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Danger,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("Delete")
                    }
                }
            } else if (!showUnlock) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Danger
                    )
                ) {
                    Text("Delete Key")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EncryptionBadge(isEncrypted: Boolean) {
    val (color, label) = if (isEncrypted) {
        Success to "Encrypted"
    } else {
        Warning to "Unprotected"
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@Composable
private fun CopyableField(
    label: String,
    value: String,
    context: Context
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    copyToClipboard(context, label, value)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = SignetPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BunkerURIField(
    pubkey: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Bunker URI",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "bunker://${pubkey.take(16)}...",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "Show QR code",
                    tint = SignetPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = SignetPurple
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun formatDateTime(timestamp: String): String {
    return try {
        if (timestamp.contains("T")) {
            val datePart = timestamp.substringBefore("T")
            val timePart = timestamp.substringAfter("T").substringBefore(".")
            "$datePart $timePart"
        } else {
            timestamp
        }
    } catch (e: Exception) {
        timestamp
    }
}
