package com.namazim

import org.json.JSONArray
import org.json.JSONObject

// Kadınlar için özel günler (hayız / lohusalık), design `design/design-system/ozel-gun-secenekler.html`, approved 2026-11-08.
// During a period the prayers are not owed and have no kaza. Whether the vakit it starts or ends in is owed depends on
// the mezhep (rules below). The app shows its result and saves it with the user's OK; it gives no fetva.
// shortcut: the minute values (gusül + tekbir, time to pray) are estimates; have a hoca confirm them.

/** One period; [end] null while it lasts. [nifas] = lohusalık (else hayız). Times in epoch millis. */
data class OzelGun(val start: Long, val end: Long?, val nifas: Boolean)

const val DAY_MS = 86_400_000L
private const val MIN = 60_000L
const val GUSUL_TEKBIR_MS = 15 * MIN   // Hanefî: clean early enough to wash and say the opening tekbir before the vakit ends
const val TEKBIR_MS = 1 * MIN          // only the tekbir (Şâfiî; Hanefî when the longest length was reached)
const val KILACAK_MS = 10 * MIN        // Şâfiî: the vakit had gone on long enough to pray it before the period began

fun maxDays(nifas: Boolean, hanefi: Boolean) = if (nifas) (if (hanefi) 40 else 60) else (if (hanefi) 10 else 15)
/** Shorter than this is not hayız (the prayers of those days are owed); lohusalık has no minimum. */
fun minDays(nifas: Boolean, hanefi: Boolean) = if (nifas) 0 else (if (hanefi) 3 else 1)
fun reachedMax(p: OzelGun, hanefi: Boolean) = p.end != null && p.end - p.start >= maxDays(p.nifas, hanefi) * DAY_MS - MIN

/** Is prayer [s] not owed because of [p]? [next] is the following slot of the same day (Şâfiî: a period that ends in
 *  ikindi also makes öğle owed, one that ends in yatsı also akşam). */
fun exemptBy(s: Slot, next: Slot?, p: OzelGun, hanefi: Boolean): Boolean {
    val b = p.end ?: Long.MAX_VALUE
    if (p.start >= s.end || b <= s.start) return false   // the period does not touch this vakit
    return if (hanefi) {
        // the duty attaches to the end of the vakit: owed only if clean early enough before it ends
        !(b < s.end && s.end - b >= if (reachedMax(p, true)) TEKBIR_MS else GUSUL_TEKBIR_MS)
    } else {
        val cleanAtStart = p.start > s.start && p.start - s.start >= KILACAK_MS
        val cleanAtEnd = b < s.end && s.end - b >= TEKBIR_MS
        val cem = next != null && (s.vakit == Vakit.OGLE || s.vakit == Vakit.AKSAM) && p.end != null &&
            p.end >= next.start && p.end < next.end && next.end - p.end >= TEKBIR_MS
        !(cleanAtStart || cleanAtEnd || cem)
    }
}

fun exemptAny(s: Slot, next: Slot?, ps: List<OzelGun>, hanefi: Boolean) = ps.any { exemptBy(s, next, it, hanefi) }

/** What starting at [start] means for the vakit it falls in ([s] null = no prayer time, e.g. güneş–öğle). */
fun startNote(s: Slot?, start: Long, prayed: Boolean, hanefi: Boolean): String = when {
    s == null -> "Şu an namaz vakti değil. Sonraki vakitten itibaren takip durur."
    prayed -> "${s.vakit.ad} namazını kılmıştın. Sonraki vakitten itibaren takip durur."
    hanefi -> "${s.vakit.ad} vakti içinde başladı ve henüz kılmamıştın: ${s.vakit.ad} namazı düşer, kazası yoktur."
    start - s.start >= KILACAK_MS -> "${s.vakit.ad} vakti girdikten sonra kılacak kadar zaman geçmiş: kılmadıysan ${s.vakit.ad} namazı borç olur ve kazaya yazılır."
    else -> "${s.vakit.ad} vakti yeni girmişti, kılacak kadar zaman geçmemiş: ${s.vakit.ad} namazı düşer."
}

/** What ending at [end] means for the vakit it falls in, and (Şâfiî) the one before it. */
fun endNote(s: Slot?, end: Long, p: OzelGun, hanefi: Boolean): String {
    if (s == null) return "Şu an namaz vakti değil. Öğleden itibaren takip sürer. Gusül alıp namaza başlayabilirsin."
    val left = s.end - end
    val need = if (!hanefi || reachedMax(p.copy(end = end), hanefi)) TEKBIR_MS else GUSUL_TEKBIR_MS
    val owed = left >= need
    val cem = !hanefi && owed && (s.vakit == Vakit.IKINDI || s.vakit == Vakit.YATSI)
    return if (owed) "${s.vakit.ad} vaktinin çıkmasına ${left / MIN} dk var: ${s.vakit.ad} namazı borcun." +
        (if (cem) " ${if (s.vakit == Vakit.IKINDI) "Öğle" else "Akşam"} namazı da borç olur." else "") +
        " Gusül alıp kıl; kılmazsan kazaya yazılır."
    else "${s.vakit.ad} vaktinin çıkmasına ${if (hanefi) "gusül ve tekbir" else "tekbir"} kadar zaman kalmamış: ${s.vakit.ad} namazı düşer. Sonraki vakitten itibaren takip sürer."
}

fun ozelToJson(ps: List<OzelGun>): String = JSONArray().apply {
    ps.forEach { put(JSONObject().put("start", it.start).put("end", it.end ?: 0L).put("nifas", it.nifas)) }
}.toString()

fun ozelFromJson(raw: String?): List<OzelGun> {
    if (raw.isNullOrEmpty()) return emptyList()
    val a = JSONArray(raw)
    return (0 until a.length()).map { a.getJSONObject(it).run { OzelGun(getLong("start"), getLong("end").takeIf { it > 0 }, optBoolean("nifas")) } }
}
