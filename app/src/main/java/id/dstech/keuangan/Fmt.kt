package id.dstech.keuangan

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val ID = Locale("id", "ID")

    fun rupiah(n: Long): String {
        val df = DecimalFormat("#,##0", DecimalFormatSymbols(ID))
        return "Rp " + df.format(n)
    }

    fun date(ts: Long): String = SimpleDateFormat("d MMM yyyy", ID).format(Date(ts))
}
