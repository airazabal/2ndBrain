package com.alex.a2ndbrain.core.sync

import android.content.Context
import android.util.Log
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.health.HealthSnapshotEntity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Firestore cloud sync for health snapshots.
 *
 * Phone (has HC + wearable): pushes today's snapshot to Firestore every 30 min.
 * Tablet (no wearable): pulls latest snapshot from Firestore when P2P hasn't synced recently.
 *
 * Authentication: silent Google Sign-In → Firebase Auth. Both devices share the same
 * Firebase UID because they are signed into the same Google account.
 *
 * Requires app/google-services.json (symlinked from ~/DriveSyncFiles/2ndBrain/).
 * See ~/DriveSyncFiles/2ndBrain/README.md for setup steps.
 * No-ops silently when Firebase is not configured or sign-in fails.
 */
class CloudHealthSyncManager(
    private val context: Context,
    internal val healthRepository: HealthRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val isFirebaseAvailable: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Silent Google Sign-In → Firebase Auth. Both devices must be signed into the
     * same Google account for their UIDs to match.
     */
    suspend fun ensureSignedIn(): Boolean = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable) return@withContext false
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) return@withContext true
        try {
            val webClientId = BuildConfig.FIREBASE_WEB_CLIENT_ID
                .takeIf { it.isNotEmpty() } ?: return@withContext false
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            val account = suspendCancellableCoroutine<com.google.android.gms.auth.api.signin.GoogleSignInAccount?> { cont ->
                GoogleSignIn.getClient(context, gso).silentSignIn()
                    .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
                    .addOnFailureListener { e ->
                        Log.w("CloudHealthSync", "Silent sign-in failed: ${e.message}")
                        cont.resumeWith(Result.success(null))
                    }
            } ?: run {
                Log.w("CloudHealthSync", "Silent sign-in returned null account — no Google account or not previously authorized")
                return@withContext false
            }
            val idToken = account.idToken ?: return@withContext false
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            Log.d("CloudHealthSync", "Signed in as ${auth.currentUser?.uid}")
            true
        } catch (e: Exception) {
            Log.w("CloudHealthSync", "Sign-in failed", e)
            false
        }
    }

    /**
     * Pushes today's snapshot to Firestore. Called on the phone (device with wearable).
     */
    suspend fun pushTodaySnapshot() = withContext(Dispatchers.IO) {
        if (!ensureSignedIn()) return@withContext
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext
        try {
            val snapshots = healthRepository.getSnapshotsForSync(1)
            val snap = snapshots.firstOrNull() ?: run {
                Log.w("CloudHealthSync", "pushTodaySnapshot: no local snapshot found — skipping")
                return@withContext
            }
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("health_snapshots").document(snap.date)
                .set(snap.toMap())
                .await()
            Log.d("CloudHealthSync", "Pushed snapshot for ${snap.date}: sleep=${snap.sleepMinutes}min")
        } catch (e: Exception) {
            Log.e("CloudHealthSync", "pushTodaySnapshot failed", e)
        }
    }

    /**
     * Pulls today's snapshot from Firestore. Returns null if not found or sign-in fails.
     */
    suspend fun pullTodaySnapshot(): HealthSnapshotEntity? = withContext(Dispatchers.IO) {
        if (!ensureSignedIn()) return@withContext null
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
        val today = dateFormat.format(Date())
        try {
            val doc = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("health_snapshots").document(today)
                .get().await()
            if (!doc.exists()) return@withContext null
            HealthSnapshotEntity(
                date         = doc.getString("date") ?: today,
                deviceId     = doc.getString("deviceId") ?: "cloud",
                steps        = doc.getLong("steps") ?: 0L,
                sleepMinutes = (doc.getLong("sleepMinutes") ?: 0L).toInt(),
                minHeartRate = (doc.getLong("minHeartRate") ?: 0L).toInt(),
                maxHeartRate = (doc.getLong("maxHeartRate") ?: 0L).toInt(),
                avgHeartRate = (doc.getLong("avgHeartRate") ?: 0L).toInt(),
                lastTimestamp = doc.getLong("lastTimestamp") ?: System.currentTimeMillis()
            ).also { Log.d("CloudHealthSync", "Pulled snapshot for $today: sleep=${it.sleepMinutes}min") }
        } catch (e: Exception) {
            Log.e("CloudHealthSync", "pullTodaySnapshot failed", e)
            null
        }
    }

    /** Returns true when a snapshot is missing or older than 4 hours. */
    fun isStale(snapshot: HealthSnapshotEntity?): Boolean {
        if (snapshot == null) return true
        return System.currentTimeMillis() - snapshot.lastTimestamp > 4 * 60 * 60 * 1000L
    }
}

private fun HealthSnapshotEntity.toMap(): Map<String, Any> = mapOf(
    "date"          to date,
    "deviceId"      to deviceId,
    "steps"         to steps,
    "sleepMinutes"  to sleepMinutes,
    "minHeartRate"  to minHeartRate,
    "maxHeartRate"  to maxHeartRate,
    "avgHeartRate"  to avgHeartRate,
    "lastTimestamp" to lastTimestamp
)
