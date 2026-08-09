package org.hdhmc.saki.build

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.Serializable
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

data class SakiVersionInfo(
    val versionName: String,
    val versionCode: Int,
) : Serializable

abstract class PrintSakiVersionTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @TaskAction
    fun printVersion() {
        println("versionName=${versionName.get()}")
        println("versionCode=${versionCode.get()}")
    }
}

internal object SakiVersionRules {
    private val baseVersionPattern = Regex("^(?:v)?(\\d+)\\.(\\d+)\\.(\\d+)$")
    private val exactVersionTagPattern = Regex("^v\\d+\\.\\d+\\.\\d+$")
    private val unsafeBranchCharacters = Regex("[^A-Za-z0-9]+")
    private val commitPattern = Regex("^[0-9a-fA-F]{7,64}$")

    fun calculate(
        baseVersion: String,
        mainlineDistance: Int,
        mainlineVersionCode: Int,
        branch: String,
        commit: String,
        isMainBranch: Boolean,
        dirty: Boolean,
    ): SakiVersionInfo {
        require(mainlineDistance >= 0) { "mainlineDistance must be non-negative" }
        require(mainlineVersionCode > 0) { "mainlineVersionCode must be positive" }

        val match = requireNotNull(baseVersionPattern.matchEntire(baseVersion)) {
            "Version baseline must use MAJOR.MINOR.PATCH, but was '$baseVersion'"
        }
        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        val basePatch = match.groupValues[3].toInt()
        val candidateIncrement = if (isMainBranch) 0 else 1
        val patch = Math.addExact(basePatch, Math.addExact(mainlineDistance, candidateIncrement))
        val versionCode = Math.addExact(mainlineVersionCode, candidateIncrement)
        require(versionCode <= 2_100_000_000) {
            "Android versionCode $versionCode exceeds the supported maximum"
        }
        require(commitPattern.matches(commit)) { "Git commit '$commit' is invalid" }

        val branchSlug = branch
            .removePrefix("refs/heads/")
            .replace(unsafeBranchCharacters, "-")
            .trim('-')
            .take(48)
            .trimEnd('-')
            .ifBlank { "detached" }
        val shortCommit = commit.lowercase().take(7)
        val dirtySuffix = if (dirty) "-dirty" else ""

        return SakiVersionInfo(
            versionName = "$major.$minor.$patch-$branchSlug-$shortCommit$dirtySuffix",
            versionCode = versionCode,
        )
    }

    fun nearestVersionTag(
        firstParentCommits: List<String>,
        tags: List<GitTagRef>,
    ): String? {
        val commitOrder = firstParentCommits
            .mapIndexed { index, commit -> commit.lowercase() to index }
            .toMap()
        return tags
            .asSequence()
            .filter { exactVersionTagPattern.matches(it.name) }
            .mapNotNull { tag ->
                val target = tag.peeledTarget.ifBlank { tag.directTarget }.lowercase()
                commitOrder[target]?.let { order -> tag.name to order }
            }
            .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
            ?.first
    }
}

internal data class GitTagRef(
    val name: String,
    val directTarget: String,
    val peeledTarget: String,
)

abstract class GitVersionValueSource :
    ValueSource<SakiVersionInfo, GitVersionValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        val repositoryDirectory: DirectoryProperty
        val fallbackBaseVersion: Property<String>
        val fallbackBaseRef: Property<String>
        val mainBranch: Property<String>
        val mainRef: Property<String>
        val branchOverride: Property<String>
        val commitOverride: Property<String>
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): SakiVersionInfo {
        val repository = parameters.repositoryDirectory.asFile.get()
        if (git(repository, "rev-parse", "--is-shallow-repository") == "true") {
            throw GradleException(
                "Saki Git versioning requires a full clone. Fetch the complete history or pass both " +
                    "-Psaki.versionName and -Psaki.versionCode."
            )
        }
        val commit = parameters.commitOverride.orNull
            ?.takeIf(String::isNotBlank)
            ?: git(repository, "rev-parse", "HEAD")
        val verifiedCommit = git(repository, "rev-parse", "--verify", "$commit^{commit}")
        val rawBranch = parameters.branchOverride.orNull
            ?.takeIf(String::isNotBlank)
            ?: gitOrNull(repository, "symbolic-ref", "--quiet", "--short", "HEAD")
            ?: "detached"
        val mainBranch = parameters.mainBranch.get()
        val normalizedBranch = rawBranch.removePrefix("refs/heads/")
        val isMainBranch = normalizedBranch == mainBranch
        val mainlinePoint = if (isMainBranch) {
            verifiedCommit
        } else {
            git(repository, "merge-base", verifiedCommit, parameters.mainRef.get())
        }

        val versionTag = findNearestVersionTag(repository, mainlinePoint)
        val baseVersion: String
        val baseRef: String
        if (versionTag != null) {
            baseVersion = versionTag
            baseRef = versionTag
        } else {
            baseVersion = parameters.fallbackBaseVersion.get()
            baseRef = parameters.fallbackBaseRef.get()
        }

        git(repository, "merge-base", "--is-ancestor", baseRef, mainlinePoint)
        val mainlineDistance = git(
            repository,
            "rev-list",
            "--count",
            "--first-parent",
            "$baseRef..$mainlinePoint",
        ).toIntOrNull() ?: throw GradleException("Git returned an invalid mainline distance")
        val versionCodePoint = if (isMainBranch) {
            mainlinePoint
        } else {
            git(repository, "rev-parse", "--verify", "${parameters.mainRef.get()}^{commit}")
        }
        val mainlineVersionCode = git(
            repository,
            "rev-list",
            "--count",
            "--first-parent",
            versionCodePoint,
        ).toIntOrNull() ?: throw GradleException("Git returned an invalid version code")
        val dirty = git(
            repository,
            "status",
            "--porcelain=v1",
            "--untracked-files=normal",
        ).isNotEmpty()

        return SakiVersionRules.calculate(
            baseVersion = baseVersion,
            mainlineDistance = mainlineDistance,
            mainlineVersionCode = mainlineVersionCode,
            branch = normalizedBranch,
            commit = verifiedCommit,
            isMainBranch = isMainBranch,
            dirty = dirty,
        )
    }

    private fun findNearestVersionTag(repository: File, mainlinePoint: String): String? {
        val firstParentCommits = git(
            repository,
            "rev-list",
            "--first-parent",
            mainlinePoint,
        ).lineSequence().filter(String::isNotBlank).toList()
        val tags = git(
            repository,
            "for-each-ref",
            "--format=%(refname:short)%09%(objectname)%09%(*objectname)",
            "refs/tags",
        ).lineSequence().filter(String::isNotBlank).mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size < 2) {
                null
            } else {
                GitTagRef(
                    name = fields[0],
                    directTarget = fields[1],
                    peeledTarget = fields.getOrElse(2) { "" },
                )
            }
        }.toList()
        return SakiVersionRules.nearestVersionTag(firstParentCommits, tags)
    }

    private fun git(repository: File, vararg arguments: String): String {
        val output = runGit(repository, arguments.toList())
        if (output.exitCode != 0) {
            val detail = output.error.ifBlank { "exit code ${output.exitCode}" }
            throw GradleException(
                "Git command failed: git ${arguments.joinToString(" ")} ($detail). " +
                    "Build from a full Git checkout or pass both " +
                    "-Psaki.versionName and -Psaki.versionCode."
            )
        }
        return output.standardOutput
    }

    private fun gitOrNull(repository: File, vararg arguments: String): String? {
        val output = runGit(repository, arguments.toList())
        return output.standardOutput.takeIf { output.exitCode == 0 && it.isNotBlank() }
    }

    private fun runGit(repository: File, arguments: List<String>): GitOutput {
        val standardOutput = ByteArrayOutputStream()
        val errorOutput = ByteArrayOutputStream()
        val result = execOperations.exec {
            workingDir(repository)
            commandLine(listOf("git") + arguments)
            isIgnoreExitValue = true
            this.standardOutput = standardOutput
            this.errorOutput = errorOutput
        }
        return GitOutput(
            exitCode = result.exitValue,
            standardOutput = standardOutput.toString(StandardCharsets.UTF_8).trim(),
            error = errorOutput.toString(StandardCharsets.UTF_8).trim(),
        )
    }

    private data class GitOutput(
        val exitCode: Int,
        val standardOutput: String,
        val error: String,
    )
}
