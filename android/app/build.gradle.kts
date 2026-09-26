import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.husarp.browsertwins"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.husarp.browsertwins"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.2.2"
    }

    // The release key lives outside the project (never published): its file and passwords are in
    // ~/.keystores/browsertwins-signing.properties. Without it - someone else building this - the
    // release build is signed with that PC's debug key instead.
    val signing = Properties().apply {
        val f = File(System.getProperty("user.home"), ".keystores/browsertwins-signing.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")

    // The clone engine. ARSCLib rewrites an APK's package name in the binary manifest and resource
    // table (the same ApkModule.setPackageName proven on the PC); apksig re-signs the result with our
    // own key. Both are pure Java, so they run on the phone.
    implementation("io.github.reandroid:ARSCLib:1.3.8")
    implementation("com.android.tools.build:apksig:8.6.1")
    // Makes the self-signed certificate for our signing key (Android has no built-in X.509 builder).
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    testImplementation("junit:junit:4.13.2")
}
