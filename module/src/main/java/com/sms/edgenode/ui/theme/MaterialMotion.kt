package com.sms.edgenode.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Standard Material 3 Motion specifications.
 * These transitions provide a cohesive, physical feel to navigation across the app.
 */
object MaterialMotion {
    
    /**
     * Fade Through is used for transitions between peer screens that do not have a strong 
     * spatial relationship to each other (e.g., Bottom Navigation destinations).
     * Outgoing element fades out quickly, then incoming element fades and scales in.
     */
    fun fadeThrough(): ContentTransform {
        return (fadeIn(
            animationSpec = tween(
                durationMillis = 210, 
                delayMillis = 90, 
                easing = LinearOutSlowInEasing
            )
        ) + scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(
                durationMillis = 210, 
                delayMillis = 90, 
                easing = LinearOutSlowInEasing
            )
        )).togetherWith(
            fadeOut(
                animationSpec = tween(
                    durationMillis = 90, 
                    easing = FastOutLinearInEasing
                )
            )
        )
    }

    /**
     * Shared Z-Axis is used for elements that have a parent-child relationship, 
     * moving forward or backward in the hierarchy.
     */
    fun sharedZAxis(forward: Boolean): ContentTransform {
        return if (forward) {
            (fadeIn(
                animationSpec = tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)
            ) + scaleIn(
                initialScale = 0.8f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )).togetherWith(
                fadeOut(
                    animationSpec = tween(90, easing = FastOutLinearInEasing)
                ) + scaleOut(
                    targetScale = 1.1f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            )
        } else {
            (fadeIn(
                animationSpec = tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)
            ) + scaleIn(
                initialScale = 1.1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )).togetherWith(
                fadeOut(
                    animationSpec = tween(90, easing = FastOutLinearInEasing)
                ) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            )
        }
    }

    /**
     * Shared X-Axis is used for spatial relationship between horizontal peers
     * (e.g., advancing through a setup flow).
     */
    fun sharedXAxis(forward: Boolean): ContentTransform {
        val slideOffset = 300 // slide by 300 pixels
        return if (forward) {
            (slideInHorizontally(
                initialOffsetX = { slideOffset },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(
                animationSpec = tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)
            )).togetherWith(
                slideOutHorizontally(
                    targetOffsetX = { -slideOffset },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(90, easing = FastOutLinearInEasing)
                )
            )
        } else {
            (slideInHorizontally(
                initialOffsetX = { -slideOffset },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(
                animationSpec = tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)
            )).togetherWith(
                slideOutHorizontally(
                    targetOffsetX = { slideOffset },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(
                    animationSpec = tween(90, easing = FastOutLinearInEasing)
                )
            )
        }
    }
}
