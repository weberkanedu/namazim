// Physically based sky for Namazım (Seher theme). Shared by the test page and the design artboard.
var SKY_FS = `
precision highp float;
uniform vec2 uRes;
uniform float uTime;
uniform vec3 uSun;
uniform vec3 uMoon;
uniform float uPhase;
uniform float uStarRot;
uniform vec4 uMeteor;
uniform vec4 uPlane;
uniform vec4 uBirds;
varying vec2 vUv;

#define PI 3.14159265
const float HZ = 0.17;     // horizon height in uv
const float EL_SCALE = 1.05;
const float AZ_SCALE = 1.365;

float hash(vec2 p){ p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float hash3(vec3 p){ p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float noise(vec2 p){
  vec2 i = floor(p), f = fract(p); vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
float fbm(vec2 p){
  float v = 0.0, a = 0.5; mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
  for (int i = 0; i < 6; i++) { v += a * noise(p); p = m * p; a *= 0.5; }
  return v;
}

vec2 rsi(vec3 r0, vec3 rd, float sr){
  float a = dot(rd, rd), b = 2.0 * dot(rd, r0), c = dot(r0, r0) - sr * sr;
  float d = b * b - 4.0 * a * c;
  if (d < 0.0) return vec2(1e5, -1e5);
  return vec2((-b - sqrt(d)) / (2.0 * a), (-b + sqrt(d)) / (2.0 * a));
}

// Single-scattering Rayleigh + Mie atmosphere.
vec3 atmosphere(vec3 r, vec3 pSun, float iSun){
  const float rPlanet = 6371e3, rAtmos = 6471e3;
  vec3 r0 = vec3(0.0, 6372e3, 0.0);
  const vec3 kRlh = vec3(5.5e-6, 13.0e-6, 22.4e-6);
  const float kMie = 21e-6, shRlh = 8e3, shMie = 1.2e3, g = 0.758;
  vec2 p = rsi(r0, r, rAtmos);
  if (p.x > p.y) return vec3(0.0);
  float ground = rsi(r0, r, rPlanet).x;
  if (ground > 0.0) p.y = min(p.y, ground);
  float iStep = (p.y - max(p.x, 0.0)) / 16.0;
  float iTime = max(p.x, 0.0);
  vec3 totR = vec3(0.0), totM = vec3(0.0);
  float iOdR = 0.0, iOdM = 0.0;
  float mu = dot(r, pSun), mumu = mu * mu, gg = g * g;
  float pR = 3.0 / (16.0 * PI) * (1.0 + mumu);
  float pM = 3.0 / (8.0 * PI) * ((1.0 - gg) * (mumu + 1.0)) / (pow(1.0 + gg - 2.0 * mu * g, 1.5) * (2.0 + gg));
  for (int i = 0; i < 16; i++) {
    vec3 iPos = r0 + r * (iTime + iStep * 0.5);
    float iH = length(iPos) - rPlanet;
    float odR = exp(-iH / shRlh) * iStep, odM = exp(-iH / shMie) * iStep;
    iOdR += odR; iOdM += odM;
    float jStep = rsi(iPos, pSun, rAtmos).y / 8.0;
    float jT = 0.0, jOdR = 0.0, jOdM = 0.0;
    for (int j = 0; j < 8; j++) {
      vec3 jPos = iPos + pSun * (jT + jStep * 0.5);
      float jH = max(length(jPos) - rPlanet, -2000.0);
      jOdR += exp(-jH / shRlh) * jStep; jOdM += exp(-jH / shMie) * jStep;
      jT += jStep;
    }
    vec3 attn = exp(-(kMie * (iOdM + jOdM) + kRlh * (iOdR + jOdR)));
    totR += odR * attn; totM += odM * attn;
    iTime += iStep;
  }
  return iSun * (pR * kRlh * totR + pM * kMie * totM);
}

vec3 dirFrom(float az, float el){ return vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el)); }

// Sunlight left after passing through the air (warm and dim near the horizon).
vec3 sunLight(vec3 s){
  float am = 1.0 / (max(s.y, -0.05) + 0.12);
  return exp(-vec3(0.17, 0.40, 0.90) * am * 0.35) * smoothstep(-0.06, 0.03, s.y);
}

float segDist(vec2 p, vec2 a, vec2 b){ vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }

void main(){
  vec2 uv = vUv;
  float az = (uv.x - 0.5) * AZ_SCALE;
  float el = (uv.y - HZ) * EL_SCALE;
  vec3 rd = dirFrom(az, el);
  vec3 rdSky = dirFrom(az, max(el, 0.012));
  float pix = EL_SCALE / uRes.y; // one pixel in radians

  float dayF = smoothstep(-0.14, 0.2, uSun.y);
  float nightF = 1.0 - smoothstep(-0.2, -0.04, uSun.y);
  vec3 sl = sunLight(uSun);

  // Sky
  vec3 col = atmosphere(rdSky, uSun, 22.0);
  vec3 nightBase = vec3(0.0006, 0.0010, 0.0024) * (1.0 + 2.0 * smoothstep(0.3, 0.0, el));
  col += nightBase;
  // moonlight brightens the night sky a little around the moon
  float moonUp = smoothstep(-0.05, 0.1, uMoon.y);
  float illum = 0.5 + 0.5 * cos(uPhase);
  float dm = acos(clamp(dot(rdSky, uMoon), -1.0, 1.0));
  col += vec3(0.6, 0.7, 0.9) * (exp(-dm * 9.0) * 0.004 + exp(-dm * 60.0) * 0.02) * illum * moonUp;

  // Sun disc
  float ds = acos(clamp(dot(rd, uSun), -1.0, 1.0));
  float sunR = 0.0125;
  float disc = smoothstep(sunR, sunR - 1.5 * pix, ds);
  float limb = sqrt(max(0.0, 1.0 - (ds * ds) / (sunR * sunR)));
  col += disc * sl * (25.0 * (0.6 + 0.4 * limb)) * step(0.0, el);

  // Moon with real phase lighting
  float moonR = 0.016;
  vec4 moonC = vec4(0.0);
  if (dm < moonR * 1.2) {
    vec3 m = uMoon;
    vec3 right = normalize(cross(m, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, m);
    vec2 q = vec2(dot(rd - m, right), dot(rd - m, up)) / moonR;
    float r2 = dot(q, q);
    if (r2 < 1.0) {
      vec3 n = vec3(q, sqrt(1.0 - r2));                       // facing the viewer
      vec3 st = uSun - dot(uSun, m) * m;                       // sun direction on the moon's disc
      vec2 sd = normalize(vec2(dot(st, right), dot(st, up)) + 1e-5);
      vec3 sv = vec3(sd * sin(uPhase), cos(uPhase));           // light vector in disc space
      float lit = smoothstep(-0.04, 0.12, dot(n, sv));
      float maria = smoothstep(0.42, 0.68, fbm(q * 2.3 + 4.0));
      float albedo = 0.85 - 0.32 * maria - 0.12 * smoothstep(0.55, 0.8, fbm(q * 9.0));
      float edge = smoothstep(1.0, 1.0 - 2.5 * pix / moonR, sqrt(r2));
      vec3 ext = mix(vec3(1.0, 0.75, 0.5), vec3(1.0), smoothstep(0.0, 0.25, m.y));
      moonC = vec4(vec3(1.0, 0.97, 0.92) * ext * albedo * (lit * 0.16 + 0.004), edge);
    }
  }
  col = mix(col, col * 0.15 + moonC.rgb, moonC.a * moonUp);

  // Clouds on a flat layer, drifting with the wind
  float cloudA = 0.0;
  if (el > 0.0) {
    vec2 cp = rdSky.xz / rdSky.y * 0.9 + vec2(uTime * 0.0045, uTime * 0.0012);
    float n1 = fbm(cp * 1.4);
    float dens = smoothstep(0.50, 0.80, n1);
    if (dens > 0.0) {
      float n2 = fbm(cp * 1.4 + normalize(uSun.xz + 1e-4) * 0.06);
      float light = clamp(0.55 + (n1 - n2) * 7.0, 0.12, 1.0);
      float mu = dot(rdSky, uSun);
      float silver = pow(max(mu, 0.0), 12.0) * 2.5;
      vec3 amb = mix(vec3(0.0008, 0.001, 0.0018), vec3(0.18, 0.22, 0.30), dayF);
      vec3 cc = amb + sl * (1.25 * light + silver) * dayF + vec3(0.7, 0.8, 1.0) * 0.006 * illum * moonUp;
      cloudA = dens * smoothstep(0.0, 0.18, el) * 0.95;
      col = mix(col, cc, cloudA);
    }
  }

  // Stars (hidden by daylight, clouds and the moon's glare)
  float skyLum = dot(col, vec3(0.3, 0.5, 0.2));
  float starVis = clamp(1.0 - skyLum * 60.0, 0.0, 1.0) * (1.0 - cloudA) * smoothstep(0.0, 0.05, el);
  vec3 starCol = vec3(0.0);
  if (starVis > 0.0) {
    float c = cos(uStarRot), s = sin(uStarRot);
    vec3 d = vec3(c * rd.x - s * rd.z, rd.y, s * rd.x + c * rd.z);
    vec3 gpos = d * 200.0; vec3 id = floor(gpos); vec3 f = fract(gpos) - 0.5;
    float h = hash3(id);
    if (h > 0.982) {
      float tw = 0.65 + 0.35 * sin(uTime * (1.5 + h * 6.0) + h * 50.0);
      float b = smoothstep(0.42, 0.0, length(f)) * tw * (0.35 + 1.6 * (h - 0.982) / 0.018);
      starCol = b * mix(vec3(1.0, 0.86, 0.72), vec3(0.78, 0.88, 1.0), fract(h * 91.7)) * starVis;
    }
  }

  // Shooting star
  if (uMeteor.z > 0.0) {
    vec2 dir = normalize(vec2(1.0, -0.5));
    vec2 head = uMeteor.xy + dir * uMeteor.w * 0.45;
    vec2 tail = head - dir * 0.16;
    vec2 P = uv * uRes, H = head * uRes, T = tail * uRes;
    float dd = segDist(P, T, H);
    float along = clamp(dot(P - T, H - T) / dot(H - T, H - T), 0.0, 1.0);
    float fade = sin(clamp(uMeteor.w, 0.0, 1.0) * PI);
    starCol += vec3(0.9, 0.95, 1.0) * exp(-dd * dd * 0.8) * along * along * fade * 1.4 * starVis;
  }

  // Distant hills, faint in the haze, with a mosque on the far ridge
  float far = 0.012 + 0.024 * fbm(vec2(az * 4.0, 7.0));
  float near = -0.006 + 0.034 * fbm(vec2(az * 2.1 + 3.0, 1.0));
  vec3 haze = col;
  vec3 farCol = mix(haze, vec3(0.012, 0.016, 0.026) * (0.4 + dayF * 3.0), 0.32);
  vec3 nearCol = mix(haze, vec3(0.006, 0.008, 0.012) * (0.4 + dayF * 4.0), 0.72);

  // mosque silhouette (az/el units)
  float mx = 0.27;
  float base = 0.012 + 0.024 * fbm(vec2(mx * 4.0, 7.0)) - 0.002;
  vec2 mp = vec2(az - mx, el - base);
  float dome = length(mp - vec2(0.0, 0.016)) - 0.024;
  dome = max(dome, -(mp.y - 0.016));
  float drum = max(abs(mp.x) - 0.034, abs(mp.y - 0.008) - 0.010);
  float fin = max(abs(mp.x) - 0.0012, abs(mp.y - 0.047) - 0.006);
  float body = min(min(dome, drum), fin);
  for (int k = 0; k < 2; k++) {
    float sx = k == 0 ? -0.05 : 0.05;
    vec2 q = mp - vec2(sx, 0.0);
    float shaft = max(abs(q.x) - 0.0032, abs(q.y - 0.04) - 0.04);
    float cone = max(abs(q.x) - 0.0032 * clamp((0.098 - q.y) / 0.018, 0.0, 1.0), abs(q.y - 0.089) - 0.009);
    float bal = max(abs(q.x) - 0.0055, abs(q.y - 0.066) - 0.0018);
    body = min(body, min(min(shaft, cone), bal));
  }
  float mosque = smoothstep(pix, -pix, body);
  float farMask = max(smoothstep(far + pix, far - pix, el), mosque);
  col = mix(col, farCol, farMask);
  // lit windows at night
  float win = step(abs(mp.y - 0.006), 0.0028) * step(abs(mp.x), 0.028) * step(0.5, fract((mp.x + 0.028) / 0.008));
  col += vec3(1.0, 0.75, 0.4) * 0.03 * win * mosque * nightF;
  float nearMask = smoothstep(near + pix, near - pix, el);
  col = mix(col, nearCol, nearMask);
  // foreground ground darkens quickly below the horizon
  vec3 groundCol = vec3(0.0025, 0.003, 0.0045) * (0.5 + dayF * 6.0);
  col = mix(col, groundCol, smoothstep(-0.005, -0.06, el) * nearMask);

  // Airplane with contrail (daytime)
  if (uPlane.z > 0.0) {
    vec2 P = uv * uRes, A = uPlane.xy * uRes;
    float trail = exp(-pow((P.y - A.y) / (1.0 + max(A.x - P.x, 0.0) * 0.012), 2.0)) * step(P.x, A.x)
      * exp(-max(A.x - P.x, 0.0) / 140.0);
    vec3 tc = sl * 1.3 + vec3(0.15, 0.18, 0.24);
    col = mix(col, tc, trail * 0.55 * dayF);
    float body2 = exp(-dot(P - A, P - A) * 0.6);
    col = mix(col, vec3(0.05), body2 * 0.9);
  }

  // Birds at dawn and dusk
  if (uBirds.z > 0.0) {
    vec2 P = uv * uRes;
    for (int i = 0; i < 6; i++) {
      float fi = float(i);
      vec2 c = uBirds.xy * uRes + vec2(-fi * 13.0 + mod(fi, 2.0) * 4.0, sin(fi * 2.3) * 7.0 + fi * 1.5);
      float flap = sin(uTime * 9.0 + fi * 1.7) * 2.2;
      float w = 4.0 + mod(fi, 3.0);
      float d1 = segDist(P, c, c + vec2(-w, 1.5 + flap));
      float d2 = segDist(P, c, c + vec2(w, 1.5 + flap));
      float bird = smoothstep(0.9, 0.2, min(d1, d2));
      col = mix(col, vec3(0.004, 0.005, 0.008), bird * 0.85);
    }
  }

  // Exposure that adapts from day to night, then display gamma
  float exposure = mix(9.0, 1.6, smoothstep(-0.13, 0.03, uSun.y));
  col = 1.0 - exp(-col * exposure);
  col += starCol;
  col = pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2));
  // a touch of grain so gradients don't band
  col += (hash(uv * uRes + fract(uTime)) - 0.5) / 255.0;
  gl_FragColor = vec4(col, 1.0);
}`;

var SKY_VS = 'attribute vec2 p; varying vec2 vUv; void main(){ vUv = p * 0.5 + 0.5; gl_Position = vec4(p, 0.0, 1.0); }';

var D2R = Math.PI / 180;
var LAT = 39.93 * D2R;
// Apparent sun position for Ankara on 1 Nov 2026. +1.9° covers refraction and Diyanet's
// temkin, so the disc touches the horizon at the published güneş/akşam minutes.
function sunAltAz(t) {
  var dec = -14.6 * D2R, H = (t - 752) / 60 * 15 * D2R;
  var alt = Math.asin(Math.sin(LAT) * Math.sin(dec) + Math.cos(LAT) * Math.cos(dec) * Math.cos(H)) + 1.9 * D2R;
  var az = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(LAT) - Math.tan(dec) * Math.cos(LAT));
  return [alt, az];
}
// Moon ~21 days old: dec ≈ +22°, transit ≈ 05:30.
function moonAltAz(t) {
  var dec = 22 * D2R, H = (t - 330) / 60 * 14.5 * D2R;
  var alt = Math.asin(Math.sin(LAT) * Math.sin(dec) + Math.cos(LAT) * Math.cos(dec) * Math.cos(H));
  var az = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(LAT) - Math.tan(dec) * Math.cos(LAT));
  return [alt, az];
}
function viewDir(altAz, altScale) {
  var el = altAz[0] * altScale, az = altAz[1] * 0.5; // squeeze the azimuth so rise and set stay on screen
  return [Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)];
}

function createSky(canvas) {
  var gl = canvas.getContext('webgl', { antialias: false, preserveDrawingBuffer: true });
  if (!gl) return null;
  function sh(type, src) {
    var s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
    return s;
  }
  var prog = gl.createProgram();
  gl.attachShader(prog, sh(gl.VERTEX_SHADER, SKY_VS));
  gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, SKY_FS));
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
  gl.useProgram(prog);
  var buf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  var loc = gl.getAttribLocation(prog, 'p');
  gl.enableVertexAttribArray(loc);
  gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
  var u = {};
  ['uRes', 'uTime', 'uSun', 'uMoon', 'uPhase', 'uStarRot', 'uMeteor', 'uPlane', 'uBirds'].forEach(function (n) { u[n] = gl.getUniformLocation(prog, n); });
  return function draw(o) {
    gl.viewport(0, 0, canvas.width, canvas.height);
    gl.uniform2f(u.uRes, canvas.width, canvas.height);
    gl.uniform1f(u.uTime, o.time);
    gl.uniform3fv(u.uSun, viewDir(sunAltAz(o.t), 1.0));
    gl.uniform3fv(u.uMoon, viewDir(moonAltAz(o.t), 0.62));
    gl.uniform1f(u.uPhase, 76 * D2R);
    gl.uniform1f(u.uStarRot, o.t / 1440 * 2 * Math.PI * 1.0027);
    gl.uniform4fv(u.uMeteor, o.meteor || [0, 0, 0, 0]);
    gl.uniform4fv(u.uPlane, o.plane || [0, 0, 0, 0]);
    gl.uniform4fv(u.uBirds, o.birds || [0, 0, 0, 0]);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  };
}
