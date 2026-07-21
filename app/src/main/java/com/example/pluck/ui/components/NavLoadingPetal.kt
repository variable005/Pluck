package com.example.pluck.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pluck.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small animated petal that emerges from the floating-navigation area while Pluck writes a
 * story.
 *
 * Motion is handled by Compose so Android's system animator-duration setting remains respected.
 */
@Composable
fun NavLoadingPetal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    imageSize: Dp = 52.dp,
    risesFromNavigation: Boolean = false
) {
    val lift = with(LocalDensity.current) { 6.dp.toPx() }
    val transition = rememberInfiniteTransition(label = "navLoadingPetal")
    val sway by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navLoadingPetalSway"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navLoadingPetalPulse"
    )

    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(520, easing = FastOutSlowInEasing)) {
            it * if (risesFromNavigation) 12 else 2
        },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it }
    ) {
        Image(
            painter = painterResource(R.drawable.pluck_loading_petal),
            contentDescription = null,
            modifier = Modifier
                .size(imageSize)
                .graphicsLayer {
                    translationY = -lift * sway
                    rotationZ = -7f + (sway * 14f)
                    scaleX = pulse
                    scaleY = pulse
                }
        )
    }
}

/** A petal loading treatment where layered petals spiral inward from the screen corners. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalConvergenceProgress(
    modifier: Modifier = Modifier,
    onFirstConvergence: (() -> Unit)? = null
) {
    val transition = rememberInfiniteTransition(label = "petalConvergence")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "petalConvergencePhase"
    )
    var hasSignalledConvergence by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (!hasSignalledConvergence && phase >= 0.76f) {
            hasSignalledConvergence = true
            onFirstConvergence?.invoke()
        }
    }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val cornerX = with(density) { (maxWidth / 2 - 32.dp).toPx() }
        val cornerY = with(density) { (maxHeight / 2 - 48.dp).toPx() }
        val spiralRadius = with(density) { 68.dp.toPx() }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Plucking today's story",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                modifier = Modifier.size(128.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 3.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { phase },
                        modifier = Modifier.size(96.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f),
                        strokeWidth = 9.dp,
                        gapSize = 6.dp
                    )
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        tonalElevation = 2.dp
                    ) {}
                }
            }
        }

        repeat(12) { index ->
            val corner = index % 4
            val layer = index / 4
            val localPhase = (phase + index * 0.07f) % 1f
            val convergence = FastOutSlowInEasing.transform((localPhase / 0.8f).coerceIn(0f, 1f))
            val cornerXDirection = if (corner == 0 || corner == 2) -1f else 1f
            val cornerYDirection = if (corner < 2) -1f else 1f
            val angle = (localPhase * PI.toFloat() * (2.5f + layer * 0.25f)) + (corner * PI.toFloat() / 2f)
            val remaining = 1f - convergence
            Image(
                painter = painterResource(R.drawable.pluck_loading_petal),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(58.dp)
                    .graphicsLayer {
                        translationX = (cornerXDirection * cornerX * remaining) + (sin(angle) * (spiralRadius + layer * 12.dp.toPx()) * remaining)
                        translationY = (cornerYDirection * cornerY * remaining) + (cos(angle) * (spiralRadius + layer * 12.dp.toPx()) * remaining)
                        rotationZ = (corner * 90f) + (localPhase * (720f + layer * 120f))
                        scaleX = 0.48f + (0.42f * convergence)
                        scaleY = scaleX
                        alpha = 0.1f + (0.8f * remaining)
                    }
            )
        }
    }
}
