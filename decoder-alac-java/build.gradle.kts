plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.hdhmc.saki.decoder.alac"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.media3.exoplayer)
    testImplementation(libs.junit)
}
