package com.namazim

import java.time.LocalDate

/** One outer petal of the Özet rose. [counts]: whether it takes part in "everything done". */
data class Petal(val name: String, val progress: Float, val label: String, val counts: Boolean)

/** The prayer day runs imsak to imsak: before today's imsak it is still yesterday (yatsı can be marked until then). */
fun prayerDayOf(today: LocalDate, now: Long, imsakToday: Long): LocalDate =
    if (now < imsakToday) today.minusDays(1) else today

/** "Vaktinde": marked within the first third of the prayer's window. */
fun inFirstThird(slot: Slot, at: Long?): Boolean =
    at != null && at >= slot.start && at < slot.start + (slot.end - slot.start) / 3

/** No goal set counts as done, so nobody is punished for not having one. */
fun ratio(done: Int, goal: Int): Float = if (goal <= 0) 1f else (done.toFloat() / goal).coerceIn(0f, 1f)

fun gulPetals(
    slots: List<Slot>, prayed: Set<String>, prayedAt: (String) -> Long?,
    pagesToday: Int, readGoal: Int, kazaToday: Int, kazaRate: Int, kazaOwed: Int,
): List<Petal> {
    val kept = slots.filter { it.key in prayed }
    val onTime = kept.count { inFirstThird(it, prayedAt(it.key)) }
    return listOf(
        Petal("Namaz", kept.size / 5f, "${kept.size}/5", true),
        Petal("Vaktinde", onTime / 5f, "$onTime/5", true),
        Petal("Sayfa", ratio(pagesToday, readGoal), "$pagesToday/$readGoal", true),
        Petal("Kaza", if (kazaOwed == 0) 1f else ratio(kazaToday, kazaRate), "$kazaToday/$kazaRate", true),
        Petal("Zikir", 0f, "yakında", false),   // shortcut: closed until the zikir module exists
    )
}
