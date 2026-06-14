package com.alex.a2ndbrain.core.notes

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VaultRepositoryImpl(private val context: Context) : VaultRepository {

    override suspend fun writeVoiceNote(transcript: String, vaultUri: String): Boolean {
        if (vaultUri.isEmpty()) return false
        return try {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(vaultUri))
            Log.d("VaultRepository", "root=$root exists=${root?.exists()} canWrite=${root?.canWrite()}")
            if (root == null || !root.exists() || !root.canWrite()) return false

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
            val newNoteName = "VoiceNote-$timestamp.md"
            val markdownDocFile = root.createFile("text/markdown", newNoteName) ?: return false
            Log.d("VaultRepository", "Created file: ${markdownDocFile.name}")

            val stream = context.contentResolver.openOutputStream(markdownDocFile.uri) ?: return false
            stream.use {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val dateIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                val content = buildString {
                    appendLine("---")
                    appendLine("created: $dateIso")
                    appendLine("tags:")
                    appendLine("  - voice-capture")
                    appendLine("---")
                    appendLine("# Voice Note")
                    appendLine("- **Captured**: $dateStr")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(transcript)
                }
                it.write(content.toByteArray())
                Log.d("VaultRepository", "Written ${content.length} bytes to $newNoteName")
            }
            true
        } catch (e: Exception) {
            Log.e("VaultRepository", "Failed to write voice note to vault", e)
            false
        }
    }
    override suspend fun listMarkdownNotes(vaultUri: String): List<VaultNote> {
        if (vaultUri.isEmpty()) return emptyList()
        return try {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(vaultUri))
            root?.listFiles()
                ?.filter { it.isFile && it.name?.endsWith(".md") == true }
                ?.sortedByDescending { it.lastModified() }
                ?.map { VaultNote(name = it.name ?: "", uri = it.uri.toString(), lastModified = it.lastModified()) }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e("VaultRepository", "Failed to list vault notes", e)
            emptyList()
        }
    }

    override suspend fun readNoteLines(noteUri: String): List<String> {
        return try {
            val stream = context.contentResolver.openInputStream(Uri.parse(noteUri)) ?: return emptyList()
            stream.use { BufferedReader(InputStreamReader(it)).readLines() }
        } catch (e: Exception) {
            Log.e("VaultRepository", "Failed to read note: $noteUri", e)
            emptyList()
        }
    }
}