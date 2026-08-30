package com.f3.workouttimer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f3.workouttimer.data.PaxPhotoStore
import com.f3.workouttimer.ui.theme.F3Black
import com.f3.workouttimer.ui.theme.F3Gray
import com.f3.workouttimer.ui.theme.F3White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SPLASH_MILLIS = 2400

private val LOADING_MESSAGES = listOf(
    "You working out, bro?",
    "The fartsack is not your friend.",
    "It's cold, it's dark, let's go.",
    "Somebody's gotta Q.",
    "Loading mumblechatter…",
    "You'll feel better in 45 minutes.",
    "Leave no man where you found him.",
    "The gloom is calling.",
    "Nobody ever regretted showing up.",
    "Merkins? Merkins.",
    "Counting to 20. Slowly.",
    "Third F is the whole point.",
    "Cinder block sold separately.",
    "Pain is just weakness leaving the AO.",
)

/**
 * Brief interstitial on app open: a random photo of the PAX behind a random
 * bit of encouragement. Tap to skip.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val message = remember { LOADING_MESSAGES.random() }

    val photo by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            PaxPhotoStore.randomBitmap(context)?.asImageBitmap()
        }
    }

    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        delay(SPLASH_MILLIS.toLong())
        finish()
    }

    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = SPLASH_MILLIS),
        label = "splashProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(F3Black)
            .clickableNoRipple(onClick = finish),
    ) {
        photo?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Keep the branding and the message legible over any photo.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                F3Black.copy(alpha = 0.75f),
                                F3Black.copy(alpha = 0.45f),
                                F3Black.copy(alpha = 0.9f),
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            F3Mark(size = 84.dp, fontSize = 34.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "WORKOUT TIMER",
                color = F3White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(40.dp))
            Text(
                text = message,
                color = F3White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                color = F3White,
                trackColor = F3White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
            Text("TAP TO SKIP", color = F3Gray, fontSize = 11.sp, letterSpacing = 2.sp)
        }
    }
}

/** A full-screen tap target shouldn't flash a ripple. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}
