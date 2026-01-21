package com.sms.edgenode.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sms.edgenode.ui.screens.*
import com.sms.edgenode.ui.theme.*
import com.sms.edgenode.ui.components.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

val LocalTopNotificationState = staticCompositionLocalOf<TopNotificationState> {
    error("No TopNotificationState provided")
}

class MainActivity : AppCompatActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        supportActionBar?.hide()
        
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        setContent {
            val isDarkTheme by ThemeState.isDarkTheme
            val topNotificationState = rememberTopNotificationState()
            
            EdgeNodeTheme(darkTheme = isDarkTheme) {
                CompositionLocalProvider(LocalTopNotificationState provides topNotificationState) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box {
                            AppNavigation()
                            TopNotificationHost(state = topNotificationState)
                        }
                    }
                }
            }
        }
    }

    fun requestPermissionsIfNeeded() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun requestSinglePermission(permission: String) {
        singlePermissionLauncher.launch(permission)
    }
}

@Composable
fun AppNavigation() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.sms.edgenode.EdgeNodeApp
    val authService = app.authService
    val deviceService = app.deviceService
    val scope = rememberCoroutineScope()
    
    var isLoggedIn by remember { mutableStateOf(authService.isLoggedIn()) }

    AnimatedContent(
        targetState = isLoggedIn,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AppNavigation"
    ) { loggedIn ->
        if (!loggedIn) {
            LoginScreen(onLogin = { 
                isLoggedIn = true 
            })
        } else {
            MainApp(onLogout = {
                scope.launch {
                    // 1. Tell backend we are logging out
                    authService.logout()
                    
                    // 2. Clear all local preferences
                    deviceService.clear()
                    
                    // 3. Stop the routing service
                    com.sms.edgenode.service.RoutingService.stop(context)
                    
                    // 5. Update UI state
                    isLoggedIn = false
                }
            })
        }
    }
}

@Composable
fun MainApp(onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    var previousTab by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.height(56.dp),
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        alwaysShowLabel = false,
                        onClick = { 
                            previousTab = selectedTab
                            selectedTab = 0 
                        },
                        icon = { Icon(Icons.Filled.Home, "Home") },
                        label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        alwaysShowLabel = false,
                        onClick = { 
                            previousTab = selectedTab
                            selectedTab = 1 
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, "Queue") },
                        label = { Text("Queue", style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        alwaysShowLabel = false,
                        onClick = { 
                            previousTab = selectedTab
                            selectedTab = 2 
                        },
                        icon = { Icon(Icons.Default.History, "Logs") },
                        label = { Text("Logs", style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        alwaysShowLabel = false,
                        onClick = { 
                            previousTab = selectedTab
                            selectedTab = 3 
                        },
                        icon = { Icon(Icons.Filled.Settings, "Settings") },
                        label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "tab_transition"
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(
                    modifier = Modifier.padding(padding),
                    onNavigateToLogs = { selectedTab = 2 }
                )
                1 -> QueueScreen(Modifier.padding(padding))
                2 -> LogsScreen(
                    modifier = Modifier.padding(padding),
                    onBack = { selectedTab = 0 }
                )
                3 -> SettingsScreen(Modifier.padding(padding), onLogout)
            }
        }
    }
}

