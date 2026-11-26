# PROJECT_STATE.md — Vidsize

Snapshot as of **Batch 89**. This is the first-read file per the context
hierarchy (Chat Saat Ini > this file > FILE_MANIFEST.txt > CHANGELOG.md >
README.md) — update it at the end of every batch rather than making it
stale. Full detail for anything summarized here lives in CHANGELOG.md;
architecture/quirk notes live in README.md. Untuk task polishing UI/UX,
baca juga **MICRO_POLISH_GUIDE.md** (standing playbook permanen, ditanam
Batch 35) sebelum mulai.

## Standing rules (permanent — read first, applies to every future session without exception)

- **Aturan pipeline CI (ditambahkan Batch 59; adaptasi workflow SONIX).** Ada DUA workflow terpisah:
  `build.yml` ("Build APK": build + GitHub Release + artifact APK; satu-satunya jalur rilis) dan
  `android-artifact.yml` (laporan evaluasi NON-BLOCKING: ArchUnit, tes unit, SpotBugs, detekt, lintDebug,
  peringatan kompilator Kotlin -> SATU-SATUNYA artifact `Vidsize_laporan-untuk-claude_run<N>` berisi
  `laporan-untuk-claude.txt`; retensi 7 hari; JANGAN tambah artifact kedua — koreksi user Batch 60). JANGAN gabungkan keduanya dan JANGAN jadikan analisis
  gerbang rilis. Laporan WAJIB membedakan `PASS` / `FAIL` / `NOT VERIFIED` (alat gagal atau laporan tak terbentuk
  != lulus). Skema versi/tag (`1.<run>` / `v1.<run>-build<run>`, dibaca `Regex("build(\\d+)")` di `AppUpdater.kt`),
  keystore ter-commit `release.keystore` + secret `RELEASE_KEYSTORE_PASSWORD`, Stale Run Guard, Gradle 8.7 TIDAK
  boleh diganti dengan skema SONIX (commit-count / secret keystore base64 / Gradle 8.14.3). Plugin detekt
  dimuat di setiap build (`ignoreFailures = true`); SpotBugs OPT-IN (`-Pvidsize.spotbugs=true`), ArchitectureTest
  aktif hanya dgn env `VIDSIZE_ARCH_REPORT=1`. Filter noise (detekt.yml, exclude.xml, lint `checkOnly`) hanya
  boleh ditambah dengan BUKTI dari laporan run + pembacaan kode di titik temuan.
- **[BRANDING_NAME: Vidsize] [TERMUX_ROOT: VideoResizer]** (ditambahkan
  Batch 56, koreksi user). TERMUX_ROOT = `rootProject.name` di
  `settings.gradle.kts` — TANPA hyphen. JANGAN pakai `REPO = "Video-resizer"`
  dari `AppUpdater.kt` — itu slug GitHub (dengan hyphen) untuk Releases API
  URL, BUKAN nama folder project lokal. Kedua nilai ini beda sumber, jangan
  disamakan lagi.
- **ZIP repack WAJIB FLAT, tanpa folder pembungkus** (ditegaskan Batch 56,
  koreksi keras user — "jangan nested"). Isi ZIP harus langsung
  `build.gradle.kts`, `app/`, `settings.gradle.kts`, dst di ROOT arsip —
  BUKAN dibungkus satu folder induk (mis. `vidsize-main/...`). Skrip
  Termux (`unzip -o "$LATEST_ZIP" -d "$PROJ_DIR/"`) mengasumsikan isi ZIP
  landing LANGSUNG di `$PROJ_DIR`, bukan di subfolder nested — kalau ada
  pembungkus, project jadi nested SALAH DI $PROJ_DIR/<pembungkus>/... dan
  build gagal. Cek wajib sebelum kirim ZIP: `unzip -Z1 file.zip` — baris
  pertama harus nama file/folder proyek langsung (`build.gradle.kts`,
  `app/`, dst), BUKAN satu nama folder tunggal yang membungkus semuanya.

- **Embedded Micro-Polish Guide rule (added Batch 35).** `MICRO_POLISH_GUIDE.md`
  di root adalah playbook permanen hasil embed dari upload user
  (`VideoResizer_FINAL_MICRO_POLISH_ClaudeOnly.md`). Berisi hard rules +
  8 prioritas polish UI/UX + verification gate + format laporan wajib.
  WAJIB dibaca sebelum task polishing apa pun. File ini tidak boleh
  dihapus meski seluruh 8 prioritas sudah [DONE] — cukup ditandai selesai
  per item, bukan dihapus. Ini rule permanen, bukan item batch-history.
- **Bahasa Indonesia-only documentation rule (added Batch 30).** Mulai
  sekarang, SEMUA entri dokumentasi baru (PROJECT_STATE.md, CHANGELOG.md,
  FILE_MANIFEST.txt, komentar rilis, dsb.) WAJIB ditulis dalam Bahasa
  Indonesia — tanpa kecuali, berlaku permanen untuk setiap sesi berikutnya.
  Entri/dokumentasi lama yang masih Bahasa Inggris TIDAK perlu diterjemahkan
  atau disentuh (biarkan apa adanya) — aturan ini hanya berlaku untuk entri
  baru ke depan, bukan penerjemahan retroaktif. Ini rule permanen, bukan
  item batch-history, jangan dianggap "resolved and removable".
- **Automated versioning rule (added Batch 22).** `versionCode` AND
  `versionName` are BOTH derived automatically from CI (`$GITHUB_RUN_NUMBER`
  via `VERSION_CODE_OVERRIDE`) in `app/build.gradle.kts` — see "Current
  version" below for the exact formula. **No session may ever reintroduce a
  hand-bumped per-batch version literal** (the old `semanticVersionName =
  "1.19"`-style pattern, bumped manually batch-to-batch, is retired for
  good — it was the direct cause of the version-history gap noted in the
  old Batch 12/20 entries below). The only manual version constant that
  still exists anywhere is `appVersionMajor` in `app/build.gradle.kts`
  (currently `1`) — touch it ONLY for a deliberate breaking/milestone
  release, never as routine batch work, and if it IS deliberately changed,
  the matching literal `"1"` in `.github/workflows/build.yml`'s
  `VERSION_NAME="1.${GITHUB_RUN_NUMBER}"` line (step "Determine version name",
  satu-satunya situs sejak Batch 59 — sebelumnya dua) must be updated to match
  in the same batch (situs itu dan `app/build.gradle.kts` saling merujuk di komentar). This rule itself is not a batch-history
  item and must not be treated as "resolved and removable" — it's a
  permanent policy.

## Current version
- **Batch 89 (1 berkas kode `Depth.kt`):** bug bake v88 ("bingkai" persegi di ubin) -> `bakeBitmap` memakai `inset(margin)` agar `size` = ukuran elemen; batas bake 6MB, kartu besar ikut di-bake. NOT VERIFIED di CI/HP. Detail: CHANGELOG Batch 89.
- **Batch 88 (3 berkas kode: `Depth.kt`, `Ornaments.kt`, `MainActivity.kt`):** user "masih stuttering" -> bake bitmap utas latar utk elemen depth (`DepthBakery`, `RaisedKit`) + latar tema, governor refresh rate adaptif (`FrameMetrics`). NOT VERIFIED di CI/HP. Detail: CHANGELOG Batch 88.
- **Batch 87 (1 berkas kode `MainActivity.kt`):** user "Infinix, gulir/transisi gak kayak iOS" -> layar di bawah overlay tak lagi digambar (`settledScreen`, `drawWithContent`; tetap terkomposisi), paralaks+redup Beranda cache-tekstur, kurva iOS 0.32,0.72,0,1 (420/360ms). CI run #27 (v86) hijau penuh. NOT VERIFIED di HP. Detail: CHANGELOG Batch 87.
- **Batch 86 (1 berkas kode `ui/theme/Depth.kt` + `config/spotbugs/exclude.xml`):** user "unlock 120hz, tapi app stutter" -> paint blur dibagi, `WellKit` lazy, bayangan CINCIN + kilau terbatas utk elemen besar; filter SpotBugs `SA_FIELD_SELF_ASSIGNMENT` Depth.kt (noise korutin, run #26). CI run #26 (v85) hijau kecuali itu. NOT VERIFIED di HP. Detail: CHANGELOG Batch 86.
- **Batch 85 (2 berkas kode: `MainActivity.kt`, `ui/theme/Depth.kt`; tanpa dependensi/izin):** user "not enough. dan app cuman lock di 60hz" -> `applyHighestRefreshRate()` (mode layar refresh rate tertinggi pada resolusi sama, `preferredDisplayModeId`+`preferredRefreshRate`, onCreate+onResume); depth lebih kuat (`DepthSize` ~+35%, alfa naik, `depthRowPadding()` di 14 LazyRow chip agar bayangan tak terpotong, `DepthSwitch`/`DepthSlider` baru). CI run #25 (v84) hijau penuh. NOT VERIFIED di CI/HP. Detail: CHANGELOG Batch 85.
- **Batch 84 (3 berkas: `Depth.kt`, `Color.kt`, `config/spotbugs/exclude.xml`; API publik Depth* tetap):** kedalaman neumorfik lebih realistis — bayangan berlapis (ambient+kontak), badan gradien 45 derajat + kilau radial, tepi bevel, lekuk bergibir; kontras aksen dinaikkan; SpotBugs `NP_PARAMETER` x `Depth.kt`/`Ornaments.kt` disaring (bukti run #24). CI run #24 (v83) hijau kecuali SpotBugs 10. NOT VERIFIED di CI/HP. Detail: CHANGELOG Batch 84.
- **Batch 83 (5 berkas kode: `Depth.kt` baru, `Ornaments.kt` baru, `Color.kt`, `Theme.kt`, `MainActivity.kt`; tanpa dependensi/izin):** neumorfisme timbul+cekung di semua tema (`depthRaised/depthInset`, `DepthCard/Chip/IconButton/ProgressBar/Well`), Glass kini OPAK (readability), ornamen khas tema (`themeBackdrop`, `drawCardOrnament`, `drawHeroArt`), dasar tema bernada-tengah, warna dinamis hanya aksen. CI run #23 (v82) hijau penuh. NOT VERIFIED di CI/HP. Detail: CHANGELOG Batch 83.
- **Batch 82 (1 berkas kode: `MainActivity.kt`; tanpa perubahan kontrak/dependensi):** permintaan "hierarchy belum terasa premium" -> Beranda: header besar + kartu hero (Resize) + grid 2x2 alat sekunder beraksen warna + efek tekan; `ToolTopBar` bersama (Resizer/Batch/GIF/Kompres); `glassAwareBackground()` (GIF & Kompres ikut gradien tema Glass); Resizer: tombol Resize/progres jadi DOK TETAP (`Scaffold.bottomBar`) + kartu hasil di atas pengaturan + auto-gulir ke atas; `VideoPickerCard` keadaan kosong baru (Indonesia); perbaikan SpotBugs `MS_EXPOSE_REP` (`THEME_CHOICES` -> `themeLabel()`). Detail: CHANGELOG Batch 82. NOT VERIFIED di CI/HP.
- **Batch 81 (8 berkas kode/resource; interface: state update pindah ke `VideoResizerApp`):** audit hierarki tampilan -> Update + Tema pindah dari menu titik-tiga Resizer ke top bar Beranda; unduhan update memakai cache APK (`cachedApk`, `.part` atomik, pembatalan nyata) + status "Siap dipasang" tanpa unduh ulang; catatan rilis tetap tampil saat unduh; back Resizer -> Beranda; Beranda tak lagi tembus ke Resizer saat transisi; ikon adaptif baru (+monokrom), manifest `@mipmap/ic_launcher`; penamaan konsisten (Riwayat/Ekspor Batch/Resize / Edit Video). Detail: CHANGELOG Batch 81. NOT VERIFIED di CI/HP.
- **Batch 80 (1 berkas konfigurasi: `app/build.gradle.kts`; kode Kotlin TIDAK berubah):** permintaan user "naikkan minSdk, minimal android >12" -> `minSdk` 24 -> **31** (Android 12+; ditafsirkan "12 ke atas" sesuai aturan proyek "prioritas API 31+"; bila yang dimaksud Android 13 = API 33, ubah satu angka itu). Perubahan lain hanya komentar (whitelist lint) + README (API 31+). Perangkat < Android 12 tidak bisa memasang/memperbarui app ini lagi (termasuk lewat `AppUpdater`). NOT VERIFIED di CI/HP: tak ada AGP di sandbox, hanya diff 2 baris + pemeriksaan statis pemakaian `minSdk`/`SDK_INT`.
- **Batch 79 (HANYA docs; kode TIDAK berubah):** run #20 (commit c4c9d71, atas `Vidsize_v78.zip`) HIJAU PENUH: semua exit Gradle 0; ArchUnit 12/0; SpotBugs 0; detekt 0; lint 0; kompilator 0; Tes unit 40 / 0 dilewati (7 `CrashLoggerTest` + 6 `SocialPresetTest` + 26 `VideoResizerKalkulasiTest` + 1 `ArchitectureTest`) — tepat sesuai prediksi Batch 78. Terbukti di CI/JUnit asli: perbaikan bitrate Kompres (`compressVideoBitrateBps`) dan seluruh `VideoResizer.kt` utuh (termasuk fungsi private `computeCompressTargetBitrateBps`) terkompilasi tanpa peringatan; risiko "inisialisasi kelas `VideoResizer` di classpath tes AGP" sudah tidak ada. Tidak ada temuan yang harus diperbaiki. Perilaku di HP tetap NOT VERIFIED.
- **Batch 78 (2 berkas kode; ada perubahan LOGIKA PRODUKSI, diizinkan user):** run #19 HIJAU PENUH (Tes unit 33 / 0 dilewati, ArchUnit 12/0, SpotBugs 0, detekt 0, lint 0, kompilator 0; prediksi Batch 77 terbukti). Perbaikan temuan Batch 77: mode Kompres kini memberi batas 85% bitrate video sumber PRIORITAS atas lantai 250 kbps (dulu lantai dikenakan sesudah batas -> sumber < ~294 kbps dienkode ulang ke 250 kbps = file membesar). `VideoResizer.kt`: +`compressVideoBitrateBps` (companion; dipakai bersama `computeCompressTargetBitrateBps` [ekspor] dan `estimateCompressedSizeBytes` [pratinjau] supaya tak bisa berbeda) + `MIN_COMPRESS_BITRATE_KBPS = 64` (lantai mutlak agar `setBitrate` wajib > 0 tak menerima nilai absurd); `VideoResizerKalkulasiTest.kt`: tes karakterisasi lama diganti 3 tes perilaku baru + 5 tes `compressVideoBitrateBps` (26 tes). Sandbox: kompilasi 0 peringatan, 32/32 lulus, mutasi baru terdeteksi, detekt lokal 0, uji diferensial lama-vs-baru 1152 kasus. NOT VERIFIED di CI/HP. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 77 (3 berkas kode/konfigurasi; kode produksi TIDAK berubah):** run #18 HIJAU PENUH (ArchUnit 12/0, SpotBugs 0, detekt 0, lint 0, kompilator 0, Tes unit 8 / 0 dilewati; semua prediksi Batch 76 terbukti, termasuk `CrashLoggerTest` lulus di JUnit/AGP asli). Tes perilaku KEDUA & KETIGA: `SocialPresetTest.kt` (6 tes invarian data: rasio vs dimensi eksak, genap, bitrate dalam batas keras, label unik, nilai spesifikasi) dan `VideoResizerKalkulasiTest.kt` (19 tes: `requiredBitrateKbpsForTargetSize` + `estimateCompressedSizeBytes`, nilai harapan dari implementasi referensi independen); `android-artifact.yml` hanya 2 catatan statis (baris 22 & 325) diperbarui. Sandbox: Kotlin 2.0.21 (`-language-version 1.9 -Werror`; kotlinc 1.9.24 tak tersedia kali ini) 0 peringatan, 25/25 lulus, 20 mutasi semua terdeteksi, detekt-cli 1.23.8 lokal 0 temuan (main+test). NOT VERIFIED di CI/HP. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 76 (2 berkas kode/konfigurasi; kode produksi TIDAK berubah):** run #17 HIJAU PENUH (ArchUnit 12/0, SpotBugs 0, detekt 0 dari 20, lint 0, kompilator 0; semua prediksi Batch 75 terbukti). Tes perilaku PERTAMA: `CrashLoggerTest.kt` (7 tes JVM murni, kontrak `CrashLogger.nonFatal`: nilai sukses, `Exception` -> `onFailure` instans sama, `Error` dilempar ulang, `return` non-lokal dari `block`/`onFailure`, pengecualian `onFailure` tak ditelan); `android-artifact.yml` hanya 2 catatan statis (baris 22 & 325) diperbarui. Sandbox: kotlinc 1.9.24 0 peringatan, 7/7 lulus, uji mutasi mendeteksi regresi, detekt lokal 0. NOT VERIFIED di CI/HP. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 75 (7 berkas kode):** refactor 20 temuan detekt `TooGenericExceptionCaught` (run #16) -> `CrashLogger.nonFatal` (inline; `Exception` ditangkap, `Error` dilempar ulang; semantik identik, tipe TIDAK disempitkan); `CrashLogger.install` -> `runCatching` (Throwable). `detekt.yml` TIDAK diubah (aturan tetap aktif). Pembenaran 7 berkas (>5): kontrak baru bersama `CrashLogger.nonFatal` dipakai 6 berkas lain.
  Sandbox: detekt lokal 20 -> 0 (baseline v74 = 20 cocok dgn run #16); kotlinc 1.9.24 kompilasi berkas asli + stub OK. NOT VERIFIED di CI/HP. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 74 (4 berkas kode/konfigurasi):** audit UI/UX vs Vital Guards + perbaikan: edge-to-edge (`enableEdgeToEdge` di `MainActivity.onCreate`, `Theme.kt` `DisposableEffect` `SystemBarStyle.auto`, `Surface` akar `imePadding`+cutout,
  `VideoPickerScreen.kt` `navigationBars`); `configChanges` diperluas; 22 state inti `ResizerScreen` -> `rememberSaveable`; `BatchScreen` pemilih -> `Dispatchers.IO`; 6 string UI Indonesia; tab bar `selectable`+`Role.Tab`; Studio `onLongClickLabel`.
  NOT VERIFIED di CI/HP. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 73 (docs saja, nol perubahan kode):** run #14 HIJAU PENUH (SpotBugs 0, detekt 20, kompilator 0, lint 0, ArchUnit 12/0; semua prediksi Batch 72 terbukti). Tidak ada temuan CI yang bisa ditindak;
  verifikasi perilaku di HP masih NOT VERIFIED. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 72 (4 berkas kode/tes):** run #13 HIJAU (SpotBugs 0, detekt 20, kompilator 0, lint 0, ArchUnit 12/0). `CrashLogger.logCaught` dipasang di 6 catch yang menelan pengecualian: `GifExporter.kt` (2),
  `AppUpdater.kt` (`check` generik + `download`), `MainActivity.kt` (`loadSourceMetadata`, `loadSourceInfo`); `AppUpdater.kt` masuk `LOGGER_CLIENT_FILES` di `ArchitectureTest.kt`. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 71 (3 berkas kode):** run #12 HIJAU (SpotBugs 7, detekt 20, kompilator 0, lint 0, ArchUnit 12/0; tanpa temuan baru dari `logCaught`). 7 temuan `RV_RETURN_VALUE_IGNORED` diperbaiki: hasil
  `mkdirs()`/`delete()` kini dipakai di `AppUpdater.kt` (2), `PublicMovieExporter.kt` (2), `MainActivity.kt` (3, via `ensureThumbDir`). Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 70 (5 berkas kode/tes):** run #11 HIJAU (SpotBugs 7, detekt 20, kompilator 0, lint 0, ArchUnit 12/0). KEPUTUSAN ARSITEKTUR (diserahkan user ke Claude): pengecualian TERTANGKAP kini dicatat
  mentah ke `Documents/VideoResizer/logs/error_<ts>_<uuid>.txt` lewat `CrashLogger.logCaught` (thread latar, retensi 50 per awalan, tak pernah melempar); dipasang di `VideoResizer.kt` (start + `onError`
  resize/compress), `PublicMovieExporter.kt` (4 catch), `ExportForegroundService.kt` (`startForeground`). Aturan ArchUnit "mandiri" dilonggarkan SEMPIT: 3 berkas itu boleh bergantung ke `CrashLogger.kt`
  saja. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 69 (2 berkas kode/konfigurasi):** run #10 HIJAU (SpotBugs 7, detekt 20, kompilator 0, lint 0, ArchUnit 12/0); `AppUpdater.download()` kini OkHttp 4.12.0 + Okio 3.6.0
  (`ResponseBody.source()` -> `sink().buffer()`, `emitCompleteSegments()` per 8 KB; aturan guard streaming terpenuhi); `check()` tetap HttpURLConnection. Dependency baru HANYA
  untuk `download()`. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 68 (1 berkas kode + `exclude.xml` + `android-artifact.yml`):** run #9 HIJAU (SpotBugs 31, detekt 20, kompilator 1); `exclude.xml` menyaring UUF x field `$stable` (24/24 titik,
  SEMPIT per nama field); `VideoResizer.kt` memakai `Transformer.Builder.setVideoMimeType(VIDEO_H265)` (ganti `setTransformationRequest` deprecated; ada di Media3 1.4.1, perilaku identik);
  blok "Contoh SARIF mentah" dihapus dari laporan CI. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 67 (2 berkas kode + `exclude.xml` + `android-artifact.yml`):** run #8 HIJAU (SpotBugs 33, detekt 21, kompilator 1); `ExportForegroundService.kt` mencatat `startForeground`
  yang gagal mentah ke logcat; `PublicMovieExporter.kt` menghapus baris MediaStore `IS_PENDING=1` yatim (video + GIF); `exclude.xml` menyaring NP_PARAMETER x `AppUpdater.kt`
  (2 titik :0, terbukti sintetis); laporan CI memuat `[elemen kode]` per temuan SpotBugs + contoh SARIF mentah. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 66 (3 berkas kode + `exclude.xml` + `android-artifact.yml`):** run #7 HIJAU (SpotBugs 64, detekt 37, kompilator 1); catch yang menelan pengecualian di
  `PublicMovieExporter.kt` (4), `CrashLogger.kt` (4), `MainActivity.kt` (8) kini mencatat stacktrace MENTAH ke logcat; `exclude.xml` menyaring 31 temuan
  SpotBugs terbukti noise; laporan CI memuat pesan mentah SpotBugs per titik. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 65 (2 berkas non-docs + `exclude.xml`):** run #6 HIJAU; `VideoResizerApp.themePref` (tak terpakai) dihapus, `formatPickerDate` aman-null,
  `exclude.xml` + pola NP_PARAMETER_* untuk 3 berkas Compose. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 64 (1 berkas non-docs):** memperbaiki build rusak run #5: `VideoPickerScreen.kt` `ThreadLocal` -> `java.lang.ThreadLocal`
  (`import androidx.compose.runtime.*` membayangi nama dengan kelas internal Compose). Versi/tag/keystore/signing/Stale Run Guard
  TIDAK berubah.
- **Batch 63 (3 berkas non-docs):** 9 situs ikon -> `Icons.AutoMirrored.Filled.*` (`MainActivity.kt` x7, `VideoPickerScreen.kt` x2), 3 peringatan
  kompilator ditutup (cast tak perlu, `val message` membayangi, param `themePref` `ResizerScreen` dihapus), `pickerDateFormat` jadi
  `ThreadLocal`, `exclude.xml` + sumber `LazyDsl.kt` (kode inline Compose, bukan proyek). Versi/tag/keystore/signing/Stale Run Guard
  TIDAK berubah.
- **Batch 62 (3 berkas non-docs):** penghitung isu lint di `android-artifact.yml` diperbaiki (dulu SELALU 0 = "LULUS" palsu di run #2/#3),
  `CrashLogger.kt` guard API 28 `longVersionCode`, `Haptics.kt` mencatat `SecurityException`. Versi/tag/keystore/signing/Stale Run
  Guard TIDAK berubah.
- **Batch 61 (5 berkas non-docs):** haptic kuat terpusat (`Haptics.kt` baru + 1 situs provider di `MainActivity.kt` + `VIBRATE`
  di manifest), `AppUpdater.REPO` = `vidsize`, `Haptics.kt` didaftarkan di `UI_FILES` `ArchitectureTest.kt`. Versi/tag/keystore/
  signing/Stale Run Guard TIDAK berubah.
- **Batch 60 (CI saja, nol perubahan source app):** laporan CI kini SATU artifact (`Vidsize_laporan-untuk-claude_run<N>`;
  artifact mentah dihapus), false positive ArchUnit run #1 diperbaiki di `ArchitectureTest.kt`, id lint
  `NonObservableLocale` dihapus dari `checkOnly`. Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 59 (CI saja, nol perubahan source app):** pipeline CI diadaptasi maksimal dari workflow SONIX —
  `build.yml` diperbarui (ubuntu-24.04, actions v5, ulang sekali bila unduhan gagal, Release dulu baru artifact APK
  tanpa zip, isi Release = pesan commit, ringkasan build, `log_fail_*`), `android-artifact.yml` baru (laporan Claude),
  `config/detekt/detekt.yml`, `config/spotbugs/{spotbugs.gradle.kts,exclude.xml}`, `ArchitectureTest.kt` (12 aturan
  per berkas sumber), plugin detekt 1.23.8 + lint whitelist + dependency tes (junit 4.13.2, archunit 1.4.2).
  Versi/tag/keystore/signing/Stale Run Guard TIDAK berubah.
- **Batch 57 (2 permintaan user sekaligus):**
  1. **Fix persistensi theme (root cause, konkret — bukan tebakan
     platform):** `themePref` di `MainActivity.onCreate` dulu murni
     `remember { mutableStateOf(MIDNIGHT_BLUE_GLASS) }` — TIDAK ADA kode
     mana pun yang membaca/menulis ke storage. Setiap restart app (dan
     setiap recreate Activity, termasuk rotasi layar, karena state ini
     hidup langsung di `setContent`) diam-diam reset pilihan user balik ke
     default. Fix: baca/tulis SharedPreferences "video_resizer_prefs" key
     "theme_preference" (file prefs yang sama yang sudah dipakai
     ResizerScreen/StudioScreen — tidak bikin file baru). Defensif via
     `runCatching { ThemePreference.valueOf(...) }` kalau value kesimpan
     korup/dari versi enum lama.
  2. **"Kompres GIF" DIHAPUS TOTAL** (permintaan eksplisit user: "cabut
     dan musnahkan semua dependency yang berhubungan dengan Gif" — setelah
     3 batch fix gagal total, 55b→55c→56, dinilai gagal menyelesaikan
     masalah berantai-nya). `GifCompressor.kt` dihapus. `GifEncoder.kt`
     kembali ke 1 overload (varian `delays:List<Int>` dicabut, cuma
     dipakai GifCompressor). Di `MainActivity.kt`: `GifScreenMode`/
     `GifModeTabBar`/`GifFilePickerCard`/`GifCompressPanel` + seluruh
     state/handler `compress*` di dalam `GifScreen` dicabut bersih —
     `GifScreen` balik jadi satu-mode "Video ke GIF" persis sebelum
     Batch 55. **`GifExporter.kt`/fitur "Video ke GIF" TIDAK disentuh**
     (pipeline terpisah total, tidak pernah dilaporkan bermasalah — lihat
     "Keputusan scope" di Batch history). **`Screen.COMPRESSOR`/
     `CompressorScreen` (fitur kompres VIDEO, bukan GIF) JUGA TIDAK
     disentuh** — dicek langsung isinya, nama mirip tapi fitur 100%
     berbeda, tidak ada hubungan dengan "Gif" sama sekali. History lama
     `kind="GIF_COMPRESS"` di Studio TETAP tampil benar (logic display cuma
     string-compare, sengaja dipertahankan) — user lama tidak kehilangan
     riwayat, cuma sudah tidak bisa membuat yang baru.
  Brace/paren balance: `MainActivity.kt` {} 1137/1137 () 2206/2206
  [] 11/11 depth 0; `GifEncoder.kt` {} 29/29 () 117/117 [] 7/7 depth 0.
  File disentuh: `MainActivity.kt`, `GifEncoder.kt` (edit) +
  `GifCompressor.kt` (hapus) — 3 file kode. Detail di Batch history +
  CHANGELOG.md.
- **Batch 56 (root-cause fix, atas permintaan user "perbaiki regresi
  mekanisme persisten sampai ke akarnya"):** Audit Batch 55c menemukan
  gap: canvas persisten-selamanya itu SALAH untuk 2 dari 4 metode
  disposal GIF89a (metode 2 "restore to background" + metode 3 "restore
  to previous" — TUJUANNYA justru membersihkan piksel antar frame; canvas
  yang tidak pernah di-clear akan ghosting di area itu). `Movie` tidak
  expose disposal method lewat API publik, jadi ditambahkan
  `parseGifFrameDisposals()` — parser struktur-blok GIF89a/87a (bukan
  decoder piksel, cuma baca Graphic Control Extension + Image Descriptor
  + Global Color Table) yang menerapkan disposal yang BENAR per frame:
  metode 0/1 tetap no-op (persis Batch 55c), metode 2/3 di-cat ulang ke
  warna background ASLI GIF (bukan clear transparan — `buildPaletteLocal`/
  `quantizeFrameLocal` cuma baca RGB, alpha diabaikan, jadi clear
  transparan akan diam-diam jadi hitam solid, bukan background). Fail-safe
  penuh: parsing gagal → persis perilaku Batch 55c, tidak pernah lebih
  buruk. **Catatan jujur**: audit ulang jalur kode menunjukkan keraguan
  BARU soal apakah teori dasar Batch 55c/56 (canvas butuh persistensi
  eksternal) benar-benar menjelaskan gejala "putih polos" yang
  di-screenshot user — piksel yang benar-benar kosong seharusnya
  terkuantisasi HITAM di pipeline ini, bukan putih; kemungkinan lain
  (viewer menampilkan placeholder "gambar rusak" karena file corrupt
  struktural) belum tertutup oleh fix manapun sejauh ini. Fix ini tetap
  sah diterapkan (menutup celah ghosting yang nyata), tapi kalau gejala
  PERSIS yang di-screenshot masih terjadi: kirim FILE GIF SUMBER (bukan
  screenshot hasil) di sesi berikutnya. 1 file (`GifCompressor.kt`).
  Detail di Batch history + CHANGELOG.md.
- **Batch 55c (bugfix #2, dari bukti screenshot user):** Screenshot user
  tunjukkan pola spesifik — strip warna tipis di baris paling atas,
  sisanya putih polos total. Root cause: loop decode bikin canvas BARU
  KOSONG tiap frame sample, padahal GIF player asli itu canvas-nya
  PERSISTEN antar frame (banyak GIF cuma gambar ulang bagian yang
  berubah). Fix: 1 canvas persisten dipakai untuk semua sample,
  snapshot via `Bitmap.copy()` tiap kali. Aman dua arah (tidak
  memperburuk kalau ternyata bukan itu bug-nya). 1 file
  (`GifCompressor.kt`). Detail di Batch history + CHANGELOG.md.
- **Batch 55b (bugfix #1):** User report: output GIF dari "Kompres GIF" tidak
  boleh corrupt/blank putih sama sekali. Root cause paling mungkin:
  `Movie.decodeStream()` + stream ditutup (`.use{}`) sebelum semua
  `movie.draw()` selesai dipanggil → diganti `Movie.decodeByteArray()`
  (data fully in-memory, tidak bergantung stream hidup). + jaring
  pengaman baru: gagal dgn pesan jelas kalau semua frame ternyata polos
  satu warna, bukan diam-diam lanjut. 1 file (`GifCompressor.kt`). Detail
  penuh di Batch history + CHANGELOG.md. **Belum ada device/compiler di
  sisi Claude untuk konfirmasi 100%** — kalau masih kejadian, kirim file
  GIF sumbernya biar bisa dipersempit lebih jauh.
- **Batch 55 (fitur baru):** Tab "Kompres GIF" di `GifScreen` — kompresi
  file GIF yang SUDAH ADA (bukan bikin GIF baru dari video, itu tab
  "Video ke GIF" yang sudah ada, tidak disentuh). Anti-crash (cap ukuran
  file input, cap jumlah frame, cap lebar decode, semua tahap dibungkus
  try/catch OOM/Exception → pesan ramah bukan crash) + kualitas default
  tetap HD (level Ringan tidak resize sama sekali, cuma buang frame
  duplikat). Detail penuh di Batch history di bawah + CHANGELOG.md.
  _(Catatan: bullet Batch 44-53 di section ini sebelumnya tidak
  di-backfill oleh sesi lampau — gap lama, bukan dari sesi ini. Section
  "Batch history" di bawah adalah sumber yang benar-benar ter-update
  tiap batch; section ini sejak lama lebih berfungsi sebagai ringkasan
  beberapa entri terbaru, bukan log lengkap.)_
- **Batch 43 (audit + fix):** Back/cancel konsistensi. Audit seluruh
  screen (Resizer/Batch/GIF/Compressor/Studio) + semua 13 `AlertDialog`:
  ResizerScreen & CompressorScreen SUDAH benar (confirm dulu sebelum
  keluar saat `isProcessing`). **BatchScreen & GifScreen GAP**: back
  (system gesture + toolbar arrow) langsung cancel proses + keluar
  tanpa konfirmasi sama sekali. Fix: pola `showExitWhileProcessingConfirm`
  yang sama disamakan ke 2 screen ini. 4 dialog input (CustomResolution/
  CustomBitrate/TargetSize×2) sengaja disable back-dismiss
  (`dismissOnBackPress = false`) — direview, ini BUKAN gap, ini
  proteksi anti-accidental-dismiss yang disengaja (form punya
  Save/Cancel eksplisit), tidak disentuh.
- **Batch 42 (polish):** Navigasi antar-screen (Batch/GIF/Compressor/
  Studio) sebelumnya `if (screen == Screen.X) { Screen(...) }` — instant
  cut, nol animasi (inilah akar keluhan user "transisi terlalu cepat").
  Diganti `AnimatedVisibility` bareng: slide-in dari kanan + fade
  (`IOS_PUSH_MS = 350ms`) utk masuk, slide-out ke kanan + fade
  (`IOS_POP_MS = 300ms`) utk keluar, easing `CubicBezierEasing(0.42,0,
  0.58,1)` (ease-in-out, mendekati kurva push/pop `UINavigationController`
  iOS). Konstanta `IosPushEasing`/`IOS_PUSH_MS`/`IOS_POP_MS` dipakai
  konsisten di keempat screen (bukan cuma 1 tempat) — cek pakai
  `grep "if (screen == Screen\."` = 0 hasil, semua sudah dikonversi.
- **Batch 41 (feature):** Studio — sweep-select + fix delete visibility.
  1) `StudioEntryCard` delete `IconButton` dulu di dalam Row
  `horizontalScroll` bareng 3 text button ("Edit ulang/Galeri/Bagikan") →
  di layar sempit ke-4 nya nggak cukup, delete kegeser keluar viewport
  (invisible tanpa scroll manual, sesuai screenshot user). Dipindah ke
  luar scroll, sekarang selalu terlihat. 2) Fitur baru: long-press card
  → selection mode (checkbox per card, TopAppBar berubah jadi "X
  dipilih" + Pilih Semua + Hapus massal, back/close keluar dari mode
  ini dulu sebelum navigasi keluar Studio). Bulk delete pakai
  confirm-dialog yang sama polanya dgn single delete (Batch-lama fix
  accidental-delete). Diaudit: cuma ada 1 `LazyColumn`/list screen di
  seluruh app (StudioScreen) — jadi sudah menjangkau semua "sektor".
- **Batch 40 (crash fix):** `openInGallery` & `openGifInGallery` — hapus
  `Intent.FLAG_GRANT_READ_URI_PERMISSION` (app tidak berhak grant permission
  atas `content://media/...` URI milik MediaProvider → `SecurityException`
  saat startActivity). Tambah `catch (e: SecurityException)` di kedua
  fungsi sbg fail-safe. Lihat crash log `crash_20260822_224435_*.txt`.
- **Fully automated since Batch 22 — see "Standing rules" above.**
  `versionCode = (VERSION_CODE_OVERRIDE ?: 13) + 1000`; `versionName =
  "$appVersionMajor.$VERSION_CODE_OVERRIDE"` (e.g. `"1.47"`) when running in
  CI, else `"$appVersionMajor.0-dev"` for any local/non-CI build.
  `appVersionMajor` is currently `1`. Nothing here requires — or should
  ever again receive — a manual per-batch bump.
- Historical note (pre-Batch-22, kept for context only — NOT an active
  issue): versionName used to be a hand-maintained `semanticVersionName`
  literal bumped once per batch (last manual value was `"1.19"`, Batch 20).
  A version-history gap was noticed in Batch 12 (uploaded project was a
  full minor ahead of what PROJECT_STATE.md recorded) — this whole class of
  bug is what the Batch 22 automation rule exists to permanently prevent.
- Package: `com.example.videoresizer`, minSdk 31 (Batch 80; sebelumnya 24), targetSdk/compileSdk 34
- AGP 8.4.0, Kotlin 1.9.24, Gradle 8.7 (no wrapper jar — see FILE_MANIFEST.txt)
- **Media3: 1.4.1** (bumped from 1.3.1 this batch, see Batch 9 below).
  Deliberately not bumped further — see "Defaults a new reader should know".

## Pending Queue (not done this batch — do next, in this order)
_Rebrand "Video Resizer" → "Vidsize" TUNTAS 100% (kode, UI, dokumentasi,
nama file APK Release). 1 item sisanya (rename repo GitHub) tetap aksi
manual di luar ZIP — lihat pesan chat._
🟡 **[RESUME POINT — Batch 89]** Fix bingkai bake (`Depth.kt` `bakeBitmap`: `inset(margin)`), `BAKE_MAX_BYTES` 6MB, kartu besar di-bake -> **SELESAI di ZIP; kurung seimbang + kotlinc 0 galat sintaks + detekt lokal 0; Gradle/lint/SpotBugs/tes & tampilan/kelancaran HP NOT VERIFIED (run terakhir = v86 hijau)** -> **LANGKAH BERIKUTNYA:** (1) push ZIP, kirim laporan run. Bila MERAH: baca `e:` pertama; kandidat `Depth.kt` (`CanvasDrawScope.draw`, `inset`, `ImageBitmap(w,h)`), `MainActivity.kt` (`FrameMetrics.DEADLINE`). Jalan balik = `Vidsize_v87.zip` (tanpa bake). (2) Uji HP: bingkai hilang di ubin/tombol; gulir/transisi; apakah refresh rate turun sendiri. (3) BELUM: neumorfik utk `VideoPickerScreen`, dialog, `OutlinedTextField`, header Riwayat.
🟡 **[RESUME POINT — Batch 88]** Bake bitmap depth+latar (`Depth.kt` `DepthBakery`/`RaisedKit`/`bakeBitmap`, `Ornaments.kt` `obtainBackdropBake`) + governor refresh rate (`MainActivity.onFrameMetrics`/`lowerRefreshCap`) -> **SELESAI di ZIP; kurung seimbang + kotlinc 0 galat sintaks + detekt lokal 0; Gradle/lint/SpotBugs/tes & kelancaran di Infinix NOT VERIFIED (run #27 = v86 hijau; v87 belum ada run)** -> **LANGKAH BERIKUTNYA:** (1) push ZIP, kirim laporan run berikutnya. Bila MERAH: baca `e:` pertama; kandidat `Depth.kt` (`CanvasDrawScope.draw`, `ImageBitmap(w,h)`, `lazy`/delegasi lokal), `MainActivity.kt` (`FrameMetrics.DEADLINE`). Jalan balik = `Vidsize_v87.zip`. (2) Uji Infinix: gulir/transisi; catat apakah refresh rate turun sendiri ke 90/60. Bila masih berat: bake bayangan kartu raksasa (nine-patch), kurangi lapisan, fling iOS. (3) BELUM: neumorfik utk `VideoPickerScreen`, dialog, `OutlinedTextField`, header Riwayat.
🟡 **[RESUME POINT — Batch 87]** Cull gambar layar tersembunyi + push iOS (`MainActivity.VideoResizerApp`: `settledScreen`/`resizerDrawn`/`homeDrawn`/`toolProgress`, `IosPushEasing`) -> **SELESAI di ZIP; kurung seimbang + kotlinc 0 galat sintaks + detekt lokal 0; Gradle/lint/tes & kelancaran di Infinix NOT VERIFIED (run #27 = v86: hijau penuh)** -> **LANGKAH BERIKUTNYA:** (1) push ZIP; harapan run: semua hijau. Bila MERAH: baca `e:` pertama; kandidat `MainActivity.kt` (`drawWithContent`, `CompositingStrategy`, impor `delay`). Jalan balik = `Vidsize_v86.zip`. (2) Uji Infinix: gulir Beranda/Resizer/alat, buka-tutup alat (paralaks), kembali ke Resizer tanpa kedip. Bila masih berat: minta tipe Infinix + layar terberat; opsi: cache `themeBackdrop` jadi bitmap, kurangi lapisan bayangan chip, fling ala iOS, batasi 90Hz. (3) BELUM: gaya neumorfik utk `VideoPickerScreen`, dialog, `OutlinedTextField`, header Riwayat.
🟡 **[RESUME POINT — Batch 86]** Optimasi render depth utk 120Hz (`Depth.kt`: `blurPaint` cache, `WellKit` lazy, `drawShadowBand` cincin + kilau terbatas utk elemen > 40.000dp^2) + filter SpotBugs `SA_FIELD_SELF_ASSIGNMENT` -> **SELESAI di ZIP; kurung seimbang + kotlinc 0 galat sintaks + detekt lokal 0; Gradle/lint/SpotBugs/tes & kelancaran di HP NOT VERIFIED (run #26 = v85: hijau, 1 SpotBugs noise)** -> **LANGKAH BERIKUTNYA:** (1) push ZIP; harapan run: SpotBugs 0 + semua hijau. Bila MERAH: baca `e:` pertama; kandidat `Depth.kt` (`val well by lazy`, `drawShadowBand`/`clipRect`, `density` di `drawWithCache`). Jalan balik = `Vidsize_v85.zip`. (2) Uji HP: gulir/ketuk chip/pindah layar di 120Hz; bayangan kartu besar tak berubah rupa. Bila masih stutter: minta merek/tipe HP + skenario; opsi: batasi 90Hz, cache `themeBackdrop` jadi bitmap, kurangi lapisan kontak chip. (3) BELUM: gaya neumorfik utk `VideoPickerScreen`, dialog, `OutlinedTextField`, header Riwayat.
🟡 **[RESUME POINT — Batch 85]** Kunci 60Hz (`MainActivity.applyHighestRefreshRate`) + depth lebih kuat (`Depth.kt`: ukuran/alfa, `depthRowPadding`, `DepthSwitch`, `DepthSlider`) -> **SELESAI di ZIP; kurung seimbang + kotlinc 0 galat sintaks + detekt lokal 0; Gradle/Compose, lint, ArchUnit, SpotBugs, tes & refresh rate/tampilan HP NOT VERIFIED (run #25 = v84: hijau penuh)** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run berikutnya diharapkan: build hijau, 40 tes, ArchUnit 12/0, SpotBugs 0, detekt 0, lint 0, kompilator 0 peringatan. Bila MERAH: baca `e:` pertama; kandidat: `Depth.kt` `DepthSlider`/`DepthSwitch` (`toggleable(value, interactionSource, indication, ...)`, `awaitHorizontalTouchSlopOrCancellation`, `horizontalDrag`, `ProgressBarRangeInfo`, `mutableFloatStateOf`), `MainActivity.kt` `applyHighestRefreshRate` (`display` nullability / `@Suppress("USELESS_ELVIS")`). Jalan balik = `Vidsize_v84.zip`.
(2) Uji HP (user): refresh rate naik >60Hz (Opsi pengembang -> tampilkan refresh rate; layar = Tinggi/Adaptif, Hemat baterai OFF), bayangan chip tidak terpotong, Slider/Switch bergaya timbul-cekung, gulir mulus. Tuning: `depthRowPadding()`, `DepthSize`, `DepthSpec`. Bila tetap 60Hz: kirim merek/tipe HP (OEM bisa menolak permintaan app).
(3) BELUM DIKERJAKAN: gaya neumorfik utk `VideoPickerScreen`, dialog, `OutlinedTextField`, header Riwayat; dok CTA GIF/Kompres; animasi masuk Beranda; strip "Terakhir diekspor"; splash Android 12; cek update otomatis; unduhan update di WorkManager.
🟡 **[RESUME POINT — Batch 84]** Realisme kedalaman neumorfik (Depth.kt: bayangan ambient+kontak, bevel, kilau, bibir lekuk; Color.kt aksen; exclude.xml filter Depth.kt/Ornaments.kt) -> **SELESAI di ZIP; kurung seimbang + kotlinc 0 galat parse + XML valid; Gradle/Compose, SpotBugs(10->0?)/ArchUnit/detekt/lint/tes & tampilan HP NOT VERIFIED (run #24 = v83: hijau kecuali SpotBugs 10)** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run berikutnya diharapkan: build hijau, 40 tes, ArchUnit 12/0, **SpotBugs 0**, detekt 0, lint 0, kompilator 0 peringatan. Bila MERAH: baca `e:` pertama; kandidat: `Depth.kt` (`Brush.linearGradient(vararg Pair, start, end)`, `Brush.radialGradient(vararg Pair, center, radius)`, `Stroke`). Jalan balik = `Vidsize_v83.zip` (+ filter exclude.xml Batch 84).
(2) Uji HP (user; kirim tangkapan layar bila masih kurang): bayangan kontak + ambient, bevel tepi, kilau, lekuk bergibir terlihat di 6 tema; gulir mulus. Tuning: `DepthSpec` (shadowAlpha/highlightAlpha/sheenAlpha/rim*/lip*), `DepthSize` di Depth.kt.
(3) BELUM DIKERJAKAN: gaya neumorfik utk `VideoPickerScreen`, dialog, `OutlinedTextField`, `Slider`, `Switch`, header Riwayat; dok CTA GIF/Kompres; animasi masuk Beranda; strip "Terakhir diekspor"; splash Android 12; cek update otomatis; unduhan update di WorkManager.
🟡 **[RESUME POINT — Batch 83]** Neumorfisme (timbul+cekung) di 5 gaya tema + Glass opak + ornamen khas tema (Depth.kt/Ornaments.kt baru; Color/Theme/MainActivity diubah) -> **SELESAI di ZIP; kurung seimbang + kotlinc 0 galat parse; Gradle/Compose, ArchUnit/SpotBugs/detekt/lint/tes & tampilan HP NOT VERIFIED (run #23 = v82 hijau)** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP, baca run berikutnya. Bila MERAH: baca `e:` pertama; kandidat: `Depth.kt` (`drawWithCache`/`onDrawBehind`, `android.graphics.Path.addRoundRect`, `RoundRect(...)`), `Ornaments.kt` (`drawPoints`, `clipRect`, `rotate`, `PathEffect.dashPathEffect`), `Theme.kt` (`ColorScheme.copy`), `DepthCard(onClick,...)` overload. SpotBugs: waspadai field/array publik (pola MS_EXPOSE_REP). Jalan balik = `Vidsize_v82.zip` (+ fix THEME_CHOICES sudah di v82).
(2) Uji HP (user), kirim tangkapan layar: tiap 6 tema -> Beranda/Resizer/Batch/GIF/Kompres/Riwayat: bayangan timbul & lekuk TERLIHAT (tidak nyatu), teks terbaca (Glass opak), ornamen tidak menutupi konten, gulir mulus (jumlah bayangan), dok Resizer, chip terpilih=cekung. Bila bayangan terlalu lemah/kuat: tuning `DepthSpec` (alfa/jarak) di Depth.kt, `DepthSize`.
(3) BELUM DIKERJAKAN: gaya neumorfik untuk `VideoPickerScreen`, dialog, `OutlinedTextField`, `Slider`, `Switch`, header Riwayat; `ToolTopBar` utk Riwayat; dok CTA GIF/Kompres; animasi masuk Beranda; strip "Terakhir diekspor"; splash Android 12; cek update otomatis; unduhan update di WorkManager.
🟡 **[RESUME POINT — Batch 82]** Hierarki UI "premium" (Beranda hero + grid 2x2, ToolTopBar, latar gradien seragam, dok CTA Resizer, picker kosong baru, fix SpotBugs THEME_CHOICES) -> **SELESAI di ZIP; kurung seimbang + kotlinc sintaks 0 galat parse (sisanya unresolved reference); build Gradle/AGP, Compose, SpotBugs/ArchUnit/detekt/lint, tes unit & tampilan di HP NOT VERIFIED** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run berikutnya diharapkan: build hijau, 40 tes, ArchUnit 12/0, **SpotBugs 0 (MS_EXPOSE_REP hilang)**, detekt 0, lint 0, kompilator 0 peringatan. Bila MERAH: baca `e:` pertama; kandidat: `HomeHeroCard`/`HomeToolCard` (`Card(onClick, interactionSource)` M3 1.2), `Modifier.offset`, `bottomBar` Resizer (variabel `startMs/endMs/canResize`), `ThemePreference.entries`; detekt `LongMethod`/`ComplexMethod` pada `ResizerScreen` bila ambang terlampaui. Jalan balik = `Vidsize_v81.zip` (kecuali `themeLabel()` yang menyelesaikan SpotBugs — salin ke v81).
(2) Uji HP (user), kirim tangkapan layar bila ada yang janggal: Beranda (hero terbaca di 6 tema, terutama Light/Warm Paper; ubin 2x2 seimbang; efek tekan; tombol Update/Tema 44dp); Resizer: dok tetap di bawah (tak tertutup navigasi 3-tombol/gestur), progres+Batalkan tampil terus saat menggulir, kartu hasil muncul di atas & layar menggulir ke atas otomatis, layar kecil/landscape masih menyisakan ruang gulir; GIF & Kompres: latar gradien di tema Glass, tombol kembali + konfirmasi saat proses; pemilih video kosong.
(3) TEMUAN BELUM DIKERJAKAN (ide premium berikutnya, perlu arahan): dok serupa untuk GIF & Kompres (CTA masih di ujung gulir); animasi masuk berjenjang (stagger) Beranda; strip "Terakhir diekspor" di Beranda dari riwayat; `ToolTopBar` untuk Riwayat/`VideoPickerScreen`; splash Android 12 (`windowSplashScreenBackground`); cek update otomatis saat start; unduhan update di WorkManager/foreground service (butuh dependensi).
🟡 **[RESUME POINT — Batch 81]** Audit hierarki tampilan (Update/Tema -> Beranda, cache unduhan update, catatan rilis tetap tampil, back Resizer -> Beranda, ikon adaptif) -> **SELESAI di ZIP; XML valid + kotlinc sintaks OK (hanya unresolved reference); build Gradle/AGP, Compose, ArchUnit/SpotBugs/detekt/lint, tes unit & perilaku HP NOT VERIFIED** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run berikutnya diharapkan: build hijau, 40 tes, ArchUnit 12/0, SpotBugs 0 (waspadai `RV_RETURN_VALUE_IGNORED`/NP di `AppUpdater.kt` baru), detekt 0, lint 0, kompilator 0 peringatan (`getPackageArchiveInfo` ber-`@Suppress("DEPRECATION")`). Bila MERAH: baca `e:` pertama di laporan; kandidat: `UpdateDialog`/`HomeScreen` (Compose, tak terkompilasi di sandbox), `BadgedBox`/`Badge`/`CircularProgressIndicator(progress = {})` (Material3 1.2).
(2) Uji HP (user): ikon launcher/splash/ikon bertema; Beranda -> ikon Update -> unduh -> (di pemasang) batal via gestur kembali -> buka dialog lagi: harus "Siap dipasang" TANPA unduh ulang; "Batalkan unduhan" menghentikan transfer; catatan rilis terlihat selama unduh; back di Resizer -> Beranda; transisi Beranda <-> alat tanpa Resizer tembus; menu Tema di Beranda.
(3) TEMUAN BELUM DIKERJAKAN (di luar batas 8 berkas): splash Android 12 memakai warna `windowBackground` (putih/hitam sebelum UI gelap) -> set `windowSplashScreenBackground`/ikon animasi di `themes.xml`; unduhan update masih di korutin app-scope (mati bila proses dimatikan sistem; ideal: WorkManager/foreground service, butuh dependensi); cek update otomatis saat start (lencana muncul tanpa ketuk) belum ada; `isProcessing` BackHandler Resizer tetap aktif saat di Beranda (dialog "Batalkan proses?" muncul di atas Beranda — disengaja, melindungi ekspor); pemilih video/Studio masih punya header sendiri (belum diseragamkan).
🟡 **[RESUME POINT — Batch 80]** `minSdk` 24 -> 31 (Android 12+) di `app/build.gradle.kts` -> **SELESAI di ZIP; build Gradle/AGP, ArchUnit/SpotBugs/detekt/lint, tes unit & pemasangan di HP NOT VERIFIED** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run berikutnya diharapkan: build hijau, Tes unit LULUS (40 tes, 0 dilewati), ArchUnit 12/0, SpotBugs 0, detekt 0, lint 0 (`NewApi` hanya bisa berkurang), kompilator 0. Bila MERAH: baca `e:`/pesan manifest-merger di laporan; jalan balik = `minSdk = 24` (1 baris) di `app/build.gradle.kts`.
(2) Uji HP (user): pasang v80 DI ATAS versi lama di perangkat Android 12+ (update biasa; keystore/applicationId tak berubah) -> berhasil; perangkat < 12 memang akan ditolak sistem (INSTALL_FAILED_OLDER_SDK).
(3) PEMBERSIHAN OPSIONAL (TIDAK dikerjakan; di luar permintaan, menyentuh 8 berkas kode + izin manifest -> tunggu arahan): cek `SDK_INT` yang kini selalu benar/salah (kode mati): `AppUpdater.kt` baris 91 (>=28) & 208 (>=26); `ui/theme/Theme.kt` 171/173 (>=S); `MainActivity.kt` 1814 (`<= P` = selalu false; cabang izin WRITE_EXTERNAL_STORAGE ikut mati); `CrashLogger.kt` 142 (>=P); `PublicMovieExporter.kt` 39/121 (>=Q); `ExportForegroundService.kt` 90 (>=O); `Haptics.kt` 39/49/51/66 (<O, `@RequiresApi(O)`, >=Q, >=S); `VideoPickerScreen.kt` 213 (>=Q). Tetap berlaku (>=33): `MainActivity.kt` 901/2221, `ExportForegroundService.kt` 125, `VideoPickerScreen.kt` 87. Izin `WRITE_EXTERNAL_STORAGE maxSdkVersion=28` di manifest kini tak berpengaruh (dipertahankan sesuai aturan "pertahankan izin"). `ObsoleteSdkInt` TIDAK aktif di whitelist lint, jadi kode mati ini tidak memunculkan peringatan. Resume point Batch 79 & di bawah tetap berlaku; butir "minSdk 24 vs API 31+" di Batch 74/79 SELESAI.

🟡 **[RESUME POINT — Batch 79]** Verifikasi run #20 (hijau penuh, 40 tes) -> **SELESAI di CI untuk statis/kompilasi/tes unit JVM; perilaku di HP NOT VERIFIED** (kode TIDAK berubah, hanya docs) ->
**LANGKAH BERIKUTNYA:** (1) push ZIP (docs-only): run #21 diharapkan IDENTIK dgn run #20 (Tes unit 40 / 0 dilewati; ArchUnit 12/0, SpotBugs 0, detekt 0, lint 0, kompilator 0). Berhenti mengejar angka CI: tak ada temuan terbuka; tambahan tes JVM berikutnya butuh keputusan user (Robolectric/mock `Uri`).
(2) SATU-SATUNYA verifikasi tersisa = uji manual di HP (user), kirim hasilnya ke chat: (a) Kompres video berbitrate RENDAH (video < ~290 kbps, mis. rekaman layar statis) -> file hasil harus LEBIH KECIL dari sumber (sebelum Batch 78 membesar ke 250 kbps); (b) Kompres video biasa -> hasil sama seperti sebelumnya; (c) "Perkiraan hasil" tab Kompres & "Ukuran target (MB)" vs ukuran file nyata; (d) butir uji manual Batch 74 (2) & Batch 75 (2).
(3) Butuh keputusan/masukan user sebelum batch kode berikutnya: hasil uji HP (2); Robolectric/mock `Uri` utk tes `computeTargetDimensions`/`estimateOutputSizeBytes` (dependensi tes baru); minSdk 24 vs prioritas API 31+; sisa kandidat Batch 74 (3) (`rememberSaveable` Batch/GIF/Kompres/Studio, `Saver` `trimRange`, aksi aksesibilitas `TrimHandle`, label konteks progress) — perubahan UI besar yang TIDAK bisa diverifikasi di sandbox, jadi dikerjakan hanya atas arahan. Keterbatasan Batch 78 (3) tetap berlaku. Resume point Batch 78 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 78]** Verifikasi run #19 (hijau penuh, 33 tes) + perbaikan bitrate Kompres (batas 85% sumber menang atas lantai 250 kbps; `compressVideoBitrateBps` bersama ekspor & pratinjau) -> **SELESAI di ZIP; kompilasi Kotlin 2.0.21 (bahasa 1.9) 0 peringatan, 32/32 lulus + mutasi + diferensial 1152 kasus + detekt lokal 0 di sandbox; `testReleaseUnitTest` sungguhan/ArchUnit/SpotBugs/lint & perilaku HP NOT VERIFIED** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #20 diharapkan: build hijau, Tes unit LULUS (40 tes = 8 + 6 + 26, 0 dilewati), ArchUnit 12/0, SpotBugs 0, detekt 0, lint 0, kompilator 0. Bila merah: baca bagian TES UNIT/kompilasi laporan; jalan balik = kembalikan `VideoResizer.kt` + `VideoResizerKalkulasiTest.kt` ke `Vidsize_v77.zip`.
(2) Uji manual di HP (user) — verifikasi nyata perubahan ini: kompres satu video BERBITRATE RENDAH (video < ~290 kbps; mis. rekaman layar statis) -> ukuran hasil harus LEBIH KECIL dari sumber (dulu membesar); lalu satu video biasa (bitrate tinggi) -> hasil sama seperti sebelumnya (batas 85% & level tak berubah). Bandingkan juga "Perkiraan hasil" dgn ukuran file nyata. Butir Batch 75 (2) tetap berlaku.
(3) Keterbatasan yang diketahui (tidak diubah): (a) `computeCompressTargetBitrateBps` & `estimateSourceBitrateBps` bersifat private + butuh `Uri` -> TIDAK terjangkau tes JVM CI; hanya divalidasi lewat refleksi di sandbox (kerangka ekstrak), bukan di CI; (b) bila sumber TIDAK di-mute, bitrate audio gagal diprobe, dan total bitrate sumber < 128 kbps, bitrate video sumber dianggap tak diketahui (audio asumsi 128k >= total) -> batas 85% tak dipakai (kasus langka); (c) sumber berbitrate video < ~75 kbps masih bisa naik sampai lantai mutlak 64 kbps (disengaja, kecil mutlak); (d) Media3 `DefaultEncoderFactory` terhadap bitrate rendah (mis. 64-250 kbps) pada encoder H.265 nyata NOT VERIFIED (setEnableFallback(true) tetap ada).
(4) Sisa kandidat tes: `computeTargetDimensions`/`estimateOutputSizeBytes` butuh `ResizeRequest` yang mewajibkan `Uri` Android -> perlu Robolectric/mock (dependensi tes baru = keputusan user) atau pemisahan fungsi murni (structural, tidak dikerjakan); butir Batch 74 (`rememberSaveable` Batch/GIF/Kompres/Studio, `Saver` `trimRange`, aksi aksesibilitas `TrimHandle`, minSdk 24 vs prioritas API 31+ menunggu keputusan user) tetap berlaku. Butir (3) resume point Batch 77 (lantai vs batas 85%) SELESAI di Batch 78. Resume point Batch 77 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 77]** Verifikasi run #18 (hijau penuh) + tes perilaku `SocialPresetTest` & `VideoResizerKalkulasiTest` (25 tes JVM murni) -> **SELESAI di ZIP; kompilasi Kotlin 2.0.21 (bahasa 1.9) 0 peringatan, 25/25 lulus + 20 mutasi terdeteksi + detekt lokal 0 di sandbox; `testReleaseUnitTest` sungguhan/ArchUnit/SpotBugs/lint & perilaku HP NOT VERIFIED** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #19 diharapkan: build hijau, Tes unit LULUS (33 tes = 8 + 25, 0 dilewati), ArchUnit 12/0, SpotBugs 0, detekt 0, lint 0, kompilator 0; catatan laporan kini menyebut tiga kelas tes perilaku. Bila `testReleaseUnitTest` MERAH/NOT VERIFIED: baca bagian TES UNIT laporan (kemungkinan: inisialisasi kelas `VideoResizer` di classpath tes AGP `mockable-android`/Media3 — di sandbox hanya diuji dgn kerangka ekstrak, BUKAN kelas utuh); jalan balik = hapus `SocialPresetTest.kt` + `VideoResizerKalkulasiTest.kt` + kembalikan 2 catatan `android-artifact.yml` ke `Vidsize_v76.zip` (kode produksi tak tersentuh).
(2) Uji manual di HP (user) tetap satu-satunya verifikasi perilaku nyata (butir Batch 75 (2) berlaku): bandingkan label "Perkiraan hasil" tab Kompres dan "Ukuran target (MB)" dgn ukuran file keluaran sungguhan pada satu video uji (estimasi = pendekatan, akurasi vs encoder NOT VERIFIED).
(3) Temuan untuk KEPUTUSAN user (tidak diubah di batch ini; tercatat sebagai karakterisasi di `estimasi_sumberBitrateSangatRendah_lantaiBitrateMengalahkanBatas_perilakuSaatIni`): `computeCompressTargetBitrateBps` & `estimateCompressedSizeBytes` memberi LANTAI 250 kbps yang mengalahkan batas 85% bitrate sumber, jadi sumber berbitrate video < ~294 kbps bisa menghasilkan file keluaran LEBIH BESAR dari sumber (bertentangan dgn klaim KDoc "tidak membesar"). Perbaikan menyentuh logika produksi (2 situs) -> tunggu arahan.
(4) Sisa kandidat tes: `computeTargetDimensions`/`estimateOutputSizeBytes` butuh `ResizeRequest` yang mewajibkan `Uri` Android -> perlu Robolectric/mock (dependensi tes baru = keputusan user) atau pemisahan fungsi murni (structural, tidak dikerjakan); butir Batch 74 (`rememberSaveable` Batch/GIF/Kompres/Studio, `Saver` `trimRange`, aksi aksesibilitas `TrimHandle`, minSdk 24 vs prioritas API 31+ menunggu keputusan user) tetap berlaku. Resume point Batch 76 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 76]** Verifikasi run #17 (hijau penuh) + tes perilaku pertama `CrashLoggerTest` (kontrak `CrashLogger.nonFatal`) -> **SELESAI di ZIP; kompilasi stub kotlinc 0 peringatan, 7/7 lulus + uji mutasi + detekt lokal 0 di sandbox; `testReleaseUnitTest` sungguhan/ArchUnit/SpotBugs/lint & perilaku HP NOT VERIFIED** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #18 diharapkan: build hijau, Tes unit LULUS (8 tes, 0 dilewati), ArchUnit 12/0, SpotBugs 0, detekt 0, lint 0, kompilator 0; catatan laporan kini menyebut `CrashLoggerTest`. Bila `testReleaseUnitTest` MERAH/NOT VERIFIED: baca bagian TES UNIT laporan (kemungkinan: JUnit asli menolak overload `assertEquals` atau classpath tes); jalan balik = hapus `CrashLoggerTest.kt` + kembalikan 2 catatan `android-artifact.yml` ke `Vidsize_v75.zip` (kode produksi tak tersentuh).
(2) Uji manual di HP (user) = satu-satunya verifikasi tersisa (butir Batch 75 (2) tetap berlaku): picu kegagalan (video rusak, ekspor/GIF gagal, matikan jaringan saat "Unduh & Pasang") -> pesan galat wajar + `Documents/VideoResizer/logs/error_*.txt`; `startForeground` ditolak OS -> tidak crash.
(3) Sisa kandidat: tes perilaku murni-JVM berikutnya hanya bila ada logika murni yang bisa dipisah tanpa structural rewrite (mis. `SocialPreset.kt`, kalkulasi di `VideoResizer.kt`); butir Batch 74 (`rememberSaveable` Batch/GIF/Kompres/Studio, `Saver` `trimRange`, aksi aksesibilitas `TrimHandle`, minSdk 24 vs prioritas API 31+ menunggu keputusan user) tetap berlaku. Resume point Batch 75 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 75]** Refactor 20 temuan detekt `TooGenericExceptionCaught` (run #16) ke `CrashLogger.nonFatal` (7 berkas kode) -> **SELESAI di ZIP; detekt lokal 0 temuan + kompilasi stub kotlinc OK di sandbox; build Gradle/ArchUnit/SpotBugs/lint & perilaku HP NOT VERIFIED** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #17 diharapkan: build hijau, detekt 0 (dari 20), ArchUnit 12/0, SpotBugs 0 (risiko: bytecode inline `runCatching` baru — bila SpotBugs memunculkan pola baru, baca laporan dan JANGAN filter tanpa bukti), lint 0, kompilator 0. Bila build MERAH: baca `e:` di laporan; jalan balik = kembalikan 7 berkas kode ke `Vidsize_v74.zip`.
(2) Uji manual di HP (user): picu kegagalan (video rusak/tak terbaca, ekspor/GIF gagal, matikan jaringan saat "Unduh & Pasang") -> pesan galat wajar + `Documents/VideoResizer/logs/error_*.txt` tetap terbentuk; bila OS menolak `startForeground`, aplikasi tidak crash.
(3) Sisa kandidat: tes unit perilaku `CrashLogger.nonFatal` (butuh ubah catatan statis di `android-artifact.yml` baris 22 & 325 -> di luar batas berkas batch ini); butir Batch 74 tetap berlaku. Resume point Batch 74 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 74]** Audit UI/UX vs Vital Guards + perbaikan (edge-to-edge, `rememberSaveable` 22 state `ResizerScreen`, `configChanges` diperluas, I/O pemilih Batch ke IO, bahasa UI, semantics tab/Studio) -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #15 diharapkan: build hijau, SpotBugs 0, detekt 20 (bisa bergeser), lint 0, kompilator 0, ArchUnit 12/0. API `enableEdgeToEdge`/`SystemBarStyle.auto`/`rememberSaveable`/`selectable` BELUM pernah dikompilasi di sandbox: bila build MERAH baca `e:` di laporan, jalan balik per butir di CHANGELOG Batch 74 (edge-to-edge: hapus `enableEdgeToEdge()` + modifier `Surface` akar + inset `VideoPickerScreen`, kembalikan `SideEffect` warna opak `Theme.kt`).
(2) Uji manual di HP (user): bar status/navigasi transparan & konten tidak tertutup (portrait + landscape, navigasi gestur + 3-tombol); keyboard pada kolom teks caption/dialog tidak menutupi; ubah mode gelap sistem / ukuran font saat video terpilih -> pilihan TIDAK hilang; taruh app di background lama lalu buka lagi -> pengaturan layar utama pulih (video: bila gagal terbaca, pesan galat wajar, bukan crash); `Documents/VideoResizer/logs/error_*.txt` bila ada galat; TalkBack: tab terbaca "terpilih".
(3) Sisa kandidat: `rememberSaveable` untuk Batch/GIF/Kompres/Studio; `Saver` `trimRange`; aksi aksesibilitas `TrimHandle`; label konteks progress; minSdk 24 vs prioritas API 31+ (menunggu keputusan user); WorkManager TIDAK dikerjakan (konflik dgn MICRO_POLISH_GUIDE "DILARANG refactor arsitektur"). Tidak diverifikasi di sandbox: kompilasi Kotlin, build/tes/lint/SpotBugs/detekt, perilaku di HP. Resume point Batch 73 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 73]** Verifikasi run #14 (hijau penuh) -> **SELESAI di CI untuk statis/kompilasi; perilaku di HP NOT VERIFIED** (kode TIDAK berubah, hanya docs) ->
**LANGKAH BERIKUTNYA:** (1) push ZIP (docs saja; push tetap memicu build rilis baru oleh `build.yml`). (2) Uji manual di HP (user) = satu-satunya verifikasi tersisa: pilih video rusak/tak terbaca ->
cek `Documents/VideoResizer/logs/error_*.txt` (`Tag: loadSourceMetadata`/`loadSourceInfo` + stacktrace mentah); ekspor video/GIF yang gagal -> `error_*.txt`; "Unduh & Pasang" pembaruan tetap berjalan.
(3) Tidak ada temuan CI yang bisa ditindak: SpotBugs 0, lint 0, kompilator 0, ArchUnit 12/0; sisa detekt `TooGenericExceptionCaught` x20 = gaya, TIDAK difilter (tak ada bukti baru; `startForeground` HARUS tetap
`Exception`). Butir berikutnya menunggu arahan user (bug/fitur dari uji HP), bukan perbaikan laporan CI. Butir (1) resume point Batch 72 TERBUKTI (run #14 sesuai prediksi); resume point Batch 72 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 72]** Triase run #13 + `logCaught` di `GifExporter.kt` (2), `AppUpdater.kt` (2), `MainActivity.kt` (2) + `AppUpdater.kt` jadi klien logger -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #14 diharapkan: build hijau, ArchUnit 12/0 (bila ada pelanggaran: baca `kelas proyek dari berkas lain` pada aturan "mandiri: AppUpdater.kt" — JANGAN longgarkan lebih luas
tanpa bukti), SpotBugs 0, detekt 20, lint 0, kompilator 0. Bila build MERAH: baca `e:` di laporan; jalan balik = hapus 6 baris `CrashLogger.logCaught(...)` batch ini dan kembalikan `LOGGER_CLIENT_FILES` ke 3 berkas.
(2) Uji manual di HP (user): pilih video yang tak terbaca/rusak -> cek `Documents/VideoResizer/logs/error_*.txt` (berisi `Tag: loadSourceMetadata`/`loadSourceInfo` + stacktrace mentah). (3) Sisa kandidat: HANYA detekt
`TooGenericExceptionCaught` x20 (gaya; `startForeground` HARUS tetap `Exception`) dan 2 catch frekuensi tinggi `MainActivity.kt` (`decodeSampledBitmapFromFile`, `extractVideoThumbnail`) yang sengaja tetap hanya ke
logcat; tak ada temuan SpotBugs/lint/kompilator tersisa. Butir berikutnya perlu arahan user (fitur/bug baru dari uji HP), bukan perbaikan laporan CI. Tidak diverifikasi di sandbox: kompilasi Kotlin,
build/tes/lint/SpotBugs/detekt, perilaku di HP. Butir (1) resume point Batch 71 TERBUKTI (SpotBugs 0); resume point Batch 71 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 71]** Triase run #12 + `RV_RETURN_VALUE_IGNORED` x7 diperbaiki (`mkdirs()`/`delete()` kini dipakai hasilnya) -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #13 diharapkan: build hijau, ArchUnit 12/0, detekt 20 (tak berubah), lint 0, kompilator 0, SpotBugs 0 temuan `RV_RETURN_VALUE_IGNORED` (bila masih ada: baca
`[elemen kode]` dan baris di laporan, JANGAN menyaring tanpa bukti). Bila build MERAH: baca `e:` di laporan; jalan balik = kembalikan 7 situs ke bentuk Batch 70 (`.apply { mkdirs() }` / `outFile.delete()`),
hapus `ensureThumbDir` di `MainActivity.kt` dan `import java.io.IOException` di `PublicMovieExporter.kt`. (2) Uji manual di HP (user): "Unduh & Pasang" pembaruan tetap jalan; ekspor video -> thumbnail muncul di
riwayat. (3) Sisa kandidat: pasang `logCaught` di `GifExporter.kt` (89/144), catch `MainActivity.kt` (1978/4347/4364/4492/4959), `AppUpdater.kt` (butuh `LOGGER_CLIENT_FILES` += `AppUpdater.kt`); detekt
`TooGenericExceptionCaught` x20 (`startForeground` HARUS tetap `Exception`). Tidak diverifikasi di sandbox: kompilasi Kotlin, build/tes/lint/SpotBugs/detekt, perilaku di HP. Butir (1) resume point Batch 70
TERBUKTI (run #12 sesuai prediksi); resume point Batch 70 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 70]** Triase run #11 + `CrashLogger.logCaught` (catch tertangkap -> `Documents/`) + aturan ArchUnit "mandiri" dilonggarkan sempit -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #12 diharapkan: build hijau, ArchUnit 12/0 (bila ada pelanggaran: baca `kelas proyek dari berkas lain` pada aturan "mandiri" — kemungkinan kelas sintetis
`CrashLogger$...` salah dipetakan `fileOf`, JANGAN longgarkan lebih luas tanpa bukti), detekt 20, lint 0, kompilator 0, SpotBugs 7 (+ mungkin temuan baru di `CrashLogger.kt`, mis.
`SIC_INNER_SHOULD_BE_STATIC_ANON` untuk lambda `ThreadFactory`: baca `[elemen kode]`, jangan menyaring tanpa bukti). Bila build MERAH: baca `e:` di laporan; jalan balik = hapus 9 panggilan
`CrashLogger.logCaught(...)` + `logCaught` + `caughtLogExecutor` (+ kembalikan `standalone(file)` di `ArchitectureTest.kt`). (2) Uji manual di HP (user): picu ekspor yang gagal -> cek
`Documents/VideoResizer/logs/error_*.txt` (berisi `Tag:` + stacktrace mentah). (3) Sisa kandidat: pasang `logCaught` di `GifExporter.kt` (89/144), catch `MainActivity.kt` (1978/4347/4364/4492/4959),
`AppUpdater.kt` (butuh `LOGGER_CLIENT_FILES` += `AppUpdater.kt`); RV x7 dibiarkan terlihat; detekt `TooGenericExceptionCaught` x20 (`startForeground` HARUS tetap `Exception`). Tidak diverifikasi di
sandbox: kompilasi Kotlin, build/tes/lint/SpotBugs/detekt, perilaku di HP. Resume point Batch 69 & di bawah tetap berlaku (butir "KEPUTUSAN ARSITEKTUR menunggu user" di dalamnya SUDAH diputuskan Batch 70).

🟡 **[RESUME POINT — Batch 69]** Triase run #10 + `AppUpdater.download()` OkHttp+Okio -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** -> **LANGKAH BERIKUTNYA:** (1) push ZIP; run #11
diharapkan: build hijau (resolusi `okhttp:4.12.0` + `okio:3.6.0` dari mavenCentral), detekt 20, lint 0, ArchUnit 12/0, kompilator 0, SpotBugs 7 (+ mungkin temuan baru di `AppUpdater.kt`:
baca `[elemen kode]` + pesan, JANGAN menyaring tanpa bukti). Bila build MERAH di `AppUpdater.kt`/resolusi dependency: baca `e:`/"Could not resolve" di laporan; jalan balik = kembalikan
`download()` ke HttpURLConnection (Batch 68) + hapus 2 baris dependency. (2) Uji manual di HP (user): Cek update -> unduh APK -> pasang (butuh rilis lebih baru dari yang terpasang);
cek progres 0..100% dan tidak OOM. (3) Sisa kandidat: `RV_RETURN_VALUE_IGNORED` x7 (dibiarkan terlihat); detekt `TooGenericExceptionCaught` x20 (`startForeground` HARUS tetap `Exception`).
(4) KEPUTUSAN ARSITEKTUR menunggu user: catch yang kini hanya ke logcat belum tertulis ke `Documents/` (aturan ArchUnit "mandiri" melarang `PublicMovieExporter.kt`/
`ExportForegroundService.kt` memanggil `CrashLogger.kt`). Tidak diverifikasi di sandbox: kompilasi Kotlin, build/tes/lint/SpotBugs/detekt, perilaku di HP. Resume point Batch 68 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 68]** Triase run #9: filter UUF `$stable` + `setTransformationRequest` -> `Transformer.Builder.setVideoMimeType` + blok contoh SARIF dihapus ->
**SELESAI di ZIP, NOT VERIFIED di CI/HP** -> **LANGKAH BERIKUTNYA:** (1) push ZIP; run #10 diharapkan: build hijau, SpotBugs 31 -> 7 (hanya RV x7; bila UUF muncul lagi, baca nama field di
`[elemen kode]`, jangan perluas filter), kompilator 1 -> 0 (bila tetap 1, baca baris `w:`), detekt 20, lint 0, ArchUnit 12/0. Bila build MERAH di `VideoResizer.kt`: periksa
`setVideoMimeType` pada `compressInternal` (ada di Media3 1.4.1 menurut sumber tag, tapi kompilasi belum pernah dijalankan di sandbox) -> kembalikan ke `setTransformationRequest` bila perlu.
(2) Sisa kandidat: `RV_RETURN_VALUE_IGNORED` x7 (`mkdirs()`/`delete()`) dibiarkan terlihat; detekt `TooGenericExceptionCaught` x20 (`startForeground` HARUS tetap `Exception`);
`AppUpdater.download()` belum OkHttp+Okio (aturan guard user: butuh dependency baru, verifikasi di CI). (3) KEPUTUSAN ARSITEKTUR menunggu user: catch yang kini hanya ke logcat belum
tertulis ke `Documents/` (aturan ArchUnit "mandiri" melarang `PublicMovieExporter.kt`/`ExportForegroundService.kt` memanggil `CrashLogger.kt`). Tidak diverifikasi di sandbox:
kompilasi Kotlin, build/tes/lint/SpotBugs/detekt. Resume point Batch 67 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 67]** Triase run #8: log mentah `startForeground` + baris MediaStore yatim + filter NP `AppUpdater.kt` + elemen kode/contoh SARIF di laporan CI ->
**SELESAI di ZIP, NOT VERIFIED di CI/HP** -> **LANGKAH BERIKUTNYA:** (1) push ZIP; run #9 diharapkan: build hijau, detekt 21 -> 20 (bila tetap 21, `ExportForegroundService.kt:69`
masih `SwallowedException` -> baca laporan, jangan matikan aturan), SpotBugs 33 -> 31 (UUF 24 + RV 7; bila tetap 33, filter `<Source name="AppUpdater.kt"/>` tak cocok -> baca contoh SARIF,
jangan perluas tanpa bukti), kompilator 1, ArchUnit 12/0. Run #8 sudah TERVERIFIKASI di CI: semua prediksi Batch 66 benar (detekt 37 -> 21, SpotBugs 64 -> 33, kompilator 1).
(2) BACA bagian "Titik temuan" baru (`RULE @ berkas:baris [elemen kode] | pesan`) + "Contoh SARIF mentah": bila 24 UUF ternyata semua `...$stable` -> saring dgn
`<Match><Bug pattern="UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD"/><Field name="$stable"/></Match>` (SEMPIT per nama field, JANGAN per berkas); bila ada nama field lain -> baca kode field itu
dan putuskan hapus/saring per bukti. Bila `[elemen kode]` kosong, struktur SARIF ada di blok contoh mentah -> sesuaikan ekspresi `jq` di `android-artifact.yml` (bukan menebak). Setelah UUF
terjawab, blok "Contoh SARIF mentah" boleh dihapus. (3) Sisa kandidat: `RV_RETURN_VALUE_IGNORED` x7 (`mkdirs()`/`delete()`) dibiarkan; detekt `TooGenericExceptionCaught` x20 (menyempitkan
tipe butuh kompilator; `startForeground` HARUS tetap `Exception`); `setTransformationRequest` deprecated (cek API pengganti Media3 1.4.1 saat ada akses dokumentasi/CI);
`AppUpdater.download()` belum OkHttp+Okio (aturan guard user: butuh dependency baru, verifikasi di CI). (4) KEPUTUSAN ARSITEKTUR menunggu user: catch yang kini hanya ke logcat belum
tertulis ke `Documents/` (aturan ArchUnit "mandiri" melarang `PublicMovieExporter.kt`/`ExportForegroundService.kt` memanggil `CrashLogger.kt`; bila log tangkapan harus ke Documents,
aturan itu perlu diubah). Tidak diverifikasi di sandbox: kompilasi Kotlin (tak ada kotlinc; Maven diblokir proxy), build/tes/lint/SpotBugs/detekt. Resume point Batch 66 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 66]** Triase run #7: log mentah untuk catch yang menelan pengecualian + 31 filter SpotBugs terbukti + pesan SARIF di laporan CI -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #8 diharapkan: build hijau, detekt 37 -> 21 (bila tetap 37, detekt TIDAK menganggap `e` di `Log.*` sebagai terpakai -> baca laporan,
jangan matikan aturan), SpotBugs 64 -> 33 (UUF 24 + RV 7 + NP_PARAM 2), kompilator 1, ArchUnit 12/0. Run #7 sudah TERVERIFIKASI di CI: prediksi Batch 65 (157 -> 64, kompilator 3 -> 1) benar.
(2) BACA pesan SpotBugs baru di bagian "Titik temuan" (format `RULE @ berkas:baris | pesan`): bila 24 UUF ternyata semua `...$stable` (field Compose) -> saring dengan
`<Match><Bug pattern="UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD"/><Field name="$stable"/></Match>` (SEMPIT per nama field), JANGAN per berkas. (3) Sisa kandidat: `ExportForegroundService.kt:69`
(catch menelan `startForeground`; di API 31+ bisa `ForegroundServiceStartNotAllowedException`) -> `Log.e` mentah; bug nyata kecil `PublicMovieExporter` `?: return null` meninggalkan baris
MediaStore `IS_PENDING=1` yatim (hapus baris seperti pola `CrashLogger.writeLog`); `AppUpdater.kt` NP_PARAMETER x2 (:0) belum dibaca; `RV_RETURN_VALUE_IGNORED` x7 (`mkdirs()`/`delete()`) dibiarkan;
detekt `TooGenericExceptionCaught` x20 (menyempitkan tipe butuh kompilator -> hati-hati, pelajaran Batch 63/64); `setTransformationRequest` deprecated (cek API pengganti Media3 1.4.1);
`AppUpdater.download()` belum OkHttp+Okio (butuh dependency baru, tak bisa diverifikasi tanpa CI). (4) KEPUTUSAN ARSITEKTUR menunggu user: catch yang kini hanya ke logcat belum
tertulis ke `Documents/` (aturan ArchUnit "mandiri" melarang `PublicMovieExporter.kt` memanggil `CrashLogger.kt`; bila log tangkapan harus ke Documents, aturan itu perlu diubah).
Tidak diverifikasi di sandbox: kompilasi Kotlin, build/tes/lint/SpotBugs/detekt. Resume point Batch 65 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 65]** Verifikasi run #6 + 2 peringatan baru ditutup + filter NP Compose -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; run #7 diharapkan: build hijau, peringatan kompilator 3 -> 1 (sisa `setTransformationRequest`
`VideoResizer.kt:508`), SpotBugs 157 -> 64 (bila tetap 157, filter NP tak cocok -> baca SARIF, jangan perluas tanpa bukti). Run #6 sudah
TERVERIFIKASI di CI: build hijau, ArchUnit 12 aturan 0 pelanggaran, tes 1 lulus (bukan bukti perilaku), lint 0 isu, detekt 37, SpotBugs 157,
filter `LazyDsl.kt` bekerja. (2) Sisa kandidat: pengecualian ditelan tanpa jejak mentah di `PublicMovieExporter.kt`/`MainActivity.kt`/`CrashLogger.kt`
(detekt `SwallowedException` + `TooGenericExceptionCaught`; aturan ArchUnit "mandiri" melarang `PublicMovieExporter.kt` memanggil `CrashLogger.kt` ->
perlu keputusan arsitektur); `AppUpdater.download()` belum OkHttp+Okio (sudah streaming 8 KB; butuh dependency baru, tak bisa diverifikasi tanpa CI);
`setTransformationRequest` deprecated (Media3 1.4.1 — cek API pengganti versi terpasang). (3) SpotBugs sisa yang belum dibaca: `BC_BAD_CAST_TO_ABSTRACT_COLLECTION`
(9), `MS_EXPOSE_REP` (10), `RV_RETURN_VALUE_IGNORED` (7), `REC_CATCH_EXCEPTION` (6), `UUF_UNUSED_PUBLIC_OR_PROTECTED_FIELD` (24, baris :0). Yang sudah dibaca =
false positive codegen: `NP_NULL_ON_SOME_PATH` `VideoResizer.kt`, `SA_LOCAL_SELF_ASSIGNMENT`, `OBL_UNSATISFIED_OBLIGATION` `AppUpdater.kt:158`. (4) PELAJARAN: setiap
penghapusan parameter composable bisa membuat parameter pemanggilnya ikut tak terpakai (rantai) — telusuri ke atas sampai ke sumber nilai sebelum
mengirim. Tidak diverifikasi di sandbox: kompilasi Kotlin, build/tes/lint/SpotBugs/detekt. Resume point Batch 64 & 63 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 64]** Build rusak run #5 (`ThreadLocal` dibayangi `androidx.compose.runtime.*`) -> **SELESAI di ZIP, NOT VERIFIED di
CI/HP** -> **LANGKAH BERIKUTNYA:** (1) push ZIP; run #6 harus HIJAU (`compileReleaseKotlin`). Bila masih gagal, baca baris `e:` di
laporan (hanya `VideoPickerScreen.kt:167-171` yang gagal di run #5; `MainActivity.kt` lolos analisis). (2) Semua target Batch 63 masih
menunggu verifikasi karena run #5 tak menghasilkan ArchUnit/tes/SpotBugs/lint/peringatan kompilator: peringatan kompilator 13 -> 1 (sisa
`setTransformationRequest` `VideoResizer.kt:508`), SpotBugs 226 -> 157 bila filter `LazyDsl.kt` bekerja (bila 225: filter tak cocok ->
baca SARIF, jangan perluas tanpa bukti), lint 0 isu (artifact hanya memuat `.txt`). (3) PELAJARAN: berkas ber-`import androidx.compose.runtime.*`
(`MainActivity.kt`, `VideoPickerScreen.kt`) membayangi nama `java.lang` yang sama — pakai nama penuh (`java.lang.ThreadLocal`), jangan
mengandalkan impor default; sandbox tak bisa mengompilasi, jadi perubahan yang menyentuh nama tipe/impor berisiko NOT VERIFIED sampai CI.
(4) Sisa kandidat Batch 63 butir (3)-(4) tetap berlaku. Resume point Batch 63 & 62 & di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 63]** Triase run #4: ikon AutoMirrored (9 situs), 3 peringatan kompilator, `SimpleDateFormat` ThreadLocal, filter
SpotBugs `LazyDsl.kt` -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** -> **LANGKAH BERIKUTNYA:** (1) push ZIP; run #5 diharapkan: peringatan
kompilator 13 -> 1 (sisa `setTransformationRequest` `VideoResizer.kt:508`), SpotBugs 226 -> 157 bila filter `<Source name="LazyDsl.kt"/>` bekerja
(bila tetap 225, filter tak cocok dengan SourceFile kelas sintetis -> baca SARIF, jangan perluas filter tanpa bukti). Bila build GAGAL
"Unresolved reference" pada `Icons.AutoMirrored.Filled.BrandingWatermark`: pesan deprecated di laporan terpotong ("...Filled.Brandin"),
nama diasumsikan benar -> kembalikan HANYA situs itu (import `MainActivity.kt` baris 46 + pemakaian ~1532) ke `Icons.Filled.BrandingWatermark`.
(2) Lint 0 isu run #4 konsisten dengan fix `NewApi` Batch 62 tetapi artifact hanya memuat `.txt` (XML mentah tak terlihat) — bila laporan
berikutnya memuat isu lint, triase dengan membaca kode di titiknya. (3) Sisa kandidat (belum berubah): pengecualian ditelan tanpa jejak
mentah di `PublicMovieExporter.kt`/`MainActivity.kt` (detekt `SwallowedException`; aturan ArchUnit "mandiri" melarang `PublicMovieExporter.kt`
memanggil `CrashLogger.kt` -> perlu keputusan arsitektur); `AppUpdater.download()` belum OkHttp+Okio (sudah streaming 8 KB; butuh dependency
baru, tak bisa diverifikasi tanpa CI); `setTransformationRequest` deprecated (Media3 1.4.1 — cek API pengganti versi terpasang).
(4) SpotBugs yang DIBACA = false positive codegen, jangan diubah: `NP_NULL_ON_SOME_PATH` `VideoResizer.kt`, `SA_LOCAL_SELF_ASSIGNMENT`
`MainActivity.kt:4947`, `OBL_UNSATISFIED_OBLIGATION` `AppUpdater.kt:158` (bukti di CHANGELOG Batch 63). Belum dibaca: `RV_RETURN_VALUE_IGNORED`,
`REC_CATCH_EXCEPTION`, `BC_BAD_CAST_TO_ABSTRACT_COLLECTION`, `MS_EXPOSE_REP`. Tidak diverifikasi di sandbox: kompilasi Kotlin (tak ada
kotlinc/Gradle/SDK), build/tes/lint/SpotBugs/detekt sungguhan. Resume point Batch 62 & 61 & 60 & 59 & 58 di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 62]** Penghitung lint laporan CI + guard API `CrashLogger` + log `Haptics` -> **SELESAI di ZIP, NOT VERIFIED
di CI/HP** -> **LANGKAH BERIKUTNYA:** (1) push ZIP; run #4 menampilkan temuan lint SUNGGUHAN untuk pertama kalinya — triase tiap
temuan dengan membaca kode di titiknya; status lint run #2/#3 ("LULUS") JANGAN dipercaya. Bila lint masih "LULUS (0 isu)"
padahal Gradle menandai isu, curigai penghitung lagi (format XML: `<issue` + baris baru). (2) Run #3 (Batch 61) terverifikasi
di CI: build + tes hijau, ArchUnit 12 aturan 0 pelanggaran, detekt 38 (+1 `Haptics.kt` kini ditutup). (3) Rasa getaran di HP
belum diverifikasi — tunggu laporan user; bila tetap lemah, minta setelan getaran/haptik HP, jangan ganti pendekatan tanpa bukti.
(4) Sisa kandidat (alasan di CHANGELOG Batch 61): pengecualian ditelan tanpa jejak mentah di `PublicMovieExporter.kt`/`MainActivity.kt`
(aturan ArchUnit "mandiri" melarang `PublicMovieExporter.kt` memanggil `CrashLogger.kt` -> perlu keputusan arsitektur);
`AppUpdater.download()` belum OkHttp+Okio (sudah streaming 8 KB; butuh dependency baru, verifikasi di CI); 9 peringatan deprecated
`Icons.Filled.*` -> AutoMirrored. SpotBugs `NP_*`/`SA_*`/`OBL_*` yang dibaca = false positive codegen. Tidak diverifikasi di sandbox:
build/tes/lint sungguhan. Resume point Batch 61 & 60 & 59 & 58 di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 61]** Haptic kuat terpusat + `AppUpdater.REPO` -> **SELESAI di ZIP, NOT VERIFIED di CI/HP** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP; di HP rasakan ketukan biasa (klik tegas) dan aksi `LongPress` (mulai/batal/konfirmasi,
lebih berat). Bila TETAP tak terasa, cek setelan sistem getaran/haptik HP dan kirim hasilnya — jangan ganti pendekatan tanpa
bukti. (2) Batch 60 TERVERIFIKASI di CI run #2: SATU artifact, ArchUnit 12 aturan 0 pelanggaran, lint 0 isu. (3) Triase sisa
(alasan lengkap per titik di CHANGELOG Batch 61): pengecualian ditelan tanpa jejak mentah di `PublicMovieExporter.kt`/
`MainActivity.kt` -> tambah logger pengecualian tertangani (raw stacktrace ke Documents via MediaStore); lint `NewApi`
`CrashLogger.kt:84` (run #2 melaporkan 0 isu, penyebab tak terbukti) -> guard `SDK_INT >= 28` bila minSdk tetap 24; unduhan
APK `AppUpdater.download()` sudah streaming 8 KB tetapi belum OkHttp+Okio (aturan guard) -> migrasi butuh dependency baru, verifikasi
di CI; 9 peringatan deprecated `Icons.Filled.*` -> AutoMirrored. SpotBugs `NP_*`/`SA_*`/`OBL_*` yang dibaca = false positive codegen
(jangan disaring tanpa bukti baru). Tidak diverifikasi di sandbox: build/tes/lint/SpotBugs/detekt sungguhan; `Haptics.kt` hanya
dikompilasi terhadap stub API. Resume point Batch 60 & 59 & 58 di bawah tetap berlaku (item REPO Batch 60 butir 3 = selesai).

🟡 **[RESUME POINT — Batch 60]** Artifact laporan CI jadi SATU + perbaikan false positive ArchUnit/lint dari run #1 ->
**SELESAI di ZIP, NOT VERIFIED di CI** -> **LANGKAH BERIKUTNYA:** (1) push ZIP (set dulu `git remote set-url origin
https://github.com/FDzaki-dev/vidsize.git` bila `origin` belum ada), pastikan run `Android CI Artifact for Claude` hanya
menghasilkan SATU artifact `Vidsize_laporan-untuk-claude_run<N>` dan ArchUnit tak lagi melaporkan ~133 pelanggaran
false positive (`StabilityInferred`, kelas inline bersumber `LazyDsl.kt`, tipe array enum `$VALUES`); lint tak lagi
memuat "Unknown issue id". Bila ArchUnit masih FAIL, baca baris `[FAIL]` dan perbaiki aturan, bukan menyaring tanpa baca
kode. (2) Triase temuan run #1 (BELUM diubah, perlu bukti per titik): lint `NewApi` `CrashLogger.kt:84`
(`longVersionCode` = API 28, minSdk 24; `catch (e: Exception)` tidak menangkap `NoSuchMethodError`; di luar prioritas
API 31+ tetapi nyata di API 24-27), detekt 20 `TooGenericExceptionCaught` + 17 `SwallowedException`, SpotBugs 226
(112 `NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE`, 51 `EI_EXPOSE_REP2`, dst.) — filter hanya setelah kode di
titik temuan dibaca. (3) `AppUpdater.kt` masih `REPO = "Video-resizer"` padahal repo GitHub kini `vidsize`: periksa
apakah redirect GitHub cukup sebelum mengubah (di luar scope Batch 60). Tidak diverifikasi di sandbox: build/tes/
SpotBugs/detekt/lint/ArchUnit sungguhan (tak ada Gradle/Android SDK/Maven); parse Kotlin hanya menampilkan error cascade
dari tipe ArchUnit yang tak terselesaikan. Resume point Batch 59 & 58 di bawah tetap berlaku.

🟡 **[RESUME POINT — Batch 59]** Adaptasi pipeline CI SONIX -> **SELESAI di ZIP, NOT VERIFIED di CI** ->
**LANGKAH BERIKUTNYA:** (1) push ZIP, pastikan run `Build APK` hijau dan Release `v1.<run>-build<run>` terbit dengan
isi pesan commit; bila gagal kirim artifact `log_fail_*`. Bila build rusak karena plugin detekt/dependency tes:
balik = hapus baris detekt di `build.gradle.kts` + `app/build.gradle.kts` (blok detekt + pin Kotlin + dependency
tes). (2) Kirim artifact `Vidsize_laporan-untuk-claude_run<N>` run pertama
`Android CI Artifact for Claude` -> triase temuan (SwallowedException/TooGenericExceptionCaught/SpreadOperator
detekt, isi SpotBugs mentah, ID lint tak dikenal, aturan ArchUnit yang gagal) lalu saring HANYA yang terbukti noise.
Tidak diverifikasi di sandbox (tak ada Gradle/Android SDK/jaringan Maven): sintaks YAML + `bash -n` semua blok `run`,
simulasi langkah rakit laporan dgn data palsu, `xmllint` exclude.xml, parse Kotlin ArchitectureTest (hanya error
"unresolved reference" karena jar ArchUnit tak ada), keseimbangan kurung `*.kts` = LULUS; build/tes/SpotBugs/detekt/lint
sungguhan = NOT VERIFIED. Resume point Batch 58 di bawah (konfirmasi user soal Home dashboard) tetap berlaku.

🟡 **[RESUME POINT — Batch 58]** Task: "ubah total layout aplikasi jadi
super generik kayak aplikasi populer sejenisnya di Play Store". Discovery
sebelum Batch 58: app ini SEBELUMNYA tidak punya halaman Home sama sekali
— landing langsung ke ResizerScreen (editor), 4 tool lain (Compress/Batch/
GIF/Studio) disembunyikan di overflow "More" tercampur dengan theme
switcher. Itu pola yang tidak generik dibanding app video-tool populer di
Play Store (yang biasanya punya dashboard grid tool di Home).

**Sudah dikerjakan Batch 58 (1 file, `MainActivity.kt`, additive — tidak
ada screen lama yang isinya diubah):**
- `Screen.HOME` baru + `HomeScreen`/`HomeToolCard` composable baru: title
  bar polos + grid 2 kolom 5 tool card (Resize/Kompres/GIF/Batch/Riwayat),
  pola generik standar app Play Store sejenis. Sengaja theme-agnostic (tak
  pakai glass gradient) walau tema aktif = glass, biar Home terasa seperti
  dashboard biasa.
- HOME jadi state awal `screen` (ganti dari MAIN) — digambar sebagai
  overlay di depan ResizerScreen, pola persis sama seperti Batch/GIF/
  Compressor/Studio (base tetap termount, tidak kehilangan state).
  `isForeground = screen == Screen.MAIN` tidak perlu diubah — sudah otomatis
  false saat HOME.
- 4 tool card Home panggil `onOpen*` yang sudah ada persis (Compressor/GIF/
  Batch/Studio tidak disentuh sama sekali). Card "Resize/Edit Video" →
  `screen = Screen.MAIN` (buka ResizerScreen, tidak berubah).
- `onBack` di BatchScreen/GifScreen/CompressorScreen/StudioScreen diarahkan
  ulang dari `Screen.MAIN` → `Screen.HOME` (BackHandler tiap screen itu
  cuma panggil `onBack()` yang sudah diupdate ini — tidak perlu sentuh
  BackHandler-nya sendiri). `StudioScreen.onEditAgain`/`onEditFailed` TETAP
  ke `Screen.MAIN` (sengaja, itu buka editor dengan prefill, bukan "back").
- `ResizerScreen` dapat parameter baru `onOpenHome`, dipakai di
  `navigationIcon` TopAppBar-nya (ikon `ArrowBack` baru — sebelumnya tidak
  ada navigationIcon sama sekali karena dulu dia root).
- Import baru: `LazyVerticalGrid`/`GridCells` (grid Home),
  `Icons.Filled.Crop` (icon card Resize) — `material-icons-extended` sudah
  ada di `app/build.gradle.kts`, tidak perlu dependency baru.
- Verifikasi: brace/paren/bracket balance dicek (0/0/0 selisih) — TIDAK ada
  compiler di sandbox ini, sama seperti batch-batch sebelumnya; sinyal
  compile nyata pertama tetap CI run berikutnya.

**BELUM dikerjakan (di luar scope 1-batch ini, sengaja — micro-batching
rule):** kalau maksud user "generik" juga mencakup hal-hal berikut, itu
scope baru untuk batch selanjutnya, bukan bagian dari task ini:
1. **Tema default masih "Midnight Blue Glass"** (gradient kaca gelap) —
   tidak generik secara visual. Task ini soal *layout/navigasi*, jadi tema
   sengaja tidak disentuh. Kalau user juga mau warna/tema-nya diganti ke
   Material3 polos standar (bukan cuma Home yang theme-agnostic), itu
   instruksi baru eksplisit.
2. **Layout INTERNAL tiap tool** (tab setting ResizerScreen, trim editor,
   grid Studio, form BatchScreen/CompressorScreen/GifScreen) belum diubah
   sama sekali — cuma pintu masuk/navigasi top-level yang diganti jadi
   dashboard generik. Kalau user mau isi tiap screen itu juga dirombak
   biar makin mirip app populer (misal form single-column simpel, bottom
   sheet picker, dst.), itu task terpisah per screen (guard 3-file/batch).
3. User perlu build+install ZIP ini dan konfirmasi Home dashboard tampil
   benar (grid tidak terpotong, tap card membuka tool yang benar, tombol
   back dari tiap tool balik ke Home) sebelum lanjut ke item 1/2 di atas.

🟡 **[RESUME POINT — Batch 57, masih menunggu konfirmasi user, belum
terkait Batch 58]** Batch 56's "Kompres GIF" bug-chase
(disposal method/canvas persistence) sudah MOOT — seluruh fitur "Kompres
GIF" dihapus Batch 57 atas permintaan eksplisit user, jadi bug itu tidak
relevan lagi, tidak perlu dilanjutkan. 2 hal yang perlu dikonfirmasi user
sesi berikutnya:
1. Theme sekarang persisten lintas restart/rotasi (SharedPreferences) —
   tunggu konfirmasi user setelah build+install bahwa pilihan tema memang
   sudah tidak reset lagi.
2. **Scope keputusan Batch 57 yang perlu dikonfirmasi**: user minta
   "musnahkan SEMUA dependency yang berhubungan dengan Gif" — yang
   dihapus HANYA `GifCompressor.kt`/mode "Kompres GIF". `GifExporter.kt`
   (fitur "Video ke GIF") dan `Screen.COMPRESSOR`/`CompressorScreen`
   (fitur kompres VIDEO, nama mirip tapi tidak ada hubungan dengan Gif)
   SENGAJA dipertahankan karena keduanya tidak pernah dilaporkan
   bermasalah — lihat justifikasi lengkap di Batch history Batch 57. KALAU
   user sebenarnya mau "Video ke GIF" juga dihapus total (bukan cuma
   "Kompres GIF"): itu instruksi baru yang eksplisit, belum dieksekusi.

## Batch history (newest first — full detail in CHANGELOG.md)
- **Batch 75** — User: "semua yang didapati tidak memenuhi standar. maka di refactor saja!!" (upload `Vidsize_v74.zip` + laporan CI run #16). Satu-satunya temuan run #16 = detekt 20 x `TooGenericExceptionCaught`
  (ArchUnit 12/0, SpotBugs 0, lint 0, kompilator 0). Refactor: 20 blok `catch (e: Exception)` -> `CrashLogger.nonFatal` (inline, `Error` dilempar ulang, semantik identik, tipe tidak disempitkan — pelajaran Batch 63/64); 7 berkas kode (kontrak bersama baru).
  Verifikasi sandbox: detekt lokal 20 -> 0, kotlinc 1.9.24 + stub OK, 10 uji semantik JVM LULUS. Detail, jalan balik, dan butir yang sengaja TIDAK dikerjakan: CHANGELOG.md Batch 75.
- **Batch 74** — User: "audit kepatuhan UI/UX project terhadap aturan yang berlaku" (upload `Vidsize_v73.zip`), lalu "ya" atas tawaran perbaikan. Audit statis menemukan 4 FAIL (tidak edge-to-edge, 0 `rememberSaveable`/ViewModel dgn rotasi hanya
  ditopang `configChanges` sempit, I/O pemilih Batch di Main, string campur Inggris/Indonesia) + semantics parsial. Perbaikan: `MainActivity.kt`, `Theme.kt`, `VideoPickerScreen.kt`, `AndroidManifest.xml` (4 berkas kode/konfigurasi, dalam batas).
  Detail, daftar state, jalan balik, dan butir yang sengaja TIDAK dikerjakan: CHANGELOG.md Batch 74.
- **Batch 70** — User: "keputusan ditangan mu. yang penting terus berproses" (+ upload laporan CI run #11). Run #11 membenarkan semua prediksi Batch 69 (OkHttp+Okio terkompilasi; SpotBugs 7, detekt 20, kompilator 0).
  Keputusan arsitektur: log tangkapan masuk `Documents/` (`CrashLogger.logCaught`, `error_*.txt`, thread latar, retensi 50 per awalan); dipasang di `VideoResizer.kt` (4 titik), `PublicMovieExporter.kt`
  (4), `ExportForegroundService.kt` (1); `ArchitectureTest.kt`: "mandiri" boleh bergantung ke `CrashLogger.kt` saja untuk 3 berkas itu (aturan tetap 12). 5 berkas kode/tes (dalam batas).
- **Batch 69** — Tanpa teks user (satu upload: laporan CI run #10). Run #10 membenarkan semua prediksi Batch 68 (SpotBugs 31 -> 7, kompilator 1 -> 0, detekt 20, lint 0, ArchUnit 12/0).
  `AppUpdater.download()`: HttpURLConnection -> OkHttp 4.12.0 + Okio 3.6.0 (`source()` -> `sink().buffer()`, `emitCompleteSegments()` per 8 KB; pesan galat/progres/hapus-file-saat-gagal
  dipertahankan); `app/build.gradle.kts`: +2 dependency. API diverifikasi di sumber GitHub tag Okio 3.6.0 / OkHttp 4.12.0. Tidak disentuh: `check()`, RV x7, detekt TooGeneric x20. 2 berkas kode/konfigurasi.
- **Batch 68** — Tanpa teks user (dua upload: `Vidsize_v67.zip` + laporan CI run #9). Run #9 membenarkan semua prediksi Batch 67 (detekt 21 -> 20, SpotBugs 33 -> 31, kompilator 1).
  `exclude.xml`: UUF x24 semuanya field `$stable` (sintetis Compose) -> disaring per nama field. `VideoResizer.kt`: `setTransformationRequest` (deprecated) -> `Transformer.Builder.setVideoMimeType`
  (diverifikasi di sumber Media3 1.4.1). `android-artifact.yml`: blok contoh SARIF dihapus. Tidak disentuh: RV x7 (dibiarkan terlihat), detekt TooGeneric x20, `AppUpdater.download()` OkHttp.
  1 berkas kode + 2 konfigurasi/workflow (3 total).
- **Batch 67** — Tanpa teks user (dua upload: `Vidsize_v66.zip` + laporan CI run #8). Run #8 membenarkan semua prediksi Batch 66 (detekt 37 -> 21, SpotBugs 64 -> 33, kompilator 1).
  `ExportForegroundService.kt`: catch `startForeground` kini `Log.e` mentah (tag `VidsizeExportService`). `PublicMovieExporter.kt`: `openOutputStream(...)?.use{} ?: return null` meninggalkan
  baris `IS_PENDING=1` yatim (video + GIF) -> kini dihapus sebelum `return null`. `exclude.xml`: NP_PARAMETER x `AppUpdater.kt` disaring (0 parameter nullable di sumber; 2 lambda suspend =
  2 temuan; satu-satunya berkas non-Compose dgn keduanya). `android-artifact.yml`: laporan SpotBugs +`[elemen kode]` (logicalLocations) + contoh SARIF mentah per aturan (24 UUF belum bernama).
  Tidak disentuh: UUF x24 (tunggu nama), RV x7, detekt TooGeneric x20, `setTransformationRequest`, `AppUpdater.download()` OkHttp. 2 berkas kode non-docs + 2 konfigurasi/workflow (4 total).
- **Batch 62** — Tanpa teks user (upload laporan CI run #3). Ditemukan penghitung lint `grep -c '<issue '` selalu 0 karena XML
  lint multibaris -> "LULUS (0 isu)" palsu di run #2/#3; diganti pola ERE + penguraian detail dari XML (diuji pada XML contoh).
  Sekalian: `CrashLogger.kt` guard `longVersionCode` API 28 (lint `NewApi` run #1), `Haptics.kt` tidak lagi menelan
  `SecurityException` (detekt run #3). 3 berkas non-docs: `android-artifact.yml`, `CrashLogger.kt`, `Haptics.kt`.
- **Batch 61** — Tanpa teks user (dua upload: `Vidsize_v60.zip` + laporan CI run #2); pengingat user: haptic selalu lemah.
  Haptic bawaan Compose (`TextHandleMove`, 90 titik panggil) diganti SEKALI di akar lewat `VibratorHapticFeedback`
  (`Haptics.kt`); `AppUpdater.REPO` -> `vidsize` (bukti: nama lama = 301). Triase run #2: temuan yang dibaca ternyata false
  positive codegen atau laten, tidak diubah (rincian di CHANGELOG). 5 berkas non-docs: `Haptics.kt`, `MainActivity.kt`,
  `AndroidManifest.xml`, `AppUpdater.kt`, `ArchitectureTest.kt`.
- **Batch 60** — Koreksi user: "kenapa ada 2 output?? seharusnya cukup 1 aja!!" (run #1 menghasilkan
  `Vidsize_laporan-untuk-claude_run1` + `Vidsize_laporan-mentah_run1`). Artifact mentah = tambahan saya di luar SONIX ->
  DIHAPUS; kini tepat SATU artifact. Dari laporan mentah run #1 juga diperbaiki false positive ArchUnit (133 pelanggaran:
  `StabilityInferred`, kelas inline bersumber `LazyDsl.kt`, tipe array enum) dan id lint tak dikenal `NonObservableLocale`.
  `app/src/main` tak disentuh. 5 berkas non-docs: `android-artifact.yml`, `ArchitectureTest.kt`, `app/build.gradle.kts`,
  `config/detekt/detekt.yml`, `config/spotbugs/exclude.xml` (dua terakhir hanya komentar).
- **Batch 59** — Permintaan user: "adaptasi dengan maksimal workflow `SONIX` ke `vidsize`". Murni CI/konfigurasi
  analisis, TANPA perubahan source app (`app/src/main` tak disentuh). Pembenaran >5 berkas (8 berkas non-docs):
  workflow + berkas konfigurasi alat analisis + skrip Gradle + tes arsitektur adalah satu kontrak build yang tak bisa
  dipecah tanpa merusak konsistensi (workflow memanggil task Gradle yang bergantung pada berkas config). Berkas:
  `build.yml` (diedit; PROTECTED — diubah atas permintaan eksplisit adaptasi, aturan inti dipertahankan),
  `android-artifact.yml`, `config/detekt/detekt.yml`, `config/spotbugs/spotbugs.gradle.kts`,
  `config/spotbugs/exclude.xml`, `ArchitectureTest.kt` (baru), `build.gradle.kts` + `app/build.gradle.kts` (diedit).
  Sengaja TIDAK dibawa dari SONIX: skema versi commit-count, keystore dari secret base64, Gradle 8.14.3, gerbang
  `testReleaseUnitTest` di build.yml (belum ada tes perilaku), `ci-report.yml` stub, filter noise SONIX (tanpa bukti
  run Vidsize). Penyimpangan dari SONIX demi aturan pipeline: retensi laporan 7 hari (artifact hasil mentah tambahan DIHAPUS di Batch 60).
- **Batch 58** — Permintaan user: "ubah total layout aplikasi jadi super
  generik kayak aplikasi populer sejenisnya yang beredar di Play Store".
  Ditangani sebagai penambahan `HomeScreen` (dashboard grid 2 kolom, 5
  tool card) sebagai landing page baru di depan `ResizerScreen` — lihat
  detail lengkap teknis di [RESUME POINT — Batch 58] di atas. Semua screen
  lama (Resizer/Compressor/Batch/GIF/Studio) tidak diubah isinya, hanya
  jalur navigasi masuk/keluarnya. 1 file diubah (`MainActivity.kt`).
- **Batch 57** — 2 permintaan user: (1) "fix regresi persisten pada theme
  yang di select user", (2) "cabut dan musnahkan semua dependency yang
  berhubungan dengan Gif karena dinilai gagal dalam menyelesaikan masalah
  berantai nya".

  **(1) Theme persistence.** Ini BEDA KARAKTER total dari kasus GIF
  (Batch 55b/55c/56): bukan soal perilaku Android internal yang
  undocumented/tidak bisa dipastikan tanpa device — ini bug yang 100%
  bisa dipastikan benar cuma dari baca kode. `MainActivity.onCreate`:
  `var themePref by remember { mutableStateOf(ThemePreference.
  MIDNIGHT_BLUE_GLASS) }` — grep menyeluruh ke seluruh `themePref`/
  `onThemePrefChange` di file ini membuktikan TIDAK ADA satu baris pun
  yang membaca dari atau menulis ke SharedPreferences/DataStore/storage
  apa pun. `onThemePrefChange` cuma reassign state Compose in-memory.
  Karena state ini hidup langsung di `setContent` (bukan ViewModel),
  setiap `onCreate` jalan lagi (app restart, ATAU Activity recreate akibat
  rotasi layar — tidak ada `android:configChanges` untuk orientation di
  Manifest) balik ke nilai hardcoded default. Fix: baca SharedPreferences
  "video_resizer_prefs" (file yang SAMA dipakai ResizerScreen/
  StudioScreen — sengaja tidak bikin file prefs baru) key
  "theme_preference" saat inisialisasi state, tulis di setiap
  `onThemePrefChange`. Dibungkus `runCatching { ThemePreference.
  valueOf(saved) }.getOrNull()` supaya value tersimpan yang korup atau
  dari nama enum versi lama tidak crash — fallback ke default lama.

  **(2) Cabut "Kompres GIF" total.** Audit SEBELUM eksekusi (krusial —
  nama-nama mirip di codebase ini gampang salah tebak): `Screen` enum
  punya `GIF` dan `COMPRESSOR` sebagai 2 entri terpisah. Screen.COMPRESSOR/
  `CompressorScreen` DICEK LANGSUNG isinya — itu fitur KOMPRES VIDEO
  (pakai `CompressionLevel`/pipeline video biasa), TIDAK ADA hubungan
  dengan GIF sama sekali, cuma kebetulan nama mirip
  ("Compressor"/"GifCompressor"). Kalau sampai ke-hapus, itu akan
  menghancurkan fitur video yang bekerja normal dan TIDAK PERNAH
  dilaporkan bermasalah — pelanggaran STABILITY WINS yang tidak perlu.
  DIPERTAHANKAN, tidak disentuh sama sekali.
  Fitur "Kompres GIF" yang SEBENARNYA ternyata hidup sebagai mode kedua
  DI DALAM `GifScreen` (bukan Screen enum terpisah) — `GifScreenMode.
  {CONVERT, COMPRESS}` + tab pill `GifModeTabBar`, ditambahkan Batch 55.
  Yang dihapus:
  - `GifCompressor.kt` — dihapus total (file).
  - `GifEncoder.kt` — overload `encode(..., delays: List<Int>, ...)`
    dicabut (cuma dipakai GifCompressor), digabung balik ke 1 overload
    `delayCentiseconds: Int` — persis badan fungsi pre-Batch-55.
    `GifExporter.kt`'s call site (named args) tetap valid tanpa perubahan
    apa pun di file itu.
  - `MainActivity.kt`, di dalam `GifScreen`: enum `GifScreenMode`,
    composable `GifModeTabBar`/`GifFilePickerCard`/`GifCompressPanel`
    dihapus total; state block `screenMode` + 11 var `compress*` +
    `pickGifLauncher` + fungsi `startGifCompress()`/`cancelGifCompress()`
    dicabut; `BackHandler`/`AlertDialog` exit-confirm guard yang tadinya
    `isProcessing || compressIsProcessing` disusutkan balik ke
    `isProcessing` saja; TopAppBar title yang tadinya kondisional
    (CONVERT vs COMPRESS) di-hardcode balik ke "Video ke GIF"; struktur
    `if (screenMode==CONVERT) {...} else { GifCompressPanel(...) }`
    dibongkar — isi cabang CONVERT jadi konten langsung Column (tidak
    dibungkus `if` apa pun lagi), cabang `else`+`GifCompressPanel` dicabut
    total. Trace brace-depth manual (Python) dipakai untuk memastikan
    titik potong yang benar sebelum eksekusi delete (bukan tebak dari
    indentasi) — lihat proses di histori chat kalau perlu diverifikasi.
  - `GifExporter.kt` ("Video ke GIF") **TIDAK disentuh sama sekali** —
    pipeline terpisah total dari GifCompressor sejak awal (Batch 55's
    sendiri sudah menegaskan "deliberately not sharing code"), dan tidak
    pernah ada laporan bug untuk fitur ini. Menghapusnya akan jadi
    kerusakan yang tidak diminta terhadap fitur yang bekerja normal.
  - Studio history: entry lama dengan `kind="GIF_COMPRESS"` — logic
    display (icon Gif, label "GIF Terkompresi", widened kind-check di 4
    tempat) SENGAJA TIDAK disentuh. Ini cuma string-compare terhadap
    field String biasa di `VideoHistoryEntry`, TIDAK bergantung pada tipe
    apa pun dari `GifCompressor.kt` — riwayat lama user yang sudah ada di
    device tetap tampil benar walau fiturnya sendiri sudah tidak ada
    (tidak bisa membuat entry baru lagi, tapi yang lama tidak hilang/
    rusak tampilannya). Zero-regression untuk data user yang sudah ada.
  **Verified**: brace/paren balance FULL FILE `MainActivity.kt` — {} 1137/
  1137, () 2206/2206, [] 11/11, brace-depth walk akhir 0 (tak pernah
  negatif); `GifEncoder.kt` — {} 29/29, () 117/117, [] 7/7, depth 0.
  Grep project-wide akhir: 0 referensi aktif tersisa ke
  `GifCompressor`/`GifCompressionLevel`/`GifCompressResult`/
  `GifScreenMode`/`GifModeTabBar`/`GifFilePickerCard`/`GifCompressPanel`
  di luar 2 baris komentar historis (harmless). Tidak ada dependency
  Gradle terkait GIF (`app/build.gradle.kts` di-grep, nihil — encoder
  sejak awal ditulis manual justru untuk menghindari dependency pihak
  ketiga).
  File disentuh: `MainActivity.kt`, `GifEncoder.kt` (edit) +
  `GifCompressor.kt` (hapus) = 3 file kode.
- **Batch 56** — User: "perbaiki regresi terhadap mekanisme persistent
  sampai ke akarnya". Audit Batch 55c: blanket "canvas selalu persisten,
  never di-clear" itu benar untuk disposal method 0/1 GIF89a ("do not
  dispose") tapi PROVABLY SALAH untuk method 2 ("restore to background")
  dan method 3 ("restore to previous") — dua method yang TUJUANNYA justru
  membersihkan piksel antar frame. Canvas yang tidak pernah di-clear akan
  ghosting (piksel frame lama nyangkut) persis di area yang harusnya
  dibersihkan 2 method itu — gap yang tidak dihitung justifikasi "aman
  dua arah" Batch 55c (yang cuma menghitung skenario "Movie sudah
  composite penuh secara internal", bukan skenario disposal 2/3).
  Fix: `android.graphics.Movie` tidak expose disposal method lewat API
  publik apa pun (`setTime()`/`draw()` black box murni) — jadi
  ditambahkan `parseGifFrameDisposals()`, parser byte-level struktur GIF
  (Global Color Table, Graphic Control Extension, Image Descriptor) yang
  SAMA SEKALI BUKAN decoder piksel/LZW (`Movie` tetap satu-satunya yang
  mendekode piksel di file ini) — cuma jalan block-by-block baca ukuran
  tiap field lalu skip, untuk merekonstruksi disposal method + rectangle
  tiap frame + warna background asli GIF. Loop sampling: untuk tiap frame
  native yang window tampilnya sudah lewat sebelum sample berikutnya,
  terapkan disposal method-nya DULU sebelum `movie.draw()` sample baru —
  method 0/1: no-op (persis perilaku Batch 55c, tidak berubah); method
  2/3: `canvas.clipRect()` ke rectangle frame itu lalu `canvas.drawColor
  (backgroundArgb, PorterDuff.Mode.SRC)` (full overwrite opaque, bukan
  blend) memakai warna background YANG SEBENARNYA dideklarasikan GIF
  sumbernya (dibaca dari Global Color Table + background color index),
  bukan clear ke transparan — sengaja, karena `buildPaletteLocal`/
  `quantizeFrameLocal` di file ini HANYA membaca kanal RGB tiap piksel
  (`(p shr 16) and 0xFF` dst, alpha tidak pernah disentuh) dan
  `GifEncoder.encode()` tidak pernah mengaktifkan flag transparansi GIF —
  piksel yang di-clear ke alpha=0 akan diam-diam terkuantisasi jadi RGB
  (0,0,0) HITAM SOLID di file output, bukan sesuatu yang menyerupai
  "background" sama sekali, jadi "clear transparan" bukan fix yang jujur
  di pipeline ini. Method 3 disengaja diperlakukan SAMA seperti method 2
  (clear-ke-background, bukan snapshot-restore piksel presisi) — method 3
  jarang dipakai tool GIF (paling boros memori untuk di-encode), dan
  snapshot-restore presisi butuh manipulasi koordinat piksel Bitmap
  mentah (di luar Canvas, rawan mismatch skala kalau `finalScale != 1f`)
  untuk kasus yang jarang terjadi — trade-off yang tidak sepadan.
  Fail-safe: kalau `parseGifFrameDisposals()` gagal parse (byte stream
  tidak valid/tidak dikenali — ditangkap `runCatching`, return `null`),
  loop jatuh balik PERSIS ke perilaku Batch 55c (always-persist, tanpa
  perubahan) — fix ini murni aditif, tidak pernah membuat lebih buruk.
  **Kejujuran soal verifikasi (masih tidak ada compiler/emulator/device
  fisik di sisi Claude)**: parsing byte GIF89a/87a diverifikasi manual
  baris-per-baris terhadap spesifikasi resmi (ukuran field tiap jenis
  block, posisi bit disposal method di packed byte GCE, ukuran Global/
  Local Color Table dari 3 bit rendah packed byte, dst) — bukan tebakan.
  TAPI audit ulang jalur kode kali ini menemukan keraguan BARU yang belum
  ada di Batch 55b/55c: kalau teori "canvas Movie butuh persistensi
  eksternal" itu benar dan sebuah piksel benar-benar tidak pernah
  digambar sama sekali, piksel itu HARUSNYA terkuantisasi HITAM (bukan
  PUTIH) di pipeline final ini — tidak match sempurna dengan gejala
  "putih polos" yang di-screenshot user Batch 55c. Kemungkinan lain yang
  belum tertutup fix manapun: file GIF hasil corrupt secara STRUKTURAL
  (bukan soal warna piksel sama sekali) sehingga viewer/galeri foto
  menampilkan placeholder "gambar rusak" bawaannya sendiri (ikon kecil +
  latar putih — pola visual yang match persis dengan screenshot yang
  dilaporkan). Fix Batch 56 ini tetap sah & aman diterapkan terlepas dari
  itu (menutup celah ghosting disposal 2/3 yang nyata dan independen),
  TAPI kalau gejala PERSIS yang di-screenshot user MASIH terjadi setelah
  build+install kali ini: prioritas sesi berikutnya adalah minta FILE GIF
  SUMBER (bukan screenshot hasil kompresinya) — lihat [RESUME POINT] di
  Pending Queue di atas.
  Brace/paren balance setelah fix: `GifCompressor.kt` `{}` 67/67,
  `()` 274/274, `[]` 33/33; brace-depth walk akhir 0, tidak pernah
  negatif (min depth 0).
  File disentuh: `GifCompressor.kt` (1 file — `GifEncoder.kt`/
  `MainActivity.kt` tidak perlu diubah, disposal handling ini murni
  internal ke loop decode `GifCompressor.kt`).
- **Batch 55c** — User kirim BUKTI konkret (screenshot): hasil kompresi
  GIF adalah gambar yang cuma punya strip warna tipis di baris paling
  atas, sisanya putih polos total sampai bawah. Ini gejala yang jauh
  lebih spesifik daripada laporan verbal Batch 55b, dan menunjuk ke root
  cause KEDUA (independen dari fix Batch 55b, keduanya tetap valid &
  dipertahankan bersamaan): loop decode sebelumnya membuat
  `Bitmap.createBitmap()` + `Canvas` BARU KOSONG di SETIAP iterasi
  sample frame, lalu `movie.draw()` dipanggil ke canvas kosong itu tiap
  kali. Ini keliru untuk cara kerja GIF pada umumnya: permukaan gambar
  GIF player sungguhan itu PERSISTEN antar frame — sangat banyak GIF di
  dunia nyata (terutama yang sudah dioptimasi lewat tool semacam
  gifsicle, tapi juga banyak GIF biasa) di tiap timestamp cuma
  menggambar ULANG bagian yang BERUBAH saja dari frame sebelumnya,
  mengandalkan sisa canvas yang SUDAH ADA isinya dari frame-frame
  sebelumnya untuk bagian yang tidak berubah — bukan menggambar ulang
  frame penuh dari nol tiap kali. Ngasih `Movie` canvas kosong-baru di
  setiap panggilan `draw()` membuang total asumsi itu: bagian di luar
  region yang di-redraw pada timestamp tertentu tetap di kondisi awal
  bitmap (`ARGB_8888` baru = transparan penuh, RGBA 0,0,0,0), yang
  dirender PUTIH oleh hampir semua viewer/galeri foto — persis pola yang
  di-screenshot user.
  Fix: SATU `Bitmap`+`Canvas` persisten dibuat SEKALI sebelum loop, lalu
  dipakai ULANG untuk semua sample (`movie.setTime(t)` →
  `movie.draw(canvas, ...)` berurutan TANPA pernah membersihkan canvas
  di antaranya) — tiap sample yang mau disimpan diambil sebagai SALINAN
  independen (`canvasBmp.copy(Bitmap.Config.ARGB_8888, false)`), bukan
  referensi ke `canvasBmp` yang sama (karena `canvasBmp` itu terus
  digambar ulang untuk sample-sample berikutnya — kalau disimpan sebagai
  referensi, semua entry di `sampled` ujung-ujungnya nunjuk ke bitmap
  YANG SAMA dan cuma nyimpan state TERAKHIR, bukan snapshot per-waktu).
  Fix ini SENGAJA didesain aman dua arah tanpa perlu 100% yakin ini
  benar-benar akar masalahnya: KALAU ternyata `Movie` sebenarnya SUDAH
  meng-composite frame penuh secara internal di tiap panggilan
  `setTime()`/`draw()` (skenario di mana canvas-reuse ini TIDAK
  diperlukan), reuse canvas tidak mengubah hasil yang terlihat sama
  sekali — tiap `draw()` akan tetap menggambar ulang konten penuh yang
  identik seperti sebelumnya, cuma di atas canvas yang kebetulan tidak
  di-reset. Jadi perubahan ini strictly aman/tidak-merugikan di kedua
  skenario, bukan gambling di satu hipotesis saja.
  Jaring pengaman `isUniformColor()` dari Batch 55b SENGAJA TIDAK diubah
  — tetap dipertahankan sebagai lapisan terakhir kalau kedua fix di atas
  (55b + 55c) ternyata masih belum menutup 100% kasus di device
  tertentu.
  **Kejujuran soal verifikasi**: masih tidak ada compiler/emulator/
  device fisik di sisi Claude. TAPI beda dari Batch 55b (yang analisis
  murni dari baca kode + pemahaman API), fix ini didasarkan pada BUKTI
  VISUAL KONKRET dari user (pola spesifik strip-lalu-blank yang match
  persis dengan penjelasan "canvas di-reset tiap frame") — jadi
  confidence lebih tinggi dari sekadar analisis kode murni. Kalau
  ternyata MASIH terjadi setelah build+install kali ini: kirim FILE GIF
  SUMBER yang dipakai (bukan cuma screenshot hasil kompresinya) — supaya
  strukturnya (apakah pakai local color table per frame / disposal
  method tertentu / dsb) bisa diperiksa langsung, bukan cuma menebak
  dari gejala visual output-nya lagi.
  Brace/paren balance setelah fix: `GifCompressor.kt` `{}` 52/52,
  `()` 243/243, `[]` 47/47; brace-depth walk akhir 0, tidak pernah
  negatif.
  File disentuh: `GifCompressor.kt` (1 file — `GifEncoder.kt` dan
  `MainActivity.kt` tidak perlu diubah untuk fix ini, karena bug-nya
  murni di loop decode `GifCompressor.kt`, sudah dikonfirmasi encoder
  dan logic palet/quantize IDENTIK dengan `GifExporter.kt` yang sudah
  terbukti jalan — lihat perbandingan langsung di histori Batch 55b).
- **Batch 55b** — Feedback user: "output gif gak boleh corrupt/blank
  white sama sekali". Audit `GifCompressor.kt` (Batch 55) menemukan
  kandidat root-cause paling masuk akal: `Movie.decodeStream(stream)`
  dipanggil di dalam `context.contentResolver.openInputStream(uri)?.use
  { ... }`, yang menutup stream itu SEGERA setelah `decodeStream()`
  return — sementara `movie.draw()` baru dipanggil BELAKANGAN, berkali-
  kali (1x per frame di-sample) di loop setelahnya. Kalau `Movie` tidak
  100% self-contained setelah decode (ada kemungkinan ia menyimpan
  referensi balik ke stream, tergantung implementasi Skia versi
  Android), stream yang sudah ditutup itu bisa bikin SETIAP `draw()`
  berikutnya diam-diam gagal menggambar apa pun — Bitmap hasil
  `Bitmap.createBitmap()` yang tidak pernah benar-benar digambari tetap
  "valid" secara teknis (makanya tidak crash), tapi isinya kosong →
  persis gejala "blank/putih" yang dilaporkan. Fix: baca seluruh file ke
  `ByteArray` dulu (`readBytes()`, dibatasi `MAX_INPUT_BYTES` yang sudah
  ada dari Batch 55, ≤60MB — beda kasus dari larangan `readBytes()` utk
  Release Downloader yang soal ukuran download jaringan tak terbatas,
  bukan file lokal yang sudah dicek ukurannya), lalu
  `Movie.decodeByteArray(bytes, 0, bytes.size)` — Movie jadi berdiri
  sendiri penuh di memori, nol ketergantungan ke stream yang bisa
  ditutup.
  **Jaring pengaman tambahan** (independen dari root-cause di atas, biar
  tidak ada jalan lain buat GIF blank lolos jadi "sukses"): kalau SEMUA
  frame yang di-sample ternyata satu warna polos merata (`isUniform
  Color()`, sample strided sama seperti pengecekan dedup), proses
  digagalkan dengan pesan jelas ("frame yang terbaca kosong/polos, coba
  file lain") — bukan lanjut meng-encode hasil rusak sebagai "berhasil".
  Ini prinsip yang sama dgn "anti-crash": kalau tidak yakin outputnya
  benar, GAGAL DENGAN JELAS, jangan diam-diam kirim hasil buruk.
  **Kejujuran soal verifikasi**: fix ini analisis paling masuk akal dari
  siklus-hidup stream/Movie API (bukan tebakan acak — targetnya persis
  gejala yang dilaporkan: bukan crash, tapi output kosong/putih, yang
  cocok dengan pola "canvas dibuat tapi tidak pernah benar-benar
  digambari"), dan `decodeByteArray` memang secara desain API lebih
  defensif (data self-contained, no stream lifetime risk sama sekali).
  TAPI Claude tidak punya compiler/emulator/device fisik untuk
  mengonfirmasi 100% ini sudah menutup kasus SPESIFIK yang user alami —
  kalau masih terjadi setelah build+install, kirim file GIF sumber yang
  bermasalah (atau screenshot pesan error, karena sekarang seharusnya
  gagal dengan pesan jelas alih-alih diam-diam blank) supaya bisa
  dipersempit lebih jauh dari device asli, bukan tebak-tebak lagi dari
  sisi ini.
  Brace/paren balance setelah fix: `GifCompressor.kt` `{}` 52/52, `()`
  235/235, `[]` 47/47; brace-depth walk akhir 0, tidak pernah negatif.
  File disentuh: `GifCompressor.kt` (1 file — `GifEncoder.kt`/
  `MainActivity.kt` dari Batch 55 tidak perlu diubah untuk fix ini,
  karena bug-nya murni di sisi decode `GifCompressor.kt`, bukan di
  encoder/UI).
- **Batch 55** — Permintaan user: "tambahkan tab khusus kompresi GIF yang
  anti crash + kualitas output tetap HD". `GifScreen` sebelumnya cuma
  punya 1 mode ("Video ke GIF", via `GifExporter`) — sekarang punya 2,
  dipilih lewat pill tab baru (`GifScreenMode`/`GifModeTabBar`, visual
  identik `SettingsTabBar` Batch 51 tapi diketik ulang khusus, BUKAN
  men-generic-kan `SettingsTabBar` yang sudah ada — nol risiko ke tab
  ResizerScreen). Mode baru "Kompres GIF" mengompres file GIF YANG SUDAH
  ADA (dipilih via `ActivityResultContracts.GetContent("image/gif")`,
  bukan `PickVisualMedia` — perlu filter GIF spesifik yang `ImageOnly`
  tidak bisa) — pipeline baru `GifCompressor.kt`, TERPISAH total dari
  `GifExporter.kt` (yang mana **tidak disentuh sama sekali** batch ini).
  Teknik kompresi (2 level, `GifCompressionLevel.RINGAN`/`MAKSIMAL`,
  pola sama seperti `CompressionLevel` video Batch 19):
  1. **Dedup frame nyaris-identik** — decode via `android.graphics.Movie`
     (API 1, deprecated tapi belum dihapus, satu-satunya API publik yang
     jangkau minSdk 24 project ini DAN kasih akses per-frame — `Image
     Decoder` alternatifnya API 28+ only & render-driven, bukan "kasih
     bitmap frame ke-N"), sample tiap ~40ms (Movie cuma expose
     `duration()` total, bukan timestamp per-frame). Frame yang beda
     rata-rata pixelnya di bawah threshold level dari frame TERAKHIR
     YANG DISIMPAN (bukan frame sebelumnya berurutan — cegah "boiling
     frog drift") dibuang, delay-nya digabung ke frame yang disimpan
     (total durasi/kecepatan animasi GIF hasil tetap sama). Ini butuh
     `GifEncoder.encode()` nambah 1 overload delay-per-frame (`delays:
     List<Int>`) — overload lama (`delayCentiseconds: Int`, dipakai
     `GifExporter`) didelegasikan ke situ TANPA PERUBAHAN PERILAKU
     (named-arg call site GifExporter otomatis resolve ke overload lama,
     byte-for-byte sama seperti sebelum batch ini).
  2. **Cap lebar opsional** — HANYA di level Maksimal (720px). Level
     Ringan (default) TIDAK PERNAH resize — "kualitas output tetap HD"
     secara harfiah: resolusi keluar = resolusi masuk, kecuali kena
     backstop anti-crash di bawah.
  Anti-crash (semua eksplisit diminta user): (a) tolak file >60MB di
  awal sebelum decode, (b) cap 300 frame yang di-sample/disimpan di
  memori sekaligus (sama peran `GifExporter.MAX_FRAMES`), (c) backstop
  lebar decode 1280px ABSOLUT independen dari level (bahkan Ringan) —
  dicapai dgn PRE-SCALE Canvas sebelum `movie.draw()`, jadi bitmap
  native full-res TIDAK PERNAH dialokasikan sama sekali untuk sumber
  yang sangat besar, (d) seluruh pipeline dibungkus try/catch
  `OutOfMemoryError` + `Exception` generik → `GifCompressResult.Failure`
  dengan pesan ramah, tidak pernah propagate jadi crash.
  Palet warna & quantize nearest-color: algoritma SAMA PERSIS dengan
  `GifExporter.buildPalette`/`quantizeFrame`, tapi SENGAJA DIDUPLIKASI
  (bukan di-share lewat widen-visibility) — supaya `GifExporter.kt` bisa
  tetap 100% tidak disentuh (nol risiko ke fitur "Video ke GIF" yang
  sudah jalan), fungsi kecil & sudah terbukti benar jadi duplikasi murah/
  aman dibanding coupling lintas-file untuk kasus ini.
  Studio integration: history entry baru `kind = "GIF_COMPRESS"`.
  `onEditAgain`'s dispatch (baris ~351) SENGAJA TIDAK ditambah cabang
  baru untuk kind ini — biarkan jatuh ke default (video) branch, yang
  mana `stillPlayable` pre-check (`MediaMetadataRetriever` gagal baca
  durasi dari file GIF) sudah otomatis gagal dengan aman → `onEditFailed`
  → pesan "tidak lagi bisa diakses", BUKAN crash. Ini persis pola yang
  sudah diterima untuk gap `kind="COMPRESS"` (Batch 19, lihat "Known
  pending items") — sengaja dibiarkan simetris, bukan lupa. 4 tempat LAIN
  di `StudioScreen` (ikon/`onShare`/`onOpenInGallery`/teks detail) DI-
  WIDEN mencakup `GIF_COMPRESS` (karena keempatnya murni "ini kan cuma
  file .gif juga" — beda dgn Edit-ulang yang butuh alur re-generate
  penuh). Guard "keluar saat proses jalan" (`showExitWhileProcessingConfirm`,
  fix Batch 43) DIPERLUAS `isProcessing || compressIsProcessing` supaya
  kompresi GIF yang sedang jalan juga terlindungi, tidak cuma tab convert.
  Micro-batch: 3 file KODE (`GifCompressor.kt` baru, `GifEncoder.kt`
  +overload, `MainActivity.kt` — tab/state/wiring). `GifExporter.kt`
  tidak disentuh sama sekali (lihat alasan duplikasi di atas). Brace/
  paren balance whole-file diverifikasi ({}=1297/1297 MainActivity.kt,
  {}=44/44 GifCompressor.kt, {}=32/32 GifEncoder.kt) + brace-DEPTH walk
  (bukan cuma total count) diverifikasi kembali ke 0 di akhir file & tiap
  fungsi lokal baru ketemu di depth 1 yang benar (pola sama seperti
  `startResize`/`handlePickedVideo` yang sudah ada) — sempat ketemu 1 bug
  brace-tertukar (kurang 1 `}` sebelum `else {` cabang Compress) lewat
  pengecekan ini sebelum di-fix, dicatat di sini sebagai bukti proses
  verifikasinya jalan, bukan sekadar declare "sudah dicek".
  CHANGELOG.md Batch 51-54 juga di-backfill batch ini (gap lama, sudah
  tercatat lengkap di PROJECT_STATE.md tapi belum masuk CHANGELOG.md —
  ditemukan saat sinkronisasi VIP docs, langsung diperbaiki sekalian
  sesuai aturan Zero Stale Info).
  File disentuh: `GifCompressor.kt` (baru), `GifEncoder.kt`,
  `MainActivity.kt` — 3 file kode. Docs VIP (`PROJECT_STATE.md`,
  `CHANGELOG.md`, `README.md`, `FILE_MANIFEST.txt`) disinkronkan
  bersamaan, di luar hitungan limit file kode.
- **Batch 54** — Dua permintaan sekaligus dari user, keduanya di
  `VideoEditorPreview` (satu shared composable, dipakai 3 screen):
  1. **Fitur baru: maximize/minimize video player.** Player yang tadinya
     selalu 220dp tetap (fix Batch 53) sekarang dibungkus
     `BoxWithConstraints` supaya tahu lebar sebenarnya, lalu tinggi
     "maximize" dihitung dari ASPECT RATIO ASLI video (`sourceWidth` /
     `sourceHeight`, sudah jadi parameter composable ini) — bukan angka
     tetap sembarangan — di-cap `520.dp` biar video sangat portrait tidak
     mendorong layout kelewat panjang. Transisi tinggi antara minimize
     (220dp) ⇄ maximize dianimasikan via `animateDpAsState` (`tween(280)`).
     Tombol toggle (ikon `Fullscreen`/`FullscreenExit`, pojok kanan-atas)
     SENGAJA berbagi `controlsVisible` yang sama persis dengan tombol
     play/pause yang sudah ada — jadi ikut fade in/out otomatis dengan
     mekanisme auto-hide yang sama (bukan bikin state fade terpisah),
     sesuai permintaan eksplisit "wajib bisa fade out juga". State
     `isMaximized` di-reset ke false tiap video baru dimuat (keyed `uri`).
  2. **Bug fix: tombol ⏯️ mereset video ke titik awal.** Root cause:
     handler play SEBELUMNYA selalu memanggil `exoPlayer.seekTo(startMs)`
     tanpa syarat setiap kali ditekan — jadi video yang di-pause di
     detik manapun, begitu ditekan play lagi, langsung lompat balik ke
     `startMs` (awal rentang trim), bukan lanjut dari posisi pause.
     Fix: seek HANYA dilakukan kalau posisi saat ini (`exoPlayer.
     currentPosition`) sudah di LUAR rentang trim aktif (`pos < startMs
     || pos >= endMs`) — mencakup 3 kasus valid untuk tetap seek (play
     pertama kali, handle trim digeser hingga posisi pause jadi di luar
     rentang baru, atau playback sudah sampai ujung trim dan user mau
     replay) — di luar itu (pause-lalu-lanjut biasa, di dalam rentang
     trim), posisi persis dipertahankan. Diperiksa: `seekTo(startMs)`
     cuma ada SATU tempat di seluruh file (`VideoEditorPreview` — sudah
     shared ke 3 screen pemanggil), jadi permintaan user "berlaku pada
     semua logika video yang serupa" otomatis terpenuhi tanpa perlu cari
     tempat lain — tidak ada logika player/ExoPlayer kedua yang terpisah.
  File disentuh: `MainActivity.kt` (1 file).
- **Batch 53** — Root cause "gap kosong besar di bawah info Potong: ..."
  (dilaporkan user 2x, gagal direproduksi dari review kode di Batch 51 &
  52) AKHIRNYA ketemu berkat screenshot ke-3 yang di-crop lebih dekat:
  kotak video preview di screenshot itu tampil BESAR & PERSEGI, persis
  mengikuti rasio asli video sumber (1300×1300) — bukan ukuran tetap
  220dp yang di-hardcode di kode (`AndroidView(...).height(220.dp)`).
  Root cause: `Box` pembungkus `AndroidView(PlayerView)` di
  `VideoEditorPreview` sebelumnya TIDAK punya ukuran sendiri (cuma
  `Modifier.clickable`, tanpa `height`) — ukurannya jadi ikut mengukur
  dari child terbesarnya (perilaku normal `Box` tanpa constraint sendiri).
  `.height(220.dp)` dipasang di `AndroidView`-nya, bukan di `Box`-nya —
  dan `PlayerView` (lewat `AspectRatioFrameLayout` internalnya) kadang
  me-request ulang ukurannya sendiri mengikuti rasio video asli via
  `requestLayout()` async setelah ukuran video dari decoder diketahui;
  constraint 220dp dari Compose tidak konsisten dipertahankan pada
  re-layout itu (kasus interop `AndroidView`+`PlayerView` yang cukup
  dikenal). Efeknya: `Box` ikut membesar mengikuti `PlayerView`, `Card`
  jadi punya reserved-space sebesar video asli (persegi besar,
  match 1300×1300) — padahal karena isi visual videonya sendiri tampak
  wajar (tidak error), efeknya secara visual malah terlihat seperti ada
  "gap kosong" di ruang SISA di bawahnya sebelum teks detail, bukan
  video-nya sendiri yang terlihat salah — makanya sebelumnya tidak
  langsung kecurigaannya ke arah ukuran video-player.
  Fix: `Box` sekarang PUNYA ukuran eksplisit sendiri
  (`fillMaxWidth().height(220.dp)`, independen dari children), dan
  `AndroidView` pakai `Modifier.matchParentSize()` (bukan set ukuran
  sendiri lagi) — `matchParentSize()` memaksa `PlayerView` pas ke bounds
  `Box` yang SUDAH FIXED duluan, jadi `PlayerView` tidak bisa lagi
  mendikte ukuran parent-nya lewat `requestLayout()` apa pun yang ia
  lakukan secara internal. Fix ini otomatis berlaku ke SEMUA pemanggil
  `VideoEditorPreview` (ResizerScreen + 2 screen lain yang reuse
  composable yang sama) karena memang satu shared bug di satu fungsi
  shared — bukan scope creep, cuma efek samping wajar dari sifat kode
  yang di-reuse. File disentuh: `MainActivity.kt` (1 file).
- **Batch 52** — Follow-up polish atas tab Batch 51, dari feedback user
  (multi-select atas screenshot real): 3 dari 4 item dikerjakan dgn
  confidence tinggi, 1 item (gap kosong) di-declare BELUM bisa dipastikan
  dari source code review — lihat catatan di bawah.
  1. **Transisi tab masih instan/kaku** → 5 blok `if (selectedSettingsTab
     == X) { ... }` sibling (dari Batch 51) direstrukturisasi jadi SATU
     `AnimatedContent(targetState = selectedSettingsTab) { tab -> when
     (tab) { ... } }` dengan crossfade (`fadeIn(tween(220))
     .togetherWith(fadeOut(tween(140)))`). Alasan pakai AnimatedContent
     (bukan AnimatedVisibility per-blok): AnimatedVisibility di banyak
     sibling sekaligus akan sempat menampilkan DUA konten bertumpuk selagi
     fade in/out barengan (karena keduanya tetap sibling Column biasa,
     bukan di-overlay 1 slot) — untuk 5 tab dengan tinggi konten yang
     jauh beda (Preset pendek vs Overlay panjang) itu bakal kelihatan
     "lompat" sesaat. AnimatedContent menjaga satu slot konten aktif,
     jadi crossfade-nya bersih tanpa reflow. Konsekuensi struktural: tiap
     tab yang isinya beberapa composable sejajar (Ukuran/Overlay/Lainnya)
     sekarang WAJIB dibungkus 1 Column sendiri (spacedBy 20dp, samain apa
     yang tadinya disediakan Column halaman terluar) karena slot konten
     AnimatedContent bukan ColumnScope — tanpa pembungkus ini beberapa
     composable sejajar bakal numpuk di titik yang sama (default Box
     stacking). Preset/Kualitas sudah dari Batch 51 seluruh isinya 1
     Column, jadi tidak perlu pembungkus tambahan.
  2. **Chip "YouTube (16:9)" kepotong di tepi layar** → root cause-nya
     memang selama ini `LazyRow` Preset media sosial tidak punya
     `contentPadding` sama sekali, jadi chip terakhir mepet pas di tepi
     layar tanpa jarak — secara visual identik dengan "terpotong" padahal
     technically itu cuma ujung area scroll. Fix: `contentPadding =
     PaddingValues(end = 20.dp)` KHUSUS di LazyRow itu saja (satu-satunya
     yang di-flag user) — LazyRow lain (Resolution/Kualitas/Watermark
     position/Caption position) SENGAJA tidak ikut disentuh, sesuai
     Zero-Unnecessary-Refactor (user tidak melaporkan itu bermasalah).
  3. **"Bikin floating / biar terasa luas"** → digabung jadi satu
     perbaikan dengan #2 di atas (LazyRow yang sama) — user sendiri
     menyebut "atau apa kek", jadi diambil interpretasi paling minim-
     risiko yang langsung menjawab keluhan konkretnya (chip kepotong),
     bukan menambah elevation/shadow baru yang tidak diminta eksplisit.
  4. **BELUM DIKERJAKAN — gap/ruang kosong besar di bawah "Potong: ...".**
     Audit ulang `VideoEditorPreview` (Card → Column padding 16dp,
     spacedBy 12dp → player Box 220dp tetap, Row label waktu, Box
     filmstrip 56dp tetap, Row detail teks) TIDAK menemukan elemen apa
     pun yang secara struktural bisa menghasilkan gap sebesar yang
     terlihat di screenshot — sudah investigasi ke-2 kalinya dengan hasil
     sama (lihat juga histori lama soal "720×720 • Potong: ..." di
     CHANGELOG, kalau masih ada). Perubahan Batch 52 di atas TIDAK
     menyentuh `VideoEditorPreview` sama sekali (di luar scope tab), jadi
     kalau gap itu masih muncul setelah update ini, itu murni bug lama
     yang independen dari kerjaan tab — next step kalau masih terlihat:
     screenshot yang di-zoom/crop KHUSUS ke area gap itu saja (bukan
     screenshot full-halaman), supaya batas pastinya (elemen apa yang ada
     TEPAT di atas & di bawah gap) bisa dipetakan ke baris kode yang
     tepat, bukan nebak dari layout keseluruhan.
  File disentuh: `MainActivity.kt` (1 file, sesuai micro-batch limit).
- **Batch 51** — Permintaan user (via screenshot ResizerScreen): "kategorikan
  jadi beberapa tab menu agar terlihat clean dan minimalis". Panel setting
  `ResizerScreen` sebelumnya satu scroll panjang berisi 8 section berurutan
  (Preset media sosial → Aspect ratio → Resolution → Mode resize → Kualitas/
  bitrate → Watermark/logo → Caption → Rotasi → Flip → Frame rate → Bisukan
  audio). Dikelompokkan jadi 5 tab via `ResizerSettingsTab` enum baru +
  `SettingsTabBar` composable baru (segmented pill control ala iOS, reuse
  token visual yang sudah ada di file — surfaceVariant utk track/unselected
  persis FilterChip, gradient primary→secondary utk pill aktif persis brush
  tombol CTA "Resize video" — sengaja tidak menambah warna/komponen baru):
  **Preset** (Preset media sosial), **Ukuran** (Aspect ratio + Resolution +
  Mode resize), **Kualitas** (Kualitas/bitrate + estimasi ukuran),
  **Overlay** (Watermark/logo + Caption), **Lainnya** (Rotasi + Flip +
  Frame rate + Bisukan audio). Progress bar/tombol "Resize video"/pesan
  hasil (before/after) SENGAJA dibiarkan di luar sistem tab — selalu
  tampil apa pun tab yang aktif, karena itu status/aksi persisten bukan
  "setting" yang perlu dikategorikan. `selectedSettingsTab` direset ke
  PRESET di 2 titik: `handlePickedVideo` (video baru dipilih manual) &
  prefill effect "Edit ulang" dari Studio — supaya tab yang tersisa dari
  sesi sebelumnya tidak nyangkut saat video baru dimuat. Scope SENGAJA
  hanya `ResizerScreen` (satu-satunya screen yang discreenshot user) —
  `BatchScreen` punya struktur setting yang mirip/duplikat (Aspect ratio/
  Mode resize/Kualitas/Rotasi/Flip/Frame rate/Watermark, sekitar baris
  2200-2500-an) tapi TIDAK ikut disentuh, sesuai Zero-Unnecessary-Refactor
  — kategorisasi tab di sana, kalau memang diinginkan, adalah task/izin
  terpisah, bukan diam-diam ikut dikerjakan sekalian. Brace/paren balance
  whole-file diverifikasi ({}=1188/1188, ()=2394/2394). File disentuh:
  `MainActivity.kt` (1 file, sesuai micro-batch limit).
- **Batch 50** — Rename nama file APK GitHub Release: `VideoResizer-v...
  -release.apk` → `Vidsize-v...-release.apk`. Izin eksplisit diberikan
  user utk edit-parsial `.github/workflows/build.yml` (protected file,
  1 baris: variabel `OUT_NAME`). Diverifikasi TIDAK menyentuh/merusak:
  `RELEASE_TAG` (variabel terpisah, format tag rilis tidak berubah),
  Stale Run Guard (bandingkan `$GITHUB_SHA` vs tip `main`, tidak terkait
  nama file sama sekali), `AppUpdater.kt` (match asset rilis lewat
  `name.endsWith(".apk")` — bukan hardcode prefix nama, jadi auto-updater
  in-app TIDAK terpengaruh sama sekali oleh rename ini). README.md ikut
  diupdate (3 titik instruksi install current/forward-facing) — entri
  histori v1.9/v1.10 yg cerita soal nama file lama SENGAJA tidak
  disentuh (catatan sejarah). File disentuh: `.github/workflows/build.yml`,
  `README.md`.
- **Batch 49** — Lanjutan rebrand: sisa judul dokumentasi. `README.md`
  (H1 title + instruksi "open **Vidsize**" — HANYA teks current/forward,
  entri histori v1.9 yg cerita soal app lain kebetulan sama-sama
  bernama "Video Resizer" SENGAJA tidak diubah, itu catatan sejarah),
  `PROJECT_STATE.md` (judul), `MICRO_POLISH_GUIDE.md` (judul). Audit
  menyeluruh titik lain yg nampak ke user: notification channel/content
  title (ExportForegroundService) sudah generik ("Proses video"/
  "Memproses video…", gak pernah pakai brand name — tidak perlu diubah),
  app icon (vector, gak ada teks ter-embed), shortcuts.xml/splash
  (gak ada). 2 titik SISA yg BUTUH keputusan/izin eksplisit user (bukan
  auto-dikerjakan): nama file APK Release (di `.github/workflows/*`,
  protected) & rename repo GitHub (di luar ZIP, aksi manual). File
  disentuh: README.md, PROJECT_STATE.md, MICRO_POLISH_GUIDE.md.
- **Batch 48** — Rebrand kosmetik "Video Resizer" → **"Vidsize"** (nama
  simpel, langsung kebaca fungsinya = video+resize), atas permintaan
  user. Scope: HANYA nama tampil (display brand text), BUKAN package
  name/applicationId (`com.example.videoresizer` tidak disentuh — itu
  perubahan struktural, di luar "kosmetik", butuh izin eksplisit
  terpisah kalau memang diinginkan). 4 file kena cap batch ini:
  `strings.xml` (`app_name`), `MainActivity.kt` (Text judul TopAppBar +
  komentar bug-fix Batch 23 yg mengutip nama lama), `CrashLogger.kt`
  (header file crash log), `VideoPickerScreen.kt` (teks rationale izin
  baca video). Sisa referensi nama lama di file dokumentasi (.md)
  di-queue ke batch berikutnya (cap 3 file/task sudah tercapai).
- **Batch 47** — MICRO_POLISH_GUIDE Prioritas 8 (Duplikasi Theme/
  System-Bar) [DONE — audit saja, tidak ada defect]. Cek `themes.xml`
  (values/ + values-night/, statis, cuma ikut system dark mode) vs
  `Theme.kt`'s `VideoResizerTheme` (Compose runtime, `SideEffect` set
  `window.statusBarColor`/`navigationBarColor` +
  `isAppearanceLightStatusBars/NavigationBars` tiap composisi, ikut
  `resolvedStyle` app — SUDAH benar memperhitungkan in-app theme
  override, bukan cuma system dark mode). Kesimpulan: Compose SideEffect
  SUDAH jadi runtime source-of-truth yang benar & menang tiap frame
  setelah cold-start pertama, termasuk saat user ganti tema in-app.
  XML cuma warna tebakan awal sebelum frame Compose pertama (pola
  standar, bukan bug). TIDAK ada defect nyata → sesuai instruksi
  panduan sendiri ("kalau tidak ada defect runtime/UI nyata, biarkan"),
  TIDAK ADA PERUBAHAN KODE batch ini. **MICRO_POLISH_GUIDE.md 8/8
  prioritas tuntas.** File disentuh: PROJECT_STATE.md, CHANGELOG.md,
  MICRO_POLISH_GUIDE.md (dokumentasi saja).
- **Batch 46** — MICRO_POLISH_GUIDE Prioritas 7 (Accessibility
  Micro-Polish) [DONE]. Bug nyata: `TrimHandle` (drag handle
  trim awal/akhir, dipakai bareng di ResizerScreen/GifScreen/
  CompressorScreen via `VideoEditorPreview` yang sama) sama sekali TIDAK
  punya semantics node — cuma `Box` polos dgn raw `pointerInput`/
  `detectDragGestures`, tidak terjangkau/tidak ada nama sama sekali buat
  TalkBack. Fix: parameter `label: String` baru + `Modifier.semantics { contentDescription = label }`
  pada outer touch-target Box, isi "Batas awal/akhir potongan, <waktu>"
  di kedua titik pemanggilan (reuse `formatSeconds` yg sudah ada). Audit
  IconButton (10x) & Icon dgn `contentDescription = null` (27x) —
  semua IconButton sudah punya deskripsi benar, semua null-description
  adalah ikon dekoratif di samping Text (praktik benar, tidak disentuh).
  File disentuh: MainActivity.kt (1 file).
- **Batch 45** — MICRO_POLISH_GUIDE Prioritas 6 (Responsive/Font-Scale
  Polish) [DONE]. Audit chip rows (27 FilterChip di 16 titik) — semua
  SUDAH pakai LazyRow (scrollable), tidak disentuh. Bug nyata ditemukan:
  Row aksi hasil "Bagikan"/"Buka di Galeri" di GifScreen & CompressorScreen
  pakai `Button`/`OutlinedButton` (lebih lebar dari `TextButton`) tanpa
  proteksi scroll/wrap — berisiko overflow di font-scale besar + layar
  sempit. ResizerScreen versi sendiri (TextButton, risiko lebih kecil)
  juga ikut dilindungi utk konsistensi. Fix: tambah
  `Modifier.horizontalScroll(rememberScrollState())` ke ketiga Row
  tsb — pola persis yg sudah dipakai di tempat lain file ini (Studio
  action row Batch 41), tidak ada perubahan gaya tombol/visual identity.
  File disentuh: MainActivity.kt (1 file).
- **Batch 44** — MICRO_POLISH_GUIDE Prioritas 5 (Lifecycle Filmstrip/Frame
  Extraction) [DONE]. Audit: LaunchedEffect(selectedUri, durationMs) di
  ResizerScreen/GifScreen/CompressorScreen sudah otomatis benar utk
  cancel/no-stale-overwrite (structured concurrency Compose — key baru
  cancel coroutine lama, resume yang sudah dibatalkan gak sempat assign
  state). Bug nyata yang ditemukan: `StudioEntryCard` (list history) &
  `resultThumbnailBitmap` (before/after preview ResizerScreen) sama-sama
  `BitmapFactory.decodeFile()` thumbnail JPEG di RESOLUSI PENUH (bisa
  ~8MB per Bitmap ARGB_8888 utk export 1080p) padahal cuma ditampilkan
  72dp / setengah-lebar-layar. Fix: helper baru `decodeSampledBitmapFromFile()`
  (inJustDecodeBounds → hitung inSampleSize → decode downsampled),
  dipakai di kedua titik. Sengaja TIDAK menambah `.recycle()` manual di
  kedua titik (dipertimbangkan lalu di-drop) — downsampling sendiri sudah
  memangkas ukuran alokasi ~10x lebih kecil, sementara recycle() manual
  di tengah kemungkinan Compose masih memegang referensi lama saat draw
  berisiko crash tanpa bisa diverifikasi runtime di sesi ini. File
  disentuh: MainActivity.kt (1 file).
- **Batch 43** — Audit + fix back/cancel konsistensi [DONE]. User:
  "audit button back/cancelation yang belum merata di seluruh sektor".
  Audit semua screen + 13 AlertDialog di file. Temuan: ResizerScreen
  (Batch-lama) & CompressorScreen (Batch 34) sudah punya pola
  "Batalkan proses?" confirm sebelum keluar saat masih processing.
  BatchScreen & GifScreen TIDAK — back (baik system gesture maupun
  toolbar arrow) langsung silent-cancel + keluar, kehilangan progres
  tanpa peringatan. Fix: samakan pola `showExitWhileProcessingConfirm`
  ke 2 screen ini (dialog + BackHandler + toolbar arrow, 3 titik per
  screen). Juga diaudit: 4 dialog input custom (Resolution/Bitrate/
  TargetSize×2) sengaja `dismissOnBackPress = false` — direview dan
  disimpulkan itu proteksi anti-accidental-dismiss yang disengaja
  (form punya Save/Cancel eksplisit), BUKAN bug, tidak disentuh. File
  disentuh: `MainActivity.kt` (1 file, sesuai micro-batch limit).
- **Batch 42** — Transisi navigasi ala iOS [DONE]. User: "perbaiki semua
  transisi yang terlalu over fastest, wajib profesional seperti iOS".
  Audit animasi di seluruh file: cuma 1 `AnimatedVisibility` yang sudah
  ada (fade play/pause overlay di preview video, durasi default 300ms —
  wajar, tidak disentuh). Root cause sebenarnya: navigasi antar-screen
  (`VideoResizerApp` — Batch/GIF/Compressor/Studio) sama sekali TIDAK
  pakai animasi, cuma `if (screen == Screen.X)` biasa → screen baru
  muncul/hilang instan sebijinya, itulah yang kerasa "over fastest".
  Fix: 4 blok diganti `AnimatedVisibility` dgn pola push/pop khas iOS
  (slide dari kanan + fade masuk 350ms, slide ke kanan + fade keluar
  300ms, easing cubic-bezier ease-in-out). Konsisten di 4 tempat lewat
  konstanta bersama `IosPushEasing`/`IOS_PUSH_MS`/`IOS_POP_MS`. File
  disentuh: `MainActivity.kt` (1 file, sesuai micro-batch limit).
- **Batch 41** — Studio sweep-select + fix delete visibility [DONE].
  User report (screenshot Studio list): delete button tidak terlihat
  sama sekali + tidak ada multi-select/sweep-select. Audit: delete
  `IconButton` sebenarnya SUDAH ada di kode sejak batch lama, tapi
  ditaruh di ujung `Row(horizontalScroll)` bareng 3 text button → di
  layar sempit kegeser keluar viewport, jadi user harus scroll manual
  utk nemuinnya (root cause "gak keliatan"). Fix: pindah delete
  IconButton keluar dari scroll row, selalu terlihat. Fitur baru:
  long-press card masuk selection mode, checkbox per-card, TopAppBar
  berubah ("X dipilih" + Pilih Semua + Hapus massal via icon
  DeleteSweep), bulk delete lewat confirm dialog (pola sama dgn single
  delete). Audit "pola serupa di seluruh sektor": app cuma punya 1
  LazyColumn/list screen (StudioScreen) — sudah tercover semua. File
  disentuh: `MainActivity.kt` saja (1 file, sesuai micro-batch limit).
- **Batch 40** — Crash fix [DONE]. User upload crash log:
  `SecurityException: UID ... does not have permission to
  content://media/external/video/media/...` saat tap "Buka di Galeri".
  Root cause: `openInGallery`/`openGifInGallery` menambahkan
  `FLAG_GRANT_READ_URI_PERMISSION` pada URI MediaStore yang bukan milik
  app (bukan FileProvider) → sistem menolak grant → crash. Fix: hapus
  flag tsb (tidak diperlukan utk public MediaStore URI), tambah
  `catch (SecurityException)` di 2 fungsi sbg fail-safe kedua. File
  disentuh: `MainActivity.kt` saja (1 file, sesuai micro-batch limit).
- **Batch 39** — Eksekusi MICRO_POLISH_GUIDE.md **Prioritas 4** (Kejelasan
  Estimasi Ukuran File) [DONE]. Audit 3 tempat yang menampilkan estimasi
  ukuran/bitrate: (1) ResizerScreen "Perkiraan ukuran: ~X (kasar, hasil
  asli bisa sedikit berbeda)" — SUDAH benar, tidak disentuh; (2)
  TargetSizeDialog "≈ X kbps..." — SUDAH pakai simbol ≈, tidak disentuh;
  (3) **CompressorScreen** kotak "Ukuran asli / Perkiraan hasil" — GAP
  ditemukan: angka hasil tidak ada tilde/prefix estimasi & caption di
  bawahnya tidak menyebut hasil akhir bisa berbeda. Fix: angka jadi
  `"~${formatFileSize(...)}"` (konsisten pola ResizerScreen) + caption
  "Hemat sekitar X%..." ditambah kalimat "Perkiraan kasar, ukuran hasil
  akhir bisa sedikit berbeda." Logika `estimateCompressedSizeBytes` /
  `estimateOutputSizeBytes` TIDAK disentuh sama sekali — murni teks
  tampilan. File diubah: MainActivity.kt saja (1 lokasi, 2 baris teks).
  Brace/paren balance: 1096/1096, 2175/2175.
- **Batch 38** — Eksekusi MICRO_POLISH_GUIDE.md **Prioritas 3** (Action Row
  Layar Kecil) [DONE]. Studio history card action row (`Edit ulang` /
  `Galeri` opsional / `Bagikan` / icon Hapus) ada di dalam Column dengan
  lebar sisa sempit (Card 12dp padding + thumbnail 72dp + spacer 12dp
  memakan ruang sebelum row ini) — 4 item sekaligus berisiko clipping di
  layar sempit. Fix: tambah `Modifier.horizontalScroll(rememberScrollState())`
  ke Row tsb (+import `androidx.compose.foundation.horizontalScroll`,
  pola sama seperti `verticalScroll` yang sudah dipakai di dialog lain).
  Row lain yang diaudit (ResizerScreen "Buka di Galeri"+"Bagikan" 2-tombol,
  GifScreen/CompressorScreen "Bagikan"+"Buka di Galeri" 2-tombol) dibiarkan
  — full screen-width, hanya 2 item, tidak ada risiko clipping nyata,
  sesuai "if already correct, leave untouched". Tidak ada redesign Studio
  screen. File diubah: MainActivity.kt saja (2 lokasi: 1 import + 1 Row).
  Brace/paren balance whole-file diverifikasi ({}=1095/1095, ()=2175/2175).
- **Batch 37** — Eksekusi MICRO_POLISH_GUIDE.md **Prioritas 2** (Presentasi
  Error) [DONE]. 3 dari 7 Toast di `MainActivity.kt` dikonversi ke
  mekanisme inline `message` yang SUDAH ADA di GifScreen & CompressorScreen
  (bukan framework baru): GifScreen.handlePickedVideo + prefill effect (2
  lokasi), CompressorScreen.handlePickedVideo (1 lokasi) — semua kasus
  "video tidak bisa dibaca/diakses" saat memuat video, sekarang tampil
  sebagai Text inline di layar (sudah ada slot `message?.let {...}` di
  kedua screen), bukan Toast sekali-lewat. 4 Toast SISANYA (openInGallery,
  shareVideo, shareGifFile, openGifInGallery — semua util function lintas-
  layar tanpa state `message` di scope-nya, dipanggil dari Studio/Resizer/
  Gif/Compressor) SENGAJA dipertahankan sebagai Toast — genuinely
  appropriate per panduan, karena mengonversinya butuh plumbing
  state/callback baru lintas 4 pemanggil (melanggar "no new framework/
  abstraction"). Tidak ada duplikasi Toast+message pada kasus manapun.
  File diubah: MainActivity.kt saja.
- **Batch 36** — Eksekusi MICRO_POLISH_GUIDE.md **Prioritas 1** (Konsistensi
  Bahasa UI) [DONE]. 17 string Inggris di `MainActivity.kt` diganti ke
  Bahasa Indonesia (bahasa UI utama proyek, sudah dominan >95% string):
  "Share"→"Bagikan" (×4), "Batch Export"→"Ekspor Batch", "Custom
  Resolution"→"Resolusi Kustom", "Custom Bitrate"→"Bitrate Kustom",
  "Width"→"Lebar", "Height"→"Tinggi", "Save"→"Simpan" (×4), "Cancel"→
  "Batal" (×4). Hanya string literal UI yang diubah — tidak ada
  identifier/fungsi/variable yang di-rename (sesuai aturan panduan).
  Diverifikasi: grep residual string Inggris di semua Text/label/title/
  contentDescription MainActivity.kt + VideoPickerScreen.kt +
  AppUpdater.kt = nihil. Logika/behavior/navigasi tidak tersentuh — hanya
  literal string. File diubah: MainActivity.kt saja.
- **Batch 35** — Tanam `MICRO_POLISH_GUIDE.md` sebagai standing playbook
  permanen (sumber: upload user `VideoResizer_FINAL_MICRO_POLISH_
  ClaudeOnly.md`). TIDAK ada perubahan kode/logika sama sekali batch ini
  — murni dokumentasi. File diubah: PROJECT_STATE.md, CHANGELOG.md,
  FILE_MANIFEST.txt (referensi/manifest saja); file ditambah:
  MICRO_POLISH_GUIDE.md. Pending Queue diisi ulang mengarah ke eksekusi
  Prioritas 1 di batch berikutnya.
- **Batch 34** — Penutup audit kompresi video: fix item Pending Queue
  terakhir (dialog konfirmasi back-saat-proses). `CompressorScreen`
  sekarang punya `showExitWhileProcessingConfirm` sama persis pola
  ResizerScreen (baris ~648): back (toolbar arrow ATAU system gesture)
  saat `isProcessing` menampilkan `AlertDialog` dulu ("Batalkan proses?"),
  bukan langsung keluar diam-diam. Logika cancel-sungguhan (Batch 31)
  difaktor ke `cancelCompress()`, dipakai bareng oleh tombol "Batalkan"
  progress bar & tombol dialog. Back saat idle tidak berubah (langsung
  onBack(), tanpa dialog). File diubah: MainActivity.kt saja. Pending
  Queue kosong — audit sektor kompresi video (Batch 31) tuntas 100%.
- **Batch 33** — Lanjutan audit kompresi video: fix Pending Queue #1
  (estimasi audio flat 128kbps). `CompressorScreen` sekarang probe
  keberadaan & bitrate track audio asli lewat `MediaExtractor` (loop yang
  sama dgn probe fps Batch 32) — `CompressRequest.sourceHasAudio`/
  `sourceAudioBitrateBps` baru. Source tanpa audio track kini benar
  dikurangi 0 (bukan 128kbps) saat menghitung cap 85% bitrate sumber;
  source dgn audio bitrate tinggi/rendah dari 128kbps kini pakai nilai
  aslinya. `estimateSourceBitrateBps` & `estimateCompressedSizeBytes`
  (VideoResizer.kt) sama-sama diupdate, keduanya tetap fallback ke
  128kbps kalau probe gagal (perilaku lama, tidak berubah utk kasus itu).
  File diubah: MainActivity.kt, VideoResizer.kt. Pending Queue tersisa:
  dialog konfirmasi back-saat-proses (LOW, UX nice-to-have) — audit
  sektor kompresi video sekarang tuntas untuk semua temuan fungsional
  yang ditemukan Batch 31.
- **Batch 32** — Lanjutan audit kompresi video: fix Pending Queue #1
  (`ASSUMED_FPS=30` hardcoded). `CompressorScreen` sekarang probe fps asli
  source via `MediaExtractor` + `MediaFormat.KEY_FRAME_RATE` (bukan
  `MediaMetadataRetriever.CAPTURE_FRAMERATE` yang cuma keisi utk slow-mo
  capture, bukan playback fps biasa). `CompressRequest.sourceFps` &
  `estimateCompressedSizeBytes(sourceFps=...)` baru ditambahkan; keduanya
  fallback ke `CompressionLevel.ASSUMED_FPS` kalau probe gagal (0). Video
  60fps sekarang dapat bitrate yang benar-benar dihitung utk 60fps
  (kualitas sesuai janji preset), video 24fps gak lagi dapat bitrate
  berlebih. File diubah: MainActivity.kt, VideoResizer.kt.
- **Batch 31** — Audit mendalam sektor kompresi video (diminta user).
  Ditemukan & DIPERBAIKI (paling krusial): `CompressorScreen` tombol
  "Batalkan" + back-saat-proses tidak pernah benar-benar menghentikan
  `Transformer` (hanya cancel coroutine Job yang sudah selesai duluan) —
  encoder tetap jalan diam-diam di background, dan `CompressorScreen`
  sama sekali tidak pakai `ExportForegroundService` (beda dari
  ResizerScreen/BatchScreen) sehingga proses kompres video besar berisiko
  dibunuh OS saat app di-background. Fix: ganti `activeJob: Job` →
  `activeTransformer: Transformer`, cancel yang benar di kedua titik, +
  pasang `ExportForegroundService.start/updateProgress/stop` mengikuti
  pola ResizerScreen (baris ~587-640) persis. 2 temuan lain (ASSUMED_FPS
  hardcoded 30fps; estimasi audio bitrate flat 128kbps) masuk Pending
  Queue, belum dikerjakan sesuai Strict Micro-Batching Rule. File diubah:
  MainActivity.kt saja.
- **Batch 30** — Menambahkan standing rule permanen: seluruh entri
  dokumentasi baru (PROJECT_STATE.md, CHANGELOG.md, FILE_MANIFEST.txt, dsb.)
  wajib Bahasa Indonesia mulai sekarang, tanpa kecuali. Dokumentasi lama
  berbahasa Inggris tidak disentuh/diterjemahkan. Item Pending Queue lama
  soal penerjemahan penuh README/CHANGELOG di-supersede dan ditutup. No
  code changes (docs-only batch).
- **Batch 29** — User explicitly requested 100% haptic coverage ("Semua
  tombol (100%). selama gak menganggu UX!!") after being asked
  action-only vs full. Extended from 13 → 75 haptic call sites, covering
  every remaining `onClick` in `MainActivity.kt`: navigation (back
  buttons ×3, Studio icon, More-menu open/items, theme picks), all
  selection chips (resolution/quality/preset/mode/aspect-ratio via
  `OptionSection`, watermark position, caption position, fps, GIF width,
  compress level), dialog Save/Cancel (4 dialogs: CustomResolution,
  CustomBitrate, TargetSize, BatchTargetSize), dismiss buttons (Batal/
  Nanti/OK/Tetap-di-sini), share/gallery buttons (Resizer/GIF/Compressor
  results + Studio card), watermark pick/remove, play/pause toggle,
  "Ganti video", empty-state "Tap to choose a video" card, and Studio's
  edit-again/delete-icon. Used two tiers: `LongPress` (already
  established, kept for destructive/action-start — 13 sites) and new
  `TextHandleMove` (lighter, for selection/navigation/dismiss — 62 sites)
  so frequent light taps don't feel as heavy as starting an export or
  confirming a delete. Composables that didn't already have `haptic` in
  scope (`OptionSection`, `VideoEditorPreview`, `VideoPickerCard`,
  `CustomResolutionDialog`, `CustomBitrateDialog`, `TargetSizeDialog`,
  `BatchTargetSizeDialog`, `StudioEntryCard`) each got their own
  `val haptic = LocalHapticFeedback.current`. Added 2 new imports
  (`HapticFeedbackType`, `LocalHapticFeedback`). Coverage is now
  genuinely 100% of interactive taps in the file — no further pending
  item in this category. 1 file touched (`MainActivity.kt`).
- **Batch 28** — Fixed audit gap user caught ("termasuk tombol update juga
  gak??" — answer was no). Added haptic to the 2 remaining update-flow
  action buttons missed in Batch 27's classification: "Cek update"
  (`DropdownMenuItem` in More menu) and "Unduh & Pasang" (download+install
  confirm in the update-result dialog) — both are action-initiating CTAs
  same tier as Resize/Batch/GIF/Compress start, so they belong in the
  haptic set, not the "info/query, skip" bucket. Total haptic sites now
  13. Audit re-closed. 1 file touched (`MainActivity.kt`).
- **Batch 27** — Completed the feedback audit Batch 26 started (user asked
  "sudah diaudit belum" — answer was no, only 5/16 action points done).
  Full pass over all 61 `onClick` handlers in `MainActivity.kt`, classified
  into: (a) destructive/interrupt actions — now have haptic (11 sites
  total: 5 from Batch 26 + 6 new this batch — exit-while-processing
  confirm, change-video confirm, and the 4 "Batalkan"/cancel-job buttons
  across Resizer/Batch/GIF/Compressor); (b) non-destructive selection
  chips (fps/width/quality-level/watermark-position/caption-position/
  theme) and share/gallery/postpone buttons — deliberately left without
  haptic since they already have immediate visual highlight or hand off
  to a system intent, which is itself sufficient feedback; adding haptic
  there would be noise, not a gap. Toast/snackbar and loading-state
  coverage were already complete per Batch 26. Audit is now closed — no
  remaining gap in this category. 1 file touched (`MainActivity.kt`).
- **Batch 26** — Closed Pending Queue item 1 (feedback audit). Added
  `LocalHapticFeedback` (`HapticFeedbackType.LongPress`) to the 5 highest-
  value action points that had zero tactile feedback before: Resize CTA
  (`ResizerScreen`), Batch start (`BatchScreen.startBatch()`), GIF convert
  start (`GifScreen`), Compress start (`CompressorScreen`), and the
  destructive delete-confirm in `StudioScreen`'s history list. Loading-
  state coverage was already complete (all 4 processing screens already
  had `isProcessing` + progress UI pre-existing) so no changes needed
  there; snackbar/toast coverage for errors was also already present.
  1 file touched (`MainActivity.kt`), within cap.
- **Batch 25** — User request: fold every top-bar feature except Studio
  into the "More" overflow menu. `Compress`/`Batch export`/`Video ke GIF`
  IconButtons removed from `ResizerScreen`'s `TopAppBar` `actions` and
  re-added as `DropdownMenuItem`s at the top of the existing `showMoreMenu`
  `DropdownMenu` (above the "Cek update" divider, in Compress→Batch→GIF
  order), calling the same `onOpenCompressor`/`onOpenBatch`/`onOpenGif`
  lambdas. Studio (`onOpenStudio`) intentionally left as the sole
  standalone icon per explicit instruction. Top bar is now just
  `[Studio, More]` (2 icons) — title has more room than at any point since
  Batch 20. 1 file touched (`MainActivity.kt`), within cap.
- **Batch 24** — Follow-up fix on Batch 23's title-truncation fix (user:
  "distorsi sih nggak, tapi gak gini juga kalik" — no longer garbled, but
  "Vi…" was still unacceptable). Root cause was the bar carrying 6 action
  icons since Batch 21 (Update/Compress/Batch/GIF/Studio/Theme), squeezing
  the title slot too narrow even with maxLines=1+Ellipsis. Real fix:
  folded Update + Theme (both settings-like, not core editing actions)
  into one "More" (`MoreVert`) overflow `DropdownMenu` — restores the
  original 5-icon-wide bar (Compress/Batch/GIF/Studio/More) the title had
  before Batch 21, so "Video Resizer" fits without truncation on typical
  screen widths again. `showThemeMenu` state renamed `showMoreMenu` (now
  covers both). Update-check menu item shows "Mengecek update…" + disables
  itself while in flight — small incidental improvement toward Pending
  Queue item 1, not a full fix (see below). 1 file touched
  (`MainActivity.kt`).
- **Batch 23** — Fixed real UI bug reported via screenshot: `ResizerScreen`'s
  `TopAppBar` title ("Video Resizer") had no `maxLines`/`overflow`, and with
  6 action icons now crammed into the bar since Batch 21's update button,
  the shrunk title slot caused the text to wrap onto a second line and get
  clipped by the bar's fixed height — rendered as garbled fragments
  ("eo"/"Res" per the screenshot). Fixed: `Modifier.weight(1f, fill=false)`
  + `maxLines = 1` + `TextOverflow.Ellipsis` on the title `Text`, so it now
  always renders as one clean line (ellipsized on very narrow screens
  instead of wrapping-then-clipping). 1 file touched (`MainActivity.kt`) —
  the single most critical item from this batch's 3-item request; items 2
  and 3 recorded above in Pending Queue per Strict Micro-Batching Rule.
- **Batch 22** — Automated versioning (standing rule, see above): removed
  the hand-maintained `semanticVersionName` literal from
  `app/build.gradle.kts` entirely. `versionName` is now
  `"$appVersionMajor.$VERSION_CODE_OVERRIDE"`, fully derived from CI same
  as `versionCode` already was. `.github/workflows/build.yml`'s two steps
  that used to `grep` that literal out of the gradle file now compute
  `VERSION_NAME="1.${GITHUB_RUN_NUMBER}"` directly in bash instead (kept
  cross-referenced with the gradle constant via comments on both sides).
  Release tag format unchanged (`v<versionName>-build<n>`, slightly
  redundant now but kept byte-for-byte compatible with `AppUpdater.kt`'s
  `Regex("build(\\d+)")` parser from Batch 21 — so that file needed zero
  changes this batch). 2 protected files touched
  (`app/build.gradle.kts`, `.github/workflows/build.yml`) — within cap.
- **Batch 21** — New **in-app updater**: top bar gains a `SystemUpdate` icon
  button (`ResizerScreen`, next to Compress/Batch/GIF/Studio) that hits
  GitHub's Releases API (`GET /repos/FDzaki-dev/Video-resizer/releases/latest`)
  via a new `AppUpdater.kt` — no new Gradle dependency, uses
  `HttpURLConnection` + platform `org.json` instead of OkHttp/Retrofit for
  one endpoint + one download. Compares the release tag's `-build<N>`
  suffix against the installed `versionCode` (both already `1000+N` per
  `app/build.gradle.kts`/`build.yml`'s existing scheme — no separate
  "latest version" field needed). Shows an `AlertDialog` with release notes
  + "Unduh & Pasang"; download streams chunk-by-chunk straight to
  `cacheDir/updates/update.apk` (never `readBytes()` into RAM), explicit
  15s connect / 20s read timeouts, `followRedirects(true)` for the
  asset's S3/CDN 302, optional `Authorization: Bearer` header (blank/unused
  today — public repo). Install hands off to the system installer via the
  existing `FileProvider` authority (`file_paths.xml`'s `cache-path`
  already covers `cacheDir`, no change needed) with an
  `ACTION_VIEW`/`application/vnd.android.package-archive` intent; if
  `canRequestPackageInstalls()` is false, opens
  `ACTION_MANAGE_UNKNOWN_APP_SOURCES` first. New manifest permissions
  `INTERNET` + `REQUEST_INSTALL_PACKAGES` (edit-parsial on the protected
  `AndroidManifest.xml`). 3 files touched (`AppUpdater.kt` new,
  `MainActivity.kt`, `AndroidManifest.xml`) — at the Strict Micro-Batching
  cap.
- **Batch 20** — Fixed real CI compile failure from Batch 19 (run confirmed
  failed, `compileReleaseKotlin FAILED`, uploaded failure-log artifact
  analyzed directly per this project's own "crash/failure log first"
  convention): `CompressorScreen` in `MainActivity.kt` used `TopAppBar`/
  `TopAppBarDefaults.topAppBarColors` (both experimental Material3 APIs)
  without `@OptIn(ExperimentalMaterial3Api::class)` — same exact class of
  gap Batch 16 fixed for `VideoPickerScreen`, just missed on this new
  function. `GifScreen`, right above it in the same file, already carries
  this annotation; `CompressorScreen` simply didn't get it copied over.
  Fixed by adding `@OptIn(ExperimentalMaterial3Api::class)` directly above
  `private fun CompressorScreen(...)`. No other code touched — semantic
  version stays `1.19`, this is a pure compile fix.
- **Batch 19** — New **Compressor tab**: `Screen.COMPRESSOR` + a
  `CompressorScreen` composable (own top-bar icon, `Icons.Filled.Compress`,
  next to Batch/GIF/Studio), backed by a new `VideoResizer.compress()` path
  that is entirely additive — `ResizeRequest`/`resize()` untouched. Picks a
  video (in-app `VideoPickerScreen`, same as Resizer/GIF), lets the user
  trim, pick **Rekomendasi** or **Maksimal** compression, shows a live
  before/after size estimate, then re-encodes at the *same resolution* as
  **H.265/HEVC** at a much lower bitrate than the source's own codec needs
  for the same perceived quality — that's the actual mechanism behind
  "smaller file, same visual quality": HEVC efficiency, not literally
  lossless re-encoding (no re-encode of an already-lossy video can be
  truly lossless — see `CompressionLevel`'s doc comment in
  `VideoResizer.kt`). A safety cap (`estimateSourceBitrateBps`) means the
  target bitrate is never set *above* ~85% of the source's own estimated
  bitrate, so an already-efficient source is left alone instead of being
  re-encoded larger. Saves to Studio history with `kind = "COMPRESS"`
  (no `VideoHistoryEntry` schema change — reuses existing fields). Not
  done this batch, see Known pending items below: "Edit ulang" on a
  COMPRESS history entry currently reopens Resizer with default settings
  rather than reopening Compressor.
- **Batch 18** — Fixed real CI compile failure from Batch 17 (run confirmed
  failed, `compileReleaseKotlin FAILED`): `AnimatedVisibility(...)` at
  MainActivity.kt:2356 resolved to the `ColumnScope.AnimatedVisibility`
  extension (found via implicit-receiver search up through the enclosing
  Card's Column, even though the call site is inside a nested Box) instead
  of the intended top-level `androidx.compose.animation.AnimatedVisibility`
  composable — Kotlin's error: "can't be called in this context by
  implicit receiver". Fixed by fully-qualifying the call
  (`androidx.compose.animation.AnimatedVisibility(...)`) and dropping the
  now-unused bare import. Batch 17's other 2 changes (TrimHandle clamp,
  controlsVisible state/timer) were untouched — only this one call site.
- **Batch 17** — 3 UI bugs reported against `VideoEditorPreview` (screenshot,
  currently-installed APK — **note**: no APK has been released since before
  Batch 16's compile fix, so this screenshot is almost certainly an OLD
  build; re-verify all 3 once the new release installs):
  1. Trim handle "gets thinner toward the ends" — real bug, confirmed in
     current source: the visible 16dp bar was centered exactly on
     `fraction * trackWidthPx` with no clamp of its own, so near fraction
     0f/1f up to half the bar sat outside `[0, trackWidthPx]` and got
     sliced off by the shared rounded-corner `.clip()` on the parent Box.
     Fixed: added `barCorrectionPx` that clamps the bar's own left edge to
     `[0, trackWidthPx - handleWidthPx]` and nudges it inward by exactly
     that amount, so the full bar is always inside the clip — no more
     shrinking, and it now sits flush (not overhanging) at the extremes.
  2. "Big empty gaps" around the `720×720 • Potong: ...` details row —
     **could not reproduce in current source**: that Card uses
     `spacedBy(12.dp)` internally and the outer Column uses
     `spacedBy(20.dp)`, nowhere near the ~130dp/~110dp gaps described. No
     `weight()`, no reserved-height placeholder, nothing else in that
     Composable that could produce this. Left AS-IS pending a fresh
     screenshot from the Batch 16+ build — likely explained by #2's note
     above (stale APK).
  3. Play/pause button never fades (during playback or while paused) — real
     bug, confirmed: there was no auto-hide logic at all, `IconButton` was
     permanently opaque. Added `controlsVisible` state + `AnimatedVisibility`
     (fadeIn/fadeOut): auto-hides 2.5s into playback, reappears on tap
     (button or video area), stays visible whenever paused/static.
- **Batch 16** — Fixed real CI compile failure (run #46, `assembleRelease`
  exit 1): `VideoPickerScreen.kt`'s `VideoPickerScreen` composable (uses
  `TopAppBar`/`DropdownMenu`/`DropdownMenuItem`, all experimental Material3
  APIs) was missing `@OptIn(ExperimentalMaterial3Api::class)` — every other
  Composable using these APIs project-wide already carries this annotation
  (see `MainActivity.kt`), this one function was the sole gap. Added the
  annotation directly above `internal fun VideoPickerScreen(...)`. Also
  confirms the `log_fail_<version>_<run-number>` failure-artifact step
  (`Prepare failure log name` / `Upload failure logs` in `build.yml`,
  added Batch-unknown before this file's tracking) was simply absent from
  *that specific* failed run's log bundle because it predates this fix —
  `build.yml` already has both steps present and correctly gated on
  `if: failure()`; no workflow change needed, only the compile fix.
- **Batch 15** — Added Stale Run Guard to `build.yml`: new first step
  (right after checkout, before JDK setup/build/sign/release) compares
  `$GITHUB_SHA` against `origin/main`'s current tip via `git ls-remote`.
  Mismatch (e.g. a GitHub "Re-run jobs" click on an old run after main has
  since moved forward) aborts with `exit 1` before any build or publish
  step runs — prevents a stale re-run from silently publishing an
  outdated APK as a fresh GitHub Release. No app code touched.
- **Batch 14** — Added `.gitattributes` (was missing since project start
  despite being a [PROTECTED] asset category in user preferences): forces
  LF line endings on all source/text files and marks `release.keystore`/
  `*.apk`/`*.aab`/images explicitly `binary -diff -merge` so git never
  attempts text-mode EOL conversion or diff/patch on them. No app code
  touched.
- **Batch 13b** — Follow-up to Batch 13's flagged "UI asimetris" item:
  user's clarification ("semua bagian yang gak kelihatan simetris") wasn't
  specific enough to target, so rather than keep asking, shipped the one
  concrete issue found on review — `VideoEditorPreview`'s trim handles
  visually overhanging the filmstrip's rounded edges by ~8dp at the two
  trim extremes, now fixed by clipping the whole filmstrip+handles Box to
  one shared shape. Still open if this isn't what the user meant — see
  pending items.
- **Batch 13** — New `VideoPickerScreen.kt`: full-screen in-app video
  picker (MediaStore-backed, Videos/Folders tabs, list rows with
  thumbnail/name/duration/resolution/date/size, sort menu, explicit
  "Batal" cancel), replacing the OS Photo Picker for `ResizerScreen`'s and
  `GifScreen`'s single-video pick only — `BatchScreen`'s multi-pick and
  both watermark-image pickers are unchanged (see CHANGELOG's "out of
  scope" note). "UI asimetris" on the trim editor was investigated but not
  fixed — see "Known pending items" below.
- **Batch 12** — Closed both of Batch 9's explicit scope cuts: BatchScreen
  now has Flip/Frame Rate/Target-Size (MB) — the last one resolved
  per-item against each queued video's own duration, not one shared
  bitrate — and GIF exports now save to Studio history with their own
  `kind`/"Edit ulang" (`GifPrefill`)/share-mime handling.
- **Batch 11** — Debug/polish pass over Batch 9/10's GIF + target-size +
  Studio history code (no new features): GIF playback-delay drift fix,
  corrupt-frame guard, a perf fix in GIF quantization, a target-size-clamp
  warning in the dialog, flip/frame-rate now shown in Studio history
  cards, and three `!!` force-unwraps in `GifScreen` rewritten to match
  the file's existing convention.
- **Batch 10** — Fixed the real `:app:compileReleaseKotlin` failure Batch 9
  shipped (`GifExporter.kt` LongArray `+=` Int type mismatch — see
  CHANGELOG), plus a `log_fail_<version>_<run_number>` GitHub Actions
  artifact uploaded automatically whenever a build fails (captured Gradle
  output + reports), so future failures don't need a manual "download log
  archive" round-trip to diagnose.
- **Batch 9 (Atomic)** — Video ke GIF (`GifEncoder.kt`/`GifExporter.kt`,
  new files, own from-spec GIF89a/LZW encoder, no Transformer involved),
  Flip/mirror + Frame Rate control in the main Resizer screen (Media3
  bumped 1.3.1→1.4.1 for `FrameDropEffect`), and Compress-by-Target-Size
  (MB) reusing the existing CUSTOM-bitrate plumbing rather than adding a
  new pipeline field. BatchScreen intentionally not extended with the two
  new resize controls this round — see CHANGELOG's "Not done this batch".
- **Batch 8** — Caption text overlay (Resizer + Batch screens, reuses the
  watermark's overlay pipeline), per-item thumbnails in the Batch Export
  queue, caption fields added to Studio history/"Edit ulang", and
  `versionName` made dynamic (`-build<n>` suffix, same as `versionCode`).
  Closes out every previously-pending item at once.
- **Batch 7** — `versionCode` is now dynamic: `1000 + $GITHUB_RUN_NUMBER`
  (was a static `13` since before Batch 1). `versionName` stayed manual in
  this batch specifically — see Batch 8, which revisited that.
- **Batch 6** — Added this file and FILE_MANIFEST.txt (were missing since
  Batch 1 despite being required by the context hierarchy).
- **Batch 5** — Fixed GitHub Release tag/APK-name collisions: tag now
  includes `$GITHUB_RUN_NUMBER` so repeat pushes without a versionName
  bump stop overwriting/duplicating the same release.
- **Batch 4** — CI build-speed tuning (gradle.properties heap/cache flags,
  `lint.checkReleaseBuilds = false`, explicit `--parallel --build-cache`).
  Config-only, zero app-code files touched.
- **Batch 3 (Atomic)** — "Midnight Blue Glass" theme (iOS glassmorphism +
  midnight-blue gradient), now the app's default. 4 other themes still
  selectable. Touched `ui/theme/*` + UI-only parts of `MainActivity.kt`.
- **Batch 2** — Built-in crash logger (`CrashLogger.kt`,
  `VideoResizerApp.kt`, MediaStore-based, FIFO 50), GitHub Release
  publishing (was Actions-artifact-only before), cleaned all remaining
  force-unwrap (`!!`) in `MainActivity.kt`/`VideoResizer.kt`.
- **Batch 1** — (pre-dates this file; see CHANGELOG.md/README.md) core
  export-path crash hardening, `VideoHistoryStore.kt` audit, keystore
  tracking regression fix.

## Defaults a new reader should know
- **Default theme**: `MIDNIGHT_BLUE_GLASS` (Batch 3) — not `DARK`. Theme
  picker in the top bar still offers Light/Dark/Midnight Neon/Warm
  Paper/Midnight Blue Glass, all fully working.
- **`isMinifyEnabled = false`** for release builds, deliberately — R8 risk
  with Media3 Transformer's reflection use hasn't been verified with a real
  device install. Do not flip this on without a real test pass.
- **CI publishes to GitHub Releases**, not just Actions artifacts, tagged
  `v<versionName>-build<run_number>` (Batch 5). Needs repo secret
  `RELEASE_KEYSTORE_PASSWORD` to sign correctly — verify it's still set if
  a release APK won't install over a previous one.
- **Crash logs** land in `Documents/VideoResizer/logs/` on-device via
  MediaStore, FIFO-capped at 50 files — check there first before asking
  for Logcat/ADB on any crash report.
- **Media3 pinned at 1.4.1, do not bump past 1.5.x without care** (Batch
  9) — 1.6.0 flips the `OverlaySettings` anchor-point sign convention used
  by watermark/caption placement (`ScaleAndRotateTransformation`/
  `OverlayEffect` usage in `VideoResizer.kt`). Any future media3 bump needs
  that anchor math re-verified against whatever version is being moved to.
- **GIF export is a separate pipeline**, not part of `VideoResizer`/
  Transformer — `GifExporter.kt` decodes/quantizes/encodes everything
  itself. If GIF output quality ever needs to improve, the palette
  algorithm (`GifExporter.buildPalette`, a frequency-bucket approach) is
  the place to swap in something like median-cut/NeuQuant.
- **Single-video pick goes through `VideoPickerScreen.kt`, not the OS
  Photo Picker, as of Batch 13** — `ResizerScreen`/`GifScreen` only.
  `BatchScreen`'s multi-pick and the two watermark-image pickers still use
  `ActivityResultContracts.PickVisualMedia`/`PickMultipleVisualMedia`
  unchanged. If multi-select ever needs the same list-style treatment,
  that's new scope, not an extension of the existing single-pick screen.

## Defaults a new reader should know (cont'd — GifCompressor, Batch 55, REMOVED Batch 57)
- **"Kompres GIF" (GifCompressor.kt, the Movie-based existing-GIF
  compressor) no longer exists** — removed entirely in Batch 57 at the
  user's explicit request, after 3 straight batches (55b/55c/56) failed
  to fully resolve its decode-corruption reports. Full technical history
  (why it existed, every fix attempt, and the final removal) is in the
  Batch 55/55b/55c/56/57 entries below and in CHANGELOG.md — kept for the
  record, not as current-architecture guidance. `GifEncoder.kt` is back
  to its original single `encode(..., delayCentiseconds: Int, ...)`
  overload. `GifExporter.kt` ("Video ke GIF") was never part of this and
  needs no special handling — it's the one remaining GIF pipeline.

## Defaults a new reader should know (cont'd — Compressor, Batch 19)
- **Compressor is a separate pipeline call, not a mode of `resize()`** —
  `VideoResizer.compress()`/`CompressRequest`/`CompressionLevel` are all
  new, additive members; nothing about `ResizeRequest`/`resize()`/
  `QualityOption` changed.
- **Always forces H.265/HEVC** via `TransformationRequest.setVideoMimeType`
  + `DefaultEncoderFactory.setEnableFallback(true)` — falls back to
  whatever the device's encoder supports if no HEVC hardware encoder is
  present, same fallback pattern `resize()`'s bitrate path already uses.
- **No aspect/resolution/watermark/caption controls** — same resolution as
  source in, source (or trimmed clip) out, just re-encoded smaller.

## Known pending items (not yet actioned)
- 🟡 **GIF_COMPRESS "Edit ulang" not wired (Batch 55, sengaja, simetris
  dengan item COMPRESS di bawah)** — `kind = "GIF_COMPRESS"` jatuh ke
  default (non-GIF) branch di `onEditAgain`, tapi `stillPlayable`
  pre-check di `StudioScreen` (coba baca durasi via
  `MediaMetadataRetriever` dari `entry.sourceUri`, yang untuk entry ini
  adalah file GIF bukan video) sudah gagal dengan aman lebih dulu →
  `onEditFailed` → pesan jelas, BUKAN crash & BUKAN salah-buka Resizer
  dengan data video yang salah. Kalau nanti mau didukung penuh: butuh
  `GifCompressPrefill` data class (uri + level) + cabang ke-3 di
  `onEditAgain` yang buka `Screen.GIF` dengan `screenMode =
  GifScreenMode.COMPRESS` — deferred sesuai micro-batching rule (Batch
  55 sengaja fokus 1 fitur: tab kompresi itu sendiri).
- 🟡 **Compressor "Edit ulang" not wired (Batch 19)** — a `kind =
  "COMPRESS"` Studio history entry falls into `onEditAgain`'s default
  (non-GIF) branch today, which reopens **Resizer** with mostly-default
  settings rather than reopening **Compressor** with its trim/level
  restored. Needs a `CompressPrefill` data class + a third branch in
  `VideoResizerApp`'s `onEditAgain`, mirroring how `GifPrefill` already
  works — deferred to keep Batch 19 to its one task (add the tab) per the
  micro-batching rule.
- 🟡 **"UI asimetris" on the trim editor — one concrete cause fixed
  (Batch 13b), may not be the whole story.** User's clarification was
  "semua bagian yang gak kelihatan simetris" (not specific enough to
  target further), so rather than keep asking, Batch 13b shipped the one
  issue actually found on review: the trim handles overhanging the
  filmstrip's rounded edges by ~8dp at the trim extremes — now fixed via
  a shared clip on the filmstrip+handles Box. No other structural cause
  was found in `VideoEditorPreview` (player box/time-labels/details row
  all plain `spacedBy(12.dp)`, no `weight`/`fillMaxHeight`). **If this
  wasn't it: next useful input is a marked-up screenshot** (arrow/circle
  on the actual element) rather than another verbal description — visual
  layout bugs are hard to pin down blind, and two rounds of guessing
  without one risks burning batches on a no-op.
- 🟡 **Batch 13/13b changes not yet CI-verified** — same caveat as every
  batch: structural checks (brace/paren balance) + manual review only,
  push and confirm the next Actions run is green.
- 🟡 Manual-only cleanup: the pre-Batch-5 duplicated `v1.13` GitHub Release
  (old tag collision) — needs `gh release delete v1.13 -y` on the user's
  end; not something a code batch can retroactively fix.
- ⚪ Not done, flagged as risky-without-a-real-build (see CHANGELOG.md
  Batch 4 "Deliberately not done" section): AGP/Kotlin version bump,
  `org.gradle.configuration-cache=true`.

## Known constraints on this side (Claude's sandbox)
- No `gradle`/`kotlinc`/`gh` available here — every batch is verified by
  structural checks (brace/paren balance, XML/YAML parse) and careful
  manual review, not an actual compile. First real compile signal is
  always the next GitHub Actions run after push.
- Media3 API surface used in Batch 9 (`FrameDropEffect
  .createDefaultFrameDropEffect`, `ScaleAndRotateTransformation
  .Builder().setScale(x, y)`) was verified against public docs/real-world
  usage examples via web search rather than a local compile — same
  "structural checks + review, not a real compile" caveat as always
  applies; treat the next CI run as the actual first signal.

