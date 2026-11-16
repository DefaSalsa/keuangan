package id.dstech.keuangan

/** Satu setoran (amount > 0) atau penarikan (amount < 0) pada sebuah tujuan tabungan. */
data class Deposit(
    val amount: Long,
    val note: String,
    val at: Long
)

/**
 * Satu tujuan tabungan, mis. "Liburan", "Dana darurat". Saldo = jumlah seluruh riwayat.
 * daily = rencana menabung per hari (Rp), 0 = belum diatur.
 * icon = nama ikon Font Awesome tanpa awalan "fa-" (mis. "plane"), kosong = ikon bawaan.
 */
data class Goal(
    val id: Long,
    val name: String,
    val target: Long,
    val createdAt: Long,
    val history: List<Deposit>,
    val daily: Long = 0L,
    val icon: String = ""
) {
    val saved: Long get() = history.sumOf { it.amount }

    val percent: Int
        get() = if (target <= 0L) 0 else ((saved * 100L) / target).coerceIn(0L, 100L).toInt()

    val reached: Boolean get() = target > 0L && saved >= target

    val remaining: Long get() = (target - saved).coerceAtLeast(0L)

    /** Sisa hari sampai target jika menabung sesuai rencana harian, null bila sudah tercapai atau belum ada rencana. */
    val daysLeft: Long?
        get() = if (reached || daily <= 0L) null else (remaining + daily - 1L) / daily

    /** Mis. "32 hari lagi • 16 Des 2026", atau null bila tidak bisa dihitung. */
    fun etaText(now: Long = System.currentTimeMillis()): String? {
        val d = daysLeft ?: return null
        return "$d hari lagi • ${Fmt.date(now + d * 86_400_000L)}"
    }
}
