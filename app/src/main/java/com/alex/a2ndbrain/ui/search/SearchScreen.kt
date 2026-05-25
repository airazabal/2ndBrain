package com.alex.a2ndbrain.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.HabitEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onMemorySelected: (MemoryEntity) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                keyboard?.hide()
                onBack()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            TextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search everything…") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
            if (query.isNotEmpty()) {
                TextButton(onClick = { viewModel.setQuery("") }) { Text("Clear") }
            }
        }

        HorizontalDivider()

        when {
            query.length < 2 -> SearchSuggestions(onSuggestion = viewModel::setQuery)
            results.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            results.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results for \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> SearchResultsList(query = query, results = results, onMemorySelected = { onMemorySelected(it) })
        }
    }
}

@Composable
private fun SearchSuggestions(onSuggestion: (String) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Search emails, messages, and everything else captured today",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// Gmail inbox bundles store multiple subjects as newline-separated content.
// Expand them so each subject is its own tappable row that opens a targeted Gmail search.
private fun expandGmailBundles(memories: List<MemoryEntity>, query: String): List<Pair<String, MemoryEntity>> =
    memories.flatMap { memory ->
        if (memory.packageName == "com.google.android.gm") {
            val lines = memory.content.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size > 1) {
                val matching = if (query.isBlank()) lines else lines.filter { it.contains(query, ignoreCase = true) }
                val toShow = if (matching.isEmpty()) lines.take(3) else matching
                toShow.mapIndexed { i, subject ->
                    "${memory.id}_$i" to memory.copy(title = subject, content = subject)
                }
            } else {
                listOf("${memory.id}" to memory)
            }
        } else {
            listOf("${memory.id}" to memory)
        }
    }

@Composable
private fun SearchResultsList(
    query: String,
    results: SearchResults,
    onMemorySelected: (MemoryEntity) -> Unit
) {
    val flatMemories = remember(results.memories, query) {
        expandGmailBundles(results.memories, query)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        if (flatMemories.isNotEmpty()) {
            item {
                SectionHeader("Captures (${flatMemories.size})")
            }
            items(flatMemories, key = { it.first }) { (_, memory) ->
                MemoryResultItem(memory = memory, query = query, onClick = { onMemorySelected(memory) })
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            }
        }

        if (results.summaries.isNotEmpty()) {
            item { SectionHeader("Reflections (${results.summaries.size})") }
            items(results.summaries, key = { it.id }) { summary ->
                SummaryResultItem(summary = summary, query = query)
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            }
        }

        if (results.habits.isNotEmpty()) {
            item { SectionHeader("Habits (${results.habits.size})") }
            items(results.habits, key = { it.id }) { habit ->
                HabitResultItem(habit = habit, query = query)
                HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun MemoryResultItem(memory: MemoryEntity, query: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (!memory.title.isNullOrBlank()) {
            Text(
                text = highlightText(memory.title, query),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = highlightText(contentSnippet(memory.content, query), query),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
        Text(
            formatTimestamp(memory.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SummaryResultItem(summary: DailySummaryEntity, query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                summary.type.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                summary.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = highlightText(summary.summary.take(150), query),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
    }
}

@Composable
private fun HabitResultItem(habit: HabitEntity, query: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = highlightText(habit.name, query),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun contentSnippet(content: String, query: String, window: Int = 120): String {
    if (query.isEmpty()) return content.take(window)
    // For multi-line content (inbox bundles, chat threads), return only the matching lines
    val lines = content.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size > 1) {
        val matchingLines = lines.filter { it.contains(query, ignoreCase = true) }
        if (matchingLines.isNotEmpty()) return matchingLines.take(3).joinToString("\n")
    }
    // Single-line / paragraph: return a window centred on the first match
    val idx = content.lowercase().indexOf(query.lowercase())
    if (idx == -1) return content.take(window)
    val start = (idx - 40).coerceAtLeast(0)
    val end = (start + window).coerceAtMost(content.length)
    return buildString {
        if (start > 0) append("…")
        append(content.substring(start, end))
        if (end < content.length) append("…")
    }
}

@Composable
private fun highlightText(text: String, query: String) = buildAnnotatedString {
    if (query.isEmpty()) { append(text); return@buildAnnotatedString }
    var start = 0
    val lower = text.lowercase()
    val lowerQuery = query.lowercase()
    while (true) {
        val idx = lower.indexOf(lowerQuery, start)
        if (idx == -1) { append(text.substring(start)); break }
        append(text.substring(start, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFA726))) {
            append(text.substring(idx, idx + query.length))
        }
        start = idx + query.length
    }
}

private fun formatTimestamp(ms: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
