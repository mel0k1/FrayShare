import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ksPropsFile = rootProject.file("keystore.properties")
val ksProps = Properties()
if (ksPropsFile.exists()) {
    ksPropsFile.inputStream().use { ksProps.load(it) }
}
val ksPath = System.getenv("FRAY_KEYSTORE") ?: ksProps.getProperty("storePath")
val ksStorePass = System.getenv("FRAY_KEYSTORE_PASSWORD") ?: ksProps.getProperty("storePassword")
val ksAlias = System.getenv("FRAY_KEY_ALIAS") ?: ksProps.getProperty("keyAlias")
val ksKeyPass = System.getenv("FRAY_KEY_PASSWORD") ?: ksProps.getProperty("keyPassword")
val canSign = ksPath != null && ksStorePass != null && ksAlias != null && ksKeyPass != null

android {
    namespace = "com.frayshare.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.frayshare.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.3.1"
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = file(ksPath as String)
                storePassword = ksStorePass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            if (canSign) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.compose.material3:material3:1.5.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    testImplementation("junit:junit:4.13.2")
}
