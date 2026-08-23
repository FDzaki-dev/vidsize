# Changelog

## Batch 49: Fix ExportForegroundService tidak pernah dipanggil di GIF (audit umum)

Audit cari-bug umum menemukan bug nyata: `ExportForegroundService`
(proteksi anti-background-kill saat proses ekspor berjalan lama) sudah
dipanggil dengan benar di `ResizerScreen`, `BatchScreen`, dan
`CompressorScreen` — tapi `GifScreen` terlewat total sejak fitur GIF
pertama kali dibuat. Dampak nyata: kalau user mengunci layar atau
pindah ke app lain saat GIF sedang dibuat, proses tidak punya
proteksi foreground-priority sama sekali — OS bisa membunuh proses di
background dan konversi GIF mati diam-diam, persis bug class yang
`ExportForegroundService` memang dibuat untuk mencegah.

Fix: 5 titik ditambah di `GifScreen` supaya simetris dengan 3 screen
lain — `ExportForegroundService.start(context)` saat proses mulai,
`updateProgress(context, p)` di callback progress (dipanggil bareng
update state Compose di Main thread), dan `stop(context)` di 3 jalur
keluar: selesai normal, tombol "Batalkan" saat proses berjalan, dan
konfirmasi keluar-saat-proses (dialog "Batalkan proses?").

Diaudit juga jalur resource lain yang rawan leak: 6 instantiasi
`MediaMetadataRetriever`/`MediaExtractor` di seluruh file — semua sudah
`try/finally { retriever.release() }` atau `runCatching` + release
tanpa terkecuali, tidak ada leak. `CrashLogger.kt` dan
`ExportForegroundService.kt` sendiri direview baris-per-baris — tidak
ada bug ditemukan di keduanya. 1 file disentuh (`MainActivity.kt`).

## Batch 48: Fix regresi gesture-back memicu close app langsung

Root cause: `android:enableOnBackInvokedCallback` tidak pernah
ditambahkan ke `<application>` di AndroidManifest.xml walau
`targetSdk=34` (Android 14). Tanpa flag ini, sistem predictive-back
tidak tahu app sudah register `OnBackPressedCallback` (basis semua
`BackHandler` Compose yang menangani navigasi Batch/GIF/Compressor/
StudioScreen) — OS jadi selalu menampilkan preview animasi "app akan
tertutup" untuk setiap gesture back, walau `BackHandler` di kode
sebenarnya intercept-nya di detik akhir. Ini persis match gejala yang
dilaporkan: "gesture back memicu close app langsung, bukan balik
bertahap".

Audit juga dilakukan atas seluruh 5 screen (Resizer/Batch/GIF/
Compressor/Studio) untuk pastikan tidak ada gap lain di kelas bug yang
sama: keempat sub-screen sudah punya `BackHandler(enabled = true)`
sejak Batch 43, dan toolbar-arrow-nya konsisten logic dengan gesture
(sama-sama cek `isProcessing`/`selectionMode` sebelum `onBack()`) — jadi
tidak ada regresi lain ditemukan di jalur back itu sendiri.
ResizerScreen sengaja `enabled = isProcessing` saja karena ia layar
utama — back saat idle memang harus keluar app, bukan bug.

Fix: tambah 1 atribut `android:enableOnBackInvokedCallback="true"` +
komentar penjelasan di manifest. XML divalidasi well-formed lewat
`xml.etree.ElementTree`. 1 file disentuh (`AndroidManifest.xml`,
protected asset — edit parsial only, tidak ada elemen lain yang
disentuh/dihapus).

## Batch 47: MICRO_POLISH_GUIDE Prioritas 8 — Duplikasi Theme/System-Bar (audit, tidak ada defect)

### AUDITED
- `app/src/main/res/values/themes.xml` & `values-night/themes.xml` —
  statis, hardcode warna status/nav bar, cuma resolve lewat qualifier
  `values-night` (ikut SYSTEM dark mode saja).
- `ui/theme/Theme.kt` — `VideoResizerTheme()` punya `SideEffect` yang
  jalan tiap composisi: set `window.statusBarColor`/`navigationBarColor`
  = `colorScheme.background.toArgb()` DAN
  `isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars` =
  `!isDark`, keduanya diturunkan dari `resolvedStyle` (hasil resolve
  `ThemePreference` in-app milik user — SYSTEM/LIGHT/DARK/3 tema
  kustom — BUKAN cuma system dark mode mentah).

### KESIMPULAN — TIDAK ADA DEFECT
Compose `SideEffect` di atas SUDAH menjadi runtime source-of-truth yang
benar, dan menang setiap frame setelah cold-start pertama — termasuk
saat user ganti tema in-app secara manual (mis. pilih "Dark" saat sistem
Light, atau sebaliknya), yang tadinya dikira berpotensi jadi kasus
"XML vs Compose disagree". Ternyata SideEffect membaca `resolvedStyle`
(hasil akhir preferensi in-app), bukan `isSystemInDarkTheme()` mentah,
jadi selalu sinkron dengan tampilan aktual. XML hanya berperan sebagai
tebakan warna awal sebelum frame Compose pertama — pola standar Android
utk menghindari flash warna salah saat cold-start, bukan bug, bukan
sumber kebenaran yang bersaing.

### TIDAK DILAKUKAN
Sesuai instruksi eksplisit Prioritas 8 sendiri ("Kalau tidak ada defect
runtime/UI nyata, biarkan") — **tidak ada perubahan kode** batch ini.

### VERDICT
Prioritas 8: **selesai (audit, tidak ada defect ditemukan)**.
**MICRO_POLISH_GUIDE.md 8/8 prioritas tuntas (Batch 36-47).** STOP
CONDITION panduan tercapai — polishing lanjutan menunggu temuan/arahan
baru dari user, bukan eksekusi otomatis lanjutan. File diubah:
PROJECT_STATE.md, CHANGELOG.md, MICRO_POLISH_GUIDE.md (dokumentasi saja,
0 file kode).

## Batch 46: MICRO_POLISH_GUIDE Prioritas 7 — Accessibility Micro-Polish

### AUDITED (tidak diubah — sudah benar)
- 10 `IconButton` di file — semua sudah punya `contentDescription`
  bermakna ("Kembali", "Studio", "Menu lainnya", "Play"/"Pause",
  "Pilih semua", "Hapus yang dipilih", "Batal pilih"/"Kembali", dst).
- 27 `Icon(contentDescription = null)` — direview satu-satu, semuanya
  ikon dekoratif bersanding langsung dengan `Text` (leadingIcon chip,
  label menu, dsb.) — `null` di situ justru praktik a11y yang BENAR
  (mencegah TalkBack mengumumkan dua kali: ikon + teks di sampingnya).
- `FilterChip` (selected state) & tombol destruktif (Hapus) — semantics
  selected/warna sudah ditangani otomatis oleh komponen M3, tidak ada
  yang menyesatkan.

### FOUND & FIXED
`TrimHandle` (drag handle batas potong video) — dipakai BERSAMA oleh
ResizerScreen, GifScreen, dan CompressorScreen lewat `VideoEditorPreview`
yang sama — outer touch-target `Box`-nya (raw `pointerInput` +
`detectDragGestures`, bukan komponen Material bawaan) sama sekali TIDAK
punya semantics node. Buat pengguna TalkBack, handle ini tidak terlihat/
tidak punya nama sama sekali — kontrol krusial (menentukan potongan video)
yang sepenuhnya tidak terjangkau non-visual.

### CHANGED
- `TrimHandle`: parameter baru `label: String`.
- `Modifier.semantics { contentDescription = label }` ditambah ke outer
  touch-target Box (48dp), di atas `Modifier.pointerInput`.
- 2 titik pemanggilan (start/end handle) diisi label deskriptif:
  `"Batas awal potongan, <waktu>"` / `"Batas akhir potongan, <waktu>"`,
  pakai `formatSeconds(startMs/endMs)` yang sudah ada di scope yang sama
  — tidak ada helper/format baru.

### CONSIDERED, TIDAK DILAKUKAN
Custom accessibility actions (mis. drag lewat TalkBack gesture/slider
role penuh) — di luar scope micro-polish, akan jadi "framework
accessibility baru" yang eksplisit dilarang panduan. `contentDescription`
statis + info posisi saat ini sudah memberi konteks bermakna tanpa itu.

### VERIFIED
- Static: grep ulang semua `IconButton(` dan `contentDescription = null`
  di file, direview satu-satu.
- `startMs`/`endMs`/`formatSeconds` dikonfirmasi sudah ada di scope
  pemanggil (dipakai juga oleh Text "Potong: ..." di baris yang sama).
- Import `androidx.compose.ui.semantics.semantics` +
  `.contentDescription` ditambah (belum ada sebelumnya).
- Balance kurung `{}`/`()` MainActivity.kt dicek (net 0/0).

### NOT VERIFIED
- Runtime/device: belum dites dengan TalkBack sungguhan di
  device/emulator pada sesi ini.

### UNTOUCHED
- Progress indicators (`CircularProgressIndicator`, teks "X%") — sudah
  disertai `Text` yang otomatis terbaca TalkBack, tidak ada isu nyata.
- Logika drag/gesture TrimHandle itu sendiri — tidak disentuh sama
  sekali, murni tambahan semantics.

### VERDICT
Prioritas 7: **selesai untuk temuan konkret yang ada**. File diubah:
`MainActivity.kt` saja (1 file).

## Batch 45: MICRO_POLISH_GUIDE Prioritas 6 — Responsive/Font-Scale Polish

### AUDITED (tidak diubah — sudah benar)
- Seluruh 27 pemanggilan `FilterChip` (16 kluster) — semua sudah dibungkus
  `LazyRow`, otomatis scrollable, tidak ada risiko clipping/overflow di
  font-scale besar atau layar sempit.
- Studio action row (`StudioEntryCard`) — sudah difix Batch 41
  (`weight(1f, fill=false).horizontalScroll`, delete dipin di luar
  scroll). Tidak disentuh.
- Dialog input (CustomResolution/CustomBitrate/TargetSize×2) — tombol
  Save/Cancel M3 `AlertDialog` bawaan, sudah responsif secara default.

### FOUND & FIXED
Row aksi hasil export "Bagikan" / "Buka di Galeri" di **GifScreen** &
**CompressorScreen** pakai `Button`/`OutlinedButton` (lebih lebar dari
`TextButton` — padding horizontal M3 lebih besar) berdampingan dalam
`Row(horizontalArrangement = spacedBy(10.dp))` polos, TANPA proteksi
scroll/wrap. Di layar sempit (~360dp) + font-scale besar (130%+, umum di
setting aksesibilitas), 2 tombol ini berisiko overflow di luar batas layar
tanpa cara menjangkaunya. ResizerScreen punya Row serupa (baris ~1628)
tapi pakai `TextButton` yang lebih ringkas (risiko lebih kecil, tapi
tetap tidak dilindungi) — ikut difix untuk konsistensi.

### CHANGED
- 3 titik Row (ResizerScreen ~1628, GifScreen ~4410, CompressorScreen
  ~4866): tambah `Modifier.horizontalScroll(rememberScrollState())`.
  Pola identik dengan fix Studio action row Batch 41 — bukan pola baru.
  Tidak ada perubahan gaya/warna/ukuran tombol (identitas visual utuh).

### VERIFIED
- Static: grep ulang semua `Row(` yang berisi ≥2 komponen
  Button-family, cross-check mana yang sudah/belum pakai
  `horizontalScroll`/`weight`.
- Import `horizontalScroll`/`rememberScrollState` sudah ada di file,
  tidak perlu import baru.
- Balance kurung `{}`/`()` MainActivity.kt dicek (net 0/0).

### NOT VERIFIED
- Runtime/device: tampilan aktual di font-scale besar/layar sempit
  sungguhan tidak dites di device/emulator pada sesi ini.

### UNTOUCHED
- Resolution/bitrate controls, progress UI, dialog — sudah diperiksa,
  tidak ditemukan isu clipping/overflow nyata, tidak disentuh. Gaya
  tombol (Button vs TextButton per screen) sengaja dibiarkan berbeda —
  itu identitas visual eksisting, bukan bug, di luar scope Prioritas 6.

### VERDICT
Prioritas 6: **selesai untuk temuan konkret yang ada**. File diubah:
`MainActivity.kt` saja (1 file, 3 titik, sesuai micro-batch limit).

## Batch 44: MICRO_POLISH_GUIDE Prioritas 5 — Lifecycle Filmstrip/Frame Extraction

### CHANGED
- Helper baru `decodeSampledBitmapFromFile(path, reqHeightPx)` di
  MainActivity.kt: `inJustDecodeBounds` baca dimensi dulu tanpa alokasi
  pixel, hitung `inSampleSize` power-of-two, baru decode downsampled.
- `StudioEntryCard` (list history Studio, thumbnail 72dp): ganti
  `BitmapFactory.decodeFile()` mentah → `decodeSampledBitmapFromFile(path, 240)`.
- `ResizerScreen`'s `resultThumbnailBitmap` (preview before/after,
  setengah-lebar-layar): sama, → `decodeSampledBitmapFromFile(path, 480)`.

### AUDITED (tidak diubah — sudah benar)
- `LaunchedEffect(selectedUri, durationMs)` di ResizerScreen/GifScreen/
  CompressorScreen (filmstrip 8-frame): Compose structured concurrency
  sudah otomatis benar — key baru cancel coroutine lama; kalaupun
  extraction lama sempat selesai di background sebelum ter-cancel,
  resume coroutine yang sudah dibatalkan tidak akan sempat menjalankan
  assignment `filmstrip = ...` (CancellationException dilempar duluan).
  Jadi "ekstraksi frame usang menimpa UI aktif" & "ganti video cepat
  nyisa preview basi" TIDAK terbukti sebagai bug nyata di kode saat ini.
- `FilmstripExtractor.extract()`: raw frame dari `getFrameAtTime()` sudah
  di-`recycle()` setelah discale (kalau scale benar-benar terjadi) — sudah
  benar, tidak disentuh.

### FOUND & FIXED
Bug nyata yang ditemukan: 2 titik decode thumbnail dari file JPEG hasil
`extractVideoThumbnail()` — yang disimpan pada RESOLUSI OUTPUT PENUH (bisa
1080p+, ~8MB per Bitmap ARGB_8888 saat didekode mentah) — didekode ulang
tanpa downsampling padahal cuma ditampilkan kecil (72dp list item / preview
setengah-layar). Compounding risk terbesar ada di `StudioEntryCard` karena
dipanggil per-row di `LazyColumn` (berpotensi banyak instance hidup
bersamaan saat scroll cepat).

### CONSIDERED, TIDAK DILAKUKAN
Sempat menambah `.recycle()` manual untuk bitmap lama (baik saat
`resultThumbnailBitmap` diganti, maupun via `DisposableEffect(onDispose)`
di `StudioEntryCard`) — di-drop setelah dipertimbangkan ulang: downsampling
sendiri sudah memangkas alokasi ~10x, dan recycle() manual di titik yang
mungkin masih aktif di-draw Compose berisiko crash "Bitmap already
recycled" yang tidak bisa diverifikasi tanpa runtime testing di sesi ini.
Sesuai VERIFICATION GATE #8 (kalau runtime testing tidak tersedia,
nyatakan eksplisit, jangan klaim lolos) — patch teraman yang dipilih.

### VERIFIED
- Static: setiap call site `BitmapFactory.decodeFile`/`decodeStream` di
  file di-grep ulang & diperiksa satu-satu.
- Balance kurung `{}`/`()` seluruh MainActivity.kt dicek (net 0/0).
- Import `Bitmap`/`BitmapFactory` sudah ada, tidak perlu import baru.

### NOT VERIFIED
- Runtime/device: perilaku decode/scroll performa Studio list & preview
  before/after tidak dites di device/emulator sungguhan pada sesi ini.

### UNTOUCHED
- Logika resize/compress/export, Media3/encoder, navigasi, persistence —
  tidak disentuh sama sekali. `AsyncThumbnail` (thumbnail gambar watermark
  yang dipilih user, BUKAN frame video) sengaja tidak disentuh — di luar
  scope Prioritas 5 ("ekstraksi thumbnail/filmstrip VIDEO").

### VERDICT
Prioritas 5: **selesai untuk temuan konkret yang ada**. File diubah:
`MainActivity.kt` saja (1 file, sesuai micro-batch limit).

## Batch 43: Audit + Fix Back/Cancel Konsistensi Antar-Screen

### AUDITED
Seluruh screen (`ResizerScreen`, `BatchScreen`, `GifScreen`,
`CompressorScreen`, `StudioScreen`) + semua 13 `AlertDialog` di file,
fokus pada perilaku back/cancel saat proses sedang berjalan.

### FIXED
`MainActivity.kt`, `BatchScreen` & `GifScreen` — kedua screen ini
adalah satu-satunya yang TIDAK konfirmasi sebelum keluar saat proses
(export batch / pembuatan GIF) sedang berjalan; back (system gesture
maupun toolbar arrow) langsung `cancel...()` + `onBack()` secara
diam-diam, kehilangan progres tanpa peringatan sama sekali.
`ResizerScreen` dan `CompressorScreen` sudah punya proteksi ini dari
batch sebelumnya. Fix: pola `showExitWhileProcessingConfirm` (dialog
"Batalkan proses?" + tombol "Batalkan proses"/"Tetap di sini")
disamakan ke `BatchScreen` & `GifScreen` — 3 titik per screen:
`BackHandler`, `IconButton` navigationIcon toolbar, dan dialog
konfirmasi itu sendiri. Back/cancel saat idle (tidak sedang proses)
tidak berubah — tetap langsung `onBack()`.

### REVIEWED (bukan bug)
4 `AlertDialog` input custom (Resolusi Kustom, Bitrate Kustom, Ukuran
Target×2 untuk Resizer & Batch) sengaja set
`dismissOnBackPress = false` + `onDismissRequest` kosong, sehingga back
tidak menutup dialog. Ini proteksi anti-accidental-dismiss yang
disengaja dari batch sebelumnya (form punya tombol Save/Cancel
eksplisit di bawahnya) — bukan gap, tidak disentuh.

### File disentuh
`MainActivity.kt` (1 file).

## Batch 42: Transisi Navigasi Ala iOS (Push/Pop Slide + Fade)

### CHANGED
`MainActivity.kt`, `VideoResizerApp` — navigasi ke 4 full-screen
overlay (`BatchScreen`, `GifScreen`, `CompressorScreen`, `StudioScreen`)
sebelumnya `if (screen == Screen.X) { Screen(...) }`: nol animasi,
screen langsung muncul/hilang instan tiap tap. Diganti
`AnimatedVisibility` dengan transisi ala `UINavigationController` iOS:
- **Masuk:** slide dari tepi kanan layar ke posisi 0 + fade in,
  `tween(350ms, easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f))`
  (kurva ease-in-out, dekat dengan default push curve iOS).
- **Keluar:** slide ke tepi kanan + fade out, `tween(300ms)` — sedikit
  lebih cepat dari masuk, meniru pop iOS yang terasa lebih ringan.
- Konstanta (`IosPushEasing`, `IOS_PUSH_MS`, `IOS_POP_MS`) dideklarasi
  sekali di atas `VideoResizerApp`, dipakai identik di semua 4 tempat
  — konsisten, bukan hanya 1 screen yang diperbaiki.
- Audit: sebelumnya cuma ada 1 titik animasi lain di seluruh app (fade
  overlay tombol play/pause di preview video, durasi default sudah
  wajar) — tidak disentuh, di luar scope keluhan.
- File disentuh: `MainActivity.kt` (1 file).

## Batch 41: Studio — Sweep-Select + Fix Tombol Hapus Tak Terlihat

### FIXED
`MainActivity.kt`, `StudioEntryCard` — tombol hapus (ikon `Delete`)
sebelumnya ada di dalam `Row(Modifier.horizontalScroll(...))` bersama
3 `TextButton` ("Edit ulang", "Galeri", "Bagikan"). Di layar sempit,
keempatnya tidak muat sekaligus sehingga ikon hapus tergeser keluar
viewport — user harus scroll horizontal manual di dalam kartu untuk
menemukannya (screenshot user tidak menampakkan tombol hapus sama
sekali). Fix: `IconButton` hapus dipindah keluar dari row yang
di-scroll, sekarang selalu terlihat fixed di sisi kanan kartu.

### ADDED
`MainActivity.kt`, `StudioScreen` & `StudioEntryCard` — fitur
sweep-select (multi-select untuk hapus massal), sebelumnya tidak ada
sama sekali:
- Long-press kartu mana pun → masuk selection mode. Tap kartu lain
  untuk toggle pilih/batal.
- Indikator: checkbox (`CheckCircle`/`RadioButtonUnchecked`) muncul di
  kiri thumbnail saat selection mode aktif; kartu terpilih dapat
  highlight (`primaryContainer` + border tebal).
- `TopAppBar` berubah saat selection mode: judul jadi "X dipilih",
  navigation icon jadi `Close` (keluar mode), actions jadi "Pilih
  Semua" (`CheckCircle`) + "Hapus yang dipilih" (`DeleteSweep`).
- Bulk delete lewat `AlertDialog` konfirmasi terpisah
  (`pendingBulkDelete`), pola sama persis dengan single-delete
  confirm dialog yang sudah ada (mencegah mis-tap hapus permanen).
- System back / tombol close saat selection mode aktif → keluar dari
  selection mode dulu, bukan langsung navigasi keluar Studio.
- Audit "pola serupa di seluruh sektor" (per instruksi user): dicek
  seluruh file, hanya ada **1** `LazyColumn`/list screen di app
  (`StudioScreen`) — jadi fitur ini sudah menjangkau semua tempat yang
  relevan, tidak ada sektor lain yang butuh fitur sama.
- File disentuh: `MainActivity.kt` (1 file).

## Batch 40: Crash Fix — SecurityException saat "Buka di Galeri"

### FIXED
`MainActivity.kt`, `openInGallery` & `openGifInGallery` — crash log user
(`crash_20260822_224435_*.txt`): `SecurityException: UID 10555 does not
have permission to content://media/external/video/media/1000043486`,
terjadi saat `context.startActivity(intent)`.
- Root cause: kedua fungsi memanggil
  `intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)` pada
  `content://media/...` URI (MediaStore) yang bukan milik app (bukan
  URI FileProvider app sendiri). Flag ini hanya valid dipakai app yang
  memang berhak grant akses ke URI tsb; untuk URI MediaStore publik
  sistem menolaknya → `SecurityException`.
- Fix: hapus `addFlags(FLAG_GRANT_READ_URI_PERMISSION)` di kedua fungsi
  (tidak diperlukan — `ACTION_VIEW` ke `content://media/...` publik
  sudah bisa dibuka aplikasi Galeri tanpa grant tambahan). Tambah
  `catch (e: SecurityException)` di kedua fungsi sbg fail-safe kedua
  (toast pesan gagal, bukan crash).
- File disentuh: `MainActivity.kt` (1 file).

## Batch 39: Eksekusi Prioritas 4 MICRO_POLISH_GUIDE.md — Kejelasan Estimasi Ukuran File

### CHANGED
`MainActivity.kt`, `CompressorScreen` — kotak perbandingan "Ukuran asli /
Perkiraan hasil":
- Angka "Perkiraan hasil": `formatFileSize(estimatedNewSizeBytes)` →
  `"~${formatFileSize(estimatedNewSizeBytes)}"` (tambah prefix tilde,
  pola sama persis dengan estimasi ukuran di ResizerScreen).
- Caption di bawahnya: `"Hemat sekitar ${savedPercent}% — encode H.265,
  resolusi & kualitas tampilan tetap sama."` → ditambah kalimat
  `"Perkiraan kasar, ukuran hasil akhir bisa sedikit berbeda."` di
  akhirnya.

### AUDIT (3 lokasi ditemukan, hanya 1 butuh fix)
1. **ResizerScreen** — `"Perkiraan ukuran: ~X (kasar, hasil asli bisa
   sedikit berbeda)."` — sudah punya tilde + disclaimer eksplisit. Sudah
   benar → **tidak disentuh**.
2. **TargetSizeDialog** — `"≈ X kbps untuk durasi klip yang dipilih saat
   ini."` — sudah pakai simbol ≈ (bitrate, bukan ukuran file langsung,
   tapi prinsip sama). Sudah benar → **tidak disentuh**.
3. **CompressorScreen** — kotak "Ukuran asli / Perkiraan hasil": label
   "Perkiraan hasil" sudah ada, TAPI angkanya sendiri tidak bertanda
   estimasi (tanpa tilde) dan caption di bawahnya tidak menyebut hasil
   akhir bisa berbeda — **GAP, difix** (lihat CHANGED di atas).

### VERIFIED
- Baca ulang `estimateCompressedSizeBytes` di `VideoResizer.kt` — TIDAK
  disentuh, hanya string display yang berubah, kalkulasi identik 1:1.
- Brace `{}` / paren `()` balance seluruh file setelah edit: 1096/1096,
  2175/2175 (naik dari 1095/1095 & 2175/2175 di Batch 38 — bertambah 1
  brace dari interpolasi `"~${...}"` baru, seimbang & diharapkan).
- Grep `"Perkiraan hasil"` → hanya 1 kemunculan, sesuai lokasi yang
  dimaksud, tidak ada duplikasi tak sengaja.

### NOT VERIFIED
Build Gradle & runtime device (tidak ada environment Android build di
sandbox ini). Perubahan murni string literal, risiko compile error
praktis nol.

### UNTOUCHED
Logika/algoritma `estimateCompressedSizeBytes` & `estimateOutputSizeBytes`
tidak disentuh sama sekali (sesuai larangan "DILARANG menambah algoritma
prediksi rumit" — bukan cuma tidak ditambah, memang tidak perlu diubah
sama sekali karena masalahnya murni presentasi). ResizerScreen &
TargetSizeDialog tidak disentuh (sudah benar). Semua logika resize/
compress/export, navigasi, persistence, signing/versioning/CI tidak
disentuh.

### VERDICT
Prioritas 4 — COMPLETE.

## Batch 38: Eksekusi Prioritas 3 MICRO_POLISH_GUIDE.md — Action Row Layar Kecil

### CHANGED
`MainActivity.kt` — Studio history card action row (`StudioScreen`, row
berisi `Edit ulang` / `Galeri` opsional / `Bagikan` / icon `Hapus`)
sekarang `Modifier.horizontalScroll(rememberScrollState())`. +1 import
baru: `androidx.compose.foundation.horizontalScroll`.

### KENAPA
Row ini ada di dalam `Card` (fillMaxWidth, padding 12dp) → `Row` berisi
thumbnail 72dp + spacer 12dp → `Column(weight(1f))` yang memuat SEMUA teks
detail entry DAN action row ini di baris paling bawah. Di layar sempit
(±360dp), ruang tersisa untuk Column itu hanya sekitar 250dp, sementara 4
item action row (2 TextButton teks + 1 TextButton + 1 IconButton) butuh
lebih dari itu → berisiko clipping/terpotong. Row lain yang berisi tombol
serupa (ResizerScreen, GifScreen, CompressorScreen) hanya 2 item DAN
punya lebar layar penuh (bukan di dalam Column tersempit thumbnail),
sehingga tidak diaudit sebagai berisiko dan sengaja tidak disentuh.

### FIX YANG DIPILIH (dan yang TIDAK dipilih)
`horizontalScroll` dipilih karena: (a) modifier bawaan Compose Foundation
yang SUDAH dipakai polanya di file ini (`verticalScroll` pada beberapa
dialog) — bukan abstraksi baru; (b) menjamin nol-clipping di semua lebar
layar & semua font-scale tanpa perlu logika kondisional; (c) tidak
mengubah teks/urutan/perilaku tombol sama sekali, hanya menambah
kemampuan scroll kalau kontennya memang lebih lebar dari ruang tersedia
(kalau muat, tidak terlihat beda sama sekali). Alternatif yang
dipertimbangkan tapi TIDAK dipakai: FlowRow (butuh cek versi Compose
foundation-layout & bisa jadi masih experimental di versi project ini —
risiko lebih tinggi untuk gain yang sama), mengecilkan padding/spacing
(rapuh, bisa tetap clip di font-scale besar atau layar sangat sempit).

### VERIFIED
- Brace `{}` dan paren `()` balance seluruh file dihitung ulang setelah
  edit: 1095/1095 dan 2175/2175 (sebelum edit juga sama-seimbang) — tidak
  ada syntax error terjadi dari penyisipan modifier.
- Baca ulang seluruh isi Row (4 child composable) untuk pastikan tidak
  ada yang tertinggal/terhapus saat menyisipkan parameter `modifier`.
- Grep konfirmasi `horizontalScroll` hanya dipakai di 1 tempat yang
  dimaksud, import baru hanya 1 baris.

### NOT VERIFIED
Build Gradle & runtime device/emulator (tidak ada environment Android
build di sandbox ini) — tidak bisa memverifikasi visual scroll behavior
secara langsung, hanya lewat pembacaan kode & pola yang identik dengan
`verticalScroll` yang sudah terbukti jalan di dialog lain di file yang
sama.

### UNTOUCHED
Tidak ada redesign StudioScreen. Urutan/teks/logika tombol (Edit ulang/
Galeri/Bagikan/Hapus) sama persis. Row 2-tombol lain (Resizer/Gif/
Compressor) tidak disentuh. Semua logika resize/compress/export,
navigasi, persistence, signing/versioning/CI tidak disentuh.

### VERDICT
Prioritas 3 — COMPLETE.

## Batch 37: Eksekusi Prioritas 2 MICRO_POLISH_GUIDE.md — Presentasi Error

### CHANGED
`MainActivity.kt` — audit 7 pemakaian Toast, 3 dikonversi ke mekanisme
inline `message` yang sudah ada di screen terkait:
- `GifScreen.handlePickedVideo`: Toast "Video ini tidak bisa dibaca." →
  `message = "Video ini tidak bisa dibaca."`
- `GifScreen` prefill `LaunchedEffect`: Toast "Video sumber tidak lagi
  bisa diakses..." → `message = "..."` (teks sama persis, logika sama)
- `CompressorScreen.handlePickedVideo`: Toast "Video ini tidak bisa
  dibaca." → `message = "..."`

Ketiganya dipilih karena terjadi di dalam Composable screen yang SUDAH
punya slot tampilan inline `message?.let { Text(msg, ...) }` dan sudah
dipakai untuk pesan lain di screen yang sama (mis. "Dibatalkan.", "Gagal
membuat GIF: ..."), jadi ini konsistensi dengan pola yang sudah ada, bukan
infrastruktur baru.

### NOT CHANGED (dipertahankan sengaja, dengan alasan)
4 Toast di util function `openInGallery`, `shareVideo`, `shareGifFile`,
`openGifInGallery` TETAP Toast. Alasan: fungsi-fungsi ini menerima
`context: Context` polos dan dipanggil dari 4 screen berbeda (Studio,
Resizer, Gif, Compressor) — tidak ada state `message` yang bisa mereka
tulisi tanpa menambah parameter callback baru ke tiap pemanggil, yang
berarti abstraksi/plumbing baru. Panduan eksplisit melarang membangun
framework error baru dan mengizinkan Toast tetap dipakai "where it is
genuinely appropriate" — kasus ini masuk kategori itu.

### VERIFIED
- `grep -n "Toast"` → tersisa persis 4, semua di 4 util function yang
  memang dipertahankan sengaja (bukan residual tak sengaja).
- `grep -n 'message = "'` → 3 assignment baru muncul persis di tempat
  yang dimaksud, tidak ada baris yang masih punya Toast+message berbarengan
  (no duplicate messaging).
- Read-through: `message = null` reset di awal `handlePickedVideo` &
  prefill effect kedua screen tidak berubah — behavior "pesan lama hilang
  begitu user pilih video baru" tetap sama.

### NOT VERIFIED
Build Gradle & runtime device (tidak ada environment Android build di
sandbox ini). Perubahan sintaksis sederhana (1 baris Toast-call diganti 1
baris assignment ke var String? yang sudah dideklarasikan di scope yang
sama), risiko compile error praktis nol.

### UNTOUCHED
Semua logika resize/compress/export, navigasi, persistence, layout,
signing/versioning/CI — tidak disentuh. 4 Toast lain, styling `message`
Text (warna/urutan), dan pesan-pesan `message` lain yang sudah ada
(Dibatalkan/Gagal) tidak diubah.

### VERDICT
Prioritas 2 — COMPLETE.

## Batch 36: Eksekusi Prioritas 1 MICRO_POLISH_GUIDE.md — Konsistensi Bahasa UI

### CHANGED
`MainActivity.kt` — 17 string literal UI berbahasa Inggris diganti ke
Bahasa Indonesia (bahasa UI utama proyek):
- "Share" → "Bagikan" (4 lokasi: tombol Studio action row, tombol hasil
  Compressor, tombol hasil GifScreen, tombol hasil ResizerScreen)
- "Batch Export" → "Ekspor Batch" (judul top bar BatchScreen)
- "Custom Resolution" → "Resolusi Kustom" (judul dialog)
- "Custom Bitrate" → "Bitrate Kustom" (judul dialog)
- "Width" → "Lebar", "Height" → "Tinggi" (label field dialog resolusi)
- "Save" → "Simpan" (4 lokasi: dialog Resolusi/Bitrate/Target Size/Batch
  Target Size)
- "Cancel" → "Batal" (4 lokasi, pasangan tombol Save di atas)

Hanya string literal yang diubah — TIDAK ada rename identifier/fungsi/
variable Kotlin, TIDAK ada perubahan logika, TIDAK ada perubahan
navigation route.

### VERIFIED
- `grep` residual seluruh 8 kata contoh guide (Custom Resolution/Bitrate,
  Save, Cancel, Share, Width, Height, Batch Export) di MainActivity.kt →
  nihil, semua tergantikan.
- `grep` pola umum string Inggris kapital di Text/label/title/
  contentDescription pada MainActivity.kt, VideoPickerScreen.kt,
  AppUpdater.kt → nihil residual bermakna (hanya false-positive Indonesia
  seperti "Tetap di sini", "Tidak valid", "Edit ulang").
- Static read-through tiap call site yang diubah (17 lokasi) untuk
  memastikan tidak ada string lain di baris yang sama ikut tersentuh.

### NOT VERIFIED
- Build Gradle aktual & runtime device (tidak ada environment Android
  build di sandbox ini) — perubahan murni string literal 1:1 sehingga
  risiko compile error praktis nol, tapi tetap dicatat sesuai
  verification gate panduan.

### UNTOUCHED
Seluruh logika resize/compress/export, navigasi, persistence, layout
(spacing/wrapping), signing/keystore, versioning, CI — tidak disentuh
sama sekali batch ini.

### VERDICT
Prioritas 1 — COMPLETE.

## Batch 35: Tanam MICRO_POLISH_GUIDE.md sebagai standing playbook permanen

Sesuai permintaan user, panduan `VideoResizer_FINAL_MICRO_POLISH_
ClaudeOnly.md` (upload user) ditanam permanen ke project sebagai
`MICRO_POLISH_GUIDE.md` di root — bukan dieksekusi, hanya disimpan
sebagai referensi standing rule yang otomatis dibaca setiap sesi
berikutnya lewat context hierarchy PROJECT_STATE.md.

**Yang dilakukan:**
- File baru `MICRO_POLISH_GUIDE.md`: hard rules + 8 prioritas polish
  UI/UX (bahasa UI, error presentation, action row layar kecil, estimasi
  ukuran file, lifecycle filmstrip, responsive/font-scale, accessibility,
  duplikasi theme/system-bar) + verification gate + format laporan.
- `PROJECT_STATE.md`: standing rule baru + Pending Queue diarahkan ke
  eksekusi Prioritas 1 di batch berikutnya.
- `FILE_MANIFEST.txt`: baris manifest untuk file baru.

**TIDAK ada perubahan kode/logika/UI apa pun batch ini** — murni
dokumentasi/embedding, sesuai instruksi user ("tanamkan panduan tersebut
ke dalam project secara permanen"). Eksekusi aktual 8 prioritas menyusul
bertahap mulai batch berikutnya.

## Batch 34: Dialog konfirmasi back-saat-proses di CompressorScreen (penutup audit kompresi video)

Item terakhir Pending Queue dari audit Batch 31. `CompressorScreen`
sebelumnya membiarkan back (baik tombol panah toolbar maupun gesture/
tombol back sistem) langsung membatalkan proses & keluar tanpa
peringatan sama sekali — beda dari ResizerScreen yang sudah punya dialog
konfirmasi (`showExitWhileProcessingConfirm`, baris ~648) untuk kasus
yang sama persis.

**Fix:**
- Logika cancel-sungguhan (`activeTransformer?.cancel()` +
  `ExportForegroundService.stop()`, hasil fix Batch 31) difaktor jadi
  fungsi `cancelCompress()` — dipakai tombol "Batalkan" di progress bar
  DAN tombol "Batalkan proses" di dialog baru, tidak ada logika
  terduplikasi.
- Toolbar back-arrow & `BackHandler` sistem: kalau `isProcessing`, tampilkan
  `AlertDialog` ("Batalkan proses?" / "Video sedang dikompres. Keluar
  sekarang akan menghentikan dan membatalkan proses ini.") dengan pilihan
  "Batalkan proses" (merah, benar-benar cancel + keluar) atau "Tetap di
  sini" (dismiss, lanjut proses). Kalau tidak sedang proses, back tetap
  langsung `onBack()` seperti sebelumnya — tidak ada perubahan perilaku
  untuk kasus idle.

File diubah: `MainActivity.kt` saja. **Pending Queue sekarang kosong** —
audit sektor kompresi video yang diminta user di Batch 31 tuntas: 2
temuan HIGH (Transformer.cancel() sungguhan, foreground service), 2
temuan MEDIUM/LOW (fps asli, audio bitrate asli), 1 item UX (dialog
konfirmasi ini) — semuanya sudah diperbaiki lintas Batch 31-34.

## Batch 33: Fix estimasi audio flat 128kbps — probe audio track asli source

Lanjutan audit kompresi video (Batch 31 Pending Queue). Cap "jangan
re-encode di atas bitrate sumber" (85% dari estimasi bitrate video
sumber) dan preview before/after di UI selama ini asumsi flat 128kbps
AAC audio track pada SETIAP source, apapun kenyataannya. Source tanpa
audio track sama sekali (mis. hasil rekam layar tanpa suara) jadinya
salah dikurangi 128kbps dari estimasi bitrate video — memperketat cap
tanpa alasan, bikin hasil kompres sedikit lebih agresif dari seharusnya.

**Fix:**
- `CompressorScreen.loadSourceInfo()` — MediaExtractor pass yang sama
  dipakai Batch 32 untuk probe fps sekarang juga cek track audio: ada/
  tidaknya, dan `MediaFormat.KEY_BIT_RATE`-nya kalau tersedia.
- `CompressRequest` dapat 2 field baru: `sourceHasAudio: Boolean = true`,
  `sourceAudioBitrateBps: Int = 0`. Default `true`/`0` menjaga kompatibilitas
  — pemanggil lama tanpa field ini tetap dapat perilaku persis seperti
  sebelumnya (asumsi ada audio, fallback 128kbps).
- `estimateSourceBitrateBps` (VideoResizer.kt, dipakai saat export
  sungguhan) & `estimateCompressedSizeBytes` (dipakai preview UI) sama-sama
  diupdate: `!hasAudio → 0 bps`, `hasAudio & bitrate diketahui → pakai
  nilai asli`, `hasAudio tapi bitrate gak kebaca → fallback 128kbps` (sama
  seperti perilaku lama).

File diubah: `MainActivity.kt`, `VideoResizer.kt`. Pending Queue tersisa
hanya 1 item LOW (dialog konfirmasi back-saat-proses, UX nice-to-have) —
audit sektor kompresi video (Batch 31) tuntas untuk semua temuan
fungsional.

## Batch 32: Fix ASSUMED_FPS hardcoded — probe fps asli source utk target bitrate kompresi

Lanjutan audit kompresi video (Batch 31 Pending Queue #1). Formula target
bitrate H.265 di Compressor tab (`bits-per-pixel-per-frame × fps`) selama
ini selalu pakai `ASSUMED_FPS = 30` hardcoded, apapun fps asli source.
Video 60fps (rekaman aksi/slow-mo umum di HP modern) jadinya dikompres
dengan bitrate yang sebenarnya dihitung untuk setengah frame-rate-nya —
kualitas per-frame turun lebih dari yang dijanjikan preset "Rekomendasi"/
"Maksimal". Video 24fps sebaliknya dapat bitrate lebih besar dari perlu.

**Fix:**
- `CompressorScreen.loadSourceInfo()` sekarang probe fps asli lewat
  `android.media.MediaExtractor` + `MediaFormat.KEY_FRAME_RATE` pada video
  track pertama yang ditemukan. Sengaja BUKAN
  `MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE` — key itu cuma
  terisi untuk video slow-motion (capture rate ≠ playback rate), null di
  video normal.
- `CompressSourceInfo` dan `CompressRequest` dapat field baru `fps`/
  `sourceFps: Int`. 0 = gagal probe → fallback ke `ASSUMED_FPS` (perilaku
  lama, tidak berubah untuk kasus ini).
- `computeCompressTargetBitrateBps` (VideoResizer.kt, dipakai saat export
  sungguhan) dan `estimateCompressedSizeBytes` (dipakai preview
  before/after di UI) sama-sama pakai fps asli ini sekarang — preview yang
  ditampilkan ke user sebelum menekan "Kompres Video" jadi akurat sesuai
  hasil sebenarnya, bukan cuma export-nya saja yang benar.

File diubah: `MainActivity.kt`, `VideoResizer.kt`. Pending Queue tersisa
(estimasi audio 128kbps flat; dialog konfirmasi back-saat-proses) — lihat
PROJECT_STATE.md.

## Batch 31: Audit kompresi video — fix Transformer.cancel() & foreground service di CompressorScreen

Audit mendalam sektor kompresi video atas permintaan user, menuju "100%
polished". Membandingkan `CompressorScreen` terhadap pola yang sudah
terbukti benar di `ResizerScreen` dan `BatchScreen` (keduanya sudah pernah
melalui bug-fix serupa, lihat komentar "BUG FIX" di baris ~634 dan ~1821).

**Ditemukan (3 item), 1 diperbaiki batch ini:**

1. **[HIGH — DIPERBAIKI] Tombol "Batalkan" & back-saat-proses tidak
   menghentikan proses kompres sungguhan.** `CompressorScreen` menyimpan
   `activeJob: Job` dari `scope.launch { VideoResizer(context).compress(...) }`
   — tapi `compress()` bukan fungsi `suspend`, jadi Job itu selesai
   seketika setelah memulai pipeline async-nya, jauh sebelum user sempat
   menekan Batalkan. `activeJob?.cancel()` akibatnya cancel Job yang sudah
   mati, encoder (`Transformer`) tetap jalan di background sampai
   selesai sendiri, dan `onDone` callback-nya masih bisa menulis hasil ke
   galeri/history walau UI sudah menampilkan "Dibatalkan". Fix: ganti ke
   `activeTransformer: Transformer?` (persis pola ResizerScreen), cancel
   sungguhan lewat `activeTransformer?.cancel()` di kedua titik (tombol +
   BackHandler).
2. **[HIGH — DIPERBAIKI] Tidak pakai `ExportForegroundService`.**
   ResizerScreen dan BatchScreen membungkus proses panjangnya dengan
   foreground service (notifikasi progres + mencegah OS membunuh proses
   saat app di-background). `CompressorScreen` tidak sama sekali —
   kompres video besar sambil app di-background berisiko proses mati
   diam-diam tanpa hasil. Fix: tambah `ExportForegroundService.start/
   updateProgress/stop` mengikuti pola persis ResizerScreen.
3. **[MEDIUM — masuk Pending Queue, belum dikerjakan]** `ASSUMED_FPS = 30`
   hardcoded di formula target bitrate `CompressionLevel`, tidak
   menyesuaikan fps asli source (60fps/24fps) — lihat PROJECT_STATE.md
   Pending Queue #1 untuk detail & rencana fix.
4. **[LOW — masuk Pending Queue]** `estimateSourceBitrateBps` asumsi flat
   128kbps audio track. Lihat Pending Queue #2.

File diubah: `MainActivity.kt` saja (`CompressorScreen`). Tidak ada
perubahan pada `VideoResizer.kt` batch ini — item #3 dan #4 di atas ada
di sana tapi sengaja ditunda ke batch berikutnya sesuai Strict
Micro-Batching Rule (1 task krusial per batch).

## Batch 30: Standing rule permanen — dokumentasi baru wajib Bahasa Indonesia

Atas permintaan user, ditambahkan standing rule permanen di
PROJECT_STATE.md: seluruh entri dokumentasi baru (PROJECT_STATE.md,
CHANGELOG.md, FILE_MANIFEST.txt, dsb.) wajib ditulis Bahasa Indonesia
mulai batch ini, berlaku untuk semua sesi berikutnya tanpa kecuali.
Dokumentasi lama yang masih Bahasa Inggris (termasuk entri CHANGELOG di
bawah ini) sengaja TIDAK diterjemahkan/disentuh sesuai instruksi user.
Item Pending Queue lama soal penerjemahan penuh README.md/CHANGELOG.md
ke Bahasa Indonesia di-supersede dan ditutup — tidak ada task
penerjemahan retroaktif. Batch ini docs-only, tidak ada perubahan kode.

## Unreleased — Batch 24: Fold Update+Theme into overflow menu (fix title truncation regression)

Follow-up on Batch 23. Batch 23's `maxLines=1` + `TextOverflow.Ellipsis`
fix stopped the garbled two-line clipping, but with 6 action icons in the
bar (since Batch 21's standalone update button), the title slot was still
so narrow that "Video Resizer" truncated down to "Vi…" — not distorted,
but not acceptable per user feedback either.

Real fix: consolidated the Update-check button and the Theme picker (both
settings-like, not core video-editing actions) into a single "More"
(`MoreVert`) overflow `DropdownMenu`. This restores the exact 5-icon-wide
action bar (Compress/Batch/GIF/Studio/More) the title had before Batch
21, so it fits without truncation on typical screen widths again.

- `showThemeMenu` state renamed to `showMoreMenu` (now backs both).
- Update-check is now the first `DropdownMenuItem` in that menu, labeled
  "Cek update" / "Mengecek update…" while in flight, and disables itself
  during the check — a small incidental step toward Pending Queue item 1
  ("kurang feedback pada tombol update"), NOT the full audit that item
  still needs across the rest of the app.
- Theme options are the same 6 entries as before, now with leading icons
  and Indonesian labels ("Tema: Dark", "Tema: Light", "Tema: Ikuti
  sistem", "Tema: Midnight Neon", "Tema: Warm Paper", "Tema: Midnight
  Blue Glass").

1 file touched: `MainActivity.kt`.

## Unreleased — Batch 23: Fix TopAppBar title text distortion (wrap-then-clip)

Real UI bug, reported via screenshot: `ResizerScreen`'s `TopAppBar` title
Text ("Video Resizer") had no `maxLines`/`overflow` set. Since Batch 21
added a 6th action icon (update button) to the same bar, the title slot's
available width shrank enough that the text wrapped onto a second line —
which then got clipped by the bar's fixed height, rendering as garbled
fragments ("eo" / "Res" in the reported screenshot) instead of the full
"Video Resizer" label.

Fix: `Modifier.weight(1f, fill = false)` on the title `Text` (lets it
shrink to whatever width remains instead of forcing the Row wider than
available space), plus `maxLines = 1` and `overflow = TextOverflow.Ellipsis`
(guarantees a single clean line — ellipsized only if truly out of room,
never wrapped-then-clipped). 1 file touched: `MainActivity.kt`.

Requested alongside 2 other items this batch — per Strict Micro-Batching
Rule, only this one (the most critical / visually broken) was done. Other
2 items recorded in `PROJECT_STATE.md`'s Pending Queue:
1. Feedback kurang pada tombol update aplikasi (+ audit pola serupa di
   seluruh sektor project).
2. Dokumentasi repository wajib Bahasa Indonesia saja (README.md +
   CHANGELOG.md perlu diterjemahkan penuh, dikerjakan bertahap).

## Unreleased — Batch 22: Automated versioning (standing rule — no more manual version bumps, ever)

Per explicit user instruction, permanently enshrined as a standing rule in
`PROJECT_STATE.md` ("Standing rules" section, applies to all future
sessions without exception): version bump/name must be automatic from
GitHub CI, never manual, to eliminate the whole class of "forgot to bump"
bugs (the Batch 12 version-history gap being the concrete precedent).

`app/build.gradle.kts`: removed the hand-maintained `semanticVersionName`
literal (`"1.19"` as of Batch 20) entirely. `versionName` is now computed
as `"$appVersionMajor.$VERSION_CODE_OVERRIDE"` — fully derived from
`$GITHUB_RUN_NUMBER`, same source `versionCode` already used since Batch 7.
`appVersionMajor` (currently `1`) is the one remaining manual constant,
explicitly scoped to deliberate breaking/milestone bumps only — never
routine batch work.

`.github/workflows/build.yml`: both steps that used to `grep
'semanticVersionName = '` out of the gradle file (Locate APK,
Prepare-failure-log-name) now compute `VERSION_NAME="1.${GITHUB_RUN_NUMBER}"`
directly in bash, cross-referenced by comment with the gradle constant so
a future deliberate major bump touches both sites together. Tag format
(`v<versionName>-build<n>`) is unchanged, so `AppUpdater.kt`'s
`Regex("build(\\d+)")` parser (Batch 21) needed no changes.

2 files touched (`app/build.gradle.kts`, `.github/workflows/build.yml`) —
Strict Micro-Batching cap.

## Unreleased — Batch 21: In-app updater (GitHub Releases, streamed APK download + install)

New `AppUpdater.kt`: `check()` calls the GitHub Releases API for
`FDzaki-dev/Video-resizer`, parses the `-build<N>` suffix off the latest
release tag and compares it (as `1000+N`) against the installed
`versionCode` — reuses the exact numbering `app/build.gradle.kts` and
`.github/workflows/build.yml` already establish, no new "latest version"
source of truth. `download()` streams the APK asset chunk-by-chunk to
`cacheDir/updates/update.apk` via `HttpURLConnection` (15s connect / 20s
read timeout, `followRedirects(true)` for the S3/CDN 302, optional
`Authorization: Bearer` header support though unused for this public repo)
— never loads the whole APK into memory. `install()` reuses the existing
`FileProvider` authority to launch the system package installer;
`canInstall()`/`requestInstallPermission()` handle the API 26+
"install unknown apps" AppOps gate via `ACTION_MANAGE_UNKNOWN_APP_SOURCES`.

`MainActivity.kt`: `ResizerScreen`'s top bar gets a new `SystemUpdate`
icon button (shows a small spinner while checking) and an `AlertDialog`
covering all three `AppUpdater.CheckResult` states — Available (release
notes + "Unduh & Pasang" with a live progress bar), UpToDate (auto-dismiss,
no dialog shown), Error (message + OK).

`AndroidManifest.xml` (edit-parsial): added `INTERNET` +
`REQUEST_INSTALL_PACKAGES` permissions. No change to `file_paths.xml` —
its existing `cache-path` already covers the new `cacheDir/updates/`
output location.

3 files touched total (`AppUpdater.kt` new, `MainActivity.kt`,
`AndroidManifest.xml`) — Strict Micro-Batching cap for this batch.

## Unreleased — Batch 20: Fix CI compile failure from Batch 19 (missing @OptIn on CompressorScreen)

Real CI signal: `assembleRelease` → `compileReleaseKotlin FAILED`, three
errors, all pointing at the same missing annotation.

- **`MainActivity.kt`**: `CompressorScreen`'s `TopAppBar(...)` and
  `TopAppBarDefaults.topAppBarColors(...)` calls (both experimental
  Material3 APIs) had no `@OptIn(ExperimentalMaterial3Api::class)` on the
  function — `GifScreen`, defined immediately above it, already carries
  this exact annotation (every other Composable in this file using these
  APIs does too), so this was the one gap, not a project-wide pattern
  miss. Fixed by adding `@OptIn(ExperimentalMaterial3Api::class)` directly
  above `private fun CompressorScreen(onBack: () -> Unit) {`. No behavior
  change, no other lines touched.

## Unreleased — Batch 19: New Compressor tab (on-device H.265 re-encode)

New feature, no CI failure to fix this batch.

- **`VideoResizer.kt`** (additive only — `ResizeRequest`/`resize()`/
  `QualityOption` untouched):
  - `CompressionLevel` enum (`RECOMMENDED`, `MAXIMUM`) — each carries a
    bits-per-pixel-per-frame target tuned for H.265 to look visually
    transparent at a much lower bitrate than the source's own codec
    needed. Doc comment is deliberately explicit that this is *not*
    literally lossless — no re-encode of an already-lossy source can be —
    it's "no perceptible difference on a phone screen", the same honest
    framing any CapCut/InShot-style "smart compress" feature relies on.
  - `CompressRequest` data class — source identity + optional trim +
    `CompressionLevel`, no aspect/resolution/watermark/caption fields.
  - `VideoResizer.compress()` / `compressInternal()` — same
    try/catch-wrapped, `Transformer.Listener` + progress-poll pattern as
    `resize()`, but forces `TransformationRequest.setVideoMimeType(MimeTypes.VIDEO_H265)`
    with `DefaultEncoderFactory.setEnableFallback(true)` (falls back to
    whatever the device's encoder supports if there's no HEVC hardware
    encoder). No crop/rotate/watermark/caption effects — only adds a
    `Presentation` if the source's own width/height happen to be odd
    (encoders need even dimensions).
  - `computeCompressTargetBitrateBps()` / `estimateSourceBitrateBps()`
    (private) — resolves the target bitrate from the chosen level, capped
    at ~85% of the source's own estimated bitrate (file size ÷ duration,
    minus an assumed 128kbps audio track) so an already-efficient source
    is never re-encoded at a *higher* bitrate than it started with.
  - `VideoResizer.estimateCompressedSizeBytes()` (companion, static) —
    same formula as the real target-bitrate calc, callable from
    `CompressorScreen` before an export starts for the before/after size
    preview.
- **`MainActivity.kt`**:
  - `Screen.COMPRESSOR` added to the screen enum; `VideoResizerApp` renders
    `CompressorScreen` as the same kind of full-screen overlay Batch/GIF/
    Studio already use (stays composed underneath, nothing loses state).
  - New top-bar `IconButton` (`Icons.Filled.Compress`, needs
    `material-icons-extended` — already a dependency) next to
    Batch/GIF/Studio's icons, wired through a new `onOpenCompressor`
    param on `ResizerScreen`.
  - New `CompressorScreen` composable (end of file): reuses
    `VideoPickerScreen` for source pick, `VideoEditorPreview` for the
    trim UI, `formatFileSize`/`extractVideoThumbnail`/`shareVideo`/
    `openInGallery` — all already-private helpers in this same file, so
    nothing needed a visibility change to `internal`. Shows a live
    before/after size + "hemat ~N%" estimate as the level chip changes,
    then a progress bar + Share/"Buka di Galeri" on completion, same UX
    shape as GifScreen.
  - Saves a Studio history entry with `kind = "COMPRESS"` — no
    `VideoHistoryEntry` schema change, reuses existing fields (aspect/
    resolution/rotation default to ORIGINAL/NONE since Compressor doesn't
    touch any of those).
- **Not done this batch** (see PROJECT_STATE.md's pending items): Studio's
  "Edit ulang" doesn't yet have a COMPRESS-specific branch — reopening a
  COMPRESS entry currently falls into the default (non-GIF) path and
  reopens Resizer instead of Compressor. Deferred per the one-task-per-batch
  rule rather than scope-creeping this batch into StudioScreen's routing.
- **`app/build.gradle.kts`**: `semanticVersionName` `"1.18"` → `"1.19"`.

## Unreleased — Batch 18: Fix CI compile failure from Batch 17 (AnimatedVisibility receiver ambiguity)

Real CI signal: `assembleRelease` → `compileReleaseKotlin FAILED`, one error.

- **`MainActivity.kt`**: `AnimatedVisibility(...)` around the play/pause
  `IconButton` (added in Batch 17) failed to compile: `can't be called in
  this context by implicit receiver`. Cause — that call sits inside a `Box`
  which is itself nested inside the Card's outer `Column`; Kotlin's
  implicit-receiver lookup walks outward through enclosing scopes and found
  `ColumnScope.AnimatedVisibility` (an extension used for animating a
  Column child's presence) before falling back to the plain top-level
  `androidx.compose.animation.AnimatedVisibility` composable actually
  intended here — and the former isn't legally callable from inside a Box.
  Fix: fully-qualified the call as
  `androidx.compose.animation.AnimatedVisibility(...)`, which bypasses
  implicit-receiver resolution entirely, and removed the now-unused
  `import androidx.compose.animation.AnimatedVisibility`. No behavior
  change — same fade-in/fade-out, same trigger logic from Batch 17.

## Unreleased — Batch 17: Trim handle clip fix + play button auto-fade

Reported via screenshot against `VideoEditorPreview` (main resizer screen).

- **`MainActivity.kt` — `TrimHandle`**: fixed handle bar thinning near the
  ends of the track. Root cause: the visible 16dp bar was centered on
  `fraction * trackWidthPx` with no boundary clamp of its own; near
  fraction 0f/1f that center point puts part of the bar outside
  `[0, trackWidthPx]`, and the shared `.clip(RoundedCornerShape(10.dp))`
  on the parent Box (added in the earlier "asymmetric edges" fix) slices
  that overhanging part off — less of the bar survives the clip the
  closer it gets to an edge, which reads as progressive thinning. Added
  `barCorrectionPx`: clamps the bar's own left edge to
  `[0, trackWidthPx - handleWidthPx]` and offsets the visible bar inward
  by the clamp delta, so the bar itself never crosses the track boundary
  — nothing left for the clip to cut, at any fraction, and it now sits
  flush against the edge instead of overhanging OR thinning.
- **`MainActivity.kt` — `VideoEditorPreview`**: play/pause button now
  auto-fades. Added `controlsVisible` state driving an `AnimatedVisibility`
  (fadeIn/fadeOut) around the `IconButton`: hides 2.5s after playback
  starts, reappears immediately on tapping the button or anywhere on the
  video itself, and stays visible the whole time while paused/static
  (nothing to auto-hide from when there's no playback to watch
  unobstructed). Previously there was no fade logic at all — the button
  was permanently opaque regardless of play state.
- Investigated a third report ("big empty gaps" around the
  `720×720 • Potong: ...` row) — **not reproducible in current source**;
  that Card's internal spacing is a tight `spacedBy(12.dp)` /
  `spacedBy(20.dp)`, nowhere close to the size of gap described. No code
  change made. Likely cause: no APK has actually been *released* since
  before Batch 16's compile fix (`assembleRelease` was failing), so the
  screenshot is probably from an older installed build with different
  layout code. Needs a fresh screenshot from the next successful build to
  confirm either way.

## Unreleased — Batch 16: Fix CI compile failure (missing @OptIn)

Real CI signal from run #46 (`gradle assembleRelease`, exit code 1):
Kotlin compiler treated 3 experimental-Material3-API usages as errors,
all inside one function.

- **`app/src/main/java/com/example/videoresizer/VideoPickerScreen.kt`** —
  added `@OptIn(ExperimentalMaterial3Api::class)` above `internal fun
  VideoPickerScreen(...)`. That function's `TopAppBar` (line ~413) and
  `DropdownMenu`/`DropdownMenuItem` (line ~453) are experimental Material3
  APIs; every other Composable in the project using these APIs already has
  this annotation (`MainActivity.kt` has 5 instances) — this was the one
  function missing it, and the only compile error in the run.
- Investigated why the failed run's log bundle had no
  `log_fail_1.18_46` artifact even though `build.yml` defines that step:
  confirmed it's simply because that step (`Prepare failure log name` +
  `Upload failure logs`, both `if: failure()`) is already present and
  correctly wired in the current `build.yml` — nothing to fix there. The
  next push (with this fix) should compile clean and never hit that path
  at all.

## Unreleased — Batch 15: Stale Run Guard in build.yml (anti-desync)

Identified via preference audit: workflow had no protection against
GitHub's "Re-run jobs" being clicked on an *old* run after `main` had
since moved forward — that re-run keeps the original run's `$GITHUB_SHA`
(checkout still resolves to the old commit), so it would silently
build+publish a GitHub Release from stale source, indistinguishable from
a legitimate fresh release.

- **`.github/workflows/build.yml`** — new step `Stale Run Guard
  (Anti-Desync)` inserted immediately after `Checkout sources`, before JDK
  setup / build / signing / release. Resolves `origin/main`'s current tip
  via `git ls-remote origin refs/heads/main` and compares it to
  `$GITHUB_SHA`; on mismatch (or if the tip can't be resolved at all),
  logs an `::error::` explaining the likely cause and `exit 1`s
  immediately — before any build minutes are spent or any release step
  runs. On match, logs confirmation and proceeds exactly as before.
- `FILE_MANIFEST.txt` — `build.yml` row's inline note updated to mention
  the guard.
- `PROJECT_STATE.md` — batch bumped 14 → 15, history entry added.

## Unreleased — Batch 14: Add missing .gitattributes (protected-asset gap fix)

Identified via preference audit: `.gitattributes` is listed as a
[PROTECTED] asset category in user preferences but never actually existed
in this project. Added it now, config-only, zero app-code files touched.

- **`.gitattributes` (new)** — `* text=auto eol=lf` normalizes all
  source/text files (`.kt`, `.kts`, `.xml`, `.md`, `.properties`, etc.) to
  LF on checkin, avoiding CRLF diff noise between environments (Termux/
  Linux CI vs any Windows/macOS contributor). Binary assets
  (`release.keystore`, `*.apk`, `*.aab`, `*.jks`, images/fonts) are marked
  `binary -diff -merge` — `release.keystore` gets its own explicit line
  beyond the `*.keystore` pattern so it stays byte-exact and un-diffable
  even if that pattern is ever narrowed later.
- `FILE_MANIFEST.txt` — added `.gitattributes` row under ROOT, marked
  `[PROTECTED]`.
- `PROJECT_STATE.md` — batch bumped 13b → 14, history entry added.

## Unreleased — Batch 13: Custom in-app video picker (replaces OS Photo Picker for single-video pick)

User feedback on the existing flow: the OS Photo Picker's look (grid tiles,
OEM-themed "Video"/"Koleksi" tabs) isn't something this app controls, and
they wanted a list-style picker instead — one row per video with thumbnail
+ filename + duration + resolution + date + size, plus a Videos/Folders
split, styled after a reference file-manager-style picker they use. Also
asked for a clear cancel affordance for a wrong tap.

- **`VideoPickerScreen.kt` (new)** — full-screen composable, queries
  `MediaStore.Video.Media` directly (already covered by the existing
  `READ_MEDIA_VIDEO`/`READ_EXTERNAL_STORAGE` manifest permission, so no new
  permission was added):
  - **Videos tab**: flat list, newest-first by default, each row showing a
    lazily-loaded thumbnail (`ContentResolver.loadThumbnail` on API 29+,
    the older `MediaStore.Video.Thumbnails` table below that — minSdk is
    still 24), filename, `mm:ss • WxH`, and `date • size`.
  - **Folders tab**: videos grouped by `BUCKET_ID`/`BUCKET_DISPLAY_NAME`,
    each row showing an item/duration/size summary and the most-recent
    video's thumbnail as the cover; tapping drills into that folder's own
    video list (same row layout as the Videos tab).
  - **Sort menu** (top bar): Terbaru / Terlama / Nama (A-Z) / Ukuran
    terbesar — applies to whichever video list is currently showing
    (root Videos tab or an open folder).
  - **Cancel**: both the back arrow (steps out of an open folder first,
    only exits the picker once already at the root) and an always-visible
    "Batal" text button in the top bar — the explicit ask from this batch,
    so a wrong tap into the picker always has one unambiguous way out
    regardless of whether a folder is open.
  - **Permission handling**: if READ_MEDIA_VIDEO/READ_EXTERNAL_STORAGE
    isn't granted, shows an explanatory panel with a Grant button instead
    of silently listing nothing.
- **`ResizerScreen`/`GifScreen` integration (`MainActivity.kt`)** — the
  `ActivityResultContracts.PickVisualMedia()` launcher used for the single
  main-video pick is gone from both screens; their `VideoPickerCard` tap,
  "Ganti video", and the change-video confirm dialog now all set a local
  `showVideoPicker` flag instead, which renders `VideoPickerScreen` as a
  full-screen overlay — same "Scaffold wrapped in a Box, overlay drawn on
  top, nothing underneath loses state" pattern `VideoResizerApp` already
  uses for Studio/Batch/GIF. The old picker-launcher callback body was
  extracted into a plain `handlePickedVideo(uri)` function in each screen
  so the metadata-load/state-reset logic didn't need duplicating.
- **Explicitly out of scope this batch**: `BatchScreen`'s multi-video pick
  (`PickMultipleVisualMedia`) and both watermark-image pickers
  (`PickVisualMedia.ImageOnly`, in `ResizerScreen`/`BatchScreen`) are
  unchanged — the reported UI/reference screenshots were both single-video
  pick flows, and folding multi-select into the same custom picker is a
  larger change (selection state, a confirm/done bar, etc.) better done as
  its own batch if wanted.
- **Reported but not fixed this batch**: "UI asimetris" on the trim editor
  (`VideoEditorPreview`) — reviewed the composable's layout code closely
  (player box → time labels → filmstrip/trim-handle box → details row, all
  plain `Arrangement.spacedBy(12.dp)`, no `weight`/`fillMaxHeight` that
  could explain a large gap) and couldn't identify a concrete structural
  cause with high confidence from code review alone. Flagged back to the
  user for a pointer to the specific element rather than shipping a
  guessed fix — see PROJECT_STATE.md's pending items.
- `semanticVersionName` bumped `1.16` → `1.17` (app/build.gradle.kts).

## Unreleased — Batch 13b: Trim-editor edge-clip fix ("UI asimetris" follow-up)

User's answer to "which part looks asymmetric" was "all the parts that
don't look symmetric" — not specific enough to point at one thing, so
rather than keep asking, this batch ships the one concrete, code-level
issue Batch 13's review actually found and is confident about, instead of
guessing at layout changes with no way to verify them visually here.

- **`VideoEditorPreview`'s filmstrip/trim-handle `Box` now clips to the
  same rounded shape as the filmstrip itself** — the two `TrimHandle`s (and
  the dim overlays / selection frame) are siblings of the filmstrip `Row`
  in that `Box`, not children of it, so the filmstrip's own
  `.clip(RoundedCornerShape(10.dp))` never applied to them. Each handle's
  invisible 48dp touch target is wider than its visible 16dp bar, and at
  the two extremes (trim start at 0f / trim end at 1f) that let the
  *visible* bar hang ~8dp past the filmstrip's left/right edge — a
  lopsided sliver sitting outside the rounded corners on whichever side(s)
  the trim wasn't already pulled all the way to. Clipping the whole `Box`
  (filmstrip + overlays + frame + both handles together) to one shared
  shape makes every layer stop flush at the same edge; nothing in the
  stack can overhang anymore. Touch/drag behavior is unchanged — `clip`
  only affects drawing, not the handle's hit-test region.
- **Still open**: if this wasn't the asymmetry the user meant, the next
  step is a marked-up screenshot (arrow/circle on the actual element) —
  see PROJECT_STATE.md's pending items. Not re-guessing further without
  one, to avoid spending another batch on a possible no-op.
- `semanticVersionName` bumped `1.17` → `1.18`.

## Unreleased — Batch 12: Close out Batch 9's two scope cuts (BatchScreen controls, GIF history)

Both of Batch 9's explicitly-deferred items, done together since they're
both direct continuations of that same feature — user asked to "tuntaskan
yang pending+urgent" after confirming the Batch 11 build was green.

- **BatchScreen: Flip/Frame Rate/Target-Size (MB)** — same three controls
  ResizerScreen already had, now available for the whole queue:
  - Flip and Frame Rate: identical `OptionSection` chips to ResizerScreen's,
    new `flip`/`frameRate` state, passed straight through into every
    item's `ResizeRequest`.
  - Target-Size (MB) is **not** a single shared bitrate the way
    ResizerScreen's version is — a batch queue can hold videos of very
    different lengths, and one bitrate computed from one assumed duration
    would badly miss the target for anything shorter/longer than that.
    Instead `targetSizeMb: Double?` just stores the target itself; a new
    `BatchTargetSizeDialog` (deliberately without ResizerScreen's live
    "≈ X kbps" preview, since there's no single duration to compute it
    against here) collects it, and `startBatch()`'s per-item loop — which
    already probes each item's own duration before building its
    `ResizeRequest` — solves MB→kbps freshly for *that* item via the same
    `VideoResizer.requiredBitrateKbpsForTargetSize` ResizerScreen's dialog
    uses, falling back to `MIN_BITRATE_KBPS` (best effort, not a failure)
    for the rare item whose duration makes the target impossible.
  - The Quality chip row and the Social Preset row both now clear
    `targetSizeMb` when picked, so the three quality-selection paths
    (preset chips / custom kbps / target size) stay mutually exclusive —
    same as ResizerScreen's already-existing preset/custom-kbps exclusivity.
- **GIF exports now save to Studio history** (`VideoHistoryStore.kt` — new
  `kind`/`gifFps`/`gifWidthPx` fields, `"VIDEO"`/0/0 defaults so entries
  saved before this batch stay readable; `MainActivity.kt` — GifScreen
  writes an entry on export success):
  - `thumbnailPath` deliberately points at the GIF file itself, not a
    separately-extracted static frame — `BitmapFactory.decodeFile` (what
    `StudioEntryCard` already uses) reads a GIF's first frame as a plain
    `Bitmap` with zero extra code, unlike the video path which needs
    `extractVideoThumbnail` because `MediaMetadataRetriever` can't decode
    a *video* thumbnail without one.
  - `StudioEntryCard` now branches its detail line on `entry.kind`: GIF
    entries show "GIF • Nfps • Wpx" instead of the video-specific
    aspect/resolution/rotation/quality/watermark/caption/flip/frame-rate
    lines, since none of those fields apply to a GifExporter output.
  - "Edit ulang" on a GIF entry reopens `GifScreen` (not `ResizerScreen`)
    via a new, deliberately separate `GifPrefill` data class — a GIF
    entry carries none of `PrefillSettings`' resize-specific fields, so
    routing it through the video prefill path would've reopened the wrong
    screen with mostly-default settings.
  - Share/"Buka di Galeri" for a GIF entry now use `shareGifFile`/
    `shareGifUri` (new)/`openGifInGallery` (`image/gif` mime) instead of
    the video-mime helpers, via a `entry.kind == "GIF"` branch at the
    `StudioScreen` call site.
  - `GifScreen`'s picker-launcher metadata probe and its new prefill
    effect shared near-identical `MediaMetadataRetriever` code; factored
    into one local `loadSourceMetadata` suspend fun (same "local suspend
    fun inside the composable" convention `ResizerScreen`'s own
    `loadVideoMetadata` already uses) rather than duplicating it a third
    time.
- **Not done this batch**: the pre-Batch-5 duplicated `v1.13` GitHub
  Release still needs a manual `gh release delete v1.13 -y` — not
  something a code batch can act on, noted again here since it's still
  the one open item that isn't code.

## Unreleased — Batch 11: Debug/polish pass across GIF, target-size, and Studio history

Not a new feature — a review pass over everything Batch 9/10 touched
(user asked to "fokus debugging/polish semua fitur" after the green
build), looking for real bugs and rough edges the sandbox's structural
checks alone couldn't have caught.

- **Fixed**: GIF playback delay could drift from the actually-extracted
  frame spacing (`GifExporter.export`) — `delayCentiseconds` was computed
  from the *requested* fps, but the real spacing between frames
  (`actualIntervalMs`) only equals `1000/fps` when `MAX_FRAMES` isn't hit;
  for a clip long/fast enough to hit that cap, the GIF would've played
  back faster than the source. Now derives the delay from
  `actualIntervalMs` directly, so it's correct in both cases. (Currently
  unreachable in practice — the GIF screen's own frame-count estimate
  disables the button before this cap is hit — but `GifExporter` is a
  general-purpose object, not something that should only be correct when
  called through that one screen.)
- **Fixed**: `GifExporter` skipped a `frame.width <= 0`/`frame.height <= 0`
  guard — a corrupt/unreadable frame from `getFrameAtTime` would have
  produced `Infinity`/`NaN` math feeding `Bitmap.createScaledBitmap` and
  likely crashed the export. Now such frames are recycled and skipped,
  same as an outright-null frame already was.
- **Perf**: `GifExporter.quantizeFrame` rebuilt the palette's R/G/B split
  arrays on every single frame call, even though the palette is identical
  for the whole clip — up to `MAX_FRAMES` (200) redundant allocations of
  the same data. Split once in `export()` now, passed down instead.
- **UX fix**: `requiredBitrateKbpsForTargetSize` clamps its result to
  `MIN_BITRATE_KBPS..MAX_BITRATE_KBPS` (same floor/ceiling
  `estimateOutputSizeBytes` uses) so a bad target can't produce an
  unusable file — but an extreme target (e.g. 1MB for a 10-minute clip)
  silently returned a bitrate bigger than what the size implied, with the
  dialog showing "≈ X kbps" as if it were exact. `TargetSizeDialog` now
  detects the clamp and says so explicitly instead of quietly showing a
  number that looks precise but won't hit the target.
- **Consistency fix**: Studio history cards showed rotation/quality/
  watermark/caption but never flip or frame rate, even though both have
  been saved per-entry since Batch 9 — same blind spot as caption briefly
  was after Batch 8. Added a "Flip Horizontal • 30 fps"-style line,
  same pattern as the existing "Watermark aktif"/"Caption aktif" lines,
  only shown when either is non-default.
- **Code-hygiene fix**: `GifScreen` (new in Batch 9) had introduced three
  `!!` force-unwraps (`selectedUri!!`, `resultFile!!`, `galleryUri!!`),
  inconsistent with this file's established "local val capture instead of
  `!!`" convention (see the comments at `currentUri` in `ResizerScreen`,
  predating this batch) and with Batch 2's explicit goal of having none
  left in `MainActivity.kt`/`VideoResizer.kt`. Rewritten to match.

## Unreleased — Batch 10: Fix CI build failure (GifExporter.kt) + failure-log artifact

- **Bug fix (real compile error, not a style issue)**: `GifExporter.kt:165`
  — `sums[0] += r` where `sums` is a `LongArray` and `r`/`g`/`b` are `Int`
  failed to compile: `e: No set method providing array access`. Root
  cause: Kotlin has no implicit `Int` → `Long` widening, so the indexed
  `+=` desugaring to `sums.set(0, sums.get(0) + r)` can't resolve. Fixed
  with explicit `.toLong()` on each RHS (`r.toLong()`, `1L`, etc.). Found
  from the user-supplied Batch 9 CI failure log
  (`:app:compileReleaseKotlin FAILED`) — first real compiler signal on
  Batch 9's GIF code, exactly the kind of thing the sandbox's
  structural-checks-only verification can't catch.
- **New: failure-log artifact** (`.github/workflows/build.yml`,
  `[PROTECTED]`) — "Build release APK" now pipes its output through `tee
  build-output.log` (GitHub Actions' default bash runs with `pipefail`, so
  a failing `gradle` still fails the step through the pipe). Two new
  `if: failure()` steps follow: one computes a
  `log_fail_<version>_<run_number>` name from `semanticVersionName` +
  `$GITHUB_RUN_NUMBER` (falls back to "unknown" if the grep comes up
  empty, e.g. checkout itself failed), the other uploads
  `build-output.log` plus any Gradle/lint report files under that name via
  `actions/upload-artifact@v4`. Only runs when an earlier step in the job
  failed — a normal successful build produces no extra artifact.

## Unreleased — Batch 9 (Atomic): Video-ke-GIF, Flip/Frame Rate, Compress by Target Size

Atomic change (not split across multiple batches) because all three features
share the same export/UI surface (`VideoResizer.kt`'s pipeline,
`ResizeRequest`, and the Resizer screen's option sections) and reviewing
them together was more coherent than reviewing three partial diffs to the
same functions.

- **New: Video ke GIF** (`GifEncoder.kt`, `GifExporter.kt`, both new files;
  new `GifScreen` in `MainActivity.kt`, reachable via a new nav icon next
  to Batch) — converts a trimmed clip into an animated GIF, entirely
  independent of the Media3 Transformer pipeline the rest of the app uses,
  since GIF isn't a video codec Transformer can target:
  - `GifEncoder.kt` — a from-spec, dependency-free GIF89a + LZW encoder
    (no third-party GIF library). Single shared global color table for the
    whole clip, NETSCAPE2.0 loop extension, standard 12-bit/4096-entry LZW
    dictionary with clear-code reset.
  - `GifExporter.kt` — extracts frames via
    `MediaMetadataRetriever.getFrameAtTime` (same technique
    `FilmstripExtractor` already used, just at higher frame count/res),
    builds one 256-color palette for the whole clip with a frequency/
    popularity bucket algorithm (deliberately simpler than median-cut/
    NeuQuant — easier to verify by hand without a local compiler; a good
    target to revisit if quality matters more than this first pass),
    nearest-color-quantizes every frame against it, hands the result to
    `GifEncoder`. Hard-capped at `MAX_FRAMES = 200` as a backstop; the UI
    estimates frame count live and disables the button before hitting it.
  - Fps presets 5/10/15, width presets 240/360/480px, publishes to
    `Pictures/VideoResizer/` via a new `PublicMovieExporter.publishImage()`
    (mirrors the existing `publish()` for video, just `image/gif` mime and
    the Images MediaStore collection instead of Video).
  - **Not done this batch**: GIF results aren't written to Studio history
    (`VideoHistoryStore`) — they're share/gallery-only for now, same as a
    fresh video export before it's saved. Flagged for a future batch if
    GIF re-editing turns out to matter.
- **New: Flip / mirror** (`VideoResizer.kt` — `FlipOption` enum;
  `MainActivity.kt` — chip row in `ResizerScreen`, right under Rotasi) —
  horizontal/vertical mirror, folded into the *same*
  `ScaleAndRotateTransformation` that already handled rotation (its builder
  accepts scale and rotation together) rather than adding a second Effect
  entry, so flip and rotation compose in a single GL pass exactly like
  before this feature existed for rotation alone.
- **New: Frame rate control** (`VideoResizer.kt` — `FrameRateOption` enum,
  backed by `androidx.media3.effect.FrameDropEffect
  .createDefaultFrameDropEffect(targetFps)`; `MainActivity.kt` — chip row
  right under Flip) — Original/24/30/60fps presets. `FrameDropEffect` only
  needs the *target* rate (it decides per-frame keep/drop from presentation
  timestamps), so no "read the source's frame rate first" step was needed.
  **Required bumping `media3-transformer`/`-effect`/`-common`/`-exoplayer`/
  `-ui` from 1.3.1 → 1.4.1** (`app/build.gradle.kts`, `[PROTECTED]`,
  edited per the edit-parsial-only rule) — `FrameDropEffect` doesn't exist
  at 1.3.1. Deliberately stopped at 1.4.1, not the latest: Media3 1.6.0
  flips the `OverlaySettings` anchor-point sign convention (see the
  `WatermarkPosition` doc comment in `VideoResizer.kt`, added back when
  that was first discovered), which would silently break watermark/caption
  placement if picked up here. 1.4.1 is the earliest stable release with
  `FrameDropEffect` and predates that change.
- **New: Compress by Target File Size (MB)** (`VideoResizer.kt` — new
  `requiredBitrateKbpsForTargetSize(targetSizeMb, durationMs, muteAudio)`
  companion function, the algebraic inverse of the existing
  `estimateOutputSizeBytes`; `MainActivity.kt` — new `TargetSizeDialog` +
  an extra chip in the Quality row) — deliberately **not** a new
  `ResizeRequest` field or export-pipeline code path: the dialog solves
  MB → kbps once and writes the result into the *existing*
  `quality = CUSTOM` / `customBitrateKbps` state, the same state
  `CustomBitrateDialog` already drives. The export pipeline needed zero
  changes for this feature as a result.
- **`VideoHistoryStore.kt`**: `flipName`/`frameRateName` fields added to
  `VideoHistoryEntry` (with `FlipOption.NONE`/`FrameRateOption.ORIGINAL`
  fallbacks via `optString`, so entries saved before this batch still load
  and default to the pre-existing behavior), wired through "Edit ulang"
  the same way rotation/quality already were.
- **Not done this batch** (scope cut, noted rather than silently skipped):
  Flip/Frame Rate/Target-Size controls were added to `ResizerScreen` only,
  not `BatchScreen` — batch-export jobs currently always use
  `FlipOption.NONE`/`FrameRateOption.ORIGINAL` (safe defaults, matches old
  behavior exactly). Extending `BatchScreen` similarly is straightforward
  follow-up, deferred to keep this batch's diff reviewable.
- **Known limitation**: GIF export's "Batalkan" (cancel) button is a soft
  cancel — it hides the progress UI immediately, but `GifExporter.export`
  runs as one synchronous call on `Dispatchers.Default` with no internal
  cancellation checks, so frame extraction/quantization/encoding already in
  flight keeps running to completion in the background (result just gets
  discarded) rather than stopping instantly. Not a correctness bug — no
  wrong file is ever shown or saved — but worth knowing before assuming
  "Batalkan" frees up CPU immediately.

## Unreleased — Batch 8: Finish everything left pending (caption overlay, batch thumbnails, dynamic versionName)

Closes out every item that had been left as a "not done yet" note across
Batches 1–7, in one pass, instead of leaving them scattered:

- **New: text caption overlay** (`VideoResizer.kt`) — a short line of text
  burned into the exported video as a static overlay (white fill + black
  outline for legibility over any footage), reusing the exact same
  `OverlayEffect`/`BitmapOverlay` pipeline the watermark feature already
  uses: `buildWatermarkOverlay` is now a thin wrapper around a new shared
  `buildImageOverlay(uri, position, scale, opacity)`, and captions render
  their text to a PNG in the app's cache dir via a plain `Canvas`/`Paint`
  (`renderCaptionBitmap`) and feed that file's `Uri` through the same
  helper — so this adds zero new Media3 API surface, only reusing an
  already-working path. `ResizeRequest` gained `captionText`/
  `captionPosition` (the latter reuses `WatermarkPosition` rather than a
  new enum — same five anchor points apply). UI: both `ResizerScreen` and
  `BatchScreen` gained a "Caption" text field + position chips, directly
  below their existing watermark section. Fixed style/scale only (no
  color/size picker) for this first pass — same scope level the watermark
  feature itself started at before scale/opacity sliders were added later.
- **New: per-item thumbnail preview in the Batch Export queue**
  (`MainActivity.kt`'s `BatchScreen`) — each queued video now shows a small
  40dp preview next to its filename instead of just text. Reuses
  `FilmstripExtractor.extract(..., count = 1)` (already used for the trim
  scrubber) to grab a single frame per video on `Dispatchers.IO`, cached in
  a `Map<Uri, Bitmap>` keyed by Uri so re-picking more videos on top of an
  existing queue doesn't re-decode already-thumbnailed ones.
- **Studio history now remembers captions too** (`VideoHistoryStore.kt` +
  `MainActivity.kt`'s `PrefillSettings`) — `captionText`/
  `captionPositionName` added to `VideoHistoryEntry` (with safe
  `optString` fallbacks so older saved entries without these fields still
  load), and wired through "Edit ulang" the same way watermark settings
  already were, so re-opening a past export for editing doesn't silently
  drop its caption.
- **`versionName` is now dynamic too** (`app/build.gradle.kts` +
  `.github/workflows/build.yml`) — Batch 7 only made `versionCode`
  dynamic and deliberately left `versionName` as a static human label;
  revisited per this batch's "finish everything" scope. `versionName` now
  appends `-build<n>` using the same `VERSION_CODE_OVERRIDE` env var
  `versionCode` already reads, so a device's Settings > App info shows
  exactly which CI run produced the installed APK. The base label moved
  into a `val semanticVersionName = "1.13"` literal (still bumped
  manually per feature batch) so there's still exactly one clear place to
  change it, and `.github/workflows/build.yml`'s "Locate APK" step now
  greps that instead of the old (now-gone) plain `versionName = "..."`
  literal.

**Not respun in this batch, and why:** the already-duplicated `v1.13`
GitHub Release from before Batch 5's fix — that's a one-time manual
cleanup on the person's end (`gh release delete v1.13 -y` in Termux), not
something any code change here can reach back and undo.

## Unreleased — Batch 7: Dynamic versionCode

- **`app/build.gradle.kts`** — `versionCode` was a flat hand-maintained
  integer (`13`) that sat unchanged across Batches 1–6 even though the app
  itself changed every batch, because nobody's job in this workflow was to
  remember to bump it. Now reads `VERSION_CODE_OVERRIDE` from the
  environment, offset by a fixed `1000` so it can never dip below the old
  value regardless of exact run count (Android refuses to install an APK
  as an "update" over a higher versionCode already on the device — going
  backward would force a manual uninstall). Falls back to `1013` for any
  build that isn't running in this CI (e.g. Android Studio).
- **`.github/workflows/build.yml`** — `Build release APK` step now sets
  `VERSION_CODE_OVERRIDE: ${{ github.run_number }}`, the same run number
  already used for the release tag/APK filename since Batch 5 — so a given
  CI run's versionCode, release tag, and APK filename all derive from one
  consistent number instead of three separately-tracked ones.
- **`versionName` stays manual on purpose** — it's the human-chosen
  semantic label ("1.13"), bumped deliberately per feature batch, not per
  build. Auto-incrementing it too would mean every CI run reports a
  different marketing version even for a pure CI-config batch like this
  one, which isn't what semantic versioning is for. `versionCode` is what
  actually needed to be dynamic (it's what Android and this workflow's own
  tag/filename logic depend on), so that's the one batch this touches.

## Unreleased — Batch 6: Add missing PROJECT_STATE.md / FILE_MANIFEST.txt

Housekeeping — no app code or CI touched. The context hierarchy in user
preferences (Chat Saat Ini > PROJECT_STATE.md > FILE_MANIFEST.txt >
CHANGELOG.md > README.md) has required these two files from the start;
Batches 1–5 only ever used CHANGELOG.md/README.md, so this fills the gap.

- **New: `FILE_MANIFEST.txt`** — every file in the project, one line each,
  with its purpose and whether it's `[PROTECTED]` (edit-parsial-only per
  user preferences).
- **New: `PROJECT_STATE.md`** — current-state snapshot: version, batch
  history summary, defaults a new reader needs to know (default theme,
  minifyEnabled=false, crash-log location, CI release-tag format), pending
  items, and this sandbox's own constraints (no local compiler).

**Going forward**: both files get updated at the end of every batch instead
of drifting stale — PROJECT_STATE.md's "Batch history" section and
FILE_MANIFEST.txt's file list in particular.

## Unreleased — Batch 5: Fix duplicate/colliding GitHub Releases

- **`.github/workflows/build.yml`** — release tag and APK filename were
  built from `versionName` alone (`v${VERSION_NAME}`). `versionName` is
  bumped per feature batch, not per push, so several pushes in a row with
  no `app/build.gradle.kts` version bump (exactly what Batches 3 and 4
  were) produced the **same tag**. `softprops/action-gh-release` treats a
  repeat tag as "edit this release", not "make a new one" — hence the
  duplicated "Full Changelog" section and same-named APK asset just getting
  silently replaced each run, seen in the repo's Releases page. Fixed by
  appending `${GITHUB_RUN_NUMBER}` (a GitHub-guaranteed strictly-increasing
  per-workflow counter) to both the tag and the APK filename — e.g.
  `v1.13-build17`, `VideoResizer-v1.13-build17-release.apk` — so every CI
  run now always produces its own release and its own uniquely-named APK,
  even when versionName hasn't changed.

**Not done / out of scope for this fix:** deleting the already-duplicated
`v1.13` release on GitHub itself — that's a one-time manual cleanup on the
repo's Releases page (or `gh release delete v1.13`), not something a code
change here can retroactively undo.

## Unreleased — Batch 4: CI build-speed tuning

Config-only batch — no app code touched, purely how fast `assembleRelease`
runs in GitHub Actions (this project has no local Termux `gradle build`
step in its own workflow — the Termux commands only `git commit`/`push` —
so "compile time" here means CI time, tuned for GitHub's `ubuntu-latest`
runners specifically).

- **`gradle.properties`** — daemon heap 2048m → 4096m (`ubuntu-latest` has
  ~7GB free, and the Compose compiler plugin is memory-hungry enough that
  the old 2048m ceiling was likely forcing extra GC pauses mid-compile),
  switched to `-XX:+UseParallelGC` (throughput-oriented, better fit for a
  short-lived one-shot compile process than the default G1), and turned on
  `org.gradle.caching` (Gradle's build cache — skips re-running tasks
  entirely when their inputs haven't changed, which matters a lot for
  *this* project's actual usage pattern: small daily-update batches where
  most files are unchanged between pushes) and `org.gradle.parallel`
  (limited win with only one Gradle module, `:app`, but free to leave on).
- **`app/build.gradle.kts`** — `lint { checkReleaseBuilds = false }`.
  `assembleRelease` otherwise drags AGP's lint-vital analysis pass in as a
  dependency, which re-parses/re-analyzes the whole module every single
  build; this CI job's only job is producing a signed, installable APK, not
  gating on lint findings, so it's skipped rather than paid for on every
  push.
- **`.github/workflows/build.yml`** — `gradle assembleRelease` now passes
  `--parallel --build-cache` explicitly, as a defense-in-depth belt-and-
  suspenders alongside the `gradle.properties` flags (in case any future
  edit to `gradle.properties` accidentally drops them, the CI command
  itself still asks for both).

**Deliberately not done, and why:**
- **No AGP/Kotlin version bump.** Newer Kotlin/AGP releases do compile
  faster, but changing them is a real compatibility risk (Compose compiler
  extension version is pinned to a specific Kotlin version) that can't be
  verified without an actual build — there's no `gradle`/`kotlinc` in this
  environment to compile-check it first. Worth doing as its own batch, with
  a real CI run to confirm it, not bundled sight-unseen into a "make it
  faster" pass.
- **No `org.gradle.configuration-cache=true`.** This is the other big lever
  for repeat CI runs, but Gradle's configuration-cache entries live in a
  *project-local* `.gradle/configuration-cache` folder, not the user-home
  cache `gradle/actions/setup-gradle` already persists across runs — so it
  would need its own `actions/cache` step to actually pay off, and some
  third-party Gradle plugins still don't fully support it, which can turn
  into an outright build failure rather than a slow one. Left off until it
  can be tried and watched on a real run rather than guessed at here.
- **`isMinifyEnabled` stays `false`.** Turning R8 shrinking *on* would make
  the build slower, not faster, so it's untouched — it was already off, and
  it stays off for this specific goal too.

## Unreleased — Batch 3 (Atomic): "Midnight Blue Glass" UI/UX overhaul

Single atomic change — a full visual-identity overhaul can't be meaningfully
split into smaller batches without leaving the UI in an inconsistent
half-restyled state, so this exceeds the usual per-batch file guidance on
purpose (justification per the Atomic Change rule).

Adds a fifth selectable theme, **Midnight Blue Glass** — an iOS-style
glassmorphism look with a midnight-blue gradient backdrop — and makes it the
app's **new default** (Dark/Light/Midnight Neon/Warm Paper are all still
selectable from the theme menu; nothing existing was removed).

- **`ui/theme/Color.kt`** — new palette: `GlassGradientTop/Mid/Bottom` (the
  backdrop), translucent `GlassSurface`/`GlassSurfaceVariant` (what makes
  cards read as frosted glass), `GlassBorder` (the frosted-edge hairline),
  `GlassPrimary`/`GlassSecondary` (iOS-blue/cyan accents), plus
  `MidnightBlueGlassGradient`, the actual `Brush` painted behind every
  screen (a flat `ColorScheme.background` can't hold a gradient, hence a
  separate Brush constant).
- **`ui/theme/Type.kt`** — `GlassTypography`: tighter (slightly negative)
  letter-spacing and bolder titles for the dense, tracked-in look of iOS
  system type. Platform default sans family — no bundled font files.
- **`ui/theme/Theme.kt`** — `GlassColors` ColorScheme, `GlassShapes` (large
  iOS-"squircle" corner radii: 10/14/20/26/34dp vs. Material3's
  4/8/12/16/28dp default), `AppThemeStyle.MIDNIGHT_BLUE_GLASS` added to the
  enum, wired into every `when` in `VideoResizerTheme`, and made the default
  when following system dark mode. New `LocalIsGlassTheme` CompositionLocal
  so each screen can tell whether to paint the gradient or a plain flat
  background, without threading an extra parameter through every screen's
  function signature.
- **`MainActivity.kt`** (UI-only edits, business logic untouched):
  - `ThemePreference` enum + theme dropdown menu gained a "Midnight Blue
    Glass" entry; default `themePref` state changed to it.
  - Each of the three screens (`ResizerScreen`, `StudioScreen`,
    `BatchScreen`) now paints its own opaque background — the Midnight Blue
    Glass gradient, or the previous flat color for every other theme — via
    its `Scaffold`'s own `modifier`, with `containerColor = Color.Transparent`
    so that background shows through. This is deliberately **per-screen**,
    not a single shared background painted once at the app root: the v1.8
    fix that keeps the main screen permanently composed underneath
    Studio/Batch relies on each overlay screen's background being fully
    opaque on its own, and a single root layer would have broken that
    occlusion.
  - The four `Card(...)` composables (result card, video-editor-preview
    card, video-picker card, batch queue-item card) gained a themed
    `border` (invisible-thin on Dark/Light/Neon/Paper, a visible frosted
    hairline on Glass) and switched from hardcoded per-call
    `RoundedCornerShape(..dp)` to `MaterialTheme.shapes.large`, so they
    finally pick up each theme's shape language the way every other
    component already did (a small pre-existing inconsistency, fixed as
    part of the same pass rather than left half-done).
  - The two hardcoded `AccentPrimary`/`AccentSecondary` gradients (app-mark
    icon in the top bar, the primary "Resize video" CTA button) now read
    `MaterialTheme.colorScheme.primary`/`.secondary` instead, so they
    actually pick up each theme's accent colors — previously they always
    rendered Dark theme's purple/teal regardless of which theme was active,
    which would have undercut Glass's whole point (its accent is iOS-blue/
    cyan, not purple/teal). Zero visual change for Dark itself, since
    `DarkColors.primary`/`.secondary` already equal those same constants.

No true backdrop blur (`RenderEffect`, Android 12+/API 31 only): the
translucent glass fills + gradient backdrop + frosted border already read
clearly as glassmorphism without needing API-level gating, and true blur
would need to sample the gradient itself, not surrounding app content, so
it would add little.

## Unreleased — Batch 2: Crash logger, GitHub Release publishing, force-unwrap cleanup

Atomic batch — three independent-but-related hardening items shipped together
since none of them touch overlapping code and all were already flagged as
pending in the previous batch's notes.

- **New: built-in crash logger** (`CrashLogger.kt` + `VideoResizerApp.kt`).
  Installs a global uncaught-exception handler at app startup that writes a
  plain-text report — version, OS, device model, timestamp, thread, full
  stack trace — to `Documents/VideoResizer/logs/crash_<yyyyMMdd_HHmmss>_<UUID>.txt`
  via `MediaStore` (API 29+), with no legacy storage permission needed (same
  own-file-insert exception `PublicMovieExporter.kt` already relies on).
  FIFO retention keeps at most 50 logs, oldest deleted first. Every write is
  independently try/caught so a logging failure can never mask or add to the
  real crash. Registered via `AndroidManifest.xml`'s new
  `android:name=".VideoResizerApp"`.
- **`.github/workflows/build.yml`** — release APK is now published as an
  actual **GitHub Release** (`softprops/action-gh-release@v2`, tagged
  `v<versionName>`), not just an Actions artifact. Previously the signed APK
  only showed up inside a specific workflow run's Artifacts tab; it now
  appears directly in the repo's **Releases** sidebar, ready to download and
  install from the repo homepage. Artifact upload step kept as-is alongside
  it (harmless, some workflows still expect it).
- **Force-unwrap (`!!`) cleanup** — the five remaining call sites flagged in
  the previous batch's notes (`MainActivity.kt`'s `selectedUri`,
  `selectedSocialPreset` ×2, `watermarkUri`, `resultThumbnailBitmap`, plus
  `VideoResizer.kt`'s `watermarkUri`) are gone. All were already
  null-guarded by their enclosing `if`, so this was a style/robustness fix,
  not a live bug — but Compose mutable state (`by mutableStateOf`) can't be
  smart-cast across statement/lambda boundaries the way a plain local `val`
  can, which is why the `!!` existed in the first place. Replaced with
  either `?.let { }` (single read → non-null lambda parameter) or an
  explicit local `val` capture before the null check, both of which are
  smart-cast-safe by construction. `VideoResizer.buildWatermarkOverlay` also
  had its signature changed to take the already-checked `watermarkUri: Uri`
  directly instead of re-deriving it from `request.watermarkUri` internally.

Riwayat versi v1.0–v1.13 (fitur-fitur awal sampai before/after preview) ada
di bagian **Changelog** README.md — file ini melanjutkan dari situ, jadi
sesi AI mana pun yang mengerjakan proyek ini berikutnya bisa lihat di sini
dulu untuk konteks perubahan terbaru sebelum menyentuh kode.

## Unreleased — Batch 1: Backend hardening

Tujuan batch ini: kokohkan jalur export inti sebelum menambah fitur baru
lagi, supaya biaya debugging ke depan lebih kecil. Tidak ada perubahan
behavior yang disengaja dari sisi user — semua penambahan di bawah ini
murni menangkap kegagalan yang sebelumnya bikin app crash, dan
mengarahkannya ke jalur "export gagal" yang sudah ada di UI.

- **`VideoResizer.kt`** — `resize()` sebelumnya menjalankan seluruh setup
  pipeline (hitung dimensi, bangun efek watermark, `Transformer.start()`)
  tanpa try/catch sama sekali. Exception sinkron di sana (izin URI
  watermark dicabut, resolusi tidak valid, encoder ditolak device sebelum
  pipeline async jalan) bikin seluruh app crash. Sekarang dibungkus:
  kegagalan setup diarahkan ke `ResizeResult.Failure` yang sama persis
  dipakai `Transformer.Listener.onError` — satu jalur penanganan gagal,
  bukan crash. Signature `resize()`/`runResize()` berubah dari
  `Transformer` jadi `Transformer?` (aman — pemanggilnya sudah pakai state
  nullable `activeTransformer`).
- **`ExportForegroundService.kt`** — `startForeground()` di `onCreate()`
  sekarang dibungkus try/catch. Di Android 12+ OS bisa menolak promosi ke
  foreground priority (`ForegroundServiceStartNotAllowedException`); dulu
  ini mematikan seluruh proses app. Sekarang service berhenti sendiri
  kalau ditolak, dan export tetap jalan — cuma kehilangan proteksi
  background-kill untuk kejadian itu saja.
- **`VideoHistoryStore.kt`** — diaudit ulang, ternyata sudah aman
  (`runCatching` per-entry saat parse JSON histori). Tidak ada perubahan.
- Dibersihkan: file sampah tak terkait proyek (`signing history
  (4001440) ==="` — potongan teks bantuan `less` yang salah kesasar jadi
  nama file dari sesi lampau sebelumnya) sudah dihapus dari repo.

### Regresi yang ditemukan & diperbaiki di batch yang sama

- Perubahan awal Batch 1 sempat menambahkan `release.keystore` ke
  `.gitignore` (alasannya waktu itu: mencegah keystore ter-commit
  mentah). Ini ternyata regresi — lihat catatan **"Keystore HARUS
  ter-commit ke git"** di README.md bagian *Notes*. Build APK di GitHub
  Actions gagal di step `validateSigningRelease` karena `build.yml` tidak
  merekonstruksi `release.keystore` dari secret, ia mengandalkan file itu
  ada langsung di hasil checkout. Sudah dikembalikan: `release.keystore`
  tetap ter-track di git seperti semula.
