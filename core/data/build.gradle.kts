plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.krelzox.launcher.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
