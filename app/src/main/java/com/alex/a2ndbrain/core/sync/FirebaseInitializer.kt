package com.alex.a2ndbrain.core.sync

import android.content.Context
import android.util.Log
import com.alex.a2ndbrain.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    fun init(context: Context) {
        if (BuildConfig.FIREBASE_APP_ID.isEmpty()) return
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        try {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build()
            FirebaseApp.initializeApp(context, options)
            Log.d("FirebaseInitializer", "Firebase initialized for project ${BuildConfig.FIREBASE_PROJECT_ID}")
        } catch (e: Exception) {
            Log.e("FirebaseInitializer", "Firebase init failed", e)
        }
    }
}
