package com.sms.edgenode.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.*
import com.sms.edgenode.ui.theme.DesignTokens
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalPagerApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestPermissions: () -> Unit = {}
) {
    val pages = listOf(
        OnboardingPage(
            title = "SMS Gateway",
            description = "Transform your device into a powerful SMS routing gateway for seamless message delivery",
            icon = Icons.Outlined.CloudQueue
        ),
        OnboardingPage(
            title = "Smart Routing",
            description = "Intelligent multi-SIM management with automatic failover and load balancing",
            icon = Icons.Outlined.Route
        ),
        OnboardingPage(
            title = "Real-time Sync",
            description = "Instant message processing with live status updates and comprehensive audit logs",
            icon = Icons.Outlined.Sync
        ),
        OnboardingPage(
            title = "Secure & Private",
            description = "End-to-end encryption with local data storage. Your messages stay private",
            icon = Icons.Outlined.Security
        ),
        OnboardingPage(
            title = "System Permissions",
            description = "To operate seamlessly, Conduit requires background processing, notification access, and SMS routing permissions.",
            icon = Icons.Outlined.AdminPanelSettings
        )
    )

    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            HorizontalPager(
                count = pages.size,
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                OnboardingPageContent(pages[page])
            }

            HorizontalPagerIndicator(
                pagerState = pagerState,
                modifier = Modifier.padding(vertical = DesignTokens.Spacing6),
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                indicatorWidth = 8.dp,
                indicatorHeight = 8.dp,
                spacing = 8.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing6, vertical = DesignTokens.Spacing8),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing4)
            ) {
                AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = DesignTokens.ButtonShape
                    ) {
                        Text("Back")
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onRequestPermissions()
                            onComplete()
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = DesignTokens.ButtonShape
                ) {
                    Text(
                        if (pagerState.currentPage == pages.size - 1) "Grant & Start" else "Next",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        TextButton(
            onClick = {
                onRequestPermissions()
                onComplete()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(DesignTokens.Spacing4)
        ) {
            Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val scale by rememberInfiniteTransition(label = "scale").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DesignTokens.Spacing8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(DesignTokens.Spacing8))

        Text(
            page.title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(DesignTokens.Spacing4))

        Text(
            page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
    }
}
