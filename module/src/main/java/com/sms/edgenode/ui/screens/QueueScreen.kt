package com.sms.edgenode.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sms.edgenode.data.db.SmsEntity
import com.sms.edgenode.data.db.SmsStatus
import com.sms.edgenode.ui.LocalTopNotificationState
import com.sms.edgenode.ui.theme.DesignTokens
import com.sms.edgenode.ui.viewmodel.QueueViewModel
import org.koin.androidx.compose.koinViewModel
import com.sms.edgenode.ui.components.shimmerEffect
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = koinViewModel()
) {
    val queueState by viewModel.filteredMessages.collectAsState()
    val isFiltering = viewModel.searchQuery.isNotEmpty() || viewModel.selectedFilter != StatusFilter.ALL

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Edge-to-edge header panel (matches HomeScreen HeroHeader) ──
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
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing3)
            ) {
                // Display-scale title
                Text(
                    "Message Queue",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                val count = if (queueState is com.sms.edgenode.ui.state.UiState.Success) {
                    (queueState as com.sms.edgenode.ui.state.UiState.Success).data.size
                } else 0
                Text(
                    "$count messages in pipeline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Soft tonal search field — no indicator lines
                TextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Search sender or message...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery = "" }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = DesignTokens.ChipShape,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    )
                )

                // Filter chips row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = StatusFilter.entries
                    items(filters) { filter ->
                        FilterChip(
                            selected = viewModel.selectedFilter == filter,
                            onClick = { viewModel.selectedFilter = filter },
                            label = { Text(filter.label) },
                            leadingIcon = {
                                Icon(
                                    filter.icon,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = CircleShape,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = viewModel.selectedFilter == filter,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }

        // Subtle divider seam below header
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 1.dp
        )

        // ── Content rides directly on background — no card wrappers ──
        when (val state = queueState) {
            is com.sms.edgenode.ui.state.UiState.Loading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = DesignTokens.Spacing2)
                ) {
                    items(6) { index ->
                        ShimmerSmsRow()
                        if (index < 5) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
            is com.sms.edgenode.ui.state.UiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    com.sms.edgenode.ui.components.EmptyStateView(
                        icon = if (isFiltering) Icons.Outlined.SearchOff else Icons.Outlined.Inbox,
                        title = if (isFiltering) "No matches found" else "Queue is empty",
                        message = if (isFiltering) "Try a different search term" else "Outgoing messages will appear here"
                    )
                }
            }
            is com.sms.edgenode.ui.state.UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = DesignTokens.Spacing2)
                ) {
                    itemsIndexed(state.data, key = { _, sms -> sms.id }) { index, sms ->
                        SwipeToDismissSms(
                            sms = sms,
                            modifier = Modifier.animateItem(),
                            onDismiss = { viewModel.deleteMessage(sms) },
                            onUndo = { viewModel.undoDelete(sms) }
                        )
                        if (index < state.data.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
            is com.sms.edgenode.ui.state.UiState.Error -> {}
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissSms(
    sms: SmsEntity,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onUndo: () -> Unit
) {
    val topNotification = LocalTopNotificationState.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val canDismiss = sms.status == SmsStatus.DELIVERED || sms.status == SmsStatus.FAILED || sms.status == SmsStatus.LOOP_DETECTED

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (canDismiss && value != SwipeToDismissBoxValue.Settled) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                topNotification.showNotification(
                    "Removed message from ${sms.sender}",
                    Icons.Default.Delete,
                    Color(0xFFF44336),
                    "UNDO",
                    onUndo
                )
                onDismiss()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        modifier = modifier.fillMaxWidth(),
        state = dismissState,
        enableDismissFromEndToStart = canDismiss,
        enableDismissFromStartToEnd = canDismiss,
        backgroundContent = {
            val color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    ) {
        // Direct background — no inner Surface wrapper
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SmsRow(sms = sms)
        }
    }
}

@Composable
private fun SmsRow(sms: SmsEntity) {
    val isFailed = sms.status == SmsStatus.FAILED || sms.status == SmsStatus.LOOP_DETECTED
    val isDelivered = sms.status == SmsStatus.DELIVERED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val icon = when {
            isDelivered -> Icons.AutoMirrored.Filled.CallMade
            isFailed -> Icons.AutoMirrored.Filled.CallMissed
            else -> Icons.AutoMirrored.Filled.CallReceived
        }

        Icon(
            icon,
            contentDescription = "Status: ${sms.status}",
            tint = when {
                isDelivered -> MaterialTheme.colorScheme.secondary
                isFailed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    sms.sender,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(sms.receivedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                sms.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (sms.failureReason != null) {
                Text(
                    "Error: ${sms.failureReason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ShimmerSmsRow() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .shimmerEffect()
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.4f).clip(CircleShape).shimmerEffect())
                Box(modifier = Modifier.height(12.dp).width(48.dp).clip(CircleShape).shimmerEffect())
            }
            Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.9f).clip(CircleShape).shimmerEffect())
            Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.6f).clip(CircleShape).shimmerEffect())
        }
    }
}
