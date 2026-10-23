package br.com.dhc.solar.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Molas usadas no app inteiro: respostas rápidas com leve "overshoot", como no iOS. */
object SolarMotion {
    fun <T> snappy() = spring<T>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
    fun <T> gentle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
}

/** Encolhe levemente ao pressionar e dá um toque háptico ao soltar. */
@Composable
fun Modifier.pressable(onClick: (() -> Unit)? = null): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, SolarMotion.snappy(), label = "press")
    val haptics = LocalHapticFeedback.current
    val scaled = graphicsLayer { scaleX = scale; scaleY = scale }
    return if (onClick == null) scaled else scaled.clickable(interactionSource = interaction, indication = null) {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
}
