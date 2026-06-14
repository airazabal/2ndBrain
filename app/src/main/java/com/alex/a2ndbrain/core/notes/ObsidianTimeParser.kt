package com.alex.a2ndbrain.core.notes

object ObsidianTimeParser {

    /**
     * Extracts the first time reference from a markdown line.
     * Supports H:MM [AM/PM], H AM/PM, and 4-digit military time with at/@ prefix.
     * Returns (display string, minutes from midnight) or null if no time found.
     */
    fun findTimePattern(line: String): Pair<String, Int>? {
        val regex1 = Regex("(?:\\b|@|at\\s+)(\\d{1,2}):(\\d{2})\\s*(AM|PM|am|pm)?\\b")
        val match1 = regex1.find(line)
        if (match1 != null) {
            val hourStr = match1.groupValues[1]; val minStr = match1.groupValues[2]; val ampm = match1.groupValues[3]
            var hour = hourStr.toIntOrNull() ?: return null
            val min = minStr.toIntOrNull() ?: return null
            if (ampm.isNotBlank()) {
                val suffix = ampm.uppercase()
                if (suffix == "PM" && hour < 12) hour += 12
                if (suffix == "AM" && hour == 12) hour = 0
            } else { if (hour >= 24) return null }
            val totalMinutes = hour * 60 + min
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val displayAmpm = if (hour >= 12) "PM" else "AM"
            return Pair("$displayHour:${min.toString().padStart(2, '0')} $displayAmpm", totalMinutes)
        }

        val regex2 = Regex("(?:\\b|@|at\\s+)(\\d{1,2})\\s*(AM|PM|am|pm)\\b")
        val match2 = regex2.find(line)
        if (match2 != null) {
            val hourStr = match2.groupValues[1]; val ampm = match2.groupValues[2]
            var hour = hourStr.toIntOrNull() ?: return null
            if (hour > 12) return null
            val suffix = ampm.uppercase()
            val totalMinutes = when {
                suffix == "PM" && hour < 12 -> (hour + 12) * 60
                suffix == "AM" && hour == 12 -> 0
                else -> hour * 60
            }
            return Pair("$hour:00 $suffix", totalMinutes)
        }

        val regex3 = Regex("(?:@|at\\s+)([01]\\d|2[0-3])([0-5]\\d)\\b")
        val match3 = regex3.find(line)
        if (match3 != null) {
            val hour = match3.groupValues[1].toIntOrNull() ?: return null
            val min = match3.groupValues[2].toIntOrNull() ?: return null
            val totalMinutes = hour * 60 + min
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val displayAmpm = if (hour >= 12) "PM" else "AM"
            return Pair("$displayHour:${min.toString().padStart(2, '0')} $displayAmpm", totalMinutes)
        }

        return null
    }

    /** Strips markdown list syntax, time tokens, and at/@ keywords from a note line. */
    fun cleanAgendaLine(line: String, timeStr: String): String {
        var cleaned = line
            .replace(Regex("^\\s*[-*+]\\s*(\\[[ xX]])?\\s*"), "")
            .replace(Regex("(?:@|at\\s+)?\\b\\d{1,2}:\\d{2}\\s*(?:AM|PM|am|pm)?\\b"), "")
            .replace(Regex("(?:@|at\\s+)?\\b\\d{1,2}\\s*(?:AM|PM|am|pm)\\b"), "")
            .replace(Regex("(?:@|at\\s+)(?:[01]\\d|2[0-3])[0-5]\\d\\b"), "")
            .replace(Regex("\\b(?:at|@)\\b"), "")
            .trim()
        if (cleaned.isBlank()) {
            val rawSnippet = line.replace(Regex("^\\s*[-*+]\\s*(\\[[ xX]])?\\s*"), "").trim()
            cleaned = if (rawSnippet.isNotEmpty() && rawSnippet != timeStr)
                rawSnippet.take(25) + if (rawSnippet.length > 25) "..." else ""
            else "Scheduled Time Block"
        }
        return cleaned
    }
}
