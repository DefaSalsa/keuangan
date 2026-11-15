# Keuangan

Aplikasi Android (Kotlin, minSdk 26, targetSdk 34) untuk mencatat hutang, piutang, tabungan, dan uang tunai, dengan tiga widget layar utama yang terpisah. Tampilannya dibuat dengan HTML (Tailwind CSS + Font Awesome) di dalam WebView; data, login, dan widget tetap Kotlin. Tema hitam pekat dengan aksen biru. Data disimpan di Firebase (login email + password) dan tetap bisa dipakai saat offline.

## Fitur

**Hutang Piutang**
- Dua jenis catatan: **Hutang** (saya berhutang, nominal merah) dan **Piutang** (orang berhutang ke saya, nominal hijau), dengan nama, jumlah, catatan.
- Catatan **dikelompokkan per nama orang** (huruf besar/kecil dan spasi ekstra diabaikan). Tiap kelompok menampilkan total hutang dan piutang yang belum lunas, bisa dilipat, dan yang paling dekat jatuh tempo ada di atas.
- **Janji dibayar kapan** (opsional) per catatan, dengan penanda "N hari lagi", "Hari ini", atau "Terlambat N hari".
- Ringkasan total belum lunas, filter Semua / Hutang / Piutang, tandai lunas, ubah, hapus.

**Tabungan**
- Banyak tujuan tabungan sekaligus (mis. Liburan, Dana darurat, Motor), masing-masing dengan target.
- Setor, tarik, dan riwayat setoran per tujuan (setiap entri riwayat bisa diubah atau dihapus kalau salah ketik), bilah progres dengan persentase, dan penanda saat target tercapai.
- Rencana menabung per hari untuk tiap tujuan (Rp per hari). Dari situ aplikasi menghitung estimasi tanggal tercapai dan berapa hari lagi, tampil di kartu tujuan dan di widget.
- Ringkasan total terkumpul dan total target.

**Uang (khusus tunai)**
- Satu saldo uang tunai di luar tabungan, dengan riwayat uang masuk dan keluar.
- Tombol **+** di kanan bawah membuka form catat tunai: isi jumlah dan keterangan, lalu pilih **Uang masuk** (dari mana, mis. gaji) atau **Uang keluar** (wajib isi dipakai untuk apa, mis. makan siang). Pengurangan tidak bisa melebihi saldo.
- Setiap catatan di riwayat bisa diubah (jumlah, keterangan, masuk/keluar) atau dihapus kalau salah ketik. Saldo selalu dihitung dari riwayat.
- Ringkasan **Total tunai** dan **Tunai + tabungan**.

**Widget (tiga widget terpisah, pilih sendiri di daftar widget)**
- **Hutang Piutang:** total Hutang (merah) dan Piutang (hijau) berdampingan di atas, lalu 3 orang untuk masing-masing, dipisah sekat. Catatan orang yang sama digabung jadi satu baris, dengan janji bayar terdekat di bawah namanya (merah bila hari ini atau terlambat, biru bila masih ada waktu). Orang dengan jatuh tempo terdekat ada di atas.
- **Tabungan:** total terkumpul dan target, lalu progres 3 tujuan (belum tercapai lebih dulu).
- **Uang:** saldo tunai dan tunai + tabungan berdampingan, lalu 4 catatan terakhir (hijau uang masuk, merah uang keluar).
- Tombol **+** di tiap widget membuka formulir tambah pada tab yang sesuai.

**Tampilan**
- Navigasi bawah berbentuk kapsul yang melayang di atas isi layar, berlatar putih dengan opasitas 20% dan blur sungguhan (`backdrop-filter`).
- Animasi halus: pil biru di kapsul bergeser antar tab, layar bergeser dan memudar saat pindah tab, kartu daftar muncul berurutan, bilah progres mengalir, angka ringkasan menghitung naik/turun, dan tombol mengecil sedikit saat ditekan.

**Data**
- Tersimpan di Firestore (`users/{uid}/debts`, `users/{uid}/goals`, dan `users/{uid}/cash`), jadi aman walau HP rusak. Cache lokal dipakai untuk widget dan mode offline.

## Tampilan HTML

- Sumber tampilan: `app/src/main/assets/www/index.html` dan `app.js`. Gaya memakai kelas Tailwind dan ikon Font Awesome.
- `tailwind.css` dan folder `fa/` **tidak ada di repo**. Workflow membuatnya saat build (`npm install` Tailwind 3.4 dan Font Awesome 6 Free, lalu menyalin hasilnya ke assets), jadi di APK semuanya sudah terbundel dan jalan tanpa internet.
- Konfigurasi Tailwind (warna, animasi) ada di `web/tailwind.config.js`, CSS tambahan di `web/input.css`. Tulis nama kelas utuh di `app.js` (jangan disambung-sambung) supaya terbaca saat build.
- Build lokal di Android Studio: jalankan dulu perintah yang sama dengan langkah "Siapkan Tailwind dan Font Awesome" di `.github/workflows/build.yml`.
- Kotlin dan HTML bicara lewat objek `native` (`MainActivity.kt`, kelas `Bridge`).

## Setup Firebase (sekali saja)

1. Buka https://console.firebase.google.com lalu **Add project**.
2. Di proyek itu, **Add app > Android**.
   - Package name: sama persis dengan `APPLICATION_ID` di `build.env` (saat ini `id.dstech.keuangan`).
   - SHA-1 tidak perlu diisi.
   - Unduh `google-services.json`.
3. **Build > Authentication > Get started > Sign-in method**, aktifkan **Email/Password**.
4. **Build > Firestore Database > Create database**.
   - Pilih production mode, lokasi `asia-southeast2` (Jakarta).
   - Buka tab **Rules**, tempel isi `firestore.rules`, lalu **Publish**.
5. Di repo GitHub: **Settings > Secrets and variables > Actions > New repository secret**.
   - Name: `GOOGLE_SERVICES_JSON`
   - Secret: tempel seluruh isi file `google-services.json`.

`google-services.json` sengaja tidak ikut di repo (ada di `.gitignore`). Workflow membuatnya dari secret saat build.

## Build lewat GitHub Actions

1. Push seluruh isi proyek ini ke repo GitHub (branch `main`).
2. Buka tab **Actions**, workflow **Build APK** jalan otomatis. Bisa juga dijalankan manual lewat **Run workflow**.
3. Unduh APK dari bagian **Artifacts** pada halaman run.
4. Push tag berawalan `v` (contoh `v1.0`) untuk membuat **Release** otomatis berisi APK.

APK yang dihasilkan adalah versi debug (bisa langsung dipasang di HP). Untuk rilis yang ditandatangani, tambahkan keystore lewat GitHub Secrets.

## Konfigurasi (`build.env`)

```
APP_NAME=Keuangan
APPLICATION_ID=id.dstech.keuangan
VERSION_NAME=1.0
VERSION_CODE=1
LOGO_FOREGROUND=logo/logo_foreground.png
ICON_BACKGROUND=#000000
MIN_SDK=26
TARGET_SDK=34
JAVA_VERSION=17
GRADLE_VERSION=8.7
ANDROID_PLATFORM=34
BUILD_TOOLS_VERSION=34.0.0
```

`APP_NAME` dipakai sebagai nama aplikasi di launcher, judul layar, dan nama file APK (`<APP_NAME>-<versi>.apk`, spasi otomatis jadi `_`). File ini dibaca oleh workflow (JDK, Gradle, SDK) dan Gradle (ID aplikasi, versi). Untuk rilis baru, naikkan `VERSION_CODE` dan `VERSION_NAME`.

## Logo

Logo berupa dompet biru dengan kancing putih, disimpan sebagai PNG transparan 432x432 di `logo/logo_foreground.png`. Aplikasi memakai satu ikon adaptif (minimal Android 8): lapisan depan dari file itu, latar dari `ICON_BACKGROUND` di `build.env` (default hitam `#000000`). Ganti logo dengan menimpa file PNG tersebut (logo di tengah) atau ubah path-nya di `build.env`.

## Catatan

- Jika build gagal karena pustaka AndroidX meminta `compileSdk` 35, ubah `ANDROID_PLATFORM` menjadi `35` di `build.env`.
- Jika `APPLICATION_ID` diganti, daftarkan ulang app Android di Firebase dan perbarui secret `GOOGLE_SERVICES_JSON`.
- Tombol kembali menutup sheet yang terbuka, lalu kembali ke tab Hutang Piutang, baru keluar aplikasi.
- Widget membaca cache lokal. Perubahan dari perangkat lain muncul di widget setelah aplikasi dibuka.
- Saldo tabungan dihitung dari seluruh riwayat setoran dan penarikan, jadi tidak pernah selisih dengan riwayatnya.

## Struktur

```
build.env                     konfigurasi build
web/                          konfigurasi Tailwind (dipakai saat build)
app/src/main/assets/www/      tampilan HTML (index.html, app.js)
logo/                         logo aplikasi
firestore.rules               aturan keamanan Firestore
.github/workflows/build.yml   workflow build APK
app/src/main/java/id/dstech/keuangan/
  MainActivity.kt             WebView + jembatan Kotlin ke HTML (data, login, sinkron)
  DebtStore.kt, GoalStore.kt, CashStore.kt  cache lokal
  CloudRepo.kt                sinkron Firestore
  DebtWidget.kt, SavingsWidget.kt, MoneyWidget.kt   tiga widget
```
