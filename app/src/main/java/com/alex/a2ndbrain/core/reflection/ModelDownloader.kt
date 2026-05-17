package com.alex.a2ndbrain.core.reflection

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

class ModelDownloader(private val context: Context) {
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
                name = "Gemma-4-E2B-it",
                url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
                description = "Google's newest generation (2.5B). Optimized for 0.11.0 Engine.",
                sizeLabel = "2.6GB"
            ),
            LiteRTModel(
                name = "Phi-4-mini-it",
                url = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                description = "Microsoft Compact (3.8B). High intelligence, verified structural bundle.",
                sizeLabel = "2.4GB"
            ),
            LiteRTModel(
                name = "DeepSeek-R1-Qwen-1.5B",
                url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                description = "DeepSeek Reasoning model. Verified multi-prefill bundle.",
                sizeLabel = "1.8GB"
            )
        )
    }

    suspend fun fetchAvailableModels(): List<LiteRTModel> = withContext(Dispatchers.IO) {
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

    private fun createConnection(urlPath: String): HttpURLConnection {
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

    fun downloadModel(model: LiteRTModel): Flow<DownloadStatus> = flow {
        try {
            emit(DownloadStatus.Starting)
            val modelFile = File(context.filesDir, "models/${model.name}.litertlm")
            val parentDir = modelFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            val connection = createConnection(model.url)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode}")
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(modelFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            var lastUpdate = 0L
            
            while (inputStream.read(data).also { count = it } != -1) {
                total += count
                outputStream.write(data, 0, count)
                
                val now = System.currentTimeMillis()
                if (fileLength > 0 && now - lastUpdate > 100) { // Throttle updates to 10Hz
                    emit(DownloadStatus.Progress(total.toFloat() / fileLength))
                    lastUpdate = now
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            // Verify file size
            val actualSize = modelFile.length()
            Log.d("ModelDownloader", "Download complete. Expected: $fileLength, Actual: $actualSize")
            
            if (fileLength > 0 && actualSize < fileLength) {
                modelFile.delete()
                throw Exception("Download incomplete: $actualSize of $fileLength bytes received")
            }
            
            emit(DownloadStatus.Success)
        } catch (e: Exception) {
            Log.e("ModelDownloader", "Download failed", e)
            emit(DownloadStatus.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    sealed class DownloadStatus {
        object Starting : DownloadStatus()
        data class Progress(val progress: Float) : DownloadStatus()
        object Success : DownloadStatus()
        data class Error(val message: String) : DownloadStatus()
    }
}
