package com.alex.a2ndbrain.core.notes

interface VaultRepository {
    suspend fun writeVoiceNote(transcript: String, vaultUri: String): Boolean
    suspend fun listMarkdownNotes(vaultUri: String): List<VaultNote>
    suspend fun readNoteLines(noteUri: String): List<String>
}
