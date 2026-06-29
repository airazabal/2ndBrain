package com.zepp.sdk

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

// Firestore-backed relay — writes sync payloads to Firestore.
// The Zepp OS side-service/index.js polls Firestore and forwards to the watch
// via messageBuilder.call(), which app.js receives via messageBuilder.on('call').
class WatchMessageHelper private constructor() {

    companion object {
        private const val TAG = "WatchMessageHelper"
        private const val COLLECTION = "watch_sync"
        private const val DOC_ID = "current"

        @Volatile private var instance: WatchMessageHelper? = null

        @JvmStatic
        fun getInstance(context: Context): WatchMessageHelper =
            instance ?: synchronized(this) {
                instance ?: WatchMessageHelper().also { instance = it }
            }
    }

    fun connect() {
        Log.d(TAG, "connect() — Firestore relay ready")
    }

    fun sendMessage(data: ByteArray) {
        val payload = data.decodeToString()
        // Route SYNC_HABITS to a separate doc so habit taps don't overwrite stats/briefing
        val docId = when {
            payload.contains("\"SYNC_HABITS\"")   -> "habits"
            payload.contains("\"SYNC_BRIEFING\"") -> "briefing"
            else -> DOC_ID
        }
        FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(docId)
            .set(mapOf("payload" to payload, "ts" to System.currentTimeMillis()))
            .addOnSuccessListener { Log.d(TAG, "Watch sync written to Firestore ($docId)") }
            .addOnFailureListener { e -> Log.e(TAG, "Firestore write failed: ${e.message}") }
    }
}
