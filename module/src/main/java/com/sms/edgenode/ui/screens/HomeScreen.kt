package com.sms.edgenode.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.edgenode.ui.LocalTopNotificationState
import com.sms.edgenode.ui.theme.DesignTokens
import com.sms.edgenode.ui.viewmodel.HomeViewModel
import com.sms.edgenode.ui.components.bounceClick
import com.sms.edgenode.ui.components.shimmerEffect
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel()
) {
    val topNotification = LocalTopNotificationState.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val queueSize by viewModel.queueSize.collectAsState()
    val deliveredCount by viewModel.deliveredCount.collectAsState()
    val failedCount by viewModel.failedCount.collectAsState()
    val totalProcessed by viewModel.totalProcessed.collectAsState()
    val successRate by viewModel.successRate.collectAsState()
    
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        delay(600)
        isLoading = false
    }

    val pullToRefreshState = rememberPullToRefreshState()
    
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                delay(800)
                isRefreshing = false
            }
        },
        state = pullToRefreshState,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = DesignTokens.Spacing8),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing6)
        ) {
            // Edge-to-edge hardware console header
            HeroHeader(
                isServiceRunning = viewModel.isServiceRunning,
                onToggleService = {
                    viewModel.toggleService()
                    topNotification.showNotification(
                        if (viewModel.isServiceRunning) "Network bridge secured" else "Routing offline",
                        if (viewModel.isServiceRunning) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (viewModel.isServiceRunning) Color(0xFF4CAF50) else Color(0xFF9E9E9E)
                    )
                }
            )

            // Layout for widgets: 2x2 grid approach using standard Rows & Columns for performance
            Column(
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing4),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top row of KPIs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricWidget(
                        modifier = Modifier.weight(1f),
                        title = "Delivered",
                        value = deliveredCount,
                        color = Color(0xFF4CAF50),
                        icon = Icons.AutoMirrored.Filled.CallMade,
                        isLoading = isLoading
                    )
                    
                    MetricWidget(
                        modifier = Modifier.weight(1f),
                        title = "Pending Queue",
                        value = queueSize,
                        color = MaterialTheme.colorScheme.primary,
                        icon = Icons.Outlined.CloudQueue,
                        isLoading = isLoading
                    )
                }

                // Second row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricWidget(
                        modifier = Modifier.weight(1f),
                        title = "Failed",
                        value = failedCount,
                        color = MaterialTheme.colorScheme.error,
                        icon = Icons.AutoMirrored.Filled.CallMissed,
                        isLoading = isLoading
                    )

                    SuccessRateWidget(
                        modifier = Modifier.weight(1f),
                        rate = successRate,
                        isLoading = isLoading
                    )
                }
                
                // Full width total processed banner widget
                TotalProcessedWidget(
                    total = totalProcessed,
                    isLoading = isLoading
                )
            }
        }
    }
}

@Composable
private fun HeroHeader(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit
) {
    // A clean, card-less edge-to-edge header that acts like an embedded panel
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                start = DesignTokens.Spacing4, 
                end = DesignTokens.Spacing4, 
                top = 28.dp, 
                bottom = 24.dp
            )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing title
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isServiceRunning) MaterialTheme.colorScheme.primary.copy(alpha = alpha) 
                                else MaterialTheme.colorScheme.error
                            )
                    )
                    
                    Text(
                        text = if (isServiceRunning) "SYSTEM ACTIVE" else "OFFLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
                
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { onToggleService() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Conduit Node",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Live telemetry & message orchestration",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    // Bottom subtle divider for the header
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        thickness = 1.dp
    )
}

@Composable
private fun MetricWidget(
    title: String,
    value: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // Clean layout, utilizing minimal surface and structural spacing instead of heavy dropshadows
    Surface(
        modifier = modifier,
        shape = DesignTokens.CardShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isLoading) {
                    Box(modifier = Modifier.height(36.dp).width(60.dp).clip(DesignTokens.ButtonShape).shimmerEffect())
                } else {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { h -> h } + fadeIn()).togetherWith(slideOutVertically { h -> -h } + fadeOut())
                            } else {
                                (slideInVertically { h -> -h } + fadeIn()).togetherWith(slideOutVertically { h -> h } + fadeOut())
                            }
                        },
                        label = "WidgetValue"
                    ) { targetCount ->
                        Text(
                            text = targetCount.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SuccessRateWidget(
    rate: Float,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = DesignTokens.CardShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Success Rate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).shimmerEffect()
                    )
                } else {
                    val animatedProgress by animateFloatAsState(
                        targetValue = rate,
                        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                        label = "progress"
                    )
                    
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(64.dp),
                        color = Color(0xFF4CAF50),
                        strokeWidth = 6.dp,
                        strokeCap = StrokeCap.Round
                    )
                    
                    val percentage = (rate * 100).toInt()
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalProcessedWidget(
    total: Int,
    isLoading: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DesignTokens.CardShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Total System Volume",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                
                if (isLoading) {
                    Box(modifier = Modifier.height(28.dp).width(80.dp).clip(DesignTokens.ButtonShape).shimmerEffect())
                } else {
                    AnimatedContent(
                        targetState = total,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { h -> h } + fadeIn()).togetherWith(slideOutVertically { h -> -h } + fadeOut())
                            } else {
                                (slideInVertically { h -> -h } + fadeIn()).togetherWith(slideOutVertically { h -> h } + fadeOut())
                            }
                        },
                        label = "TotalValue"
                    ) { count ->
                        Text(
                            text = String.format("%,d", count),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            
            Icon(
                Icons.Outlined.Analytics,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
