package com.pjyama.imdbtomdblistunofficial

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storedApiKey = getApiKey()

        // Check if app was opened from a share or manually
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank() && storedApiKey != null) {
                handleSharedIntent(sharedText, storedApiKey)
                return
            }
        }

        // Open settings screen if manually opened
        setContent {
            ApiKeyScreen(storedApiKey) { newApiKey ->
                saveApiKey(newApiKey)
                showToast("✅ API Key Saved!")
                finish() // Close app after saving API key
            }
        }
    }

    @Composable
    fun ApiKeyScreen(existingApiKey: String?, onSave: (String) -> Unit) {
        var apiKey by remember { mutableStateOf(existingApiKey ?: "") }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Enter your MDBList API Key:")
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onSave(apiKey) }) {
                Text("Save API Key")
            }
        }
    }

    private fun handleSharedIntent(sharedText: String, apiKey: String) {
        val imdbId = extractImdbId(sharedText)
        if (imdbId != null) {
            sendToMDBList(imdbId, apiKey)
        } else {
            showToast("❌ Invalid IMDb URL")
        }
    }

    private fun extractImdbId(url: String): String? {
        val pattern = Pattern.compile("tt\\d+")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group() else null
    }

    private fun sendToMDBList(imdbId: String, apiKey: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.mdblist.com/watchlist/items/add?apikey=$apiKey"

                val payload = JSONObject().apply {
                    put("movies", JSONArray().put(JSONObject().put("imdb", imdbId)))
                    put("shows", JSONArray().put(JSONObject().put("imdb", imdbId)))
                }

                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray())
                    os.flush()
                }

                val responseCode = conn.responseCode
                val responseMessage = conn.inputStream.bufferedReader().use { it.readText() }

                launch(Dispatchers.Main) {
                    if (responseCode == 200) {
                        val jsonResponse = JSONObject(responseMessage)
                        val moviesAdded = jsonResponse.optJSONObject("added")?.optInt("movies", 0) ?: 0
                        val showsAdded = jsonResponse.optJSONObject("added")?.optInt("shows", 0) ?: 0

                        when {
                            moviesAdded > 0 && showsAdded > 0 -> showToast("✅ Added Movie & Show to Watchlist")
                            moviesAdded > 0 -> showToast("✅ Added Movie to Watchlist")
                            showsAdded > 0 -> showToast("✅ Added Show to Watchlist")
                            else -> { // Nothing was added, meaning it was already in MDBList
                                val moviesExisting = jsonResponse.optJSONObject("existing")?.optInt("movies", 0) ?: 0
                                val showsExisting = jsonResponse.optJSONObject("existing")?.optInt("shows", 0) ?: 0

                                when {
                                    moviesExisting > 0 && showsExisting > 0 -> showToast("ℹ️ Movie & Show were already in your Watchlist")
                                    moviesExisting > 0 -> showToast("ℹ️ Movie was already in your Watchlist")
                                    showsExisting > 0 -> showToast("ℹ️ Show was already in your Watchlist")
                                    else -> showToast("ℹ️ Already in MDBList") // Fallback case
                                }
                            }
                        }
                    } else {
                        showToast("❌ Check your API")
                    }
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    showToast("❌ Check your API")
                    finish()
                }
            }
        }
    }

    private fun getApiKey(): String? {
        val prefs: SharedPreferences = getSharedPreferences("MDBListPrefs", Context.MODE_PRIVATE)
        return prefs.getString("MDBListApiKey", null)
    }

    private fun saveApiKey(apiKey: String) {
        val prefs: SharedPreferences = getSharedPreferences("MDBListPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("MDBListApiKey", apiKey).apply()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
