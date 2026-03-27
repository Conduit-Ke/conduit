package com.sms.edgenode.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.sms.edgenode.data.db.SmsStatus

enum class LoginStep {
    EMAIL,
    PASSWORD,
    OTP
}

enum class StatusFilter(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Outlined.AllInbox),
    PENDING("Pending", Icons.Outlined.Schedule),
    DELIVERED("Done", Icons.Outlined.CheckCircle),
    FAILED("Failed", Icons.Outlined.Error);
    
    val statuses: List<SmsStatus>
        get() = when (this) {
            ALL -> emptyList()
            PENDING -> listOf(SmsStatus.HELD, SmsStatus.QUEUED, SmsStatus.SENDING, SmsStatus.SUBMITTED)
            DELIVERED -> listOf(SmsStatus.DELIVERED, SmsStatus.SENT)
            FAILED -> listOf(SmsStatus.FAILED, SmsStatus.LOOP_DETECTED)
        }
}
