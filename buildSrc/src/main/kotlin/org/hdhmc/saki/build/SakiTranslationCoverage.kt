package org.hdhmc.saki.build

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/**
 * Coverage of one locale, measured in default entries the locale file also defines.
 *
 * [total] excludes default entries marked `translatable="false"`; [translated] counts only entries
 * whose key exists in the locale file. A localized value identical to the English default (for
 * example `WiFi`, `A-Z`, or a language endonym) is a deliberate translation and counts.
 */
data class SakiTranslationCoverage(
    val qualifier: String,
    val translated: Int,
    val total: Int,
) {
    val percent: Int get() = SakiTranslationCoverageRules.percent(translated, total)
}

internal object SakiTranslationCoverageRules {
    private val localeDirectoryPattern = Regex("values-([a-z]{2,3}(?:-r[A-Z]{2})?)")
    private val entryTags = setOf("string", "plurals", "string-array")

    /** Locale qualifier of a `values-*` resource directory, or null when it is not a locale. */
    fun localeQualifier(directoryName: String): String? =
        localeDirectoryPattern.matchEntire(directoryName)?.groupValues?.get(1)

    /** Name of the generated integer resource that carries this locale's coverage percentage. */
    fun resourceName(qualifier: String): String =
        "translation_coverage_" + qualifier.lowercase().replace('-', '_')

    /** Whole-percent coverage, truncating like the language chips always did. */
    fun percent(translated: Int, total: Int): Int =
        if (total <= 0) 0 else translated * 100 / total

    /** Entry names of default files that translators are expected to cover. */
    fun translatableNames(files: List<File>): Set<String> =
        files.flatMap(::entries).filterNot { it.untranslatable }.map { it.name }.toSet()

    /** Entry names a locale file defines, whatever the value. */
    fun localizedNames(files: List<File>): Set<String> =
        files.flatMap(::entries).map { it.name }.toSet()

    private data class Entry(val name: String, val untranslatable: Boolean)

    private fun entries(file: File): List<Entry> {
        val document = try {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }.newDocumentBuilder().parse(file)
        } catch (exception: Exception) {
            throw GradleException("Unable to read translation resources from ${file.path}", exception)
        }

        val resources = document.documentElement ?: return emptyList()
        val entries = mutableListOf<Entry>()
        var child = resources.firstChild
        while (child != null) {
            if (child is Element && child.tagName in entryTags) {
                val name = child.getAttribute("name")
                if (name.isNotBlank()) {
                    entries += Entry(name, child.getAttribute("translatable") == "false")
                }
            }
            child = child.nextSibling
        }
        return entries
    }
}

/**
 * Writes `values/translation_coverage.xml` with one integer per locale directory, so the Settings
 * language chips can show build-time coverage without reflecting over `R.string` at runtime.
 */
abstract class GenerateTranslationCoverageTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val resDirectory = resDirectory.get().asFile
        val defaultNames = SakiTranslationCoverageRules.translatableNames(xmlFiles(File(resDirectory, "values")))

        val coverages = resDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .mapNotNull { directory ->
                val qualifier = SakiTranslationCoverageRules.localeQualifier(directory.name)
                    ?: return@mapNotNull null
                val localizedNames = SakiTranslationCoverageRules.localizedNames(xmlFiles(directory))
                SakiTranslationCoverage(
                    qualifier = qualifier,
                    translated = defaultNames.count { it in localizedNames },
                    total = defaultNames.size,
                )
            }
            .sortedBy { it.qualifier }
            .toList()

        val collisions = coverages
            .groupingBy { SakiTranslationCoverageRules.resourceName(it.qualifier) }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (collisions.isNotEmpty()) {
            throw GradleException("Translation coverage resource names collide: ${collisions.joinToString()}")
        }

        val outputFile = File(outputDirectory.get().asFile, "values/translation_coverage.xml")
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<!-- Generated by GenerateTranslationCoverageTask. Do not edit. -->")
                appendLine("<resources>")
                coverages.forEach { coverage ->
                    appendLine(
                        "    <integer name=\"${SakiTranslationCoverageRules.resourceName(coverage.qualifier)}\">" +
                            "${coverage.percent}</integer>",
                    )
                }
                appendLine("</resources>")
                appendLine()
            },
        )

        coverages.forEach { coverage ->
            logger.lifecycle(
                "Translation coverage ${coverage.qualifier}: ${coverage.percent}% " +
                    "(${coverage.translated}/${coverage.total} entries)",
            )
        }
    }

    private fun xmlFiles(directory: File): List<File> =
        directory.listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }.sorted()
}
