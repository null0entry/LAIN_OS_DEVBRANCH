plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "dev.lain.os"
    compileSdk = 35
    ndkVersion = "29.0.13113456"
    defaultConfig {
        applicationId = "dev.lain.os"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4-interface"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-std=c++17"
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Release signing is intentionally owner-configured, never embedded.
        }
    }
    buildFeatures { viewBinding = true }
}

// Copy only the Python package into generated input; never bundle the repo,
// local credentials, tests, or session state as application assets.
val preparePython by tasks.registering(Sync::class) {
    from(rootProject.file("../lain")) { into("lain") }
    into(layout.buildDirectory.dir("generated/python"))
    exclude("**/__pycache__/**", "**/*.pyc")
}
chaquopy {
    defaultConfig {
        version = "3.11"
        pyc { src = false }
    }
    sourceSets.getByName("main") {
        srcDir(layout.buildDirectory.dir("generated/python"))
    }
}
tasks.configureEach {
    if (name == "preBuild" || name.endsWith("PythonSources") ||
        (name.startsWith("generate") && name.contains("Python"))) dependsOn(preparePython)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
