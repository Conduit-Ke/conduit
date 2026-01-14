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
                    modifier = Modifier.height(64.dp)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { 
                            previousTab = selectedTab
                            selectedTab = 0 
                        },
                        icon = { 
                            Icon(
                                if (selectedTab == 0) Icons.Filled.Home else Icons.Filled.Home,
                                "Home"
                            ) 
                        },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { 
                            previousTab = selectedTab
                            selectedTab = 1 
                        },
                        icon = { 
                            Icon(
                                if (selectedTab == 1) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Filled.List,
                                "Queue"
                            ) 
                        },
                        label = { Text("Queue") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { 
                            previousTab = selectedTab
                            selectedTab = 2 
                        },
                        icon = { 
                            Icon(
                                if (selectedTab == 2) Icons.Filled.Settings else Icons.Filled.Settings,
                                "Settings"
                            ) 
                        },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
            }
        ) { tab ->
            when (tab) {
                0 -> HomeScreen(Modifier.padding(padding))
                1 -> QueueScreen(Modifier.padding(padding))
                2 -> SettingsScreen(Modifier.padding(padding), onLogout)
            }
        }
    }
}
