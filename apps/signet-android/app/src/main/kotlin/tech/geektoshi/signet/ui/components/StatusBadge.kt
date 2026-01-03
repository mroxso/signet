package tech.geektoshi.signet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tech.geektoshi.signet.ui.theme.Danger
import tech.geektoshi.signet.ui.theme.Success
import tech.geektoshi.signet.ui.theme.TextMuted
import tech.geektoshi.signet.ui.theme.Warning

enum class BadgeStatus {
    PENDING,
    APPROVED,
    AUTO_APPROVED,
    DENIED,
    EXPIRED
}

@Composable
fun StatusBadge(
    status: BadgeStatus,
    modifier: Modifier = Modifier
) {
    val (text, backgroundColor, textColor) = when (status) {
        BadgeStatus.PENDING -> Triple("Pending", Warning.copy(alpha = 0.15f), Warning)
        BadgeStatus.APPROVED -> Triple("Approved", Success.copy(alpha = 0.15f), Success)
        BadgeStatus.AUTO_APPROVED -> Triple("Auto Approved", Success.copy(alpha = 0.15f), Success)
        BadgeStatus.DENIED -> Triple("Denied", Danger.copy(alpha = 0.15f), Danger)
        BadgeStatus.EXPIRED -> Triple("Expired", TextMuted.copy(alpha = 0.15f), TextMuted)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
