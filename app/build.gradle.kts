import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val versionPropsFile = file("version.properties")
val versionProps = Properties()

if (!versionPropsFile.exists()) {
    versionProps["VERSION_CODE"] = "2"
    versionProps["VERSION_NAME"] = "1.0.1"
    versionProps.store(FileOutputStream(versionPropsFile), null)
}

versionProps.load(FileInputStream(versionPropsFile))

val currentVersionCode = versionProps["VERSION_CODE"].toString().toInt()
val nextVersionCode = currentVersionCode + 1
val nextVersionName = "1.0.$nextVersionCode"

// Update the file for the next build
versionProps["VERSION_CODE"] = nextVersionCode.toString()
versionProps["VERSION_NAME"] = nextVersionName
versionProps.store(FileOutputStream(versionPropsFile), null)
dependencies {
    implementation(libs.litert)
    implementation(libs.litertlm)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Gemini AI
    implementation(libs.google.generativeai)
    implementation(libs.kotlin.reflect.artifact)
    implementation(libs.kotlin.stdlib.artifact)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
android {
    namespace = "com.alex.a2ndbrain"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alex.a2ndbrain"
        minSdk = 26
        targetSdk = 36
        versionCode = nextVersionCode
        versionName = nextVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        val geminiModel = project.findProperty("gemini.model")?.toString() ?: "gemini-1.5-flash"
        buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")
        
        val geminiKey = project.findProperty("gemini.api.key")?.toString() ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // For AGP 9.0+, kotlin configuration is built-in
    //kotlin {
      //  compilerOptions {
        //    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        //}
    //}
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            pickFirsts += "**/libLiteRt*.so"
        }
        resources {
            pickFirsts += "**/libLiteRt*.so"
        }
    }
}

