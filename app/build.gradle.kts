plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStoreFile = providers.gradleProperty("FOCUSTUBE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("FOCUSTUBE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("FOCUSTUBE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("FOCUSTUBE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
require(releaseSigningValues.all { it.isNullOrBlank() } || hasReleaseSigning) {
    "Set all four FOCUSTUBE signing properties in ~/.gradle/gradle.properties, or leave all unset for an unsigned local release."
}

val configuredVersionCode = providers.gradleProperty("FOCUSTUBE_VERSION_CODE").orElse("1").get().toInt()
require(configuredVersionCode > 0) { "FOCUSTUBE_VERSION_CODE must be a positive integer." }
val configuredVersionName = providers.gradleProperty("FOCUSTUBE_VERSION_NAME").orElse("1.0").get()
require(configuredVersionName.isNotBlank()) { "FOCUSTUBE_VERSION_NAME must not be blank." }

android {
    namespace = "com.focustube.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.focustube.app"
        minSdk = 26
        targetSdk = 36
        versionCode = configuredVersionCode
        versionName = configuredVersionName
    }
    buildFeatures {
        buildConfig = true
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val configuredPath = requireNotNull(releaseStoreFile)
                storeFile = file(if (configuredPath.startsWith("~/")) {
                    System.getProperty("user.home") + configuredPath.substring(1)
                } else {
                    configuredPath
                })
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
