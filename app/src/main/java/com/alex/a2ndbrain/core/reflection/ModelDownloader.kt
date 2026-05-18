package com.alex.a2ndbrain.core.reflection

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

class ModelDownloader(private val context: Context, private val scope: CoroutineScope) {

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadingModelName = MutableStateFlow<String?>(null)
    val downloadingModelName = _downloadingModelName.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError = _downloadError.asStateFlow()
    data class LiteRTModel(
        val name: String,
        val url: String,
        val description: String,
        val sizeLabel: String
    )

    companion object {
        private const val REGISTRY_URL = "https://raw.githubusercontent.com/alexirazabal/2ndBrain-Models/main/models.json"
        
        val DEFAULT_MODELS = listOf(
            LiteRTModel(
                name = "Gemma-3-1B-IT",
                url = "https://huggingface.co/lotapa/gemma3-1b-it-int4.litertlm/resolve/main/gemma3-1b-it-int4.litertlm?download=true",
                description = "Google Gemma 3 (1B). Extremely fast and smart on-device flagship.",
                sizeLabel = "584MB"
            ),
            LiteRTModel(
                name = "Qwen-3-0.6B",
                url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true",
                description = "Qwen 3 0.6B parameter model. Highly efficient and fast.",
                sizeLabel = "614MB"
            )
        )
    }

    suspend fun fetchAvailableModels(): List<LiteRTModel> = withContext(Dispatchers.IO) {
        return@withContext getRegisteredModels()
    }

    suspend fun fetchRemoteRegistry(): List<LiteRTModel> = withContext(Dispatchers.IO) {
        try {
            val connection = createConnection(REGISTRY_URL)
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = Scanner(connection.inputStream).useDelimiter("\\A").next()
                val models = parseModelsJson(jsonString)
                
                // Cache the successful result
                context.getSharedPreferences("model_cache", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_models_json", jsonString)
                    .apply()
                
                return@withContext models
            }
        } catch (e: Exception) {
            Log.e("ModelDownloader", "Failed to fetch remote models, checking cache", e)
        }

        // Fallback to cache
        val cachedJson = context.getSharedPreferences("model_cache", Context.MODE_PRIVATE)
            .getString("last_models_json", null)
            
        if (cachedJson != null) {
            try {
                return@withContext parseModelsJson(cachedJson)
            } catch (e: Exception) {
                Log.e("ModelDownloader", "Failed to parse cached models", e)
            }
        }

        return@withContext DEFAULT_MODELS
    }

    private fun parseModelsJson(json: String): List<LiteRTModel> {
        val array = JSONArray(json)
        val result = mutableListOf<LiteRTModel>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(LiteRTModel(
                name = obj.getString("name"),
                url = obj.getString("url"),
                description = obj.getString("description"),
                sizeLabel = obj.getString("sizeLabel")
            ))
        }
        return result
    }

    private fun createConnection(urlPath: String, existingSize: Long = 0L): HttpURLConnection {
        var currentUrl = urlPath
        var connection: HttpURLConnection
        var redirectCount = 0
        val maxRedirects = 5
        // Use a more common browser User-Agent to avoid being flagged as a bot
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

        while (true) {
            Log.d("ModelDownloader", "Connecting to: $currentUrl (Redirect count: $redirectCount)")
            val url = URL(currentUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            if (existingSize > 0) {
                connection.setRequestProperty("Range", "bytes=$existingSize-")
            }
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false // We handle it manually
            
            val status = connection.responseCode
            Log.d("ModelDownloader", "Response code: $status for $currentUrl")

            if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                status == HttpURLConnection.HTTP_MOVED_PERM || 
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                
                if (redirectCount >= maxRedirects) throw Exception("Too many redirects")
                
                val location = connection.getHeaderField("Location")
                if (location == null) throw Exception("Redirect missing Location header")
                
                // Handle relative URLs
                currentUrl = if (location.startsWith("http")) location 
                            else URL(url, location).toString()
                
                connection.disconnect()
                redirectCount++
                continue
            }
            break
        }
        return connection
    }

    fun startDownload(model: LiteRTModel) {
        scope.launch(Dispatchers.IO) {
            try {
                _downloadingModelName.value = model.name
                _downloadProgress.value = 0f
                _downloadError.value = null
                
                val finalModelFile = File(context.filesDir, "models/${model.name}.litertlm")
                val tmpModelFile = File(context.filesDir, "models/${model.name}.litertlm.tmp")
                val parentDir = finalModelFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs()
                }

                var existingSize = 0L
                if (tmpModelFile.exists()) {
                    existingSize = tmpModelFile.length()
                } else if (finalModelFile.exists()) {
                    // Already completely downloaded!
                    _downloadProgress.value = null
                    _downloadingModelName.value = null
                    return@launch
                }

                val connection = createConnection(model.url, existingSize)
                val isResuming = connection.responseCode == 206
                
                if (connection.responseCode != HttpURLConnection.HTTP_OK && connection.responseCode != 206) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }
                
                if (!isResuming && existingSize > 0) {
                    existingSize = 0L
                    tmpModelFile.delete()
                }

                val contentLength = connection.contentLengthLong
                val totalLength = if (contentLength > 0) existingSize + contentLength else -1L

                if (totalLength > 0 && existingSize == totalLength) {
                    _downloadProgress.value = null
                    _downloadingModelName.value = null
                    return@launch
                }

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(tmpModelFile, isResuming)

                val data = ByteArray(8192)
                var currentSize = existingSize
                var count: Int
                var lastUpdate = 0L
                
                while (inputStream.read(data).also { count = it } != -1) {
                    currentSize += count
                    outputStream.write(data, 0, count)
                    
                    val now = System.currentTimeMillis()
                    if (totalLength > 0 && now - lastUpdate > 100) {
                        _downloadProgress.value = currentSize.toFloat() / totalLength
                        lastUpdate = now
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                
                val finalSize = tmpModelFile.length()
                Log.d("ModelDownloader", "Download complete. Expected: $totalLength, Actual: $finalSize")
                
                if (totalLength > 0 && finalSize < totalLength) {
                    throw Exception("Download incomplete: $finalSize of $totalLength bytes received. Tap download to resume.")
                }
                
                // Success! Rename the temp file to the final model file.
                if (finalModelFile.exists()) {
                    finalModelFile.delete()
                }
                tmpModelFile.renameTo(finalModelFile)
                
                _downloadProgress.value = null
                _downloadingModelName.value = null
            } catch (e: Exception) {
                Log.e("ModelDownloader", "Download failed", e)
                _downloadError.value = e.message ?: "Unknown error"
                _downloadProgress.value = null
                _downloadingModelName.value = null
            }
        }
    }

    fun getRegisteredModels(): List<LiteRTModel> {
        val prefs = context.getSharedPreferences("custom_models", Context.MODE_PRIVATE)
        val json = prefs.getString("models_list", null)
        if (json == null) {
            // First boot: auto-populate with DEFAULT_MODELS!
            saveRegisteredModels(DEFAULT_MODELS)
            return DEFAULT_MODELS
        }
        return try {
            parseModelsJson(json)
        } catch (e: Exception) {
            DEFAULT_MODELS
        }
    }

    fun registerModel(model: LiteRTModel) {
        val current = getRegisteredModels().toMutableList()
        if (current.none { it.name == model.name }) {
            current.add(model)
            saveRegisteredModels(current)
        }
    }

    fun unregisterModel(name: String) {
        val current = getRegisteredModels().filter { it.name != name }
        saveRegisteredModels(current)
    }

    private fun saveRegisteredModels(models: List<LiteRTModel>) {
        val array = JSONArray()
        for (m in models) {
            val obj = JSONObject()
            obj.put("name", m.name)
            obj.put("url", m.url)
            obj.put("description", m.description)
            obj.put("sizeLabel", m.sizeLabel)
            array.put(obj)
        }
        context.getSharedPreferences("custom_models", Context.MODE_PRIVATE)
            .edit()
            .putString("models_list", array.toString())
            .apply()
    }

    fun getCustomModels(): List<LiteRTModel> = getRegisteredModels()
    fun addCustomModel(model: LiteRTModel) = registerModel(model)
    fun removeCustomModel(name: String) = unregisterModel(name)
}
