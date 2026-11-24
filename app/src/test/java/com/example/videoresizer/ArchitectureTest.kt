package com.example.videoresizer

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Batch 59 — aturan arsitektur (ArchUnit) sebagai LAPORAN, bukan gerbang. Diadaptasi dari `ArchitectureTest` SONIX.
 *
 * - Dilewati (JUnit "skipped") kecuali env `VIDSIZE_ARCH_REPORT=1` (hanya dipasang oleh
 *   `.github/workflows/android-artifact.yml`) -> `build.yml` (`assembleRelease`) tidak menjalankannya.
 * - Saat aktif TIDAK PERNAH gagal karena pelanggaran: hasil ditulis ke `app/build/reports/archunit/archunit-report.txt`
 *   (dibaca workflow). Bila ArchUnit sendiri gagal jalan atau tak ada kelas ter-import -> `ARCHUNIT_STATUS=NOT_VERIFIED`
 *   (tool gagal != lulus).
 * - Beda dari SONIX: semua kelas proyek ada di SATU package (`com.example.videoresizer`) + `ui.theme`, jadi lapisan
 *   dibedakan per BERKAS SUMBER Kotlin (atribut `SourceFile` bytecode; mencakup kelas fasad `XxxKt`, lambda, dan
 *   `ComposableSingletons` dari berkas itu). Aturan = hanya arah dependensi yang SAAT INI sudah dipenuhi (dicek dari
 *   kode sumber tanpa komentar/string, Batch 59), jadi pelanggaran baru = regresi arsitektur.
 * - Pakai `archunit` inti (BUKAN `archunit-junit5`): tes proyek ini JUnit 4.
 */
class ArchitectureTest {

    private class RuleOutcome(val name: String, val violations: Int, val details: List<String>)

    @Test
    fun writeArchitectureReport() {
        assumeTrue(
            "Dilewati: set $ENV_FLAG=1 untuk menjalankan (dipasang android-artifact.yml).",
            System.getenv(ENV_FLAG) == "1",
        )
        val text = try {
            buildReport()
        } catch (t: Throwable) {
            "ARCHUNIT_STATUS=NOT_VERIFIED\nREASON=${t.javaClass.name}: ${t.message}\n"
        }
        val file = File(REPORT_PATH)
        file.parentFile?.mkdirs()
        file.writeText(text)
        println(text)
    }

    private fun buildReport(): String {
        val classes = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption { location -> !location.contains("UnitTest") }
            .importPackages(ROOT)
        val total = classes.count()
        if (total == 0) {
            return "ARCHUNIT_STATUS=NOT_VERIFIED\nREASON=0 kelas ter-import dari $ROOT (classpath tes tak memuat kelas app)\n"
        }
        val outcomes = rules().map { (name, rule) -> evaluate(name, rule, classes) }
        val violations = outcomes.sumOf { it.violations }
        return buildString {
            appendLine("ARCHUNIT_STATUS=OK")
            appendLine("CLASSES=$total")
            appendLine("RULES=${outcomes.size}")
            appendLine("VIOLATIONS=$violations")
            outcomes.forEach { outcome ->
                if (outcome.violations == 0) {
                    appendLine("[PASS] ${outcome.name}")
                } else {
                    appendLine("[FAIL x${outcome.violations}] ${outcome.name}")
                    outcome.details.forEach { appendLine("    $it") }
                    if (outcome.violations > outcome.details.size) {
                        appendLine("    ... (${outcome.violations - outcome.details.size} baris lain disingkat)")
                    }
                }
            }
        }
    }

    private fun evaluate(name: String, rule: ArchRule, classes: JavaClasses): RuleOutcome =
        try {
            rule.check(classes)
            RuleOutcome(name, 0, emptyList())
        } catch (e: AssertionError) {
            val message = e.message.orEmpty()
            val count = VIOLATION_COUNT.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val lines = message.lines().drop(1).map { it.trim() }.filter { it.isNotEmpty() }
            RuleOutcome(name, count, lines.take(MAX_DETAILS))
        }

    private fun predicate(description: String, check: (JavaClass) -> Boolean): DescribedPredicate<JavaClass> =
        DescribedPredicate.describe<JavaClass>(description) { c -> check(c) }

    /** Nama berkas sumber Kotlin kelas ini (atribut `SourceFile`); kosong bila tak tercatat. */
    private fun sourceFile(c: JavaClass): String = c.source.map { it.fileName.orElse("") }.orElse("")

    /** Kelas proyek = package root atau `ui.theme`, tanpa kelas generate `R` dan `BuildConfig`. */
    private fun isAppClass(c: JavaClass): Boolean =
        (c.packageName == ROOT || c.packageName.startsWith(UI_THEME_PREFIX)) &&
            c.simpleName != "R" && c.simpleName != "BuildConfig" && !c.name.contains(".R\$")

    private fun isUiClass(c: JavaClass): Boolean =
        isAppClass(c) && (sourceFile(c) in UI_FILES || c.packageName.startsWith(UI_THEME_PREFIX))

    /** Berkas [file] tidak boleh bergantung ke kelas proyek dari berkas lain (R/BuildConfig tidak dihitung). */
    private fun standalone(file: String): ArchRule =
        noClasses()
            .that(predicate("kelas dari $file") { isAppClass(it) && sourceFile(it) == file })
            .should().dependOnClassesThat(predicate("kelas proyek dari berkas lain") { isAppClass(it) && sourceFile(it) != file })

    private fun rules(): List<Pair<String, ArchRule>> {
        val engine = predicate("mesin (berkas non-UI)") { isAppClass(it) && sourceFile(it) in ENGINE_FILES }
        val result = mutableListOf<Pair<String, ArchRule>>()
        result.add(
            "mesin (non-UI) tidak bergantung ke layar UI / ui.theme" to
                noClasses().that(engine).should().dependOnClassesThat(predicate("layar UI / ui.theme") { isUiClass(it) }),
        )
        result.add(
            "mesin (non-UI) tidak bergantung ke Compose" to
                noClasses().that(engine).should().dependOnClassesThat().resideInAnyPackage("androidx.compose.."),
        )
        result.add(
            "ui.theme mandiri: tidak bergantung ke kelas proyek di package root" to
                noClasses().that().resideInAPackage("$UI_THEME_PREFIX..")
                    .should().dependOnClassesThat(predicate("kelas proyek di package root") { isAppClass(it) && it.packageName == ROOT }),
        )
        result.add(
            "MainActivity.kt = akar UI: tidak ada berkas lain yang bergantung padanya" to
                noClasses()
                    .that(predicate("kelas proyek di luar MainActivity.kt") { isAppClass(it) && sourceFile(it) != MAIN_FILE })
                    .should().dependOnClassesThat(predicate("kelas dari MainActivity.kt") { isAppClass(it) && sourceFile(it) == MAIN_FILE }),
        )
        STANDALONE_FILES.forEach { file ->
            result.add("mandiri: $file tidak bergantung ke berkas proyek lain" to standalone(file))
        }
        return result
    }

    private companion object {
        const val ENV_FLAG = "VIDSIZE_ARCH_REPORT"
        const val REPORT_PATH = "build/reports/archunit/archunit-report.txt"
        const val MAX_DETAILS = 12
        val VIOLATION_COUNT = Regex("""was violated \((\d+) times?\)""")

        const val ROOT = "com.example.videoresizer"
        const val UI_THEME_PREFIX = "$ROOT.ui.theme"
        const val MAIN_FILE = "MainActivity.kt"

        /** Berkas layar UI (Compose); `ui.theme` ditangani lewat package. */
        val UI_FILES = setOf(MAIN_FILE, "VideoPickerScreen.kt")

        /** Semua berkas non-UI: mesin ekspor, penyimpanan, updater, servis, crash logger. */
        val ENGINE_FILES = setOf(
            "AppUpdater.kt", "CrashLogger.kt", "ExportForegroundService.kt", "FilmstripExtractor.kt",
            "GifEncoder.kt", "GifExporter.kt", "PublicMovieExporter.kt", "SocialPreset.kt",
            "VideoHistoryStore.kt", "VideoResizer.kt", "VideoResizerApp.kt",
        )

        /** Berkas yang SAAT INI tidak bergantung ke berkas proyek lain mana pun (Batch 59, dari kode sumber). */
        val STANDALONE_FILES = listOf(
            "VideoResizer.kt", "VideoPickerScreen.kt", "AppUpdater.kt", "CrashLogger.kt",
            "ExportForegroundService.kt", "FilmstripExtractor.kt", "GifEncoder.kt", "PublicMovieExporter.kt",
        )
    }
}
