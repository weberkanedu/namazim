package com.namazim

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.SurfaceTexture
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Photo/video versions of the Seher header, so the sky can be a real photograph instead of a shader.
 * Everything is optional and asset driven: drop the files into app/src/main/assets/sky/ and the
 * matching mode picks them up.
 *   day.webp, dusk.webp, night.webp   full-frame plates (same framing, no sun/moon drawn in)
 *   wheat.webp                        looping animated WebP of the field, drawn over the bottom part
 *   scene.mp4                         one looping clip of the whole scene (video mode)
 * The arc, the sun and the moon (with its real phase) are drawn on top, so they still move.
 */
enum class SkyMode { Shader, Photo, Video }

object SkyAssets {
    fun has(ctx: Context, name: String) = runCatching { ctx.assets.open("sky/$name").use { } }.isSuccess

    fun mode(ctx: Context): SkyMode {
        val requested = SkyDebug.mode ?: Store(ctx).skyMode
        return when {
            requested == "video" && has(ctx, "scene.mp4") -> SkyMode.Video
            requested == "photo" && has(ctx, "day.webp") -> SkyMode.Photo
            else -> SkyMode.Shader
        }
    }
}

private class Plates(val day: ImageBitmap, val dusk: ImageBitmap, val night: ImageBitmap)

private fun loadPlates(ctx: Context): Plates? = runCatching {
    fun one(name: String) = ctx.assets.open("sky/$name").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    Plates(one("day.webp"), one("dusk.webp"), one("night.webp"))
}.getOrNull()

private fun loadWheat(ctx: Context): AnimatedImageDrawable? = runCatching {
    if (!SkyAssets.has(ctx, "wheat.webp")) return null
    val src = ImageDecoder.createSource(ctx.assets, "sky/wheat.webp")
    (ImageDecoder.decodeDrawable(src) as AnimatedImageDrawable).apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        start()
    }
}.getOrNull()

/** The sun/moon path, shared with the shader: an arc from the left horizon to the right one. */
private const val ARC_PEAK = 0.31f
private const val HZ_UV = 0.42f

private fun arcPoint(s: Float, w: Float, h: Float): Offset {
    val y = HZ_UV + ARC_PEAK * sin(PI * s.coerceIn(0f, 1f).toDouble()).toFloat()
    return Offset((0.05f + 0.90f * s.coerceIn(0f, 1f)) * w, (1f - y) * h)
}

private fun sToUv(az: Double) = (az / PI + 0.5).toFloat().coerceIn(0f, 1f)

@Composable
fun SkyPhoto(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val now = rememberNow()
    val minute = now / 60_000
    val plates = remember { loadPlates(ctx) }
    val wheat = remember { loadWheat(ctx) }
    val bodies = remember(minute, lat, lng) { skyBodies(lat, lng, now + SkyDebug.offsetMs) }
    Canvas(modifier.clipToBounds()) {
        if (plates == null) return@Canvas
        val alt = bodies.sun.alt * 180 / PI
        val wDay = smoothstep(2f, 12f, alt.toFloat())
        val wNight = 1f - smoothstep(-10f, -2f, alt.toFloat())
        val wDusk = (1f - wDay - wNight).coerceIn(0f, 1f)
        drawPlate(plates.night, wNight)
        drawPlate(plates.dusk, wDusk)
        drawPlate(plates.day, wDay)
        drawCelestial(bodies)
        wheat?.let { d ->
            drawIntoCanvas { c ->
                d.setBounds(0, (size.height * 0.58f).toInt(), size.width.toInt(), size.height.toInt())
                d.draw(c.nativeCanvas)
            }
        }
    }
}

private fun DrawScope.drawPlate(img: ImageBitmap, alpha: Float) {
    if (alpha <= 0.01f) return
    // cover: keep the plate's aspect, crop the overflow
    val scale = maxOf(size.width / img.width, size.height / img.height)
    val w = img.width * scale
    val h = img.height * scale
    drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), 0),
        dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()), alpha = alpha)
}

private fun DrawScope.drawCelestial(b: SkyBodies) {
    val w = size.width
    val h = size.height
    // the path, clipped to the sky so it does not run across the wheat
    val arc = Path()
    val n = 64
    for (i in 0..n) {
        val p = arcPoint(i / n.toFloat(), w, h)
        if (i == 0) arc.moveTo(p.x, p.y) else arc.lineTo(p.x, p.y)
    }
    clipRect(0f, 0f, w, (1f - HZ_UV) * h) {
        drawPath(arc, Color(0x44FFFFFF), style = Stroke(width = 1.2f * density))
    }

    // the sun
    val sunUp = b.sun.alt > 0
    val sunS = sToUv(b.sun.az)
    val sunPos = arcPoint(sunS, w, h)
    if (sunUp) {
        val low = 1f - smoothstep(0.0f, 0.25f, minOf(sunS, 1f - sunS))
        val warm = lerpColor(Color(0xFFFFB066), Color(0xFFFFF6E8), 1f - low)
        drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = 0.55f), Color.Transparent), center = sunPos, radius = 0.28f * h),
            radius = 0.28f * h, center = sunPos)
        drawCircle(Brush.radialGradient(listOf(Color.White, warm.copy(alpha = 0.85f), Color.Transparent), center = sunPos, radius = 0.09f * h),
            radius = 0.09f * h, center = sunPos)
        drawCircle(Color(0xFFFFF6E4), radius = 0.021f * h, center = sunPos)
    }

    // the moon, with the real phase
    val moonUp = b.moon.alt > 0
    if (moonUp) {
        val moonPos = arcPoint(sToUv(b.moon.az), w, h)
        val r = 0.030f * h
        val illum = (0.5 + 0.5 * cos(b.moonPhase)).toFloat()
        drawCircle(Brush.radialGradient(listOf(Color(0x33EAF0FF), Color.Transparent), center = moonPos, radius = r * 3.2f),
            radius = r * 3.2f, center = moonPos)
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            val layer = android.graphics.Paint()
            canvas.saveLayer(0f, 0f, w, h, layer)
            drawCircle(Color(0xFFF7F2E6), radius = r, center = moonPos)
            // cut the dark side: the terminator faces away from the sun
            val toSun = (sunPos - moonPos).let { d ->
                val len = maxOf(d.getDistance(), 1f)
                Offset(d.x / len, d.y / len)
            }
            val shadow = moonPos - toSun * (2.1f * r * (1f - illum))
            drawCircle(Color.Transparent, radius = r * 1.02f, center = shadow, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            canvas.restore()
        }
    }
}

/** Just the sun/moon path, drawn over the video (the video carries its own sun and light). */
@Composable
fun SkyArc(lat: Double, lng: Double, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val minute = now / 60_000
    val bodies = remember(minute, lat, lng) { skyBodies(lat, lng, now + SkyDebug.offsetMs) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val arc = Path()
        for (i in 0..64) {
            val p = arcPoint(i / 64f, w, h)
            if (i == 0) arc.moveTo(p.x, p.y) else arc.lineTo(p.x, p.y)
        }
        clipRect(0f, 0f, w, (1f - HZ_UV) * h) {
            drawPath(arc, Color(0x3DFFFFFF), style = Stroke(width = 1.2f * density))
        }
        // a small bright dot for the sun/moon so the arc has something riding it
        if (bodies.sun.alt > 0) {
            val p = arcPoint(sToUv(bodies.sun.az), w, h)
            drawCircle(Color(0x66FFF3DC), radius = 3.5f * density, center = p)
        }
    }
}

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t, green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t, alpha = 1f,
)

/** Plays sky/scene.mp4 on a TextureView, muted and looping. */
@Composable
fun SkyVideo(name: String, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val view = remember { TextureView(ctx) }
    DisposableEffect(Unit) {
        val mp = MediaPlayer()
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
                    mp.start()
                }
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                runCatching { mp.release() }
                surface?.release()
                surface = null
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        onDispose {
            runCatching { mp.release() }
            surface?.release()
            surface = null
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}


