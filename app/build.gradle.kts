import java.util.Properties
import org.gradle.api.tasks.compile.JavaCompile
import org.hdhmc.saki.build.GitVersionValueSource
import org.hdhmc.saki.build.PrintSakiVersionTask
import org.hdhmc.saki.build.SakiVersionInfo

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun localProperty(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

fun releaseProperty(localName: String, environmentName: String): String? =
    localProperty(localName) ?: providers.environmentVariable(environmentName).orNull
        ?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseProperty("releaseStoreFile", "SAKI_RELEASE_STORE_FILE")
val releaseStorePassword = releaseProperty("releaseStorePassword", "SAKI_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseProperty("releaseKeyAlias", "SAKI_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseProperty("releaseKeyPassword", "SAKI_RELEASE_KEY_PASSWORD")

val hasReleaseSigningProperties = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

val sakiVersion = run {
    val explicitVersionName = providers.gradleProperty("saki.versionName").orNull
    val explicitVersionCode = providers.gradleProperty("saki.versionCode").orNull
    require((explicitVersionName == null) == (explicitVersionCode == null)) {
        "Pass both -Psaki.versionName and -Psaki.versionCode when overriding Git versioning"
    }
    if (explicitVersionName != null && explicitVersionCode != null) {
        require(!explicitVersionName.startsWith('v')) {
            "saki.versionName must not include the display-only 'v' prefix"
        }
        val versionCode = explicitVersionCode.toIntOrNull()
        require(versionCode != null && versionCode in 1..2_100_000_000) {
            "saki.versionCode must be a positive Android version code"
        }
        SakiVersionInfo(explicitVersionName, versionCode)
    } else {
        providers.of(GitVersionValueSource::class) {
            parameters.repositoryDirectory.set(rootProject.layout.projectDirectory)
            parameters.fallbackBaseVersion.set(providers.gradleProperty("saki.versionBase"))
            parameters.fallbackBaseRef.set(providers.gradleProperty("saki.versionBaseRef"))
            parameters.mainBranch.set("master")
            parameters.mainRef.set("origin/master")
            parameters.branchOverride.set(providers.environmentVariable("SAKI_GIT_BRANCH"))
            parameters.commitOverride.set(providers.environmentVariable("SAKI_GIT_COMMIT"))
        }.get()
    }
}

android {
    namespace = "org.hdhmc.saki"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.hdhmc.saki"
        minSdk = 24
        targetSdk = 37
        versionCode = sakiVersion.versionCode
        versionName = sakiVersion.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningProperties) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigningProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        jniLibs {
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
            )
        }
    }
}

tasks.register<PrintSakiVersionTask>("printSakiVersion") {
    group = "help"
    description = "Prints the Git-derived Android version."
    versionName.set(sakiVersion.versionName)
    versionCode.set(sakiVersion.versionCode)
}
// Moshi codegen is configured through KSP, but its artifact also exposes a
// legacy javac processor service. Hilt's Java compile tasks need annotation
// processing, so keep javac processing enabled and remove only the Moshi
// processor jar to avoid the KAPT deprecation warning.
afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        options.annotationProcessorPath = options.annotationProcessorPath?.filter { file ->
            !file.name.startsWith("moshi-kotlin-codegen")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":decoder-alac-java"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material.kolor)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
