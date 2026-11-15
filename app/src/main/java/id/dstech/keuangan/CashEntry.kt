package id.dstech.keuangan

/**
 * Satu catatan uang tunai di luar tabungan.
 * amount > 0 = uang masuk, amount < 0 = uang keluar. note = dari mana / dipakai untuk apa.
 * Saldo tunai = jumlah seluruh catatan.
 */
data class CashEntry(
    val id: Long,
    val amount: Long,
    val note: String,
    val at: Long
)
