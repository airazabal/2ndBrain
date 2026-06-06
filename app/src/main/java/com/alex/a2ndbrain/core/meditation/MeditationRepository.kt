package com.alex.a2ndbrain.core.meditation

interface MeditationRepository {
    fun loadSessions(): List<MeditationSession>
    fun insertSession(session: MeditationSession)
}
