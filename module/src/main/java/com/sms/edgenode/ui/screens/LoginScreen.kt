package com.sms.edgenode.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sms.edgenode.ui.LocalTopNotificationState
import com.sms.edgenode.EdgeNodeApp
import com.sms.edgenode.data.api.AuthService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LoginStep {
    EMAIL, PASSWORD, OTP
}

data class NetworkNode(
    val x: Float,
    val y: Float,
    val label: String,
    val icon: ImageVector
)

@Composable
fun NetworkMapBackground() {
    val nodeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val smsColor = MaterialTheme.colorScheme.tertiary

    // Full-screen mesh network (spread across entire screen as backdrop)
    val nodes = remember {
        listOf(
            // Spread across the full screen
            NetworkNode(0.1f, 0.08f, "User", Icons.Default.PhoneAndroid),
            NetworkNode(0.5f, 0.06f, "Gateway", Icons.Default.SettingsInputAntenna),
            NetworkNode(0.9f, 0.1f, "Cloud", Icons.Default.Cloud),
            NetworkNode(0.25f, 0.28f, "Router", Icons.Default.Router),
            NetworkNode(0.75f, 0.25f, "Server", Icons.Default.Dns),
            NetworkNode(0.08f, 0.5f, "API", Icons.Default.Api),
            NetworkNode(0.5f, 0.48f, "Hub", Icons.Default.Hub),
            NetworkNode(0.92f, 0.52f, "Edge", Icons.Default.DeviceHub),
            NetworkNode(0.2f, 0.75f, "Carrier", Icons.Default.CellTower),
            NetworkNode(0.8f, 0.78f, "Mobile", Icons.Default.Smartphone)
        )
    }

    val meshConnections = remember {
        listOf(
            0 to 1, 1 to 2,
            0 to 3, 1 to 3, 1 to 4, 2 to 4,
            3 to 5, 3 to 6, 4 to 6, 4 to 7,
            5 to 6, 6 to 7,
            5 to 8, 6 to 8, 6 to 9, 7 to 9,
            8 to 9
        )
    }

    // Round-trip path for smooth loop
    val smsPath = remember { listOf(0, 1, 4, 6, 8, 9, 6, 3, 0) }
    val numSegments = smsPath.size - 1

    val infiniteTransition = rememberInfiniteTransition(label = "NetworkPath")

    val pathProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = numSegments.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 70000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SmsTravel"
    )

    val currentSegment = pathProgress.toInt().coerceIn(0, numSegments - 1)
    val segmentFraction = pathProgress - pathProgress.toInt()
    val adjustedProgress = if (segmentFraction < 0.35f) 0f else ((segmentFraction - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val isProcessing = segmentFraction < 0.35f

    val spinnerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ProcessingSpinner"
    )

    val startNode = nodes[smsPath[currentSegment]]
    val endNode = nodes[smsPath[currentSegment + 1]]
    val smsX = startNode.x + (endNode.x - startNode.x) * adjustedProgress
    val smsY = startNode.y + (endNode.y - startNode.y) * adjustedProgress

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight

        // Canvas for mesh lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            meshConnections.forEach { (fromIdx, toIdx) ->
                val from = nodes[fromIdx]
                val to = nodes[toIdx]
                drawLine(
                    color = lineColor,
                    start = Offset(from.x * size.width, from.y * size.height),
                    end = Offset(to.x * size.width, to.y * size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                )
            }
        }

        // Node icons with labels
        nodes.forEachIndexed { index, node ->
            val isCurrentNode = index == smsPath[currentSegment] && isProcessing

            Column(
                modifier = Modifier
                    .offset(x = width * node.x - 24.dp, y = height * node.y - 20.dp)
                    .alpha(if (isCurrentNode) 0.85f else 0.4f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Pulsing ring during communication
                    if (isCurrentNode) {
                        val pulseScale = 1f + (kotlin.math.sin(spinnerRotation * 0.02f) * 0.1f)
                        Surface(
                            modifier = Modifier
                                .size((36 * pulseScale).dp)
                                .alpha(0.4f),
                            shape = MaterialTheme.shapes.small,
                            color = smsColor.copy(alpha = 0.3f)
                        ) {}
                    }
                    
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = MaterialTheme.shapes.small,
                        color = nodeColor.copy(alpha = if (isCurrentNode) 0.2f else 0.08f)
                    ) {
                        Icon(
                            imageVector = node.icon,
                            contentDescription = node.label,
                            modifier = Modifier.padding(7.dp),
                            tint = if (isCurrentNode) smsColor else nodeColor
                        )
                    }
                }
                
                // Node label
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentNode) smsColor.copy(alpha = 0.9f) else nodeColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // SMS Icon with communication feedback
        Box(
            modifier = Modifier
                .offset(x = width * smsX - 12.dp, y = height * smsY - 12.dp)
                .size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                // Communication dots animation
                val dotOffset = (spinnerRotation % 60) / 60f
                for (i in 0..2) {
                    val alpha = kotlin.math.max(0f, 1f - kotlin.math.abs(dotOffset - i * 0.33f) * 3f)
                    Surface(
                        modifier = Modifier
                            .size(6.dp)
                            .offset(x = (-12 + i * 12).dp, y = (-20).dp)
                            .alpha(alpha),
                        shape = MaterialTheme.shapes.small,
                        color = smsColor
                    ) {}
                }
                
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp).rotate(spinnerRotation),
                    strokeWidth = 1.5.dp,
                    color = smsColor.copy(alpha = 0.5f),
                    trackColor = Color.Transparent
                )
            }

            Surface(
                modifier = Modifier.size(24.dp),
                shape = MaterialTheme.shapes.small,
                color = smsColor.copy(alpha = 0.8f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = "SMS",
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onTertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.sms.edgenode.EdgeNodeApp
    val authService = remember { app.authService }

    var currentStep by remember { mutableStateOf(LoginStep.EMAIL) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf<Int?>(null) } // Store pending user ID

    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val topNotification = LocalTopNotificationState.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val shakeOffset = remember { Animatable(0f) }

    // Auto-login check
    LaunchedEffect(Unit) {
        if (authService.isLoggedIn()) {
            onLogin()
        }
    }

    fun triggerShake() {
        scope.launch {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            repeat(4) {
                shakeOffset.animateTo(
                    targetValue = if (it % 2 == 0) 15f else -15f,
                    animationSpec = tween(50, easing = LinearEasing)
                )
            }
            shakeOffset.animateTo(0f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // BACKGROUND: Network map fills entire screen behind everything
        NetworkMapBackground()

        // FOREGROUND: Login form centered on screen
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset(x = shakeOffset.value.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Branding
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null,
                                modifier = Modifier.padding(7.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "ConduIT",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "LoginStepTransition"
                    ) { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            val headline = when (step) {
                                LoginStep.EMAIL -> "Sign in"
                                LoginStep.PASSWORD -> "Enter password"
                                LoginStep.OTP -> "Verify it's you"
                            }

                            val subHeadline = when (step) {
                                LoginStep.EMAIL -> "Use your ConduIT account"
                                LoginStep.PASSWORD -> email
                                LoginStep.OTP -> "We sent a code to your device"
                            }

                            Column {
                                Text(
                                    text = headline,
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subHeadline,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            when (step) {
                                LoginStep.EMAIL -> {
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text("Email") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !isLoading,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        shape = MaterialTheme.shapes.medium
                                    )
                                }
                                LoginStep.PASSWORD -> {
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("Password") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !isLoading,
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(
                                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        shape = MaterialTheme.shapes.medium
                                    )
                                }
                                LoginStep.OTP -> {
                                    OutlinedTextField(
                                        value = otp,
                                        onValueChange = { if (it.length <= 6) otp = it },
                                        label = { Text("6-digit code") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = !isLoading,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = MaterialTheme.shapes.medium,
                                        placeholder = { Text("000000") }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (step != LoginStep.EMAIL) {
                                    TextButton(onClick = { currentStep = LoginStep.entries[step.ordinal - 1] }, enabled = !isLoading) {
                                        Text("Back")
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (step == LoginStep.EMAIL) {
                                                if (email.isNotBlank()) currentStep = LoginStep.PASSWORD else triggerShake()
                                                return@launch
                                            }

                                            isLoading = true
                                            
                                            when (step) {
                                                LoginStep.PASSWORD -> {
                                                    val result = authService.login(email, password)
                                                    result.onSuccess { response ->
                                                        if (response.otp_required) {
                                                            userId = response.user_id
                                                            currentStep = LoginStep.OTP
                                                            topNotification.showNotification(
                                                                response.message ?: "OTP sent", 
                                                                Icons.Default.CheckCircle, 
                                                                Color.Green
                                                            )
                                                        } else {
                                                            // Should normally require OTP, but handle case where it might not
                                                            authService.fetchUserProfile()
                                                            onLogin()
                                                        }
                                                    }.onFailure { error ->
                                                        triggerShake()
                                                        topNotification.showNotification(
                                                            error.message ?: "Login failed", 
                                                            Icons.Default.Error, 
                                                            Color.Red
                                                        )
                                                    }
                                                }
                                                LoginStep.OTP -> {
                                                    val uid = userId
                                                    if (uid != null && otp.length == 6) {
                                                        val result = authService.verifyOtp(uid, otp)
                                                        result.onSuccess {
                                                            authService.fetchUserProfile()
                                                            onLogin()
                                                        }.onFailure { error ->
                                                            triggerShake()
                                                            topNotification.showNotification(
                                                                error.message ?: "Invalid OTP", 
                                                                Icons.Default.Error, 
                                                                Color.Red
                                                            )
                                                        }
                                                    } else {
                                                        triggerShake()
                                                        topNotification.showNotification("Enter valid OTP", Icons.Default.Error, Color.Red)
                                                    }
                                                }
                                                else -> {}
                                            }
                                            isLoading = false
                                        }
                                    },
                                    modifier = Modifier.height(44.dp),
                                    enabled = !isLoading,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text(if (step == LoginStep.OTP) "Verify" else "Next")
                                    }
                                }
                            }

                            if (step == LoginStep.OTP) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            userId?.let { uid ->
                                                isLoading = true
                                                authService.resendOtp(uid)
                                                    .onSuccess { 
                                                        topNotification.showNotification("OTP Resent", Icons.Default.CheckCircle, Color.Green)
                                                    }
                                                    .onFailure {
                                                        topNotification.showNotification("Failed to resend", Icons.Default.Error, Color.Red)
                                                    }
                                                isLoading = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    enabled = !isLoading
                                ) { Text("Resend code") }
                            }
                        }
                    }
                }
            }
        }
    }
}
