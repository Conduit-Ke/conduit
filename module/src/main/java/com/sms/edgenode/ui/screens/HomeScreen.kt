package com.sms.edgenode.ui.screens

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.edgenode.EdgeNodeApp
import com.sms.edgenode.service.RoutingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as EdgeNodeApp
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val topNotification = com.sms.edgenode.ui.LocalTopNotificationState.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    var isServiceRunning by remember { mutableStateOf(false) }
    var queueSize by remember { mutableStateOf(0) }
    var deliveredCount by remember { mutableStateOf(0) }
    var failedCount by remember { mutableStateOf(0) }
    val quotaInfo by app.smsApiService.quotaInfo.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    var showServiceConfirmDialog by remember { mutableStateOf(false) }
    
    // Refresh function for pull-to-refresh
    suspend fun refreshData() {
        isRefreshing = true
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        isServiceRunning = checkServiceRunning(context)
        val statsData = getStats(app)
        queueSize = statsData.first
        deliveredCount = statsData.second
        failedCount = statsData.third
        
        // Fetch quota from API - updates the reactive flow
        app.smsApiService.getQuota()
        
        delay(500) // Brief delay for visual feedback
        isRefreshing = false
    }
    
    LaunchedEffect(Unit) {
        // Initial quota fetch on screen load
        app.smsApiService.getQuota()

        while (true) {
            isServiceRunning = checkServiceRunning(context)
            val statsData = getStats(app)
            queueSize = statsData.first
            deliveredCount = statsData.second
            failedCount = statsData.third
            delay(5000) // Poll every 5 seconds for local stats
        }
    }

    // Service confirmation dialog
    if (showServiceConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showServiceConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(if (isServiceRunning) "Stop Routing Service?" else "Start Routing Service?")
            },
            text = {
                Text(
                    if (isServiceRunning) 
                        "Stopping the service will pause all SMS routing. Pending messages will remain in queue."
                    else 
                        "Starting the service will begin routing SMS messages through this device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showServiceConfirmDialog = false
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        if (isServiceRunning) {
                            RoutingService.stop(context)
                            topNotification.showNotification(
                                message = "Service stopped successfully",
                                icon = Icons.Default.Pause,
                                iconColor = androidx.compose.ui.graphics.Color.Gray
                            )
                        } else {
                            RoutingService.start(context)
                            topNotification.showNotification(
                                message = "Service started and ready",
                                icon = Icons.Default.CheckCircle,
                                iconColor = androidx.compose.ui.graphics.Color.Green
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isServiceRunning) "Stop" else "Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServiceConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Dashboard",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isServiceRunning) 
                                            androidx.compose.ui.graphics.Color.Green 
                                        else 
                                            MaterialTheme.colorScheme.error,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Routing Service · ${if (isServiceRunning) "Active" else "Stopped"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            showServiceConfirmDialog = true
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isServiceRunning) "STOP" else "START")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()
        
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { refreshData() } },
            state = pullToRefreshState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Metrics Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem(
                            title = "Queue",
                            value = queueSize.toString(),
                            icon = Icons.AutoMirrored.Outlined.List
                        )
                        VerticalDivider(
                            modifier = Modifier.height(40.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        MetricItem(
                            title = "Delivered",
                            value = deliveredCount.toString(),
                            icon = Icons.Outlined.CheckCircle
                        )
                        VerticalDivider(
                            modifier = Modifier.height(40.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        MetricItem(
                            title = "Failed",
                            value = failedCount.toString(),
                            icon = Icons.Outlined.Warning
                        )
                    }
                }

                // Balance Card
                val balance = quotaInfo?.balance ?: quotaInfo?.received?.balance ?: quotaInfo?.sent?.balance
                balance?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${it.currency} ${String.format("%.2f", it.amount)}",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Usage Section
                quotaInfo?.let { quota ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "USAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        QuotaCard(quotaInfo = quota)
                    }
                }

                // Activity Feed Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT ACTIVITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        TextButton(
                            onClick = onNavigateToLogs,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("View All", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    ActivityFeed()
                }

                // Quick Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "QUICK ACTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionButton(
                            icon = Icons.Default.Refresh,
                            label = "Refresh",
                            modifier = Modifier.weight(1f),
                            onClick = { scope.launch { refreshData() } }
                        )
                        QuickActionButton(
                            icon = Icons.Default.DeleteSweep,
                            label = "Clear Done",
                            modifier = Modifier.weight(1f),
                            onClick = { 
                                scope.launch {
                                    app.database.smsDao().deleteDelivered()
                                    topNotification.showNotification(
                                        message = "Delivered messages cleared",
                                        icon = Icons.Default.DeleteSweep
                                    )
                                }
                            }
                        )
                        QuickActionButton(
                            icon = Icons.Default.SupportAgent,
                            label = "Support",
                            modifier = Modifier.weight(1f),
                            onClick = { /* Open support URL */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    title: String, 
    value: String, 
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuotaCard(quotaInfo: com.sms.edgenode.data.api.SmsApiService.QuotaInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quotaInfo.received?.let { 
                QuotaItem(
                    label = "Incoming SMS",
                    detail = it
                )
            }
            
            quotaInfo.sent?.let {
                QuotaItem(
                    label = "Outgoing SMS",
                    detail = it
                )
            }
        }
    }
}

@Composable
private fun QuotaItem(
    label: String,
    detail: com.sms.edgenode.data.api.SmsApiService.QuotaDetail
) {
    val progress = if (detail.limit == "unlimited") {
        0f
    } else {
        val limit = detail.limit.toFloatOrNull() ?: 1f
        (detail.used.toFloat() / limit).coerceIn(0f, 1f)
    }

    val color = when {
        !detail.allowed -> MaterialTheme.colorScheme.error
        progress > 0.9f -> MaterialTheme.colorScheme.error
        progress > 0.7f -> androidx.compose.ui.graphics.Color(0xFFFFA500) // Orange
        else -> MaterialTheme.colorScheme.primary
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label, 
                style = MaterialTheme.typography.labelLarge, 
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${detail.used} / ${detail.limit}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (!detail.allowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

private val Typography.labelExtraSmall: androidx.compose.ui.text.TextStyle
    @Composable
    get() = labelSmall.copy(fontSize = 10.sp)

private fun checkServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    @Suppress("DEPRECATION")
    return manager.getRunningServices(Int.MAX_VALUE).any {
        it.service.className == RoutingService::class.java.name
    }
}

private suspend fun getStats(app: EdgeNodeApp): Triple<Int, Int, Int> {
    val pendingCount = app.database.smsDao().getPendingCount()
    val deliveredCount = app.database.smsDao().getDeliveredCount()
    val failedCount = app.database.smsDao().getFailedCount()
    return Triple(pendingCount, deliveredCount, failedCount)
}

@Composable
private fun ActivityFeed() {
    val context = LocalContext.current
    val app = context.applicationContext as EdgeNodeApp
    val logs by app.database.auditLogDao().observeLogs().collectAsState(initial = emptyList())
    
    val recentLogs = remember(logs) { logs.take(3) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (recentLogs.isEmpty()) {
                ActivityRow(
                    item = ActivityItem(
                        icon = Icons.Default.Info,
                        message = "No recent activity",
                        time = "",
                        isPositive = null
                    )
                )
            } else {
                recentLogs.forEachIndexed { index, log ->
                    ActivityRow(
                        item = ActivityItem(
                            icon = when (log.eventType) {
                                "DELIVERED" -> Icons.Outlined.CheckCircle
                                "SUBMITTED" -> Icons.Default.Send
                                "RETRY_SCHEDULED" -> Icons.Default.Schedule
                                "FAILED" -> Icons.Default.ErrorOutline
                                "PROCESSING_ERROR" -> Icons.Default.Warning
                                else -> Icons.Default.Info
                            },
                            message = log.details,
                            time = formatLogTime(log.timestamp),
                            isPositive = when (log.eventType) {
                                "DELIVERED", "SUBMITTED" -> true
                                "FAILED", "PROCESSING_ERROR" -> false
                                else -> null
                            }
                        )
                    )
                    if (index < recentLogs.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

private fun formatLogTime(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Calendar.getInstance()
    val logTime = Calendar.getInstance().apply { time = date }
    
    return if (now.get(Calendar.DATE) == logTime.get(Calendar.DATE)) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
    }
}

private data class ActivityItem(
    val icon: ImageVector,
    val message: String,
    val time: String,
    val isPositive: Boolean?
)

@Composable
private fun ActivityRow(item: ActivityItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = when (item.isPositive) {
                true -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = item.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (item.time.isNotEmpty()) {
            Text(
                text = item.time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
