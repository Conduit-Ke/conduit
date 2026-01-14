package com.sms.edgenode.ui.screens

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Warning
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
import com.sms.edgenode.EdgeNodeApp
import com.sms.edgenode.service.RoutingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier, snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val app = context.applicationContext as EdgeNodeApp
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val topNotification = com.sms.edgenode.ui.LocalTopNotificationState.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    var isServiceRunning by remember { mutableStateOf(false) }
    var queueSize by remember { mutableStateOf(0) }
    var deliveredCount by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Determine if FAB should be expanded (collapses when scrolled)
    val isFabExpanded by remember {
        derivedStateOf { scrollState.value == 0 }
    }
    
    // Refresh function for pull-to-refresh
    suspend fun refreshData() {
        isRefreshing = true
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        isServiceRunning = checkServiceRunning(context)
        val stats = getStats(app)
        queueSize = stats.first
        deliveredCount = stats.second
        delay(500) // Brief delay for visual feedback
        isRefreshing = false
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            isServiceRunning = checkServiceRunning(context)
            val stats = getStats(app)
            queueSize = stats.first
            deliveredCount = stats.second
            delay(2000)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dashboard", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
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
                expanded = isFabExpanded,
                icon = {
                    AnimatedContent(
                        targetState = isServiceRunning,
                        transitionSpec = {
                            (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut())
                        }
                    ) { running ->
                        Icon(
                            if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                    }
                },
                text = { Text(if (isServiceRunning) "Stop Service" else "Start Service") },
                containerColor = if (isServiceRunning) 
                    MaterialTheme.colorScheme.errorContainer 
                else 
                    MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isServiceRunning) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onPrimaryContainer
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusCard(
                    title = "Routing Service",
                    status = if (isServiceRunning) "Active" else "Inactive",
                    icon = if (isServiceRunning) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    isHealthy = isServiceRunning
                )

                Text(
                    text = "Performance Metrics",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricCard(
                        title = "In Queue",
                        value = queueSize.toString(),
                        icon = Icons.Outlined.List,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Delivered",
                        value = deliveredCount.toString(),
                        icon = Icons.Outlined.Done,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Extra spacer to allow scrolling past FAB
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, status: String, icon: ImageVector, isHealthy: Boolean) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isHealthy) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        AnimatedContent(
            targetState = isHealthy,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            }
        ) { healthy ->
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (healthy) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (healthy) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = (if (healthy) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer).copy(alpha = 0.7f)
                    )
                    Text(
                        text = if (healthy) "Active" else "Inactive",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (healthy) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun checkServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Int.MAX_VALUE).any {
        it.service.className == RoutingService::class.java.name
    }
}

private suspend fun getStats(app: EdgeNodeApp): Pair<Int, Int> {
    val pendingCount = app.database.smsDao().getPendingCount()
    val deliveredCount = app.database.smsDao().getDeliveredCount()
    return pendingCount to deliveredCount
}
