import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.motorola.deptheffect"
    compileSdk = 36

    defaultConfig {
        // Must stay com.motorola.deptheffect: Personalize looks for exactly this package.
        applicationId = "com.motorola.deptheffect"
        minSdk = 34
        targetSdk = 36
        versionCode = 23
        versionName = "2.3"
    }

    // Optional personal signing key, so new builds install over an existing copy on your phone.
    // Put storeFile/storePassword/keyAlias/keyPassword in keystore.properties (git-ignored).
    // Without it, builds use the standard Android debug key.
    val keystoreProps = rootProject.file("keystore.properties")
    val personalSigning = if (keystoreProps.exists()) {
        val props = Properties().apply { keystoreProps.inputStream().use { load(it) } }
        signingConfigs.create("personal") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        debug {
            personalSigning?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfig = personalSigning ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
}
