import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Read Firebase config from google-services.json (not committed to git).
// Symlink: ln -sf ~/DriveSyncFiles/2ndBrain/google-services.json app/google-services.json
data class FirebaseConfig(
    val appId: String = "",
    val apiKey: String = "",
    val projectId: String = "",
    val webClientId: String = ""
)
val firebaseConfig: FirebaseConfig = run {
    val f = file("google-services.json")
    if (!f.exists()) return@run FirebaseConfig()
    try {
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parseText(f.readText()) as Map<String, Any>
        val projectInfo = json["project_info"] as Map<String, Any>
        val clients = json["client"] as List<Map<String, Any>>
        val client = clients.first()
        val clientInfo = client["client_info"] as Map<String, Any>
        val apiKeys = client["api_key"] as List<Map<String, Any>>
        val oauthClients = (client["oauth_client"] as? List<Map<String, Any>>) ?: emptyList()
        val appId = clientInfo["mobilesdk_app_id"] as String
        val apiKey = (apiKeys.firstOrNull()?.get("current_key") as? String) ?: ""
        val projectId = projectInfo["project_id"] as String
        val webClientId = oauthClients.firstOrNull { (it["client_type"] as? Int) == 3 }
            ?.get("client_id") as? String ?: ""
        FirebaseConfig(appId, apiKey, projectId, webClientId)
    } catch (e: Exception) {
        println("Warning: could not parse google-services.json — Firebase will be disabled: $e")
        FirebaseConfig()
    }
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
    implementation(libs.androidx.security.crypto)
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
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Health Connect
    implementation(libs.androidx.health.connect)

    // Nearby Connections
    implementation(libs.google.play.services.nearby)

    // Gemini AI
    implementation(libs.google.generativeai)
    implementation(libs.kotlin.reflect.artifact)
    implementation(libs.kotlin.stdlib.artifact)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Firebase (requires app/google-services.json — see ~/DriveSyncFiles/2ndBrain/README.md)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Glance (home screen widget)
    implementation(libs.androidx.glance.appwidget)

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
        buildConfigField("String", "FIREBASE_APP_ID",      "\"${firebaseConfig.appId}\"")
        buildConfigField("String", "FIREBASE_API_KEY",     "\"${firebaseConfig.apiKey}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID",  "\"${firebaseConfig.projectId}\"")
        buildConfigField("String", "FIREBASE_WEB_CLIENT_ID", "\"${firebaseConfig.webClientId}\"")
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
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
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

