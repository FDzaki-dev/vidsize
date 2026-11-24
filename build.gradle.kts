plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Batch 59 — detekt (analisis statis non-blocking untuk laporan Claude; adaptasi SONIX Batch 525/531).
    // 1.23.8 = versi yang SAMA dgn CI SONIX (diterapkan di app/build.gradle.kts, `ignoreFailures = true`).
    // Balik bila merusak build rilis: hapus baris ini + `id("io.gitlab.arturbosch.detekt")` dan blok detekt
    // di app/build.gradle.kts.
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}
