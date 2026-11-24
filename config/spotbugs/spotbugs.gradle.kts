// Batch 59 — SpotBugs (laporan SARIF) untuk workflow `.github/workflows/android-artifact.yml`.
// Diadaptasi dari config/spotbugs/spotbugs.gradle.kts SONIX (Batch 570/572).
// OPT-IN: berkas ini HANYA di-apply bila `-Pvidsize.spotbugs=true` (blok `if` di app/build.gradle.kts).
// Build normal (`build.yml`: `assembleRelease`) TIDAK memuat plugin/dependency/skrip ini sama sekali ->
// jalur rilis tidak tersentuh.
//
// Beda dari contoh `github-claude-artifact.md` (alasan, sama dgn SONIX):
//  - Plugin `com.github.spotbugs` hanya membuat task untuk proyek `java`; bagian "Apply to Android
//    project" di README resminya masih "TBU". Task `spotbugsRelease` TIDAK ADA di proyek Android, jadi
//    `tasks.named<SpotBugsTask>("spotbugsRelease")` gagal saat configuration dan merusak SELURUH build.
//    Di sini dipakai plugin dasar `com.github.spotbugs-base` + task didaftarkan manual (cara yang
//    disebut README resmi: "apply the base plugin instead").
//  - Versi plugin dipin 6.5.6 (SpotBugs 4.10.2 bawaan) = versi yang SUDAH berjalan di CI SONIX.
//  - Tanpa `auxClassPaths`: classpath compile Android (AAR) tak bisa dipakai SpotBugs apa adanya;
//    SpotBugs tetap jalan, hanya mencatat "classes needed for analysis were missing" (presisi turun).
// Jalankan lokal: gradle spotbugsRelease -Pvidsize.spotbugs=true  ->  app/build/reports/spotbugs/spotbugs.sarif
import com.github.spotbugs.snom.SpotBugsBasePlugin
import com.github.spotbugs.snom.SpotBugsTask

buildscript {
    repositories { gradlePluginPortal() }
    dependencies {
        classpath("com.github.spotbugs-base:com.github.spotbugs-base.gradle.plugin:6.5.6")
    }
}

// Batch 572 (SONIX): `apply(plugin = "com.github.spotbugs-base")` GAGAL ("Plugin with id ... not found"): id plugin
// dicari di scope proyek, bukan di classpath `buildscript` skrip terapan ini. Dipakai apply per KELAS
// (SpotBugsBasePlugin = `Plugin<Project>` pada dokumentasi resmi plugin) -> tak lewat pencarian id.
apply<SpotBugsBasePlugin>()

// Kelas hasil kompilasi varian release (Kotlin = tmp/kotlin-classes, Java = intermediates/javac).
val vidsizeKotlinClasses = layout.buildDirectory.dir("tmp/kotlin-classes/release").get().asFile
val vidsizeJavaClasses =
    layout.buildDirectory.dir("intermediates/javac/release/compileReleaseJavaWithJavac/classes").get().asFile

tasks.register<SpotBugsTask>("spotbugsRelease") {
    group = "verification"
    description = "SpotBugs (laporan SARIF) atas kelas varian release; NON-BLOCKING."
    dependsOn("compileReleaseKotlin", "compileReleaseJavaWithJavac")
    classes = fileTree(vidsizeKotlinClasses) { include("**/*.class") } +
        fileTree(vidsizeJavaClasses) { include("**/*.class") }
    sourceDirs.setFrom(file("src/main/java"))
    // Saring NOISE bytecode Kotlin/Compose di sumber — HANYA yang terbukti bukan bug (alasan: config/spotbugs/exclude.xml).
    excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    // Temuan tetap tercatat di laporan tapi task TIDAK gagal (selaras detekt: ignoreFailures = true).
    ignoreFailures = true
    reports.create("sarif") {
        required.set(true)
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/spotbugs.sarif"))
    }
}
