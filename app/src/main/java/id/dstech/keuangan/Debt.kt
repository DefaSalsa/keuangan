package id.dstech.keuangan

/**
 * Satu catatan hutang / piutang.
 * iOwe = true  -> saya yang berhutang ke orang tersebut
 * iOwe = false -> orang tersebut berhutang ke saya (piutang)
 * dueAt = tanggal janji dibayar (awal hari, epoch ms), 0 = belum diatur
 */
data class Debt(
    val id: Long,
    val name: String,
    val amount: Long,
    val note: String,
    val createdAt: Long,
    val paid: Boolean,
    val iOwe: Boolean,
    val dueAt: Long = 0L
)
