package com.sms.edgenode.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.edgenode.EdgeNodeApp
import com.sms.edgenode.data.db.SmsEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class StatusFilter(val label: String, val statuses: List<String>, val icon: ImageVector) {
    ALL("All", emptyList(), Icons.Outlined.Inbox),
    PENDING("Pending", listOf("PENDING", "HELD", "QUEUED", "SENDING"), Icons.Outlined.Schedule),
    DELIVERED("Delivered", listOf("DELIVERED"), Icons.Outlined.CheckCircle),
    FAILED("Failed", listOf("FAILED", "LOOP_DETECTED"), Icons.Outlined.Error)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as EdgeNodeApp
    val queue by app.database.smsDao().observeQueue().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    // Search & Filtering State
    var searchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(StatusFilter.ALL) }
    
    // Debounce search query to fix "choppiness"
    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedSearchQuery = searchQuery
    }

    val filteredQueue = remember(queue, debouncedSearchQuery, selectedFilter) {
        queue.filter { sms ->
            val matchesSearch = debouncedSearchQuery.isEmpty() || 
                sms.body.contains(debouncedSearchQuery, ignoreCase = true) ||
                sms.sender.contains(debouncedSearchQuery, ignoreCase = true)
            
            val matchesStatus = selectedFilter == StatusFilter.ALL || 
                sms.status in selectedFilter.statuses
            
            matchesSearch && matchesStatus
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // High-density Search Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search sender or message...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                )

                // Quick Filters
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(StatusFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = filter.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedFilter == filter,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }

        // Information Density Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MESSAGES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${filteredQueue.size} found",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Message List
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = filteredQueue.isEmpty(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "queue_list"
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyState(isFiltering = searchQuery.isNotEmpty() || selectedFilter != StatusFilter.ALL)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredQueue, key = { it.id }) { sms ->
                            SwipeToDismissItem(
                                sms = sms,
                                onDismiss = { 
                                    scope.launch {
                                        app.database.smsDao().delete(sms)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isFiltering: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isFiltering) Icons.Outlined.SearchOff else Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isFiltering) "No results found" else "Queue is empty",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (isFiltering) "Try adjusting your search or filters" else "New incoming messages will appear here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissItem(
    sms: SmsEntity,
    onDismiss: () -> Unit
) {
    val topNotification = com.sms.edgenode.ui.LocalTopNotificationState.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    val app = context.applicationContext as EdgeNodeApp
    val scope = rememberCoroutineScope()
    
    val canDismiss = sms.status == "DELIVERED" || sms.status == "FAILED"

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (canDismiss && (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd)) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                
                topNotification.showNotification(
                    message = "Deleted message from ${sms.sender}",
                    actionLabel = "UNDO",
                    onAction = {
                        scope.launch {
                            app.database.smsDao().insert(sms)
                        }
                    }
                )
                
                onDismiss()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = canDismiss,
        enableDismissFromStartToEnd = canDismiss,
        backgroundContent = {
            if (canDismiss) {
                val color = MaterialTheme.colorScheme.errorContainer
                val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        SmsCard(sms = sms)
    }
}

@Composable
private fun SmsCard(sms: SmsEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (sms.status) {
                                "DELIVERED" -> Icons.Default.CheckCircle
                                "FAILED", "LOOP_DETECTED" -> Icons.Default.ErrorOutline
                                "HELD" -> Icons.Default.PauseCircleOutline
                                else -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = when (sms.status) {
                                "DELIVERED" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                "FAILED", "LOOP_DETECTED" -> MaterialTheme.colorScheme.error
                                "HELD" -> androidx.compose.ui.graphics.Color(0xFFFFA500)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    Column {
                        Text(
                            text = sms.sender,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatTimestamp(sms.receivedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Status Indicator
                Surface(
                    color = when (sms.status) {
                        "DELIVERED" -> androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.1f)
                        "FAILED", "LOOP_DETECTED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        "HELD" -> androidx.compose.ui.graphics.Color(0xFFFFA500).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = sms.status,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (sms.status) {
                            "DELIVERED" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            "FAILED", "LOOP_DETECTED" -> MaterialTheme.colorScheme.error
                            "HELD" -> androidx.compose.ui.graphics.Color(0xFFFFA500)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (sms.attempts > 1 || sms.failureReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = sms.failureReason ?: "Attempts: ${sms.attempts}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { time = date }
    
    return if (now.get(Calendar.DATE) == msgTime.get(Calendar.DATE)) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
    }
}
