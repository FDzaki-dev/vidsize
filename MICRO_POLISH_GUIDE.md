# MICRO_POLISH_GUIDE.md — Video Resizer

Panduan operasional PERMANEN (ditanam Batch 35, sumber upload user:
`VideoResizer_FINAL_MICRO_POLISH_ClaudeOnly.md`). Wajib dibaca sebelum
mengerjakan task polishing UI/UX apa pun di proyek ini, di sesi mana pun
ke depan — bukan item batch-history, jangan dianggap "resolved and
removable" walau seluruh 8 prioritas di bawah sudah tuntas dikerjakan
(kalau tuntas, cukup tandai tiap prioritas [DONE] + batch berapa, jangan
hapus filenya).

**Status: SEMUA 8 PRIORITAS SELESAI (Batch 36-47).** Panduan ini TETAP
tidak boleh dihapus (aturan permanen di atas) — untuk task polishing
UI/UX di masa depan, mulai dari sini dulu sebelum audit baru.

## TUJUAN
Bawa VideoResizer ke kondisi polished akhir HANYA dengan perubahan lokal
& bertarget. DILARANG refactor arsitektur atau menulis ulang logika yang
sudah berjalan.

## ATURAN KERAS
- DILARANG refactor arsitektur.
- DILARANG menulis ulang MainActivity secara total.
- DILARANG mengganti logika Media3/encoder/resizer.
- DILARANG mengubah signing, keystore, package identity, versioning, atau
  konfigurasi CI kecuali ada bukti konkret blocker build.
- DILARANG menghapus file yang sudah ada.
- DILARANG menambah abstraksi tanpa alasan bug/UX konkret.
- DILARANG mengubah business logic yang sudah benar hanya demi "clean code".
- Pertahankan perilaku resize/compress yang sudah ada.
- Pertahankan perilaku navigasi & persistence yang sudah ada.
- Buat patch sekecil mungkin untuk setiap isu.
- Setelah tiap perubahan, cek ulang call site & state flow terdampak.
- Jika satu item sudah benar di kode saat ini, biarkan (jangan disentuh).

## PRIORITAS 1 — Konsistensi Bahasa UI [DONE — Batch 36]
Audit string user-facing, samakan bahasa di seluruh app (contoh campuran:
Custom Resolution, Custom Bitrate, Save, Cancel, Share, Width, Height,
Batch Export). Pakai bahasa UI utama proyek secara konsisten. DILARANG
mengubah nama identifier internal hanya untuk ini.

## PRIORITAS 2 — Presentasi Error [DONE — Batch 37]
Audit error user-facing yang saat ini lewat Toast. Untuk error yang butuh
aksi/konteks user: utamakan mekanisme Snackbar/inline error yang sudah
ada. Toast hanya untuk kasus yang memang tepat. Pertahankan logika error
yang mendasarinya persis sama. DILARANG membuat framework error global
baru. Syarat: error harus dimengerti, user tahu apa yang gagal, user tahu
aksi apa (jika ada) yang bisa dilakukan, hindari duplikasi Toast +
Snackbar.

## PRIORITAS 3 — Action Row Layar Kecil [DONE — Batch 38]
Audit action row Studio (Edit again, Gallery, Share, Delete, dan
sejenisnya). Tujuan: tanpa clipping, tanpa overlap tak sengaja, tanpa
touch target mikroskopis, tanpa kompresi horizontal paksa di layar
sempit. Pakai penyesuaian UI lokal terkecil (wrapping/spacing/responsive
arrangement/adaptive component yang sudah ada). DILARANG mendesain ulang
seluruh Studio screen.

## PRIORITAS 4 — Kejelasan Estimasi Ukuran File [DONE — Batch 39]
Di tempat estimasi ukuran output ditampilkan: beri label jelas sebagai
estimasi, buat jelas secara visual bahwa ukuran hasil encode final bisa
berbeda. Pertahankan logika kalkulasi/estimasi yang ada kecuali ada bukti
bug faktual. DILARANG menambah algoritma prediksi rumit.

## PRIORITAS 5 — Lifecycle Filmstrip/Frame Extraction [DONE — Batch 44]
Audit ekstraksi thumbnail/filmstrip video. Verifikasi: kerja yang
dibatalkan/diganti video benar-benar bisa dibatalkan; ekstraksi frame
usang tidak bisa menimpa UI state video yang aktif sekarang; bitmap besar
dilepas dengan tepat; pergantian video cepat tidak menyisakan preview
basi. Perbaiki hanya isu lifecycle/state konkret. DILARANG mendesain
ulang arsitektur filmstrip.

## PRIORITAS 6 — Responsive / Font-Scale Polish [DONE — Batch 45]
Audit layar sempit & font-scale sistem besar. Cek: chips, buttons, action
rows, dialogs, resolution controls, bitrate controls, progress UI.
Perbaiki hanya masalah clipping/overflow/touch-target nyata. DILARANG
mengubah identitas visual tanpa perlu.

## PRIORITAS 7 — Accessibility Micro-Polish [DONE — Batch 46]
Audit semantics/content description yang ada. Perbaiki hanya yang
hilang/menyesatkan: icon-only controls, trim handles, progress
indicators, selection chips, destructive actions. Sediakan (kalau
relevan): content description bermakna, state selected/unselected, state
enabled/disabled, makna progress/state. DILARANG membangun framework
accessibility baru.

## PRIORITAS 8 — Duplikasi Theme/System-Bar [DONE — Batch 47, audit saja, tidak ada defect]
Periksa konfigurasi XML theme & Compose theme yang ada. Kalau keduanya
mendefinisikan system-bar appearance yang overlap: tentukan runtime
source of truth sebenarnya; hapus/sesuaikan HANYA konfigurasi redundan
yang terbukti nyata menyebabkan perilaku tidak konsisten. DILARANG
refactor theme. Kalau tidak ada defect runtime/UI nyata, biarkan.

## VERIFICATION GATE (sebelum satu batch prioritas dianggap selesai)
1. Cari setiap symbol/call site yang dimodifikasi.
2. Verifikasi tidak ada perubahan logika tak sengaja.
3. Verifikasi navigation routes tetap identik.
4. Verifikasi perilaku persistence/database tetap tidak tersentuh.
5. Verifikasi logika resize/compression/export tetap tidak tersentuh
   kecuali fix bug terbukti.
6. Verifikasi tidak ada protected file ditulis ulang tanpa perlu.
7. Jalankan verifikasi static/build terkuat yang tersedia.
8. Kalau runtime testing tidak tersedia, nyatakan itu eksplisit — jangan
   klaim lolos.

## STOP CONDITION
STOP setelah: semua isu micro-UX high-value konkret di atas selesai,
tidak ada regresi baru, verifikasi build/static bersih, dan sisa isu
bersifat kosmetik/spekulatif. DILARANG polishing tanpa henti.

## FORMAT LAPORAN AKHIR (dipakai tiap kali panduan ini dieksekusi)
### CHANGED — daftar ringkas perubahan aktual
### VERIFIED — cek build/static yang benar-benar dilakukan
### NOT VERIFIED — cek runtime/device yang tidak bisa dilakukan
### UNTOUCHED — logika/arsitektur yang sengaja dipertahankan
### VERDICT — FINAL MICRO-POLISH COMPLETE atau BLOCKED: <blocker konkret>

## COMMIT MESSAGE TEMPLATE
feat(ui): final micro-polish without refactor
