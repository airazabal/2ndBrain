package com.alex.a2ndbrain.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager

@Composable
fun NotesScreen(
    settingsManager: CaptureSettingsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var vaultUri by remember { mutableStateOf(settingsManager.getObsidianVaultUri()) }
    
    // Navigation state
    var currentFolder by remember { mutableStateOf<DocumentFile?>(null) }
    var folderStack by remember { mutableStateOf(listOf<DocumentFile>()) }
    var items by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val uriString = it.toString()
            settingsManager.saveObsidianVaultUri(uriString)
            vaultUri = uriString
            // Reset navigation when vault changes
            currentFolder = null
            folderStack = emptyList()
        }
    }

    // Load items whenever vault or current folder changes
    LaunchedEffect(vaultUri, currentFolder) {
        if (vaultUri.isNotBlank()) {
            val folderToScan = currentFolder ?: DocumentFile.fromTreeUri(context, android.net.Uri.parse(vaultUri))
            items = folderToScan?.listFiles()
                ?.filter { it.isDirectory || it.name?.endsWith(".md") == true }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() })) ?: emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (folderStack.isNotEmpty()) {
                    IconButton(onClick = {
                        val newStack = folderStack.dropLast(1)
                        currentFolder = newStack.lastOrNull() // null means root
                        folderStack = newStack
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                
                Column {
                    Text(
                        text = if (folderStack.isEmpty()) "Notes" else folderStack.last().name ?: "Notes",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (folderStack.isEmpty()) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            
            if (vaultUri.isNotBlank()) {
                IconButton(onClick = { launcher.launch(null) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Change Vault")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (vaultUri.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connect your Obsidian Vault to see your notes.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { launcher.launch(null) }) {
                        Text("Select Vault Folder")
                    }
                }
            }
        } else {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This folder is empty.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items) { item ->
                        NoteOrFolderItem(
                            item = item,
                            onFolderClick = { folder ->
                                folderStack = folderStack + folder
                                currentFolder = folder
                            },
                            onFileClick = { file ->
                                openInObsidian(context, file, vaultUri)
                            }
                        )
                    }
                }
            }
        }
    }
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
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDirectory) 
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (item.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (item.isDirectory) item.name ?: "Folder" else item.name?.removeSuffix(".md") ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (item.isDirectory) FontWeight.Bold else FontWeight.Normal
                )
                if (!item.isDirectory) {
                    Text(
                        text = "Modified: ${java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.lastModified()))}",
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
    
    // Construct the relative path from vault root
    val relativePath = getRelativePath(context, file, vaultUri)
    val fileNameWithoutExt = relativePath.removeSuffix(".md")
    
    val obsidianUri = android.net.Uri.parse("obsidian://open?vault=${android.net.Uri.encode(vaultName)}&file=${android.net.Uri.encode(fileNameWithoutExt)}")
    
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, obsidianUri)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        // Obsidian not installed
    }
}

private fun getRelativePath(context: android.content.Context, file: DocumentFile, vaultUri: String): String {
    // Simplified logic: the DocumentFile API doesn't make relative paths easy.
    // We'll extract it from the Document URI if possible, or just use the name for now.
    // A robust version would crawl up 'parent' but SAF DocumentFile parent is often null.
    // For now, let's use the file name. If it's deep, Obsidian might need the full path.
    
    val fullUri = file.uri.toString()
    val decodedVaultUri = android.net.Uri.decode(vaultUri)
    val decodedFileUri = android.net.Uri.decode(fullUri)
    
    // SAF URIs look like ...tree/primary:Documents/Vault%2FSubfolder%2FNote.md
    // We can try to extract everything after the vault root identifier
    val vaultPathSegment = decodedVaultUri.substringAfterLast(":")
    val filePathSegment = decodedFileUri.substringAfterLast(":")
    
    return if (filePathSegment.startsWith(vaultPathSegment)) {
        filePathSegment.removePrefix(vaultPathSegment).removePrefix("/")
    } else {
        file.name ?: ""
    }
}
