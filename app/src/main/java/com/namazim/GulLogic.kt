package com.namazim

import java.time.LocalDate

/** One outer petal of the Özet rose. [counts]: whether it takes part in "everything done". */
data class Petal(val name: String, val progress: Float, val label: String, val counts: Boolean)

/** The prayer day runs imsak to imsak: before today's imsak it is still yesterday (yatsı can be marked until then). */
fun prayerDayOf(today: LocalDate, now: Long, imsakToday: Long): LocalDate =
    if (now < imsakToday) today.minusDays(1) else today

/** When the open screen must refresh by itself: the next imsak, where the prayer day turns. */
fun nextImsakAt(now: Long, imsakToday: Long, imsakTomorrow: Long): Long = if (now < imsakToday) imsakToday else imsakTomorrow

/** "Vaktinde": marked within the first third of the prayer's window. */
fun inFirstThird(slot: Slot, at: Long?): Boolean =
    at != null && at >= slot.start && at < slot.start + (slot.end - slot.start) / 3

/** No goal set counts as done, so nobody is punished for not having one. */
fun ratio(done: Int, goal: Int): Float = if (goal <= 0) 1f else (done.toFloat() / goal).coerceIn(0f, 1f)

/** A zikir on the user's own list; [name] is unique and keys the day's count. */
data class Zikir(val name: String, val target: Int)

/** The tesbihat the list starts with; the user may edit or empty it. */
val DEFAULT_ZIKIRS = listOf(Zikir("Sübhânallah", 33), Zikir("Elhamdülillah", 33), Zikir("Allâhu ekber", 33))

/** The zikirs of the day's list. With "tesbihat günlük zikrime sayılsın" off, the three 33s of the tesbihat are said
 *  after each prayer and kept apart, so they leave the list (Zikirhane chips, Takip, rose). A zikir the user set to
 *  another target (e.g. Sübhânallah 100) is their own and stays. */
fun dailyZikirs(all: List<Zikir>, tesbihatCounts: Boolean): List<Zikir> =
    if (tesbihatCounts) all else all.filterNot { it in DEFAULT_ZIKIRS }

/** Fifth petal: the average of each zikir's completion; an empty list counts as done. */
fun zikirPetal(zikirs: List<Zikir>, counts: Map<String, Int>): Petal {
    val r = zikirs.map { ratio(counts[it.name] ?: 0, it.target) }
    val done = r.count { it >= 1f }
    return Petal("Zikir", if (r.isEmpty()) 1f else r.average().toFloat(), "$done/${zikirs.size}", true)
}

/** After [from] is completed: the next unfinished zikir (wrapping), or [from] itself when all are done. */
fun nextOpenZikir(zikirs: List<Zikir>, counts: Map<String, Int>, from: Int): Int =
    (1 until zikirs.size).map { (from + it) % zikirs.size }
        .firstOrNull { (counts[zikirs[it].name] ?: 0) < zikirs[it].target } ?: from

/** Log key of the free counter: no target, never part of the daily goal or the rose. */
const val FREE_ZIKIR = "#serbest"

/** Namaz sonrası tesbihat in Zikirhane: which of the three 33s is being said, or -1 when all 99 are done. */
fun tesbihStep(counts: List<Int>): Int = DEFAULT_ZIKIRS.indices.firstOrNull { (counts.getOrNull(it) ?: 0) < DEFAULT_ZIKIRS[it].target } ?: -1

/** One Takip cell: [done] = target reached; [text] = the count while short, "+extra" past the target, "" for nothing. */
data class ZikirCell(val done: Boolean, val text: String)

fun zikirCell(count: Int, target: Int?): ZikirCell = when {
    target == null -> ZikirCell(false, if (count > 0) "$count" else "")
    count >= target -> ZikirCell(true, if (count > target) "+${count - target}" else "")
    else -> ZikirCell(false, if (count > 0) "$count" else "")
}

fun gulPetals(
    slots: List<Slot>, prayed: Set<String>, prayedAt: (String) -> Long?,
    pagesToday: Int, readGoal: Int, kazaToday: Int, kazaRate: Int, kazaOwed: Int,
    zikirs: List<Zikir> = emptyList(), zikirCounts: Map<String, Int> = emptyMap(),
): List<Petal> {
    val kept = slots.filter { it.key in prayed }
    val onTime = kept.count { inFirstThird(it, prayedAt(it.key)) }
    return listOf(
        Petal("Namaz", kept.size / 5f, "${kept.size}/5", true),
        Petal("Vaktinde", onTime / 5f, "$onTime/5", true),
        Petal("Sayfa", ratio(pagesToday, readGoal), "$pagesToday/$readGoal", true),
        Petal("Kaza", if (kazaOwed == 0) 1f else ratio(kazaToday, kazaRate), "$kazaToday/$kazaRate", true),
        zikirPetal(zikirs, zikirCounts),
    )
}
