import java.util.Properties

// Release signing credentials come from keystore.properties at the repo root (gitignored),
// same setup as PingDrop. Without the file the project still builds; release falls back
// to the debug key so a fresh clone works, but Play only accepts the upload-key build.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

plugins {
    id("com.android.application")
}

android {
    namespace = "com.chromaflow.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.neerajreddy.chromaflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        create("upload") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePropsFile.exists()) signingConfigs.getByName("upload")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
