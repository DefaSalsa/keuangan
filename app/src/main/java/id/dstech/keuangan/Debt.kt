package id.dstech.keuangan

/**
 * Satu catatan hutang / piutang.
 * iOwe = true  -> saya yang berhutang ke orang tersebut
 * iOwe = false -> orang tersebut berhutang ke saya (piutang)
 */
data class Debt(
    val id: Long,
    val name: String,
    val amount: Long,
    val note: String,
    val createdAt: Long,
    val paid: Boolean,
    val iOwe: Boolean
)
