package com.namazim

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * The home header ("Harem"): photoreal Mekke/Medine loops from app/src/main/assets/sky/
 * (mekke-gece, mekke-gun, medine-safak, medine-aksam .mp4), one per stretch of the day.
 * The old live shader, photo plates and single-clip modes were removed in Nov 2026.
 */
/** Harem header clip for the moment: Medine at dawn, Mekke by day, Medine at sunset, Mekke at night. */
fun haremClip(now: Long, imsak: Long, gunes: Long, ikindi: Long, aksam: Long): String = when {
    now in imsak until gunes -> "medine-safak.mp4"
    now in gunes until ikindi -> "mekke-gun.mp4"
    now in ikindi until aksam -> "medine-aksam.mp4"
    else -> "mekke-gece.mp4"
}

/** Mekke/Medine header: a photoreal loop chosen by the time of day, cross-fading into the next when the vakit turns. */
@Composable
fun HaremSky(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    val now = rememberNow()   // includes `--el skytime`, which previews the other clips
    val d = dayTimes(lat, lng, java.time.LocalDate.now())
    val clip = haremClip(now, d.imsak, d.gunes, d.ikindi, d.aksam)
    androidx.compose.animation.Crossfade(clip, modifier, androidx.compose.animation.core.tween(1500), label = "harem") {
        SkyVideo("sky/$it", Modifier.fillMaxSize())
    }
}

/** Plays a clip from assets on a TextureView: muted, looping, center-cropped, paused while the app is in the background. */
@Composable
fun SkyVideo(name: String, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val view = remember { TextureView(ctx) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val mp = MediaPlayer()
        var ready = false
        // fill the view like centerCrop: TextureView stretches the frame to its own size by default
        fun crop(w: Int, h: Int) {
            if (!ready || mp.videoWidth == 0 || w == 0 || h == 0) return
            val v = mp.videoWidth / mp.videoHeight.toFloat(); val s = w / h.toFloat()
            view.setTransform(android.graphics.Matrix().apply {
                if (v > s) setScale(v / s, 1f, w / 2f, h / 2f) else setScale(1f, s / v, w / 2f, h / 2f)
            })
        }
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE && ready) runCatching { mp.pause() }
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && ready) runCatching { mp.start() }
        }
        owner.lifecycle.addObserver(obs)
        var surface: Surface? = null
        // assets are compressed in the APK, so MediaPlayer needs a real file. Always re-copy: the asset
        // changes whenever the sky is replaced, and a cached file would silently keep playing the old clip.
        val file = File(ctx.cacheDir, name.replace('/', '_'))
        runCatching { ctx.assets.open(name).use { i -> file.outputStream().use { i.copyTo(it) } } }
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                surface = Surface(st)
                runCatching {
                    mp.setDataSource(file.absolutePath)
                    mp.setSurface(surface)
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    mp.prepare()
                    ready = true
                    crop(w, h)
                    mp.start()
                }
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = crop(w, h)
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                runCatching { mp.release() }
                surface?.release()
                surface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        onDispose {
            owner.lifecycle.removeObserver(obs)
            ready = false
            runCatching { mp.release() }
            surface?.release()
            surface = null
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}


