package com.namazim

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.*

// ---------------------------------------------------------------- Astronomy (low precision, plenty for a sky picture)

private const val D2R = PI / 180

/** Altitude (rad) and azimuth from south, positive west (rad). */
data class AltAz(val alt: Double, val az: Double)

class SkyBodies(val sun: AltAz, val moon: AltAz, val moonPhase: Double)

fun skyBodies(lat: Double, lng: Double, millis: Long): SkyBodies {
    val n = millis / 86_400_000.0 + 2440587.5 - 2451545.0
    val eps = (23.439 - 0.0000004 * n) * D2R
    // Sun
    val g = (357.528 + 0.9856003 * n) * D2R
    val ls = (280.460 + 0.9856474 * n + 1.915 * sin(g) + 0.020 * sin(2 * g)) * D2R
    val sunRa = atan2(cos(eps) * sin(ls), cos(ls))
    val sunDec = asin(sin(eps) * sin(ls))
    // Moon
    val lp = (218.316 + 13.176396 * n) * D2R
    val mp = (134.963 + 13.064993 * n) * D2R
    val f = (93.272 + 13.229350 * n) * D2R
    val lm = lp + 6.289 * D2R * sin(mp)
    val bm = 5.128 * D2R * sin(f)
    val moonDec = asin(sin(bm) * cos(eps) + cos(bm) * sin(eps) * sin(lm))
    val moonRa = atan2(sin(lm) * cos(eps) - tan(bm) * sin(eps), cos(lm))
    val elong = acos(cos(bm) * cos(lm - ls))

    val lst = (280.46061837 + 360.98564736629 * n + lng) * D2R
    fun altAz(ra: Double, dec: Double): AltAz {
        val h = lst - ra
        val phi = lat * D2R
        val alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
        val az = atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi))
        return AltAz(alt, az)
    }
    // +1.9° on the sun: refraction plus Diyanet's temkin, so the disc meets the horizon at güneş/akşam.
    val sun = altAz(sunRa, sunDec).let { it.copy(alt = it.alt + 1.9 * D2R) }
    return SkyBodies(sun, altAz(moonRa, moonDec), PI - elong)
}

// Azimuth is squeezed so sunrise and sunset stay on screen.
private fun viewDir(a: AltAz, altScale: Double): FloatArray {
    val el = a.alt * altScale
    val az = a.az * 0.5
    return floatArrayOf((sin(az) * cos(el)).toFloat(), sin(el).toFloat(), (-cos(az) * cos(el)).toFloat())
}

// ---------------------------------------------------------------- Shader (AGSL port of the design canvas sky)

private const val SKY_SHADER = """
uniform float2 uRes;
uniform float uTime;
uniform float3 uSun;
uniform float3 uMoon;
uniform float uPhase;
uniform float uStarRot;
uniform float4 uMeteor;
uniform float4 uPlane;
uniform float4 uBirds;

const float PI = 3.14159265;
const float HZ = 0.17;
const float EL_SCALE = 1.05;
const float AZ_SCALE = 1.365;

float hash(float2 p) { p = fract(p * float2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float hash3(float3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float noise(float2 p) {
  // quintic fade: no visible grid creases in the clouds
  float2 i = floor(p); float2 f = fract(p); float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x), mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}
float fbm(float2 p) {
  float v = 0.0; float a = 0.5; float2x2 m = float2x2(1.6, 1.2, -1.2, 1.6);
  for (int i = 0; i < 6; i++) { v += a * noise(p); p = m * p; a *= 0.5; }
  return v;
}
float fbm3(float2 p) {
  float v = 0.0; float a = 0.5; float2x2 m = float2x2(1.6, 1.2, -1.2, 1.6);
  for (int i = 0; i < 3; i++) { v += a * noise(p); p = m * p; a *= 0.5; }
  return v;
}
float2 rsi(float3 r0, float3 rd, float sr) {
  float a = dot(rd, rd); float b = 2.0 * dot(rd, r0); float c = dot(r0, r0) - sr * sr;
  float d = b * b - 4.0 * a * c;
  if (d < 0.0) return float2(1e5, -1e5);
  return float2((-b - sqrt(d)) / (2.0 * a), (-b + sqrt(d)) / (2.0 * a));
}
float3 atmosphere(float3 r, float3 pSun, float iSun) {
  const float rPlanet = 6371e3; const float rAtmos = 6471e3;
  float3 r0 = float3(0.0, 6372e3, 0.0);
  const float3 kRlh = float3(5.5e-6, 13.0e-6, 22.4e-6);
  const float kMie = 21e-6; const float shRlh = 8e3; const float shMie = 1.2e3; const float g = 0.758;
  float2 p = rsi(r0, r, rAtmos);
  if (p.x > p.y) return float3(0.0);
  float ground = rsi(r0, r, rPlanet).x;
  if (ground > 0.0) p.y = min(p.y, ground);
  float iStep = (p.y - max(p.x, 0.0)) / 12.0;
  float iTime = max(p.x, 0.0);
  float3 totR = float3(0.0); float3 totM = float3(0.0);
  float iOdR = 0.0; float iOdM = 0.0;
  float mu = dot(r, pSun); float mumu = mu * mu; float gg = g * g;
  float pR = 3.0 / (16.0 * PI) * (1.0 + mumu);
  float pM = 3.0 / (8.0 * PI) * ((1.0 - gg) * (mumu + 1.0)) / (pow(1.0 + gg - 2.0 * mu * g, 1.5) * (2.0 + gg));
  for (int i = 0; i < 12; i++) {
    float3 iPos = r0 + r * (iTime + iStep * 0.5);
    float iH = length(iPos) - rPlanet;
    float odR = exp(-iH / shRlh) * iStep; float odM = exp(-iH / shMie) * iStep;
    iOdR += odR; iOdM += odM;
    float jStep = rsi(iPos, pSun, rAtmos).y / 6.0;
    float jT = 0.0; float jOdR = 0.0; float jOdM = 0.0;
    for (int j = 0; j < 6; j++) {
      float3 jPos = iPos + pSun * (jT + jStep * 0.5);
      float jH = max(length(jPos) - rPlanet, -2000.0);
      jOdR += exp(-jH / shRlh) * jStep; jOdM += exp(-jH / shMie) * jStep;
      jT += jStep;
    }
    float3 attn = exp(-(kMie * (iOdM + jOdM) + kRlh * (iOdR + jOdR)));
    totR += odR * attn; totM += odM * attn;
    iTime += iStep;
  }
  return iSun * (pR * kRlh * totR + pM * kMie * totM);
}
float3 dirFrom(float az, float el) { return float3(sin(az) * cos(el), sin(el), -cos(az) * cos(el)); }
float3 sunLight(float3 s) {
  float am = 1.0 / (max(s.y, -0.05) + 0.12);
  return exp(-float3(0.17, 0.40, 0.90) * am * 0.35) * smoothstep(-0.06, 0.03, s.y);
}
float segDist(float2 p, float2 a, float2 b) { float2 pa = p - a; float2 ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }

half4 main(float2 fc) {
  float2 uv = float2(fc.x / uRes.x, 1.0 - fc.y / uRes.y);
  float az = (uv.x - 0.5) * AZ_SCALE;
  float el = (uv.y - HZ) * EL_SCALE;
  float3 rd = dirFrom(az, el);
  float3 rdSky = dirFrom(az, max(el, 0.012));
  float pix = EL_SCALE / uRes.y;
  float dayF = smoothstep(-0.14, 0.2, uSun.y);
  float nightF = 1.0 - smoothstep(-0.2, -0.04, uSun.y);
  float3 sl = sunLight(uSun);

  float3 col = atmosphere(rdSky, uSun, 22.0);
  col += float3(0.0006, 0.0010, 0.0024) * (1.0 + 2.0 * smoothstep(0.3, 0.0, el));
  float moonUp = smoothstep(-0.05, 0.1, uMoon.y);
  float illum = 0.5 + 0.5 * cos(uPhase);
  float dm = acos(clamp(dot(rdSky, uMoon), -1.0, 1.0));
  col += float3(0.6, 0.7, 0.9) * (exp(-dm * 9.0) * 0.004 + exp(-dm * 60.0) * 0.02) * illum * moonUp;

  float ds = acos(clamp(dot(rd, uSun), -1.0, 1.0));
  float sunR = 0.025;
  float disc = smoothstep(sunR, sunR - 1.5 * pix, ds);
  float limb = sqrt(max(0.0, 1.0 - (ds * ds) / (sunR * sunR)));
  col += disc * sl * (25.0 * (0.6 + 0.4 * limb)) * step(0.0, el);
  float aboveH = smoothstep(-0.01, 0.02, el);
  float glare = 1.6 * exp(-ds * 55.0) + 0.45 * exp(-ds * 14.0) + 0.08 * exp(-ds * 4.0);
  float ang = atan(dot(rd - uSun, normalize(cross(uSun, float3(0.0, 1.0, 0.0)))), rd.y - uSun.y);
  float rays = pow(abs(cos(ang * 4.0)), 60.0) * exp(-ds * 22.0) * 0.5;
  col += sl * (glare + rays) * aboveH * smoothstep(-0.02, 0.15, uSun.y);

  float moonR = 0.032;
  float4 moonC = float4(0.0);
  if (dm < moonR * 1.2) {
    float3 m = uMoon;
    float3 right = normalize(cross(m, float3(0.0, 1.0, 0.0)));
    float3 up = cross(right, m);
    float2 q = float2(dot(rd - m, right), dot(rd - m, up)) / moonR;
    float r2 = dot(q, q);
    if (r2 < 1.0) {
      float3 n = float3(q, sqrt(1.0 - r2));
      float3 st = uSun - dot(uSun, m) * m;
      float2 sd = normalize(float2(dot(st, right), dot(st, up)) + 1e-5);
      float3 sv = float3(sd * sin(uPhase), cos(uPhase));
      float lit = smoothstep(-0.04, 0.12, dot(n, sv));
      float maria = smoothstep(0.42, 0.68, fbm(q * 2.3 + 4.0));
      float albedo = 0.85 - 0.32 * maria - 0.12 * smoothstep(0.55, 0.8, fbm(q * 9.0));
      float edge = smoothstep(1.0, 1.0 - 2.5 * pix / moonR, sqrt(r2));
      float3 ext = mix(float3(1.0, 0.75, 0.5), float3(1.0), smoothstep(0.0, 0.25, m.y));
      moonC = float4(float3(1.0, 0.97, 0.92) * ext * albedo * (lit * 0.16 + 0.004), edge);
    }
  }
  col = col * (1.0 - moonC.a * moonUp * 0.85 * nightF) + moonC.rgb * moonC.a * moonUp * (1.0 + 2.0 * dayF);

  float cloudA = 0.0;
  if (el > 0.0) {
    // +0.12 keeps the cloud layer from stretching into streaks near the horizon;
    // the warp bends the noise into soft, billowy shapes instead of straight shards.
    float2 cp = rdSky.xz / (rdSky.y + 0.12) * 0.55 + float2(uTime * 0.003, uTime * 0.0009);
    float2 w = float2(fbm3(cp * 0.7 + 3.1), fbm3(cp * 0.7 + 7.7));
    float2 q = cp * 1.2 + w * 0.9;
    float n1 = fbm(q);
    float dens = smoothstep(0.50, 0.86, n1);
    if (dens > 0.0) {
      float n2 = fbm(q + normalize(uSun.xz + 1e-4) * 0.15);
      float light = clamp(0.8 + (n1 - n2) * 2.5, 0.35, 1.05);
      float mu = dot(rdSky, uSun);
      float silver = pow(max(mu, 0.0), 12.0) * 2.5;
      float3 amb = mix(float3(0.0008, 0.001, 0.0018), float3(0.18, 0.22, 0.30), dayF);
      float3 cc = amb + sl * (1.25 * light + silver) * dayF + float3(0.7, 0.8, 1.0) * 0.006 * illum * moonUp;
      cloudA = dens * smoothstep(0.02, 0.3, el) * 0.9;
      col = mix(col, cc, cloudA);
    }
  }

  float skyLum = dot(col, float3(0.3, 0.5, 0.2));
  float starVis = clamp(1.0 - skyLum * 60.0, 0.0, 1.0) * (1.0 - cloudA) * smoothstep(0.0, 0.05, el);
  float3 starCol = float3(0.0);
  if (starVis > 0.0) {
    float c = cos(uStarRot); float s = sin(uStarRot);
    float3 d = float3(c * rd.x - s * rd.z, rd.y, s * rd.x + c * rd.z);
    float3 gpos = d * 200.0; float3 id = floor(gpos); float3 f = fract(gpos) - 0.5;
    float h = hash3(id);
    if (h > 0.982) {
      float tw = 0.65 + 0.35 * sin(uTime * (1.5 + h * 6.0) + h * 50.0);
      float b = smoothstep(0.42, 0.0, length(f)) * tw * (0.35 + 1.6 * (h - 0.982) / 0.018);
      starCol = b * mix(float3(1.0, 0.86, 0.72), float3(0.78, 0.88, 1.0), fract(h * 91.7)) * starVis;
    }
  }
  if (uMeteor.z > 0.0) {
    float2 dir = normalize(float2(1.0, -0.5));
    float2 head = uMeteor.xy + dir * uMeteor.w * 0.45;
    float2 tail = head - dir * 0.16;
    float2 P = uv * uRes; float2 H = head * uRes; float2 T = tail * uRes;
    float dd = segDist(P, T, H);
    float along = clamp(dot(P - T, H - T) / dot(H - T, H - T), 0.0, 1.0);
    float fade = sin(clamp(uMeteor.w, 0.0, 1.0) * PI);
    starCol += float3(0.9, 0.95, 1.0) * exp(-dd * dd * 0.8) * along * along * fade * 1.4 * starVis;
  }

  // Hills and the mosque only exist near the horizon; skip the noise work everywhere else.
  if (el < 0.12) {
  float far = 0.012 + 0.024 * fbm(float2(az * 4.0, 7.0));
  float near = -0.006 + 0.034 * fbm(float2(az * 2.1 + 3.0, 1.0));
  float3 haze = col;
  float3 farCol = mix(haze, float3(0.012, 0.016, 0.026) * (0.4 + dayF * 3.0), 0.32);
  float3 nearCol = mix(haze, float3(0.006, 0.008, 0.012) * (0.4 + dayF * 4.0), 0.72);
  float mx = 0.27;
  float base = 0.012 + 0.024 * fbm(float2(mx * 4.0, 7.0)) - 0.002;
  float2 mp = float2(az - mx, el - base);
  float dome = length(mp - float2(0.0, 0.016)) - 0.024;
  dome = max(dome, -(mp.y - 0.016));
  float drum = max(abs(mp.x) - 0.034, abs(mp.y - 0.008) - 0.010);
  float fin = max(abs(mp.x) - 0.0012, abs(mp.y - 0.047) - 0.006);
  float body = min(min(dome, drum), fin);
  for (int k = 0; k < 2; k++) {
    float sx = k == 0 ? -0.05 : 0.05;
    float2 q = mp - float2(sx, 0.0);
    float shaft = max(abs(q.x) - 0.0032, abs(q.y - 0.04) - 0.04);
    float cone = max(abs(q.x) - 0.0032 * clamp((0.098 - q.y) / 0.018, 0.0, 1.0), abs(q.y - 0.089) - 0.009);
    float bal = max(abs(q.x) - 0.0055, abs(q.y - 0.066) - 0.0018);
    body = min(body, min(min(shaft, cone), bal));
  }
  float mosque = smoothstep(pix, -pix, body);
  float farMask = max(smoothstep(far + pix, far - pix, el), mosque);
  col = mix(col, farCol, farMask);
  float win = step(abs(mp.y - 0.006), 0.0028) * step(abs(mp.x), 0.028) * step(0.5, fract((mp.x + 0.028) / 0.008));
  col += float3(1.0, 0.75, 0.4) * 0.03 * win * mosque * nightF;
  float nearMask = smoothstep(near + pix, near - pix, el);
  col = mix(col, nearCol, nearMask);
  float3 groundCol = float3(0.0025, 0.003, 0.0045) * (0.5 + dayF * 6.0) * (0.55 + 0.6 * fbm(float2(az * 18.0, el * 60.0)));
  col = mix(col, groundCol, smoothstep(-0.005, -0.06, el) * nearMask);
  }

  if (uPlane.z > 0.0) {
    float2 P = uv * uRes; float2 A = uPlane.xy * uRes;
    float behind = (A.x - P.x) * uPlane.w;
    float trail = exp(-pow((P.y - A.y) / (0.8 + max(behind, 0.0) * 0.01), 2.0)) * step(0.0, behind)
      * exp(-max(behind, 0.0) / 110.0) * (1.0 - cloudA);
    float3 tc = sl * 1.3 + float3(0.15, 0.18, 0.24);
    col = mix(col, tc, trail * 0.5 * dayF);
    float body2 = exp(-dot(P - A, P - A) * 0.9) * (1.0 - cloudA);
    col = mix(col, float3(0.06, 0.065, 0.075), body2 * 0.8 * dayF);
  }
  if (uBirds.z > 0.0) {
    float2 P = uv * uRes;
    for (int i = 0; i < 6; i++) {
      float fi = float(i);
      float2 c = uBirds.xy * uRes + float2(-fi * 13.0 + mod(fi, 2.0) * 4.0, sin(fi * 2.3) * 7.0 + fi * 1.5);
      float flap = sin(uTime * 9.0 + fi * 1.7) * 2.2;
      float w = 4.0 + mod(fi, 3.0);
      float d1 = segDist(P, c, c + float2(-w, 1.5 + flap));
      float d2 = segDist(P, c, c + float2(w, 1.5 + flap));
      float bird = smoothstep(0.9, 0.2, min(d1, d2));
      col = mix(col, float3(0.004, 0.005, 0.008), bird * 0.85);
    }
  }

  float exposure = mix(9.0, 1.6, smoothstep(-0.13, 0.03, uSun.y));
  col = 1.0 - exp(-col * exposure);
  col += starCol;
  col = pow(clamp(col, 0.0, 1.0), float3(1.0 / 2.2));
  col += (hash(uv * uRes + fract(uTime)) - 0.5) / 255.0;
  return half4(half3(col), 1.0);
}
"""

/** Shooting stars, planes and birds come and go on their own schedule. */
private class SkyEvents {
    var meteorStart = -1L; var meteorX = 0f; var meteorY = 0f
    var planeStart = -1L; var planeY = 0f; var planeDir = 1f; var lastPlane = 0L

    fun meteor(now: Long, dark: Boolean): FloatArray {
        if (meteorStart >= 0 && now - meteorStart > 900) meteorStart = -1
        if (meteorStart < 0 && dark && Math.random() < 0.003) {
            meteorStart = now; meteorX = (0.15 + Math.random() * 0.5).toFloat(); meteorY = (0.75 + Math.random() * 0.18).toFloat()
        }
        return if (meteorStart < 0) FloatArray(4) else floatArrayOf(meteorX, meteorY, 1f, (now - meteorStart) / 900f)
    }

    fun plane(now: Long, day: Boolean): FloatArray {
        if (planeStart >= 0 && (now - planeStart > 14_000 || !day)) planeStart = -1
        // shortcut: random cadence (a plane every minute or so); real flight paths would need live data.
        if (planeStart < 0 && day && now - lastPlane > 25_000 && Math.random() < 0.0012) {
            planeStart = now; lastPlane = now
            planeY = (0.55 + Math.random() * 0.3).toFloat(); planeDir = if (Math.random() < 0.5) 1f else -1f
        }
        if (planeStart < 0) return FloatArray(4)
        val u = 0.5f + planeDir * (-0.58f + (now - planeStart) / 14_000f * 1.16f)
        return floatArrayOf(u, planeY, 1f, planeDir)
    }

    fun birds(now: Long, active: Boolean): FloatArray =
        if (!active) FloatArray(4)
        else floatArrayOf(((now / 1000.0) % 26 / 26 * 1.4 - 0.15).toFloat(), (0.42 + 0.04 * sin(now / 3000.0)).toFloat(), 1f, 0f)
}

/**
 * Live sky for the Seher theme. Long-press runs the day fast (a day per minute) to preview it.
 * Rendered at a third of the screen resolution and scaled up: the sky is soft, the GPU stays cool.
 */
@Composable
fun SkyView(lat: Double, lng: Double, modifier: Modifier = Modifier, onFastChange: (Boolean) -> Unit = {}) {
    // A shader that fails to compile on some GPU must not take the app down: fall back to a plain sky.
    val shader = remember {
        if (Build.VERSION.SDK_INT < 33) null
        else runCatching { RuntimeShader(SKY_SHADER) }.onFailure { android.util.Log.e("Namazim", "sky shader", it) }.getOrNull()
    }
    if (shader == null) {
        Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF1B2C52), Color(0xFF6B4F7A), Pal.Ground))))
        return
    }
    SkyShaderView(shader, lat, lng, modifier, onFastChange)
}

@RequiresApi(33)
@Composable
private fun SkyShaderView(shader: RuntimeShader, lat: Double, lng: Double, modifier: Modifier, onFastChange: (Boolean) -> Unit) {
    val brush = remember { ShaderBrush(shader) }
    val events = remember { SkyEvents() }
    var frame by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fastFrom by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { }
            frame = System.currentTimeMillis()
            kotlinx.coroutines.delay(70) // ~12 fps is plenty for slow clouds and keeps the GPU mostly idle
        }
    }

    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                fastFrom = if (fastFrom < 0) System.currentTimeMillis() else -1L
                onFastChange(fastFrom >= 0)
            })
        },
    ) {
        val scale = 1f
        Box(
            Modifier
                .size(maxWidth / scale, maxHeight / scale)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    val now = frame
                    val skyTime = if (fastFrom >= 0) fastFrom + (now - fastFrom) * 1440 else now
                    val b = skyBodies(lat, lng, skyTime)
                    val sunAlt = b.sun.alt / D2R
                    shader.setFloatUniform("uRes", size.width, size.height)
                    shader.setFloatUniform("uTime", (now % 1_000_000) / 1000f)
                    shader.setFloatUniform("uSun", viewDir(b.sun, 1.0))
                    shader.setFloatUniform("uMoon", viewDir(b.moon, 0.62))
                    shader.setFloatUniform("uPhase", b.moonPhase.toFloat())
                    shader.setFloatUniform("uStarRot", ((skyTime % 86_164_000) / 86_164_000.0 * 2 * PI).toFloat())
                    shader.setFloatUniform("uMeteor", events.meteor(now, sunAlt < -12))
                    shader.setFloatUniform("uPlane", events.plane(now, sunAlt > 8))
                    shader.setFloatUniform("uBirds", events.birds(now, sunAlt > -4 && sunAlt < 9))
                    drawRect(brush)
                },
        )
    }
}
