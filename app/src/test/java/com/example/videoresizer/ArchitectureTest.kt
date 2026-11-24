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
 *   kode sumber tanpa komentar/string, Batch 59), jadi pelanggaran baru = regresi arsitektur. Batch 60: run #1 CI (133
 *   "pelanggaran") ternyata artefak bytecode, bukan dependensi nyata — lihat [fileOf] dan aturan Compose di [rules].
 * - Pakai `archunit` inti (BUKAN `archunit-junit5`): tes proyek ini JUnit 4.
 */
class ArchitectureTest {

    private class RuleOutcome(val name: String, val violations: Int, val details: List<String>)

    /** Berkas sumber kelas TERLUAR (tanpa `$`) -> dipakai [fileOf] untuk kelas hasil inline/sintetis; diisi di [buildReport]. */
    private var outerFiles: Map<String, String> = emptyMap()

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
        val tops = HashMap<String, String>()
        for (c in classes) {
            if (!c.name.contains('$')) tops[c.name] = rawFile(c)
        }
        outerFiles = tops
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

    /** Nama berkas sumber (atribut `SourceFile`) kelas ini apa adanya; kosong bila tak tercatat. */
    private fun rawFile(c: JavaClass): String = c.source.map { it.fileName.orElse("") }.orElse("")

    /** Tipe array (mis. `$VALUES` enum) dipetakan ke tipe komponen dasarnya. */
    private fun baseOf(c: JavaClass): JavaClass = if (c.isArray) c.baseComponentType else c

    /**
     * Berkas sumber "logis" kelas proyek. Kelas hasil inline Kotlin (`...$$inlined$items$default$4`) mencatat `SourceFile`
     * fungsi yang di-inline (mis. `LazyDsl.kt`), BUKAN berkas pemiliknya (run #1: 63+ false positive) -> bila `SourceFile`
     * bukan berkas proyek yang dikenal, pakai berkas kelas terluar (nama sebelum `$` pertama).
     */
    private fun fileOf(c: JavaClass): String {
        val k = baseOf(c)
        val raw = rawFile(k)
        if (raw in KNOWN_FILES) return raw
        return outerFiles[k.name.substringBefore('$')] ?: raw
    }

    /** Kelas proyek = package root atau `ui.theme`, tanpa kelas generate `R` dan `BuildConfig`; array dihitung lewat komponennya. */
    private fun isAppClass(c: JavaClass): Boolean {
        val k = baseOf(c)
        return (k.packageName == ROOT || k.packageName.startsWith(UI_THEME_PREFIX)) &&
            k.simpleName != "R" && k.simpleName != "BuildConfig" && !k.name.contains(".R\$")
    }

    private fun isUiClass(c: JavaClass): Boolean =
        isAppClass(c) && (fileOf(c) in UI_FILES || baseOf(c).packageName.startsWith(UI_THEME_PREFIX))

    /**
     * Berkas [file] tidak boleh bergantung ke kelas proyek dari berkas lain (R/BuildConfig tidak dihitung), kecuali
     * berkas daun di [allowed] (Batch 70: `CrashLogger.kt`, yang sendiri tetap mandiri sehingga tak ada siklus).
     */
    private fun standalone(file: String, allowed: Set<String> = emptySet()): ArchRule =
        noClasses()
            .that(predicate("kelas dari $file") { isAppClass(it) && fileOf(it) == file })
            .should().dependOnClassesThat(
                predicate("kelas proyek dari berkas lain") { isAppClass(it) && fileOf(it) != file && fileOf(it) !in allowed },
            )

    private fun rules(): List<Pair<String, ArchRule>> {
        val engine = predicate("mesin (berkas non-UI)") { isAppClass(it) && fileOf(it) in ENGINE_FILES }
        val result = mutableListOf<Pair<String, ArchRule>>()
        result.add(
            "mesin (non-UI) tidak bergantung ke layar UI / ui.theme" to
                noClasses().that(engine).should().dependOnClassesThat(predicate("layar UI / ui.theme") { isUiClass(it) }),
        )
        // Compiler Compose memasang `@StabilityInferred` di SETIAP kelas modul (run #1: 24 false positive) — anotasi
        // bentukan compiler, bukan dependensi kode, jadi dikecualikan; dependensi Compose lain tetap dihitung.
        result.add(
            "mesin (non-UI) tidak bergantung ke Compose (selain @StabilityInferred bentukan compiler)" to
                noClasses().that(engine).should().dependOnClassesThat(
                    predicate("kelas Compose selain StabilityInferred") {
                        it.packageName.startsWith("androidx.compose") && it.simpleName != "StabilityInferred"
                    },
                ),
        )
        result.add(
            "ui.theme mandiri: tidak bergantung ke kelas proyek di package root" to
                noClasses().that().resideInAPackage("$UI_THEME_PREFIX..")
                    .should().dependOnClassesThat(predicate("kelas proyek di package root") { isAppClass(it) && baseOf(it).packageName == ROOT }),
        )
        result.add(
            "MainActivity.kt = akar UI: tidak ada berkas lain yang bergantung padanya" to
                noClasses()
                    .that(predicate("kelas proyek di luar MainActivity.kt") { isAppClass(it) && fileOf(it) != MAIN_FILE })
                    .should().dependOnClassesThat(predicate("kelas dari MainActivity.kt") { isAppClass(it) && fileOf(it) == MAIN_FILE }),
        )
        STANDALONE_FILES.forEach { file ->
            if (file in LOGGER_CLIENT_FILES) {
                result.add(
                    "mandiri: $file tidak bergantung ke berkas proyek lain (kecuali $LOGGER_FILE = logger daun)" to
                        standalone(file, setOf(LOGGER_FILE)),
                )
            } else {
                result.add("mandiri: $file tidak bergantung ke berkas proyek lain" to standalone(file))
            }
        }
        return result
    }

    private companion object {
        const val ENV_FLAG = "VIDSIZE_ARCH_REPORT"
        const val REPORT_PATH = "build/reports/archunit/archunit-report.txt"
        const val MAX_DETAILS = 30
        val VIOLATION_COUNT = Regex("""was violated \((\d+) times?\)""")

        const val ROOT = "com.example.videoresizer"
        const val UI_THEME_PREFIX = "$ROOT.ui.theme"
        const val MAIN_FILE = "MainActivity.kt"

        /** Berkas layar UI (Compose); `ui.theme` ditangani lewat package. */
        val UI_FILES = setOf(MAIN_FILE, "VideoPickerScreen.kt", "Haptics.kt")

        /** Semua berkas non-UI: mesin ekspor, penyimpanan, updater, servis, crash logger. */
        val ENGINE_FILES = setOf(
            "AppUpdater.kt", "CrashLogger.kt", "ExportForegroundService.kt", "FilmstripExtractor.kt",
            "GifEncoder.kt", "GifExporter.kt", "PublicMovieExporter.kt", "SocialPreset.kt",
            "VideoHistoryStore.kt", "VideoResizer.kt", "VideoResizerApp.kt",
        )

        /** Semua berkas proyek yang dikenal (UI + non-UI); `SourceFile` lain (mis. `LazyDsl.kt`) = hasil inline, lihat [fileOf]. */
        val KNOWN_FILES = UI_FILES + ENGINE_FILES

        /** Logger daun (Batch 70): satu-satunya berkas proyek yang boleh dipakai oleh [LOGGER_CLIENT_FILES]; ia sendiri tetap mandiri. */
        const val LOGGER_FILE = "CrashLogger.kt"

        /** Berkas mesin yang memanggil `CrashLogger.logCaught` (Batch 70) — dikecualikan dari "mandiri" HANYA untuk [LOGGER_FILE]. */
        val LOGGER_CLIENT_FILES = setOf("VideoResizer.kt", "PublicMovieExporter.kt", "ExportForegroundService.kt")

        /** Berkas yang SAAT INI tidak bergantung ke berkas proyek lain mana pun, kecuali logger daun (Batch 59/70, dari kode sumber). */
        val STANDALONE_FILES = listOf(
            "VideoResizer.kt", "VideoPickerScreen.kt", "AppUpdater.kt", "CrashLogger.kt",
            "ExportForegroundService.kt", "FilmstripExtractor.kt", "GifEncoder.kt", "PublicMovieExporter.kt",
        )
    }
}
