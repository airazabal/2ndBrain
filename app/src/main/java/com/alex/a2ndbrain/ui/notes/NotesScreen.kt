package com.alex.a2ndbrain.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesScreen(
    settingsManager: CaptureSettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var vaultUri by remember { mutableStateOf(settingsManager.getObsidianVaultUri()) }
    
    var currentFolder by remember { mutableStateOf<DocumentFile?>(null) }
    var folderStack by remember { mutableStateOf(listOf<DocumentFile>()) }
    var items by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var selectedNoteForPreview by remember { mutableStateOf<DocumentFile?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val uriString = it.toString()
            settingsManager.saveObsidianVaultUri(uriString)
            vaultUri = uriString
            currentFolder = null
            folderStack = emptyList()
        }
    }

    LaunchedEffect(vaultUri, currentFolder) {
        if (vaultUri.isNotBlank()) {
            val folderToScan = currentFolder ?: DocumentFile.fromTreeUri(context, android.net.Uri.parse(vaultUri))
            items = folderToScan?.listFiles()
                ?.filter { it.isDirectory || it.name?.endsWith(".md") == true }
                ?.sortedWith(compareBy<DocumentFile> { it.isDirectory }
                    .thenByDescending { if (it.isDirectory) 0L else it.lastModified() }
                    .thenBy { it.name?.lowercase() }
                ) ?: emptyList()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Item
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (folderStack.isNotEmpty()) {
                            IconButton(onClick = {
                                val newStack = folderStack.dropLast(1)
                                currentFolder = newStack.lastOrNull()
                                folderStack = newStack
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        
                        if (folderStack.isNotEmpty()) {
                            Text(
                                text = folderStack.last().name ?: "Notes",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Notebook",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (vaultUri.isNotBlank()) {
                        IconButton(onClick = { launcher.launch(null) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Change Vault")
                        }
                    }
                }
            }

            if (vaultUri.isBlank()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connect your Obsidian Vault")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { launcher.launch(null) }, shape = RoundedCornerShape(12.dp)) {
                                Text("Select Folder")
                            }
                        }
                    }
                }
            } else {
                if (items.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("This folder is empty.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    items(items) { item ->
                        NoteOrFolderItem(
                            item = item,
                            onFolderClick = { folder ->
                                folderStack = folderStack + folder
                                currentFolder = folder
                            },
                            onFileClick = { file ->
                                selectedNoteForPreview = file
                            }
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp)) // Extra space for FAB
            }
        }

        // Preview Dialog
        selectedNoteForPreview?.let { file ->
            NotePreviewDialog(
                file = file,
                onDismiss = { selectedNoteForPreview = null },
                onEditInObsidian = {
                    openInObsidian(context, file, vaultUri)
                    selectedNoteForPreview = null
                }
            )
        }

        if (vaultUri.isNotBlank()) {
            FloatingActionButton(
                onClick = {
                    createNewNoteInObsidian(context, vaultUri)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Note")
            }
        }
    }
}

@Composable
fun NotePreviewDialog(
    file: DocumentFile,
    onDismiss: () -> Unit,
    onEditInObsidian: () -> Unit
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("Loading...") }

    LaunchedEffect(file) {
        content = try {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: "Could not read file."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = file.name?.removeSuffix(".md") ?: "Note Preview",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onEditInObsidian, shape = RoundedCornerShape(12.dp)) {
                Text("Edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun NoteOrFolderItem(
    item: DocumentFile,
    onFolderClick: (DocumentFile) -> Unit,
    onFileClick: (DocumentFile) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (item.isDirectory) onFolderClick(item) else onFileClick(item)
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item.isDirectory) PastelGreen else PastelBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (item.isDirectory) PastelGreenText else PastelBlueText
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = if (item.isDirectory) item.name ?: "Folder" else item.name?.removeSuffix(".md") ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!item.isDirectory) {
                    Text(
                        text = "Modified ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(item.lastModified()))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private fun openInObsidian(context: android.content.Context, file: DocumentFile, vaultUri: String) {
    val root = DocumentFile.fromTreeUri(context, android.net.Uri.parse(vaultUri)) ?: return
    val vaultName = root.name ?: ""
    
    val relativePath = getRelativePath(file, vaultUri)
    val fileNameWithoutExt = relativePath.removeSuffix(".md")
    
    val obsidianUri = android.net.Uri.parse("obsidian://open?vault=${android.net.Uri.encode(vaultName)}&file=${android.net.Uri.encode(fileNameWithoutExt)}")
    
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, obsidianUri)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
    }
}

private fun getRelativePath(file: DocumentFile, vaultUri: String): String {
    val fullUri = file.uri.toString()
    val decodedVaultUri = android.net.Uri.decode(vaultUri)
    val decodedFileUri = android.net.Uri.decode(fullUri)
    
    val vaultPathSegment = decodedVaultUri.substringAfterLast(":")
    val filePathSegment = decodedFileUri.substringAfterLast(":")
    
    return if (filePathSegment.startsWith(vaultPathSegment)) {
        filePathSegment.removePrefix(vaultPathSegment).removePrefix("/")
    } else {
        file.name ?: ""
    }
}

private fun createNewNoteInObsidian(context: android.content.Context, vaultUri: String) {
    val root = DocumentFile.fromTreeUri(context, android.net.Uri.parse(vaultUri)) ?: return
    val vaultName = root.name ?: ""
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
    val newNoteName = "2ndBrain-$timestamp"
    
    val obsidianUri = android.net.Uri.parse("obsidian://new?vault=${android.net.Uri.encode(vaultName)}&name=${android.net.Uri.encode(newNoteName)}")
    
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, obsidianUri)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
    }
}
