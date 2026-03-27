package com.sms.edgenode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sms.edgenode.data.db.AuditLogEntity
import com.sms.edgenode.ui.theme.DesignTokens
import com.sms.edgenode.ui.theme.extended
import com.sms.edgenode.ui.viewmodel.LogsViewModel
import org.koin.androidx.compose.koinViewModel
import com.sms.edgenode.ui.components.shimmerEffect
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    viewModel: LogsViewModel = koinViewModel()
) {
    val logs by viewModel.logs.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Simple Top App Bar Look
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.Spacing4),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing2)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column {
                        Text(
                            "Audit Logs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val count = if (logs is com.sms.edgenode.ui.state.UiState.Success) {
                            (logs as com.sms.edgenode.ui.state.UiState.Success).data.size
                        } else 0
                        Text(
                            "$count entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (logs is com.sms.edgenode.ui.state.UiState.Success && (logs as com.sms.edgenode.ui.state.UiState.Success).data.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearLogs() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                }
            }
        }

        // Unified Card Content Section
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DesignTokens.Spacing4)
                .padding(bottom = DesignTokens.Spacing8)
        ) {
            when (val state = logs) {
                is com.sms.edgenode.ui.state.UiState.Loading -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = DesignTokens.CardShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Column {
                            repeat(8) { index ->
                                ShimmerLogsList()
                                if (index < 7) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 48.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
                is com.sms.edgenode.ui.state.UiState.Empty -> {
                    com.sms.edgenode.ui.components.EmptyStateView(
                        icon = Icons.Outlined.History,
                        title = "No logs recorded",
                        message = "Activity will appear here"
                    )
                }
                is com.sms.edgenode.ui.state.UiState.Success -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = DesignTokens.CardShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = DesignTokens.Spacing2)
                        ) {
                            itemsIndexed(state.data, key = { _, log -> log.id }) { index, log ->
                                LogRow(
                                    log = log,
                                    modifier = Modifier.animateItem()
                                )
                                if (index < state.data.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 48.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
                is com.sms.edgenode.ui.state.UiState.Error -> {}
            }
        }
    }
}

@Composable
private fun LogRow(log: AuditLogEntity, modifier: Modifier = Modifier) {
    val isFailed = log.eventType == "FAILED" || log.eventType == "PROCESSING_ERROR"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (log.eventType) {
            "DELIVERED" -> Icons.AutoMirrored.Filled.CallMade
            "SUBMITTED" -> Icons.AutoMirrored.Filled.ArrowForward
            "FAILED", "PROCESSING_ERROR" -> Icons.AutoMirrored.Filled.CallMissed
            else -> Icons.Outlined.SwapHoriz
        }
        
        Icon(
            icon,
            contentDescription = null,
            tint = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                log.details.takeIf { it.isNotBlank() } ?: log.eventType,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (log.smsId != null) {
                    Text(
                        "0x${log.smsId.toString().takeLast(8).uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerLogsList() {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .shimmerEffect()
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.8f).clip(CircleShape).shimmerEffect())
            Box(modifier = Modifier.height(12.dp).fillMaxWidth(0.5f).clip(CircleShape).shimmerEffect())
        }
    }
}
