package com.lucho314.spotter.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import coil3.compose.AsyncImage
import com.lucho314.spotter.core.designsystem.theme.SpotterColors

/**
 * `exercises.gif_url` is mislabeled in the DB: despite the name, 53 of 104 rows are `.mp4` video
 * URLs (RN bug 16, `components/exercises/exercise-gif.tsx:28`: passing them straight to an `Image`
 * component always rendered the placeholder, since RN's `Image` can't play video).
 */
fun isVideoUrl(url: String): Boolean {
    val withoutQuery = url.substringBefore('?').lowercase()
    return withoutQuery.endsWith(".mp4") || withoutQuery.endsWith(".webm") || withoutQuery.endsWith(".m3u8")
}

/**
 * Shows an exercise's media: a looping, muted video for [isVideoUrl] URLs (from [mediaUrl], i.e.
 * `gif_url`); otherwise an image (Coil, with GIF support wired up via the app's
 * `coil3.SingletonImageLoader.Factory`) - [mediaUrl] itself first when it's a still (usually an
 * actual animated `.gif`, richer than a static [imageUrl]), falling back to [imageUrl], and
 * finally to a placeholder icon when neither is usable. If the video fails to play (bad/expired
 * URL, unsupported codec), this falls back to [imageUrl] instead of showing nothing.
 */
@Composable
fun ExerciseMedia(
    mediaUrl: String?,
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val videoUrl = mediaUrl?.takeIf { isVideoUrl(it) }
    val stillUrl = mediaUrl?.takeIf { !isVideoUrl(it) } ?: imageUrl
    var videoFailed by remember(videoUrl) { mutableStateOf(false) }

    Box(modifier = modifier.background(SpotterColors.SurfaceContainer), contentAlignment = Alignment.Center) {
        when {
            videoUrl != null && !videoFailed -> LoopingVideo(url = videoUrl, onError = { videoFailed = true }, modifier = Modifier.fillMaxSize())
            stillUrl != null -> AsyncImage(model = stillUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            else -> Icon(
                imageVector = Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = SpotterColors.OnSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun LoopingVideo(url: String, onError: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player, onError) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onError()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    PlayerSurface(player = player, modifier = modifier)
}
