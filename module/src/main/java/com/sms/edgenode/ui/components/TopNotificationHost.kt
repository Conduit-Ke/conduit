package com.sms.edgenode.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TopNotificationHost(
    state: TopNotificationState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = state.isVisible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(300)
            ) + fadeOut()
        ) {
            state.currentMessage?.let { notification ->
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(0.95f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        notification.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = notification.iconColor ?: LocalContentColor.current
                            )
                        }
                        
                        Text(
                            text = notification.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )

                        notification.actionLabel?.let { label ->
                            TextButton(
                                onClick = { 
                                    notification.onAction()
                                    state.dismiss()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.inversePrimary
                                )
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

class TopNotificationState {
    var isVisible by mutableStateOf(false)
        private set
    var currentMessage by mutableStateOf<NotificationData?>(null)
        private set

    fun showNotification(
        message: String,
        icon: ImageVector? = null,
        iconColor: androidx.compose.ui.graphics.Color? = null,
        actionLabel: String? = null,
        onAction: () -> Unit = {}
    ) {
        currentMessage = NotificationData(message, icon, iconColor, actionLabel, onAction)
        isVisible = true
        
        // Auto-dismiss logic handled in the Composable or external scope
    }

    fun dismiss() {
        isVisible = false
    }
}

data class NotificationData(
    val message: String,
    val icon: ImageVector? = null,
    val iconColor: androidx.compose.ui.graphics.Color? = null,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {}
)

@Composable
fun rememberTopNotificationState(): TopNotificationState {
    val state = remember { TopNotificationState() }
    
    LaunchedEffect(state.isVisible) {
        if (state.isVisible) {
            delay(3000)
            state.dismiss()
        }
    }
    
    return state
}
