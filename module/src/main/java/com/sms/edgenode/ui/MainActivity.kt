package com.sms.edgenode.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sms.edgenode.ui.screens.*
import com.sms.edgenode.ui.theme.*
import com.sms.edgenode.ui.components.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.koin.compose.koinInject
import com.sms.edgenode.data.api.AuthService
import com.sms.edgenode.data.api.DeviceService


val LocalTopNotificationState = staticCompositionLocalOf<TopNotificationState> {
    error("No TopNotificationState provided")
}

class MainActivity : AppCompatActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        requestBatteryOptimizationIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        setContent {
            val isDarkTheme = ThemeState.isDarkTheme.value ?: isSystemInDarkTheme()
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
            Manifest.permission.CALL_PHONE,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            requestBatteryOptimizationIfNeeded()
        }
    }

    private fun requestBatteryOptimizationIfNeeded() {
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}

@Composable
fun AppNavigation() {
    val authService: AuthService = koinInject()
    val deviceService: DeviceService = koinInject()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isLoggedIn by remember { mutableStateOf(authService.isLoggedIn()) }
    var hasSeenOnboarding by remember {
        mutableStateOf(
            context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("has_seen_onboarding", false)
        )
    }

    AnimatedContent(
        targetState = when {
            !hasSeenOnboarding -> "onboarding"
            !isLoggedIn -> "login"
            else -> "main"
        },
        transitionSpec = { 
            val isForward = initialState == "onboarding" || (initialState == "login" && targetState == "main")
            MaterialMotion.sharedZAxis(forward = isForward) 
        },
        label = "AuthNavigation"
    ) { screen ->
        when (screen) {
            "onboarding" -> OnboardingScreen(
                onComplete = {
                    context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_seen_onboarding", true)
                        .apply()
                    hasSeenOnboarding = true
                },
                onRequestPermissions = {
                    (context as? MainActivity)?.requestPermissionsIfNeeded()
                }
            )
            "login" -> LoginScreen(onLogin = { isLoggedIn = true })
            "main" -> MainApp(onLogout = {
                scope.launch {
                    authService.logout()
                    deviceService.clear()
                    com.sms.edgenode.service.RoutingService.stop(context)
                    isLoggedIn = false
                }
            })
        }
    }
}

@Composable
fun MainApp(onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Solid modern background design
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing2, vertical = DesignTokens.Spacing2),
                shape = DesignTokens.CardShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = DesignTokens.ElevationCard,
                tonalElevation = DesignTokens.ElevationFloating
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.height(68.dp),
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(
                        Triple(0, Icons.Filled.Dashboard, "Dashboard"),
                        Triple(1, Icons.AutoMirrored.Filled.List, "Queue"),
                        Triple(2, Icons.Default.History, "Logs"),
                        Triple(3, Icons.Filled.Settings, "Settings")
                    )

                    items.forEach { (index, icon, label) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { 
                                Icon(
                                    icon, 
                                    label,
                                    modifier = Modifier.size(24.dp)
                                ) 
                            },
                            label = { 
                                Text(
                                    label, 
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { MaterialMotion.fadeThrough() },
            label = "ContentNavigation"
        ) { tab ->
            val paddingModifier = Modifier.padding(paddingValues)
            when (tab) {
                0 -> HomeScreen(paddingModifier)
                1 -> QueueScreen(paddingModifier)
                2 -> LogsScreen(paddingModifier, onBack = { selectedTab = 0 })
                3 -> SettingsScreen(paddingModifier, onLogout)
            }
        }
    }
}
